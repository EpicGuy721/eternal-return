"""Generates the resources for rubies and sapphires (registered by com.eternalreturn.items.Gems) and the ore
features of Eternal Return worlds' extra ores (added to biomes by com.eternalreturn.worldgen.ores.OreBonuses).

Run from anywhere: python tools/gems/build_gems.py   (then python tools/ores/build_ores.py, which makes the gems'
host stone variants from these base ores)

Writes (all under src/main/resources):
- ruby and sapphire items (icons from tools/gems/textures), their blocks (crafted from 9 and back), the ruby ore,
  sapphire ore and Nether ruby ore (textures: stone or netherrack with the ore's overlay from tools/ores/overlays),
  their models, blockstates, loot tables (Fortune and Silk Touch as diamond ore) and smelting recipes;
- the ore features: ruby ore as lapis is (2 veins of 7 a chunk around y 0, 4 buried ones lower down), sapphire ore
  as many veins as diamond, each a single block, Nether ruby ore in netherrack, and the per-biome bonuses
  (OreBonuses.BONUSES pairs each with its biomes). Each placed feature carries the eternalreturn:eternal_return_only
  placement, so other worlds never get them.

Do not hand-edit the generated files; change this script and rerun it.
"""
import glob
import json
import os
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', '..')
RES = os.path.join(ROOT, 'src', 'main', 'resources')
NS = 'eternalreturn'
LOOM = os.path.join(ROOT, '.gradle', 'loom-cache', 'minecraftMaven', 'net', 'minecraft')
COMMON = [j for j in glob.glob(os.path.join(LOOM, 'minecraft-common-*', '*', '*.jar')) if not j.endswith('-sources.jar')][0]
CLIENT = [j for j in glob.glob(os.path.join(LOOM, 'minecraft-clientOnly-*', '*', '*.jar')) if not j.endswith('-sources.jar')][0]
JARS = [zipfile.ZipFile(COMMON), zipfile.ZipFile(CLIENT)]


def read(path):
    for jar in JARS:
        if path in jar.namelist():
            return jar.read(path)
    raise KeyError(path)


def vanilla(path):
    return json.loads(read(path))


def write_json(rel, data):
    full = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def texture(rel):
    import io
    return Image.open(io.BytesIO(read(f'assets/minecraft/textures/{rel}.png'))).convert('RGBA')


TEX = os.path.join(RES, 'assets', NS, 'textures')
GEMS = ['ruby', 'sapphire']
ORES = {'ruby_ore': ('ruby', 'block/stone'), 'sapphire_ore': ('sapphire', 'block/stone'), 'nether_ruby_ore': ('ruby', 'block/netherrack')}

# ---------------------------------------------------------------- items and blocks
for gem in GEMS:
    Image.open(os.path.join(HERE, 'textures', f'{gem}.png')).convert('RGBA').save(os.path.join(TEX, 'item', f'{gem}.png'))
    Image.open(os.path.join(HERE, 'textures', f'{gem}_block.png')).convert('RGBA').save(os.path.join(TEX, 'block', f'{gem}_block.png'))
    write_json(f'assets/{NS}/models/item/{gem}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{gem}'}})
    block = f'{gem}_block'
    write_json(f'assets/{NS}/blockstates/{block}.json', {'variants': {'': {'model': f'{NS}:block/{block}'}}})
    write_json(f'assets/{NS}/models/block/{block}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/{block}'}})
    write_json(f'assets/{NS}/models/item/{block}.json', {'parent': f'{NS}:block/{block}'})
    write_json(f'data/{NS}/loot_table/blocks/{block}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'bonus_rolls': 0,
        'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{block}'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        'random_sequence': f'{NS}:blocks/{block}'})
    write_json(f'data/{NS}/recipe/{block}.json', {'type': 'minecraft:crafting_shaped', 'category': 'building', 'pattern': ['###', '###', '###'],
                                                  'key': {'#': {'item': f'{NS}:{gem}'}}, 'result': {'count': 1, 'id': f'{NS}:{block}'}})
    write_json(f'data/{NS}/recipe/{gem}_from_{block}.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
                                                             'ingredients': [{'item': f'{NS}:{block}'}], 'result': {'count': 9, 'id': f'{NS}:{gem}'}})

for gem in GEMS:
    write_json(f'data/c/tags/item/gems/{gem}.json', {'values': [f'{NS}:{gem}']})
    write_json(f'data/c/tags/item/storage_blocks/{gem}.json', {'values': [f'{NS}:{gem}_block']})
    write_json(f'data/c/tags/block/storage_blocks/{gem}.json', {'values': [f'{NS}:{gem}_block']})
write_json('data/c/tags/item/gems.json', {'replace': False, 'values': [f'#c:gems/{gem}' for gem in GEMS]})

