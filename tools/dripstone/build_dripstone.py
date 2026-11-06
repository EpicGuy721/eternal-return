"""Writes the stone dripstone resources (owner's request, 2026-11-05; com.eternalreturn.blocks.StoneDripstones) from the
table src/main/resources/eternalreturn/stone_dripstones.json (which the game also reads to register the blocks):

- block and item models: each piece is a small stepped spike of boxes (wide at the stone, narrowing to the tip), drawn
  with the stone's own block textures (minecraft:block/granite and so on; sides from the side texture, the ends from the
  stone's top where it has one), so a resource pack's stone shows on its dripstone too (the owner's report: the first
  version drew its own textures from vanilla's stone and ignored the pack); the up pieces are the down ones turned over;
- blockstates (pointed dripstone's), loot tables (each drops itself), the block tag eternalreturn:stone_dripstones (the
  pickaxe's, through data/minecraft/tags/block/mineable/pickaxe.json), the names in en_us.json (other lines kept);
- the cave features: worldgen/configured_feature/stone_dripstone.json and placed_feature/stone_dripstone.json (the
  overworld's caves) and nether_stone_dripstone.json (the Nether's), Eternal Return worlds only. Tune them here.

The script checks each texture it names exists in the Minecraft client jar in the Gradle cache (run a Gradle build first).
Run: python tools/dripstone/build_dripstone.py
"""
import glob
import json
import os
import sys
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
NS = 'eternalreturn'
TABLE = json.load(open(os.path.join(RES, NS, 'stone_dripstones.json'), encoding='utf-8'))
KINDS = {k: v for k, v in TABLE.items() if not k.startswith('_')}
JARS = glob.glob(os.path.expanduser('~/.gradle/caches/fabric-loom/1.21.1/minecraft-client.jar'))
if not JARS:
    sys.exit('Minecraft 1.21.1 client jar not found in the Gradle cache; run ./gradlew build first')
JAR = zipfile.ZipFile(JARS[0])
THICKNESSES = ['tip', 'tip_merge', 'frustum', 'middle', 'base']

# Boxes of each piece pointing down, fixed to the stone at the top: (lowest y, highest y, half-width), in sixteenths. The
# tip hangs 11 pixels (as its outline does); a merged tip runs the whole block down to meet the other.
BOXES = {'base': [(8, 16, 6), (0, 8, 5)], 'middle': [(8, 16, 5), (0, 8, 4)], 'frustum': [(8, 16, 4), (0, 8, 3)],
         'tip': [(11, 16, 3), (7, 11, 2), (5, 7, 1)], 'tip_merge': [(10, 16, 3), (4, 10, 2), (0, 4, 1)]}
ITEM_BOXES = [(12, 16, 4), (8, 12, 3), (4, 8, 2), (0, 4, 1)]
# The stones with their own end (top) texture.
ENDS = {'sandstone': 'sandstone_top', 'red_sandstone': 'red_sandstone_top', 'basalt': 'basalt_top', 'deepslate': 'deepslate_top'}

# Cave features. Overworld: tries per chunk at random heights; most land in rock and place nothing, so this is about
# one cluster in every third chunk that has caves. Nether: its open caverns take more.
OVERWORLD = {'count': 7, 'min_y': {'above_bottom': 8}, 'max_y': {'absolute': 100}, 'tries': 12, 'spread': 4, 'max_length': 4, 'floor_chance': 0.6}
NETHER = {'count': 8, 'min_y': {'above_bottom': 8}, 'max_y': {'absolute': 120}, 'tries': 12, 'spread': 4, 'max_length': 4, 'floor_chance': 0.6}


def write_json(rel, obj):
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write('\n')


def check_texture(name):
    if f'assets/minecraft/textures/block/{name}.png' not in JAR.namelist():
        sys.exit(f'no vanilla texture block/{name}')
    return f'minecraft:block/{name}'


