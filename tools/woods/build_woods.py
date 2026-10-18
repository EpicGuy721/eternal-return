"""Generates the resources for the maple and pale oak wood sets, maple petals and the autumnal forest's
trees, from vanilla's cherry wood set (read from the Minecraft jars in the Loom cache, so run a Gradle
build first).

Run from anywhere: python tools/woods/build_woods.py

Writes (all under src/main/resources), for each wood (blocks registered by com.eternalreturn.blocks.Woods):
- blockstates, block and item models, loot tables, recipes and recipe advancements of every block and item
  vanilla's cherry wood has (log, wood, stripped log and wood, planks, stairs, slab, fence, fence gate, door,
  trapdoor, pressure plate, button, sign, hanging sign, leaves, sapling, potted sapling, boat, chest boat),
  with cherry swapped for the wood; pale oak has three leaves (red, orange and yellow) in place of one;
- the eternalreturn:<wood>_logs tags, and the wood's blocks and items added to every vanilla tag that lists
  cherry's;
- maple petals, from pink petals (flowerbed models, loot table, tags);
- the falling maple leaf particle (from the cherry leaf one);
- the trees (configured and placed features) and the autumnal forest's tree and petal features.

The textures themselves are not generated: they live in assets/eternalreturn/textures (block, item,
entity/boat, entity/chest_boat, entity/signs, gui/hanging_signs, particle).

Do not hand-edit the generated files; change this script and rerun it.
"""
import glob
import json
import os
import re
import zipfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
RES = os.path.join(ROOT, 'src', 'main', 'resources')
NS = 'eternalreturn'
LOOM = os.path.join(ROOT, '.gradle', 'loom-cache', 'minecraftMaven', 'net', 'minecraft')
COMMON = [j for j in glob.glob(os.path.join(LOOM, 'minecraft-common-*', '*', '*.jar')) if not j.endswith('-sources.jar')]
CLIENT = [j for j in glob.glob(os.path.join(LOOM, 'minecraft-clientOnly-*', '*', '*.jar')) if not j.endswith('-sources.jar')]
if not COMMON or not CLIENT:
    raise SystemExit('Minecraft jars not found in the Loom cache; run ./gradlew build first')
JARS = [zipfile.ZipFile(COMMON[0]), zipfile.ZipFile(CLIENT[0])]
NAMES = {}
for jar in JARS:
    for name in jar.namelist():
        NAMES[name] = jar

# Pale oak's leaves come in three colours; maple's in one.
# Azalea has neither: its trees wear vanilla's azalea leaves, and vanilla's azalea bush is its sapling.
WOODS = {
    'maple': {'leaves': ['maple_leaves'], 'sapling': True},
    'pale_oak': {'leaves': ['red_pale_oak_leaves', 'orange_pale_oak_leaves', 'yellow_pale_oak_leaves'], 'sapling': True},
    'azalea': {'leaves': [], 'sapling': False},
}


def read(path):
    return NAMES[path].read(path).decode('utf-8')


def write_json(rel, data):
    full = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


ID = re.compile(r'(#?)minecraft:((?:block/|item/|blocks/|entity/)?)(stripped_|potted_)?cherry')


def swap(text, wood, leaves=None):
    """Vanilla cherry ids to this wood's ids (and the one cherry leaves id to the given leaves)."""
    if leaves:
        text = text.replace('minecraft:block/cherry_leaves', f'{NS}:block/{leaves}')
        text = text.replace('minecraft:blocks/cherry_leaves', f'{NS}:blocks/{leaves}')
        text = text.replace('minecraft:item/cherry_leaves', f'{NS}:item/{leaves}')
        text = text.replace('minecraft:cherry_leaves', f'{NS}:{leaves}')
    return ID.sub(lambda m: f'{m.group(1)}{NS}:{m.group(2)}{m.group(3) or ""}{wood}', text)


def copy(src, dst, wood, leaves=None):
    write_json(dst, json.loads(swap(read(src), wood, leaves)))


written = {'blocks': [], 'items': []}
for wood, spec in WOODS.items():
    # Everything named cherry_* (or stripped_cherry_*), except the leaves, which are done per colour below.
    for path in sorted(NAMES):
        m = re.match(r'(assets|data)/minecraft/(.+?)/((?:stripped_)?cherry_[a-z_]+|potted_cherry_sapling)\.json$', path)
        if not m or 'cherry_leaves' in path or 'worldgen' in path or '/tags/' in path or 'cherry_bees' in path:
            continue
        if 'sapling' in path and not spec['sapling']:
            continue
        kind, folder, name = m.groups()
        new = swap(f'minecraft:{name}', wood).split(':')[1]
        copy(path, f'{kind}/{NS}/{folder}/{new}.json', wood)
    for leaves in spec['leaves']:
        copy('assets/minecraft/blockstates/cherry_leaves.json', f'assets/{NS}/blockstates/{leaves}.json', wood, leaves)
        copy('assets/minecraft/models/block/cherry_leaves.json', f'assets/{NS}/models/block/{leaves}.json', wood, leaves)
        copy('assets/minecraft/models/item/cherry_leaves.json', f'assets/{NS}/models/item/{leaves}.json', wood, leaves)
        copy('data/minecraft/loot_table/blocks/cherry_leaves.json', f'data/{NS}/loot_table/blocks/{leaves}.json', wood, leaves)
    # The wood's logs tag (planks, wood and charcoal recipes take any of them).
    logs = [f'{NS}:{wood}_log', f'{NS}:{wood}_wood', f'{NS}:stripped_{wood}_log', f'{NS}:stripped_{wood}_wood']
    write_json(f'data/{NS}/tags/block/{wood}_logs.json', {'values': logs})
    write_json(f'data/{NS}/tags/item/{wood}_logs.json', {'values': logs})

