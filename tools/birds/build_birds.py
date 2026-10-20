"""Builds the birds' code and names from the bird table (src/main/resources/eternalreturn/birds.json, which the game also
reads) and the shapes' models (tools/models/bird_<model>.bbmodel). The textures are painted by paint_birds.py.

Run from anywhere: python tools/birds/build_birds.py

Writes:
- src/client/java/com/eternalreturn/client/BirdModels.java: each model's parts (written from its Blockbench model; an
  owl's ear tufts and a woodpecker's crest are parts of their own, so the renderer can hide them) and where its head,
  legs, wings and tufts are;
- the language lines: the bird, each shape's name and each preset's name (other lines in en_us.json are kept);
- docs/birds.md, the table of the palette, shapes and presets.
Do not hand-edit the generated files.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
NS = 'eternalreturn'
TABLE = json.load(open(os.path.join(RES, NS, 'birds.json'), encoding='utf-8'))
MODELS = sorted({shape['model'] for shape in TABLE['shapes']}, key=[s['model'] for s in TABLE['shapes']].index)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(text)


def num(v):
    v = round(v, 4)
    return f'{v:.1f}F' if v == int(v) else f'{v}F'


def deg(v):
    return '0.0F' if not v else f'{num(v)} * MathHelper.RADIANS_PER_DEGREE'


def transform(pivot, rotation):
    """ModelTransform for a pivot (Minecraft units) and a Blockbench rotation (x and y flip in Minecraft's space)."""
    rx, ry, rz = rotation or (0, 0, 0)
    if not (rx or ry or rz):
        return f'ModelTransform.pivot({num(pivot[0])}, {num(pivot[1])}, {num(pivot[2])})'
    return f'ModelTransform.of({num(pivot[0])}, {num(pivot[1])}, {num(pivot[2])}, {deg(-rx)}, {deg(-ry)}, {deg(rz)})'


def cuboid(element, origin):
    """The cuboid of a Blockbench cube, relative to a part whose pivot is at origin (Blockbench units)."""
    (fx, fy, fz), (tx, ty, tz) = element['from'], element['to']
    ox, oy, oz = origin
    u, v = element.get('uv_offset', [0, 0])
    inflate = element.get('inflate', 0)
    dilation = f', new Dilation({num(inflate)})' if inflate else ''
    mirror = '.mirrored()' if element.get('mirror_uv') else ''
    return (f'// {element["name"]}\n\t\t\t\t.uv({u}, {v}){mirror}.cuboid({num(ox - tx)}, {num(oy - ty)}, {num(fz - oz)}, '
            f'{num(tx - fx)}, {num(ty - fy)}, {num(tz - fz)}{dilation})' + ('.mirrored(false)' if mirror else ''))


def model_code(model):
    data = json.load(open(os.path.join(ROOT, 'tools', 'models', f'bird_{model}.bbmodel'), encoding='utf-8'))
    elements = {e['uuid']: e for e in data['elements']}
    groups = {g['uuid']: g for g in data.get('groups', [])}
    lines = []
    paths = {}
    counter = [0]

    def part(parent_var, parent_origin, node, path):
        group = groups.get(node['uuid'], node)
        name, origin = group['name'], group['origin']
        counter[0] += 1
        var = f'p{counter[0]}'
        cubes, own_parts = [], []
        for child in node.get('children', []):
            if isinstance(child, str):
                element = elements[child]
                if 'tuft' in element['name'] or any(element.get('rotation') or [0, 0, 0]):
                    own_parts.append(element)
                else:
                    cubes.append(cuboid(element, origin))
        pivot = (-(origin[0] - parent_origin[0]), -(origin[1] - parent_origin[1]), origin[2] - parent_origin[2])
        builder = 'ModelPartBuilder.create()' + ''.join('\n\t\t\t\t' + c for c in cubes)
        lines.append(f'\t\tModelPartData {var} = {parent_var}.addChild("{name}", {builder},\n\t\t\t\t{transform(pivot, group.get("rotation"))});')
        paths.setdefault(name, path + [name])
        for element in own_parts:
            # A part of its own: an owl's ear tuft (hidden on owls that have none) or a cube turned on its own.
            pivot_at = element.get('origin', origin) if any(element.get('rotation') or [0, 0, 0]) else origin
            own_pivot = (-(pivot_at[0] - origin[0]), -(pivot_at[1] - origin[1]), pivot_at[2] - origin[2])
            lines.append(f'\t\t{var}.addChild("{element["name"]}", ModelPartBuilder.create()\n\t\t\t\t{cuboid(element, pivot_at)},\n'
                         f'\t\t\t\t{transform(own_pivot, element.get("rotation"))});')
            paths.setdefault(element['name'], path + [name, element['name']])
        for child in node.get('children', []):
            if not isinstance(child, str):
                part(var, origin, child, path + [name])

    for node in data['outliner']:
        part('root', (0, 24, 0), node, [])
    size = data['resolution']
    method = (f'\tprivate static TexturedModelData {model}() {{\n\t\tModelData data = new ModelData();\n\t\tModelPartData root = data.getRoot();\n'
              + '\n'.join(lines) + f'\n\t\treturn TexturedModelData.of(data, {size["width"]}, {size["height"]});\n\t}}\n')

    def p(name):
        return 'new String[] {' + ', '.join(f'"{n}"' for n in paths[name]) + '}' if name in paths else 'null'

    tufts = [n for n in paths if 'tuft' in n]
    parts = (f'new Parts({p("head")}, {p("left_leg")}, {p("right_leg")}, {p("left_wing")}, {p("right_wing")}, '
             f'new String[][] {{{", ".join(p(t) for t in tufts)}}})')
    return method, parts


def java():
    methods, cases, parts = [], [], []
    for model in MODELS:
        method, part_paths = model_code(model)
        methods.append(method)
        cases.append(f'\t\t\tcase "{model}" -> {model}();')
        parts.append(f'\t\t\tMap.entry("{model}", {part_paths})')
    return f'''package com.eternalreturn.client;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.Map;

/**
 * The birds' models, one per shape model (tools/models/bird_<model>.bbmodel). Generated by tools/birds/build_birds.py;
 * do not hand-edit.
 */
public final class BirdModels {{
	public static final List<String> MODELS = List.of({", ".join(f'"{m}"' for m in MODELS)});

	/** Where a model's parts are: each a path of child names from the root (null if it has none). */
	public record Parts(String[] head, String[] leftLeg, String[] rightLeg, String[] leftWing, String[] rightWing, String[][] tufts) {{
	}}

	public static final Map<String, Parts> PARTS = Map.ofEntries(
{(","+chr(10)).join(parts)});

	private BirdModels() {{
	}}

	public static TexturedModelData get(String model) {{
		return switch (model) {{
{chr(10).join(cases)}
			default -> throw new IllegalArgumentException("no bird model " + model);
		}};
	}}

{chr(10).join(methods)}}}
'''


def lang():
    path = os.path.join(RES, 'assets', NS, 'lang', 'en_us.json')
    table = json.load(open(path, encoding='utf-8'))
    table = {k: v for k, v in table.items() if not k.startswith(f'entity.{NS}.bird')}
    table[f'entity.{NS}.bird'] = 'Bird'
    for shape in TABLE['shapes']:
        table[f'entity.{NS}.bird.shape.{shape["id"]}'] = shape['id'].replace('_', ' ').title()
    for preset in TABLE['presets']:
        table[f'entity.{NS}.bird.{preset["id"]}'] = preset['name']
    table[f'item.{NS}.bird_spawn_egg'] = 'Bird Spawn Egg'
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(table, f, indent='\t', ensure_ascii=False)
        f.write('\n')


def docs():
    out = ['# Birds', '', 'Generated by `tools/birds/build_birds.py` from `src/main/resources/eternalreturn/birds.json`; do not hand-edit.', '',
           'A bird is one mob with a shape, a base colour and up to two patterns, each in its own colour (the second drawn '
           f'over the first). {round(TABLE["preset_share"] * 100)} percent of birds are one of the presets below; the rest are '
           'random. The presets, rendered: [birds/presets.png](birds/presets.png).', '',
           '## Palette', '', '| Colour | RGB |', '|---|---|']
    out += [f'| {c["id"].replace("_", " ")} | `{c["rgb"]}` |' for c in TABLE['palette']]
    out += ['', '## Shapes', '', f'Birds fly off from a player within {TABLE["wary_distance"]} blocks (half that if they sneak). '
            'A shape that perches flies up to rest on leaves and logs; the others stay low (ducks swim). Flocking shapes take '
            'off together when one is startled (or now and then on their own) and fly as a flock by the boids rules; loose '
            'ones hold together less; solo ones fly alone.', '',
            '| Shape | Model | Tufts or crest | Perches | Flocking | Flight speed (blocks/s) | Size (blocks) |', '|---|---|---|---|---|---|---|']
    out += [f'| {s["id"].replace("_", " ")} | `bird_{s["model"]}` | {"yes" if s["tufts"] else "no"} | {"yes" if s["perches"] else "no"} | '
            f'{s["flocking"]} | {round(s["flight_speed"] * 20)} | {s["width"]} x {s["height"]} |' for s in TABLE['shapes']]
    out += ['', '## Patterns', '', ', '.join(TABLE['patterns']), '', '## Presets', '', '| Bird | Shape | Base | Patterns |', '|---|---|---|---|']
    for p in TABLE['presets']:
        layers = ', '.join(f'{l["pattern"]} {l["colour"].replace("_", " ")}' for l in p['patterns']) or 'none'
        out.append(f'| {p["name"]} (`{p["id"]}`) | {p["shape"].replace("_", " ")} | {p["base"].replace("_", " ")} | {layers} |')
    write(os.path.join(ROOT, 'docs', 'birds.md'), '\n'.join(out) + '\n')


write(os.path.join(ROOT, 'src', 'client', 'java', 'com', 'eternalreturn', 'client', 'BirdModels.java'), java())
lang()
docs()
print(f'{len(MODELS)} bird models, {len(TABLE["shapes"])} shapes, {len(TABLE["presets"])} presets: BirdModels.java, language lines, docs/birds.md')
