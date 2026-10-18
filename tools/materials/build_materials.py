"""Generates the resources for copper and rose gold gear (registered by com.eternalreturn.items.Materials), from
vanilla's iron gear (read from the Minecraft jars in the Loom cache, so run a Gradle build first).

Run from anywhere: python tools/materials/build_materials.py

Writes (all under src/main/resources):
- item models for the tools, armor and horse armor of both, and the rose gold ingot;
- their recipes (vanilla's iron ones with the ingot swapped; horse armor from 7 ingots, as before 1.14) and the
  rose gold ingot's (4 gold and 4 copper ingots make 8);
- the worn armor textures (models/armor/<material>_layer_1 and _2) and horse armor textures: the hand-made ones in
  tools/materials/textures (copper_layer_1.png, horse_armor_rosegold.png...), or, for any missing there, recoloured
  from the gold armor textures there (which are also gold armor's own worn look now, as a resource override) and
  vanilla's gold horse armor, onto the colours of each material's item icons;
- each item added to every vanilla item tag that lists its iron counterpart (swords, pickaxes, the
  enchantable/* tags, trimmable armor...), listed in tools/materials/generated_tags.txt.

The item icons are hand-made and live in assets/eternalreturn/textures/item. Do not hand-edit the generated
files; change this script and rerun it.
"""
import glob
import io
import json
import os
import re
import shutil
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', '..')
RES = os.path.join(ROOT, 'src', 'main', 'resources')
NS = 'eternalreturn'
LOOM = os.path.join(ROOT, '.gradle', 'loom-cache', 'minecraftMaven', 'net', 'minecraft')
COMMON = [j for j in glob.glob(os.path.join(LOOM, 'minecraft-common-*', '*', '*.jar')) if not j.endswith('-sources.jar')]
CLIENT = [j for j in glob.glob(os.path.join(LOOM, 'minecraft-clientOnly-*', '*', '*.jar')) if not j.endswith('-sources.jar')]
if not COMMON or not CLIENT:
    raise SystemExit('Minecraft jars not found in the Loom cache; run ./gradlew build first')
NAMES = {}
for jar in [zipfile.ZipFile(COMMON[0]), zipfile.ZipFile(CLIENT[0])]:
    for name in jar.namelist():
        NAMES[name] = jar

MATERIALS = {'copper': 'minecraft:copper_ingot', 'rosegold': f'{NS}:rosegold_ingot'}
PIECES = ['sword', 'pickaxe', 'axe', 'shovel', 'hoe', 'helmet', 'chestplate', 'leggings', 'boots']
TEXTURES = os.path.join(RES, 'assets', NS, 'textures')


def read(path):
    return NAMES[path].read(path)


def write_json(rel, data):
    full = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def handheld(piece):
    return piece in ('sword', 'pickaxe', 'axe', 'shovel', 'hoe')


# ---------------------------------------------------------------- items, recipes
for mat, ingot in MATERIALS.items():
    for piece in PIECES + ['horse_armor']:
        item = f'{mat}_{piece}'
        write_json(f'assets/{NS}/models/item/{item}.json', {
            'parent': 'minecraft:item/handheld' if handheld(piece) else 'minecraft:item/generated',
            'textures': {'layer0': f'{NS}:item/{item}'}})
    for piece in PIECES:
        recipe = json.loads(read(f'data/minecraft/recipe/iron_{piece}.json'))
        text = json.dumps(recipe).replace('"minecraft:iron_ingot"', f'"{ingot}"').replace(f'"minecraft:iron_{piece}"', f'"{NS}:{mat}_{piece}"')
        write_json(f'data/{NS}/recipe/{mat}_{piece}.json', json.loads(text))
    write_json(f'data/{NS}/recipe/{mat}_horse_armor.json', {
        'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['X X', 'XXX', 'X X'],
        'key': {'X': {'item': ingot}}, 'result': {'count': 1, 'id': f'{NS}:{mat}_horse_armor'}})
write_json(f'assets/{NS}/models/item/rosegold_ingot.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/rosegold_ingot'}})
write_json(f'data/{NS}/recipe/rosegold_ingot.json', {
    'type': 'minecraft:crafting_shapeless', 'category': 'misc',
    'ingredients': [{'item': 'minecraft:gold_ingot'}] * 4 + [{'item': 'minecraft:copper_ingot'}] * 4,
    'result': {'count': 8, 'id': f'{NS}:rosegold_ingot'}})

