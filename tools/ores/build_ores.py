"""Builds every ore variant's resources from src/main/resources/eternalreturn/ore_variants.json and the
vanilla game files (read from the Minecraft jar in the Gradle cache, so run a Gradle build first).

Usage: python tools/ores/build_ores.py

Writes, for every host x ore (block eternalreturn:<host>_<ore>_ore):
- the ore's overlay texture: the shared pattern (tools/ores/pattern.txt) in the ore's palette;
- block and item models (host texture per face with the overlay on top) and the blockstate;
- the loot table: vanilla's for the stone ore, with Silk Touch giving the variant;
- block and item tags: eternalreturn:ore_variants (all) and eternalreturn:ore_variants/<ore>, the vanilla
  <ore>_ores tags, and the needs_*_tool tags the vanilla ore is in;
- smelting and blasting recipes copied from the vanilla ore's (same result, experience and time), taking
  the item tag eternalreturn:ore_variants/<ore>, with their recipe-book advancements;
- docs/worldgen-maps/ores/contact_sheet.png (every ore on every host and on plain stone) and pattern.png.
See docs/worldgen.md, Ore variants. Do not hand-edit the generated files.
"""
import glob
import io
import json
import os
import shutil
import sys
import zipfile

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
NS = 'eternalreturn'
TABLE = json.load(open(os.path.join(RES, NS, 'ore_variants.json'), encoding='utf-8'))
PATTERN = [line.rstrip('\n') for line in open(os.path.join(os.path.dirname(__file__), 'pattern.txt'), encoding='utf-8') if line.strip()]
assert len(PATTERN) == 16 and all(len(row) == 16 for row in PATTERN), 'pattern.txt must be 16 rows of 16 characters'

JARS = glob.glob(os.path.expanduser('~/.gradle/caches/fabric-loom/1.21.1/minecraft-client.jar'))
if not JARS:
    sys.exit('Minecraft 1.21.1 client jar not found in the Gradle cache; run ./gradlew build first')
JAR = zipfile.ZipFile(JARS[0])


def vanilla_json(path):
    return json.loads(JAR.read(path))


def vanilla_texture(texture_id):
    namespace, path = texture_id.split(':')
    assert namespace == 'minecraft', texture_id
    return Image.open(io.BytesIO(JAR.read(f'assets/minecraft/textures/{path}.png'))).convert('RGBA')


def write_json(rel, obj):
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def path_of(block_id):
    return block_id.split(':')[1]


def faces(host):
    textures = host['textures']
    if 'all' in textures:
        return {'top': textures['all'], 'bottom': textures['all'], 'side': textures['all']}
    return {'top': textures['top'], 'bottom': textures['bottom'], 'side': textures['side']}


def overlay(ore):
    palette = ore['palette']
    image = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(PATTERN):
        for x, role in enumerate(row):
            if role != '.':
                colour = palette[role].lstrip('#')
                image.putpixel((x, y), (int(colour[0:2], 16), int(colour[2:4], 16), int(colour[4:6], 16), 255))
    return image


# Generated folders are rebuilt from scratch, so a removed ore or host leaves nothing behind.
for folder in ['assets/eternalreturn/textures/block/ore_overlay', 'data/eternalreturn/loot_table/blocks/ore_variants',
               'data/eternalreturn/tags/block/ore_variants', 'data/eternalreturn/tags/item/ore_variants',
               'data/eternalreturn/recipe/ore_variants', 'data/eternalreturn/advancement/recipes/ore_variants']:
    shutil.rmtree(os.path.join(RES, folder), ignore_errors=True)
for old in glob.glob(os.path.join(RES, 'assets/eternalreturn/blockstates/*_ore.json')) + \
        glob.glob(os.path.join(RES, 'assets/eternalreturn/models/block/*_ore.json')) + \
        glob.glob(os.path.join(RES, 'assets/eternalreturn/models/item/*_ore.json')) + \
        glob.glob(os.path.join(RES, 'data/eternalreturn/loot_table/blocks/*_ore.json')):
    os.remove(old)