def spike_model(boxes, up, parent='minecraft:block/block'):
    """A model of boxes centred on the block, turned over for a piece pointing up."""
    elements = []
    for low, high, half in boxes:
        if up:
            low, high = 16 - high, 16 - low
        faces = {d: {'texture': '#side'} for d in ('north', 'south', 'east', 'west')}
        faces.update({'up': {'texture': '#end'}, 'down': {'texture': '#end'}})
        elements.append({'from': [8 - half, low, 8 - half], 'to': [8 + half, high, 8 + half], 'faces': faces})
    return {'parent': parent, 'textures': {'particle': '#side'}, 'elements': elements}


def feature(spec, nether):
    placement = [{'type': f'{NS}:eternal_return_only'}, {'type': 'minecraft:count', 'count': spec['count']}, {'type': 'minecraft:in_square'},
                 {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform', 'min_inclusive': spec['min_y'], 'max_inclusive': spec['max_y']}},
                 {'type': 'minecraft:block_predicate_filter', 'predicate': {'type': 'minecraft:matching_blocks', 'blocks': ['minecraft:air', 'minecraft:cave_air']}}]
    config = {'type': f'{NS}:stone_dripstone', 'config': {'tries': spec['tries'], 'spread': spec['spread'], 'max_length': spec['max_length'],
                                                       'floor_chance': spec['floor_chance']}}
    name = 'nether_stone_dripstone' if nether else 'stone_dripstone'
    write_json(f'data/{NS}/worldgen/configured_feature/{name}.json', config)
    write_json(f'data/{NS}/worldgen/placed_feature/{name}.json', {'feature': f'{NS}:{name}', 'placement': placement})


def main():
    tex = os.path.join(RES, 'assets', NS, 'textures')
    models = f'assets/{NS}/models'
    lang_file = os.path.join(RES, 'assets', NS, 'lang', 'en_us.json')
    lang = json.load(open(lang_file, encoding='utf-8'))
    # The textures the first version drew are no longer used.
    for old in glob.glob(os.path.join(tex, 'block', '*_dripstone_*.png')) + glob.glob(os.path.join(tex, 'item', '*_dripstone.png')):
        os.remove(old)
    shapes = f'{models}/block/stone_dripstone'
    for t in THICKNESSES:
        for direction in ('down', 'up'):
            write_json(f'{shapes}/{direction}_{t}.json', spike_model(BOXES[t], direction == 'up'))
    write_json(f'{shapes}/item.json', spike_model(ITEM_BOXES, False))
    for kind, spec in KINDS.items():
        name = f'{kind}_dripstone'
        textures = {'side': check_texture(spec['texture']), 'end': check_texture(ENDS.get(kind, spec['texture']))}
        variants = {}
        for t in THICKNESSES:
            for direction in ('down', 'up'):
                write_json(f'{models}/block/{name}_{direction}_{t}.json', {'parent': f'{NS}:block/stone_dripstone/{direction}_{t}', 'textures': textures})
                variants[f'thickness={t},vertical_direction={direction}'] = {'model': f'{NS}:block/{name}_{direction}_{t}'}
        write_json(f'assets/{NS}/blockstates/{name}.json', {'variants': variants})
        write_json(f'{models}/item/{name}.json', {'parent': f'{NS}:block/stone_dripstone/item', 'textures': textures})
        write_json(f'data/{NS}/loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{
            'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{name}'}],
            'conditions': [{'condition': 'minecraft:survives_explosion'}]}], 'random_sequence': f'{NS}:blocks/{name}'})
        lang[f'block.{NS}.{name}'] = f'{spec["name"]} Dripstone'
    write_json(f'data/{NS}/tags/block/stone_dripstones.json', {'values': [f'{NS}:{k}_dripstone' for k in KINDS]})
    write_json(f'data/{NS}/tags/item/stone_dripstones.json', {'values': [f'{NS}:{k}_dripstone' for k in KINDS]})
    feature(OVERWORLD, False)
    feature(NETHER, True)
    with open(lang_file, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(lang, f, indent='\t', ensure_ascii=False)
        f.write('\n')
    print(f'{len(KINDS)} stone dripstones')


if __name__ == '__main__':
    main()