# ---------------------------------------------------------------- worn textures
GOLD_LAYERS = os.path.join(HERE, 'textures')
shutil.copyfile(os.path.join(GOLD_LAYERS, 'gold_layer_1.png'), os.path.join(RES, 'assets/minecraft/textures/models/armor/gold_layer_1.png'))
shutil.copyfile(os.path.join(GOLD_LAYERS, 'gold_layer_2.png'), os.path.join(RES, 'assets/minecraft/textures/models/armor/gold_layer_2.png'))


def palette(mat):
    """The material's colours, darkest to lightest, from its armor icons."""
    colours = set()
    for piece in ['helmet', 'chestplate', 'leggings', 'boots']:
        for r, g, b, a in Image.open(os.path.join(TEXTURES, 'item', f'{mat}_{piece}.png')).convert('RGBA').getdata():
            # Leave out the icons' dark outline and white glints: the worn textures have neither.
            if a == 255 and 60 < 0.299 * r + 0.587 * g + 0.114 * b < 235:
                colours.add((r, g, b))
    return sorted(colours, key=lambda c: 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2])


def recolour(image, colours):
    """Each opaque pixel takes the palette colour whose brightness sits at the same place in the palette's range
    as the pixel's does in the image's."""
    def lum(c):
        return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]
    values = [lum(p) for p in image.getdata() if p[3]]
    lo, hi = min(values), max(values)
    plo, phi = lum(colours[0]), lum(colours[-1])

    def pick(p):
        target = plo + (lum(p) - lo) / max(1.0, hi - lo) * (phi - plo)
        return min(colours, key=lambda c: abs(lum(c) - target))
    out = Image.new('RGBA', image.size)
    out.putdata([(0, 0, 0, 0) if p[3] == 0 else (*pick(p), p[3]) for p in image.getdata()])
    return out


horse = Image.open(io.BytesIO(read('assets/minecraft/textures/entity/horse/armor/horse_armor_gold.png'))).convert('RGBA')
for mat in MATERIALS:
    colours = palette(mat)
    # A hand-made texture in tools/materials/textures wins; otherwise it is recoloured from gold's.
    jobs = [(f'{mat}_layer_{layer}', ('models', 'armor'), os.path.join(GOLD_LAYERS, f'gold_layer_{layer}.png')) for layer in (1, 2)]
    jobs.append((f'horse_armor_{mat}', ('entity', 'horse', 'armor'), None))
    for name, rel, base in jobs:
        path = os.path.join(TEXTURES, *rel, f'{name}.png')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        made = os.path.join(GOLD_LAYERS, f'{name}.png')
        if os.path.exists(made):
            shutil.copyfile(made, path)
        else:
            recolour(Image.open(base).convert('RGBA') if base else horse, colours).save(path)

# ---------------------------------------------------------------- tags
OWN = os.path.join(HERE, 'generated_tags.txt')
own = set(open(OWN).read().split()) if os.path.exists(OWN) else set()
written = []
for path in sorted(NAMES):
    m = re.match(r'data/minecraft/tags/item/(.+)\.json$', path)
    if not m:
        continue
    values = json.loads(read(path))['values']
    added = []
    for value in values:
        hit = isinstance(value, str) and re.fullmatch(r'minecraft:iron_(\w+)', value)
        if hit and hit.group(1) in PIECES + ['horse_armor']:
            added += [f'{NS}:{mat}_{hit.group(1)}' for mat in MATERIALS]
    if not added:
        continue
    rel = f'data/minecraft/tags/item/{m.group(1)}.json'
    full = os.path.join(RES, rel)
    if os.path.exists(full) and rel not in own:
        raise SystemExit(f'{rel} already exists and was not written by this script; merge it by hand first')
    write_json(rel, {'replace': False, 'values': added})
    written.append(rel)
with open(OWN, 'w', newline='\n') as f:
    f.write('\n'.join(written) + '\n')
print('materials written:', ', '.join(MATERIALS), '-', len(written), 'tags')