# ---------------------------------------------------------------- textures and models
overlays = {}
for ore_name, ore in TABLE['ores'].items():
    image = overlay(ore)
    overlays[ore_name] = image
    path = os.path.join(RES, 'assets', NS, 'textures', 'block', 'ore_overlay', ore_name + '.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)

write_json(f'assets/{NS}/models/block/ore_variant.json', {
    '_comment': 'Template for the ore variants (tools/ores/build_ores.py): the host texture on each face, then the ore overlay on top.',
    'parent': 'minecraft:block/block',
    'elements': [
        {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {
            'down': {'texture': '#bottom', 'cullface': 'down'}, 'up': {'texture': '#top', 'cullface': 'up'},
            'north': {'texture': '#side', 'cullface': 'north'}, 'south': {'texture': '#side', 'cullface': 'south'},
            'west': {'texture': '#side', 'cullface': 'west'}, 'east': {'texture': '#side', 'cullface': 'east'}}},
        {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {
            side: {'texture': '#overlay', 'cullface': side} for side in ['down', 'up', 'north', 'south', 'west', 'east']}},
    ]})

variants = {ore_name: [] for ore_name in TABLE['ores']}
for host_name, host in TABLE['hosts'].items():
    host_faces = faces(host)
    for ore_name, ore in TABLE['ores'].items():
        name = f'{host_name}_{ore_name}_ore'
        variants[ore_name].append(f'{NS}:{name}')
        write_json(f'assets/{NS}/models/block/{name}.json', {
            'parent': f'{NS}:block/ore_variant',
            'textures': {'top': host_faces['top'], 'bottom': host_faces['bottom'], 'side': host_faces['side'],
                         'particle': host_faces['side'], 'overlay': f'{NS}:block/ore_overlay/{ore_name}'}})
        write_json(f'assets/{NS}/models/item/{name}.json', {'parent': f'{NS}:block/{name}'})
        states = vanilla_json(f'assets/minecraft/blockstates/{path_of(ore["block"])}.json')['variants']
        write_json(f'assets/{NS}/blockstates/{name}.json', {'variants': {key: {'model': f'{NS}:block/{name}'} for key in states}})

        # Loot: vanilla's table for the stone ore, with the Silk Touch branch giving this block.
        loot = json.dumps(vanilla_json(f'data/minecraft/loot_table/blocks/{path_of(ore["block"])}.json'))
        assert loot.count(f'"name": "{ore["block"]}"') == 1, ore['block']
        loot = json.loads(loot.replace(f'"name": "{ore["block"]}"', f'"name": "{NS}:{name}"'))
        loot['random_sequence'] = f'{NS}:blocks/{name}'
        write_json(f'data/{NS}/loot_table/blocks/{name}.json', loot)

# ---------------------------------------------------------------- tags
all_variants = []
needs = {}
for tool in ['stone', 'iron', 'diamond']:
    needs[tool] = vanilla_json(f'data/minecraft/tags/block/needs_{tool}_tool.json')['values']
needs_ours = {tool: [] for tool in needs}
for ore_name, ore in TABLE['ores'].items():
    tag = f'#{NS}:ore_variants/{ore_name}'
    write_json(f'data/{NS}/tags/block/ore_variants/{ore_name}.json', {'values': variants[ore_name]})
    write_json(f'data/{NS}/tags/item/ore_variants/{ore_name}.json', {'values': variants[ore_name]})
    all_variants.append(tag)
    # The vanilla <ore>_ores tags (Fabric's c:ores/<ore> and c:ores include them).
    vanilla_tag = path_of(ore['block']).replace('_ore', '_ores')
    for kind in ['block', 'item']:
        write_json(f'data/minecraft/tags/{kind}/{vanilla_tag}.json', {'replace': False, 'values': [tag]})
    for tool, members in needs.items():
        if ore['block'] in members:
            needs_ours[tool].append(tag)
write_json(f'data/{NS}/tags/block/ore_variants.json', {'values': all_variants})
write_json(f'data/{NS}/tags/item/ore_variants.json', {'values': all_variants})
for tool, tags in needs_ours.items():
    path = f'data/minecraft/tags/block/needs_{tool}_tool.json'
    if tags:
        write_json(path, {'replace': False, 'values': tags})
    elif os.path.exists(os.path.join(RES, path)):
        os.remove(os.path.join(RES, path))

# ---------------------------------------------------------------- smelting and blasting
recipes = {}
for entry in JAR.namelist():
    if entry.startswith('data/minecraft/recipe/') and entry.endswith('.json'):
        recipe = vanilla_json(entry)
        if recipe.get('type') in ('minecraft:smelting', 'minecraft:blasting'):
            ingredient = recipe.get('ingredient')
            if isinstance(ingredient, dict) and 'item' in ingredient:
                recipes.setdefault(ingredient['item'], []).append(recipe)
for ore_name, ore in TABLE['ores'].items():
    found = recipes.get(ore['block'], [])
    assert {r['type'] for r in found} == {'minecraft:smelting', 'minecraft:blasting'}, (ore['block'], found)
    for recipe in found:
        kind = recipe['type'].split(':')[1]
        result = path_of(recipe['result']['id'])
        recipe_id = f'ore_variants/{result}_from_{kind}_{ore_name}_ore_variants'
        copy = dict(recipe)
        copy['ingredient'] = {'tag': f'{NS}:ore_variants/{ore_name}'}
        write_json(f'data/{NS}/recipe/{recipe_id}.json', copy)
        write_json(f'data/{NS}/advancement/recipes/{recipe_id}.json', {
            'parent': 'minecraft:recipes/root',
            'criteria': {
                'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': f'{NS}:{recipe_id}'}},
                'has_ore': {'trigger': 'minecraft:inventory_changed',
                            'conditions': {'items': [{'items': f'#{NS}:ore_variants/{ore_name}'}]}}},
            'requirements': [['has_the_recipe', 'has_ore']],
            'rewards': {'recipes': [f'{NS}:{recipe_id}']}})