# Every vanilla tag that names a cherry block or item gets the same for each wood (and pink petals' tags get
# maple petals).
tags = {}
for path in sorted(NAMES):
    m = re.match(r'data/minecraft/tags/(block|item)/(.+)\.json$', path)
    if not m or m.group(2) == 'cherry_logs':
        continue
    values = json.loads(read(path))['values']
    added = []
    for value in values:
        if not isinstance(value, str):
            continue
        if value == '#minecraft:cherry_logs':
            added += [f'#{NS}:{wood}_logs' for wood in WOODS]
        elif value == 'minecraft:cherry_leaves':
            added += [f'{NS}:{leaves}' for spec in WOODS.values() for leaves in spec['leaves']]
        elif value == 'minecraft:pink_petals':
            added.append(f'{NS}:maple_petals')
        elif re.fullmatch(r'minecraft:(stripped_|potted_)?cherry_[a-z_]+', value):
            added += [swap(value, wood) for wood in WOODS if 'sapling' not in value or WOODS[wood]['sapling']]
    if added:
        tags[(m.group(1), m.group(2))] = added
OWN_TAGS = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'generated_tags.txt')
own = set(open(OWN_TAGS).read().split()) if os.path.exists(OWN_TAGS) else set()
for (kind, name), values in sorted(tags.items()):
    rel = f'data/minecraft/tags/{kind}/{name}.json'
    # Never overwrite a tag file another tool (or a hand) wrote: those would need merging by hand.
    if os.path.exists(os.path.join(RES, rel)) and rel not in own:
        raise SystemExit(f'{rel} already exists and was not written by this script; merge it by hand first')
    write_json(rel, {'replace': False, 'values': values})
with open(OWN_TAGS, 'w', newline=chr(10)) as f:
    f.write(chr(10).join(sorted(f'data/minecraft/tags/{kind}/{name}.json' for kind, name in tags)) + chr(10))
# Pale oak logs and maple logs burn (vanilla's logs_that_burn lists cherry_logs, so they are in already).

# ---------------------------------------------------------------- maple petals, from pink petals
petals = json.loads(read('assets/minecraft/blockstates/pink_petals.json'))
text = json.dumps(petals).replace('minecraft:block/pink_petals', f'{NS}:block/maple_petals')
write_json(f'assets/{NS}/blockstates/maple_petals.json', json.loads(text))
for i in range(1, 5):
    write_json(f'assets/{NS}/models/block/maple_petals_{i}.json', {
        'parent': f'minecraft:block/flowerbed_{i}',
        'textures': {'flowerbed': f'{NS}:block/maple_petals', 'stem': f'{NS}:block/maple_petals_stem'}})
write_json(f'assets/{NS}/models/item/maple_petals.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/maple_petals'}})
write_json(f'data/{NS}/loot_table/blocks/maple_petals.json',
           json.loads(read('data/minecraft/loot_table/blocks/pink_petals.json').replace('minecraft:pink_petals', f'{NS}:maple_petals')))

# ---------------------------------------------------------------- falling maple leaves
write_json(f'assets/{NS}/particles/maple_leaves.json', {'textures': [f'{NS}:maple_{i}' for i in range(12)]})

# ---------------------------------------------------------------- trees and the autumnal forest's features
cherry_tree = json.loads(read('data/minecraft/worldgen/configured_feature/cherry.json'))
oak_tree = json.loads(read('data/minecraft/worldgen/configured_feature/oak.json'))


def provider(block):
    return {'type': 'minecraft:simple_state_provider', 'state': {'Name': block, 'Properties': {'axis': 'y'}} if block.endswith('_log') else {'Name': block}}


def leaves_state(block):
    return {'type': 'minecraft:simple_state_provider',
            'state': {'Name': block, 'Properties': {'distance': '7', 'persistent': 'false', 'waterlogged': 'false'}}}