diamond_loot = json.dumps(vanilla('data/minecraft/loot_table/blocks/diamond_ore.json'))
for ore, (gem, base) in ORES.items():
    image = texture(base)
    image.alpha_composite(Image.open(os.path.join(ROOT, 'tools', 'ores', 'overlays', f'{gem}.png')).convert('RGBA'))
    image.save(os.path.join(TEX, 'block', f'{ore}.png'))
    write_json(f'assets/{NS}/blockstates/{ore}.json', {'variants': {'': {'model': f'{NS}:block/{ore}'}}})
    write_json(f'assets/{NS}/models/block/{ore}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/{ore}'}})
    write_json(f'assets/{NS}/models/item/{ore}.json', {'parent': f'{NS}:block/{ore}'})
    loot = json.loads(diamond_loot.replace('minecraft:diamond_ore', f'{NS}:{ore}').replace('"minecraft:diamond"', f'"{NS}:{gem}"')
                      .replace('minecraft:blocks/diamond_ore', f'{NS}:blocks/{ore}'))
    write_json(f'data/{NS}/loot_table/blocks/{ore}.json', loot)
    for kind, time in [('smelting', 200), ('blasting', 100)]:
        write_json(f'data/{NS}/recipe/{gem}_from_{kind}_{ore}.json', {'type': f'minecraft:{kind}', 'category': 'misc', 'cookingtime': time,
                                                                      'experience': 1.0, 'group': gem, 'ingredient': {'item': f'{NS}:{ore}'},
                                                                      'result': {'id': f'{NS}:{gem}'}})

# ---------------------------------------------------------------- features
ONLY = {'type': f'{NS}:eternal_return_only'}


def ore_feature(name, targets, size, discard=0.0, kind='minecraft:ore'):
    write_json(f'data/{NS}/worldgen/configured_feature/{name}.json', {'type': kind, 'config': {
        'discard_chance_on_air_exposure': discard, 'size': size, 'targets': targets}})


def placed(name, feature, count, height, square=True, rarity=None):
    placement = [ONLY]
    if rarity:
        placement.append({'type': 'minecraft:rarity_filter', 'chance': rarity})
    else:
        placement.append({'type': 'minecraft:count', 'count': count})
    if square:
        placement.append({'type': 'minecraft:in_square'})
    placement += [{'type': 'minecraft:height_range', 'height': height}, {'type': 'minecraft:biome'}]
    write_json(f'data/{NS}/worldgen/placed_feature/{name}.json', {'feature': feature, 'placement': placement})


STONE = {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:stone_ore_replaceables'}
NETHERRACK = {'predicate_type': 'minecraft:block_match', 'block': 'minecraft:netherrack'}


def target(test, block):
    return [{'target': test, 'state': {'Name': block}}]


def triangle(lo, hi):
    return {'type': 'minecraft:trapezoid', 'min_inclusive': {'absolute': lo}, 'max_inclusive': {'absolute': hi}}


def uniform(lo, hi):
    return {'type': 'minecraft:uniform', 'min_inclusive': {'absolute': lo}, 'max_inclusive': {'absolute': hi}}


# Rubies as lapis; sapphires as many veins as diamond, each one block; Nether rubies in netherrack.
ore_feature('ore_ruby', target(STONE, f'{NS}:ruby_ore'), 7)
ore_feature('ore_ruby_buried', target(STONE, f'{NS}:ruby_ore'), 7, 1.0)
ore_feature('ore_sapphire', target(STONE, f'{NS}:sapphire_ore'), 1, 0.5, 'minecraft:scattered_ore')
ore_feature('ore_ruby_nether', target(NETHERRACK, f'{NS}:nether_ruby_ore'), 7)
placed('ore_ruby', f'{NS}:ore_ruby', 2, triangle(-32, 32))
placed('ore_ruby_buried', f'{NS}:ore_ruby_buried', 4, uniform(-64, 64))
placed('ore_sapphire', f'{NS}:ore_sapphire', 14, triangle(-144, 16))
placed('ore_ruby_nether', f'{NS}:ore_ruby_nether', 6, uniform(10, 117))

# Per-biome bonuses: vanilla's own ore features, placed again in their biomes (OreBonuses lists which biomes).
BONUS = {
    'iron': ('minecraft:ore_iron', 10, triangle(-24, 56)),
    'coal': ('minecraft:ore_coal', 20, uniform(0, 192)),
    'gold': ('minecraft:ore_gold', 8, triangle(-64, 32)),
    'lapis': ('minecraft:ore_lapis', 4, triangle(-32, 32)),
    'emerald': ('minecraft:ore_emerald', 50, triangle(-16, 480)),
    'redstone': ('minecraft:ore_redstone', 8, uniform(-64, 15)),
    'copper': ('minecraft:ore_copper_large', 16, triangle(-16, 112)),
    'diamond': ('minecraft:ore_diamond_small', 7, triangle(-144, 16)),
    'ruby': (f'{NS}:ore_ruby', 4, triangle(-32, 32)),
    'sapphire': (f'{NS}:ore_sapphire', 14, triangle(-144, 16)),
    'ancient_debris': ('minecraft:ore_ancient_debris_small', 2, {'type': 'minecraft:uniform', 'min_inclusive': {'above_bottom': 8},
                                                                  'max_inclusive': {'below_top': 8}}),
    'quartz': ('minecraft:ore_quartz', 16, uniform(10, 117)),
}
for ore, (feature, count, height) in BONUS.items():
    placed(f'ore_bonus_{ore}', feature, count, height)
print('gems written:', ', '.join(GEMS), '-', len(BONUS), 'ore bonuses')