# ---------------------------------------------------------------- contact sheet and pattern
SCALE = 6
TILE = 16 * SCALE
GAP = 6
LABEL_W = 110
HEADER_H = 52
columns = [('stone', 'side', 'minecraft:block/stone')]
for host_name, host in TABLE['hosts'].items():
    if 'all' in host['textures']:
        columns.append((host_name, '', host['textures']['all']))
    else:
        for face in ['side', 'top', 'bottom']:
            columns.append((host_name, face, host['textures'][face]))
font = ImageFont.load_default()
sheet = Image.new('RGB', (LABEL_W + len(columns) * (TILE + GAP), HEADER_H + len(TABLE['ores']) * (TILE + GAP)), (32, 32, 32))
draw = ImageDraw.Draw(sheet)
for c, (host_name, face, texture) in enumerate(columns):
    x = LABEL_W + c * (TILE + GAP)
    label = host_name.replace('hardened_', 'hard. ').replace('_', ' ')
    draw.text((x, 6), label, fill=(255, 255, 255), font=font)
    if face:
        draw.text((x, 22), face, fill=(180, 180, 180), font=font)
    if host_name == 'stone':
        draw.text((x, 36), '(preview only)', fill=(150, 150, 150), font=font)
for r, ore_name in enumerate(TABLE['ores']):
    y = HEADER_H + r * (TILE + GAP)
    draw.text((8, y + TILE // 2 - 6), ore_name, fill=(255, 255, 255), font=font)
    for c, (host_name, face, texture) in enumerate(columns):
        tile = Image.alpha_composite(vanilla_texture(texture), overlays[ore_name])
        sheet.paste(tile.resize((TILE, TILE), Image.NEAREST).convert('RGB'), (LABEL_W + c * (TILE + GAP), y))
docs = os.path.join(ROOT, 'docs', 'worldgen-maps', 'ores')
os.makedirs(docs, exist_ok=True)
sheet.save(os.path.join(docs, 'contact_sheet.png'))

swatch = Image.new('RGB', (len(TABLE['ores']) * (TILE + GAP) + GAP, TILE + 30), (32, 32, 32))
draw = ImageDraw.Draw(swatch)
for i, ore_name in enumerate(TABLE['ores']):
    x = GAP + i * (TILE + GAP)
    backdrop = Image.new('RGBA', (16, 16), (128, 128, 128, 255))
    swatch.paste(Image.alpha_composite(backdrop, overlays[ore_name]).resize((TILE, TILE), Image.NEAREST).convert('RGB'), (x, 4))
    draw.text((x, TILE + 10), ore_name, fill=(255, 255, 255), font=font)
swatch.save(os.path.join(docs, 'pattern.png'))
print(f'{sum(len(v) for v in variants.values())} ore variants written ({len(TABLE["hosts"])} hosts x {len(TABLE["ores"])} ores)')