# Maple: cherry's broad, branching crown.
maple = json.loads(json.dumps(cherry_tree))
maple['config']['trunk_provider'] = provider(f'{NS}:maple_log')
maple['config']['foliage_provider'] = leaves_state(f'{NS}:maple_leaves')
write_json(f'data/{NS}/worldgen/configured_feature/maple.json', maple)
# Pale oak: a tall, straight trunk with an oak's round crown, in one of three colours.
for colour in ['red', 'orange', 'yellow']:
    tree = json.loads(json.dumps(oak_tree))
    tree['config']['trunk_provider'] = provider(f'{NS}:pale_oak_log')
    tree['config']['foliage_provider'] = leaves_state(f'{NS}:{colour}_pale_oak_leaves')
    tree['config']['trunk_placer'] = {'type': 'minecraft:straight_trunk_placer', 'base_height': 6, 'height_rand_a': 3, 'height_rand_b': 1}
    tree['config']['foliage_placer'] = {'type': 'minecraft:blob_foliage_placer', 'radius': 3, 'offset': 0, 'height': 4}
    write_json(f'data/{NS}/worldgen/configured_feature/{colour}_pale_oak.json', tree)
    write_json(f'data/{NS}/worldgen/placed_feature/{colour}_pale_oak_checked.json', {
        'feature': f'{NS}:{colour}_pale_oak',
        'placement': [{'type': 'minecraft:block_predicate_filter', 'predicate': {
            'type': 'minecraft:would_survive', 'state': {'Name': f'{NS}:pale_oak_sapling', 'Properties': {'stage': '0'}}}}]})
# A pale oak sapling grows any of the three.
write_json(f'data/{NS}/worldgen/configured_feature/pale_oak.json', {'type': 'minecraft:random_selector', 'config': {
    'features': [{'chance': 0.33333334, 'feature': f'{NS}:red_pale_oak_checked'},
                 {'chance': 0.5, 'feature': f'{NS}:orange_pale_oak_checked'}],
    'default': f'{NS}:yellow_pale_oak_checked'}})
write_json(f'data/{NS}/worldgen/placed_feature/maple_checked.json', {
    'feature': f'{NS}:maple',
    'placement': [{'type': 'minecraft:block_predicate_filter', 'predicate': {
        'type': 'minecraft:would_survive', 'state': {'Name': f'{NS}:maple_sapling', 'Properties': {'stage': '0'}}}}]})
# The forest: a dense mix, three maples to two pale oaks, about as thick as a forest.
# Azalea: vanilla's azalea tree, on an azalea trunk (AzaleaTrees puts it in place of some oaks in Eternal Return worlds).
azalea = json.loads(read('data/minecraft/worldgen/configured_feature/azalea_tree.json'))
azalea['config']['trunk_provider'] = provider(f'{NS}:azalea_log')
write_json(f'data/{NS}/worldgen/configured_feature/azalea_tree.json', azalea)
write_json(f'data/{NS}/worldgen/configured_feature/trees_autumnal_forest.json', {'type': 'minecraft:random_selector', 'config': {
    'features': [{'chance': 0.4, 'feature': {'feature': f'{NS}:pale_oak', 'placement': []}}],
    'default': f'{NS}:maple_checked'}})
trees = {'feature': f'{NS}:trees_autumnal_forest', 'placement': [
    {'type': 'minecraft:count', 'count': {'type': 'minecraft:weighted_list', 'distribution': [
        {'data': 10, 'weight': 9}, {'data': 11, 'weight': 1}]}},
    {'type': 'minecraft:in_square'},
    {'type': 'minecraft:surface_water_depth_filter', 'max_water_depth': 0},
    {'type': 'minecraft:heightmap', 'heightmap': 'OCEAN_FLOOR'},
    {'type': 'minecraft:biome'}]}
write_json(f'data/{NS}/worldgen/placed_feature/trees_autumnal_forest.json', trees)
# Fallen maple leaves, patched about like cherry groves' pink petals.
flower = json.loads(read('data/minecraft/worldgen/configured_feature/flower_cherry.json').replace('minecraft:pink_petals', f'{NS}:maple_petals'))
write_json(f'data/{NS}/worldgen/configured_feature/maple_petals.json', flower)
flower_placed = json.loads(read('data/minecraft/worldgen/placed_feature/flower_cherry.json').replace('minecraft:flower_cherry', f'{NS}:maple_petals'))
write_json(f'data/{NS}/worldgen/placed_feature/maple_petals.json', flower_placed)
print('woods written:', ', '.join(WOODS), '+ maple petals, particle, trees')

# ---------------------------------------------------------------- the autumnal forest
# Vanilla's forest (its ores, grass, mushrooms, pumpkins, mobs), with maple and pale oak in place of oak and
# birch, fallen maple leaves in place of its flowers, golden grass (AutumnGrass mixes in orange and red) and
# vampires among the night's monsters.
AUTUMN_GRASS = 0xD4A23C  # the sentinel AutumnGrass looks for; also the grass colour where it doesn't apply
biome = json.loads(read('data/minecraft/worldgen/biome/forest.json'))
vegetal = biome['features'][9]
vegetal[vegetal.index('minecraft:trees_birch_and_oak')] = f'{NS}:trees_autumnal_forest'
vegetal[vegetal.index('minecraft:forest_flowers')] = f'{NS}:maple_petals'
vegetal.remove('minecraft:flower_default')
biome['effects']['grass_color'] = AUTUMN_GRASS
biome['effects']['foliage_color'] = 0xD27F2B
biome['spawners']['monster'].append({'type': f'{NS}:vampire', 'maxCount': 1, 'minCount': 1, 'weight': 40})
write_json(f'data/{NS}/worldgen/biome/autumnal_forest.json', biome)
print('autumnal forest written')
