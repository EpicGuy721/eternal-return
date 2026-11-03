"""Generates the resources for wool and concrete stairs and slabs, and for layered carpets.

Run from anywhere: python tools/blocks/build_blocks.py

Writes (all under src/main/resources):
- stairs and slabs of each of the 16 colors of wool and concrete (blocks registered by
  com.eternalreturn.blocks.CutBlocks): blockstates, block and item models, loot tables, recipes
  (crafting, and the stonecutter for concrete) and tags;
- walls of the stone-like families that have stairs and a slab but no wall, and stairs of the three
  that have a slab but no stairs (both registered by CutBlocks: STONE_WALLS, MORE_STAIRS, whose tables
  must match these): blockstates, block and item models, loot tables, recipes (crafting and the
  stonecutter), tags and their English names in en_us.json (other lines kept);
- layered carpets (com.eternalreturn.blocks.CarpetLayers, CarpetBlockMixin): vanilla's carpet
  blockstates with a variant per layer count, a model per carpet and layer count (2 to 16; one layer
  keeps vanilla's model), and vanilla's carpet loot tables dropping one carpet per layer.

Do not hand-edit the generated files; change this script and rerun it.
"""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources')
NS = 'eternalreturn'
COLORS = ['white', 'light_gray', 'gray', 'black', 'brown', 'red', 'orange', 'yellow', 'lime', 'green', 'cyan', 'light_blue', 'blue',
          'purple', 'magenta', 'pink']
MATERIALS = ['wool', 'concrete']
MAX_LAYERS = 16


def write_json(path, data):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


# ---------------------------------------------------------------- stairs and slabs

def stairs_blockstate(model):
    """Vanilla's stairs blockstate: the same rotations its data generator writes."""
    base_y = {'east': 0, 'south': 90, 'west': 180, 'north': 270}
    variants = {}
    for facing in ['east', 'north', 'south', 'west']:
        for half in ['bottom', 'top']:
            for shape in ['inner_left', 'inner_right', 'outer_left', 'outer_right', 'straight']:
                kind = 'inner' if shape.startswith('inner') else 'outer' if shape.startswith('outer') else 'straight'
                y = base_y[facing]
                left = shape.endswith('_left')
                right = shape.endswith('_right')
                if half == 'bottom' and left:
                    y -= 90
                if half == 'top' and right:
                    y += 90
                y %= 360
                variant = {'model': model + {'inner': '_inner', 'outer': '_outer', 'straight': ''}[kind]}
                if half == 'top':
                    variant['x'] = 180
                if y:
                    variant['y'] = y
                if half == 'top' or y:
                    variant['uvlock'] = True
                variants[f'facing={facing},half={half},shape={shape}'] = variant
    return {'variants': variants}


def simple_loot(block_id):
    return {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [{'type': 'minecraft:item', 'name': block_id}],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        'random_sequence': block_id.replace(':', ':blocks/'),
    }


def slab_loot(block_id):
    return {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [{
            'type': 'minecraft:item', 'name': block_id,
            'functions': [
                {'function': 'minecraft:set_count', 'count': 2.0, 'add': False,
                 'conditions': [{'condition': 'minecraft:block_state_property', 'block': block_id, 'properties': {'type': 'double'}}]},
                {'function': 'minecraft:explosion_decay'},
            ]}]}],
        'random_sequence': block_id.replace(':', ':blocks/'),
    }


cut = {'wool': {'stairs': [], 'slab': []}, 'concrete': {'stairs': [], 'slab': []}}
for color in COLORS:
    for material in MATERIALS:
        base = f'{color}_{material}'
        texture = f'minecraft:block/{base}'
        stairs = f'{base}_stairs'
        slab = f'{base}_slab'
        cut[material]['stairs'].append(f'{NS}:{stairs}')
        cut[material]['slab'].append(f'{NS}:{slab}')
        sides = {'bottom': texture, 'top': texture, 'side': texture}
        for suffix, parent in [('', 'stairs'), ('_inner', 'inner_stairs'), ('_outer', 'outer_stairs')]:
            write_json(f'assets/{NS}/models/block/{stairs}{suffix}.json', {'parent': f'minecraft:block/{parent}', 'textures': sides})
        write_json(f'assets/{NS}/models/block/{slab}.json', {'parent': 'minecraft:block/slab', 'textures': sides})
        write_json(f'assets/{NS}/models/block/{slab}_top.json', {'parent': 'minecraft:block/slab_top', 'textures': sides})
        write_json(f'assets/{NS}/blockstates/{stairs}.json', stairs_blockstate(f'{NS}:block/{stairs}'))
        write_json(f'assets/{NS}/blockstates/{slab}.json', {'variants': {
            'type=bottom': {'model': f'{NS}:block/{slab}'},
            'type=double': {'model': f'minecraft:block/{base}'},
            'type=top': {'model': f'{NS}:block/{slab}_top'},
        }})
        write_json(f'assets/{NS}/models/item/{stairs}.json', {'parent': f'{NS}:block/{stairs}'})
        write_json(f'assets/{NS}/models/item/{slab}.json', {'parent': f'{NS}:block/{slab}'})
        write_json(f'data/{NS}/loot_table/blocks/{stairs}.json', simple_loot(f'{NS}:{stairs}'))
        write_json(f'data/{NS}/loot_table/blocks/{slab}.json', slab_loot(f'{NS}:{slab}'))
        ingredient = {'item': f'minecraft:{base}'}
        write_json(f'data/{NS}/recipe/{stairs}.json', {
            'type': 'minecraft:crafting_shaped', 'category': 'building', 'group': f'{material}_stairs',
            'key': {'#': ingredient}, 'pattern': ['#  ', '## ', '###'], 'result': {'count': 4, 'id': f'{NS}:{stairs}'}})
        write_json(f'data/{NS}/recipe/{slab}.json', {
            'type': 'minecraft:crafting_shaped', 'category': 'building', 'group': f'{material}_slab',
            'key': {'#': ingredient}, 'pattern': ['###'], 'result': {'count': 6, 'id': f'{NS}:{slab}'}})
        if material == 'concrete':
            write_json(f'data/{NS}/recipe/{stairs}_from_{base}_stonecutting.json', {
                'type': 'minecraft:stonecutting', 'ingredient': ingredient, 'result': {'count': 1, 'id': f'{NS}:{stairs}'}})
            write_json(f'data/{NS}/recipe/{slab}_from_{base}_stonecutting.json', {
                'type': 'minecraft:stonecutting', 'ingredient': ingredient, 'result': {'count': 2, 'id': f'{NS}:{slab}'}})

for material in MATERIALS:
    write_json(f'data/{NS}/tags/block/{material}_cut_blocks.json', {'values': cut[material]['stairs'] + cut[material]['slab']})
all_stairs = cut['wool']['stairs'] + cut['concrete']['stairs']
all_slabs = cut['wool']['slab'] + cut['concrete']['slab']
for kind in ['block', 'item']:
    write_json(f'data/minecraft/tags/{kind}/stairs.json', {'replace': False, 'values': all_stairs})
    write_json(f'data/minecraft/tags/{kind}/slabs.json', {'replace': False, 'values': all_slabs})
# Wool stairs and slabs are wool to shears (quick to cut) and to sculk (they muffle vibrations).
write_json('data/minecraft/tags/block/wool.json', {'replace': False, 'values': [f'#{NS}:wool_cut_blocks']})

# ---------------------------------------------------------------- stone walls and the missing stairs

# Wall id -> (vanilla block it's cut from, its texture, English name). Must match CutBlocks.STONE_WALL_TABLE.
STONE_WALLS = {
    'stone_wall': ('stone', 'stone', 'Stone Wall'),
    'smooth_stone_wall': ('smooth_stone', 'smooth_stone', 'Smooth Stone Wall'),
    'polished_granite_wall': ('polished_granite', 'polished_granite', 'Polished Granite Wall'),
    'polished_diorite_wall': ('polished_diorite', 'polished_diorite', 'Polished Diorite Wall'),
    'polished_andesite_wall': ('polished_andesite', 'polished_andesite', 'Polished Andesite Wall'),
    'cut_sandstone_wall': ('cut_sandstone', 'cut_sandstone', 'Cut Sandstone Wall'),
    'smooth_sandstone_wall': ('smooth_sandstone', 'sandstone_top', 'Smooth Sandstone Wall'),
    'cut_red_sandstone_wall': ('cut_red_sandstone', 'cut_red_sandstone', 'Cut Red Sandstone Wall'),
    'smooth_red_sandstone_wall': ('smooth_red_sandstone', 'red_sandstone_top', 'Smooth Red Sandstone Wall'),
    'quartz_wall': ('quartz_block', 'quartz_block_side', 'Quartz Wall'),
    'smooth_quartz_wall': ('smooth_quartz', 'quartz_block_bottom', 'Smooth Quartz Wall'),
    'purpur_wall': ('purpur_block', 'purpur_block', 'Purpur Wall'),
    'prismarine_brick_wall': ('prismarine_bricks', 'prismarine_bricks', 'Prismarine Brick Wall'),
    'dark_prismarine_wall': ('dark_prismarine', 'dark_prismarine', 'Dark Prismarine Wall'),
}
# Stairs id -> (vanilla block, side, top and bottom textures as its slab has them, English name). Must match
# CutBlocks.MORE_STAIRS_TABLE.
MORE_STAIRS = {
    'smooth_stone_stairs': ('smooth_stone', 'smooth_stone', 'smooth_stone', 'Smooth Stone Stairs'),
    'cut_sandstone_stairs': ('cut_sandstone', 'cut_sandstone', 'sandstone_top', 'Cut Sandstone Stairs'),
    'cut_red_sandstone_stairs': ('cut_red_sandstone', 'cut_red_sandstone', 'red_sandstone_top', 'Cut Red Sandstone Stairs'),
}


def wall_blockstate(model):
    """Vanilla's wall blockstate: a post when up, a low or tall side toward each neighbour."""
    parts = [{'when': {'up': 'true'}, 'apply': {'model': model + '_post'}}]
    for height, suffix in [('low', '_side'), ('tall', '_side_tall')]:
        for side, y in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
            apply = {'model': model + suffix, 'uvlock': True}
            if y:
                apply['y'] = y
            parts.append({'when': {side: height}, 'apply': apply})
    return {'multipart': parts}


stone_cut = []
names = {}
for wall, (base, texture, name) in STONE_WALLS.items():
    stone_cut.append(f'{NS}:{wall}')
    names[f'block.{NS}.{wall}'] = name
    textures = {'wall': f'minecraft:block/{texture}'}
    for suffix, parent in [('_post', 'template_wall_post'), ('_side', 'template_wall_side'), ('_side_tall', 'template_wall_side_tall'),
                           ('_inventory', 'wall_inventory')]:
        write_json(f'assets/{NS}/models/block/{wall}{suffix}.json', {'parent': f'minecraft:block/{parent}', 'textures': textures})
    write_json(f'assets/{NS}/blockstates/{wall}.json', wall_blockstate(f'{NS}:block/{wall}'))
    write_json(f'assets/{NS}/models/item/{wall}.json', {'parent': f'{NS}:block/{wall}_inventory'})
    write_json(f'data/{NS}/loot_table/blocks/{wall}.json', simple_loot(f'{NS}:{wall}'))
    ingredient = {'item': f'minecraft:{base}'}
    write_json(f'data/{NS}/recipe/{wall}.json', {
        'type': 'minecraft:crafting_shaped', 'category': 'misc', 'key': {'#': ingredient}, 'pattern': ['###', '###'],
        'result': {'count': 6, 'id': f'{NS}:{wall}'}})
    write_json(f'data/{NS}/recipe/{wall}_from_{base}_stonecutting.json', {
        'type': 'minecraft:stonecutting', 'ingredient': ingredient, 'result': {'count': 1, 'id': f'{NS}:{wall}'}})

more_stairs = []
for stairs, (base, side, end, name) in MORE_STAIRS.items():
    stone_cut.append(f'{NS}:{stairs}')
    more_stairs.append(f'{NS}:{stairs}')
    names[f'block.{NS}.{stairs}'] = name
    sides = {'bottom': f'minecraft:block/{end}', 'top': f'minecraft:block/{end}', 'side': f'minecraft:block/{side}'}
    for suffix, parent in [('', 'stairs'), ('_inner', 'inner_stairs'), ('_outer', 'outer_stairs')]:
        write_json(f'assets/{NS}/models/block/{stairs}{suffix}.json', {'parent': f'minecraft:block/{parent}', 'textures': sides})
    write_json(f'assets/{NS}/blockstates/{stairs}.json', stairs_blockstate(f'{NS}:block/{stairs}'))
    write_json(f'assets/{NS}/models/item/{stairs}.json', {'parent': f'{NS}:block/{stairs}'})
    write_json(f'data/{NS}/loot_table/blocks/{stairs}.json', simple_loot(f'{NS}:{stairs}'))
    ingredient = {'item': f'minecraft:{base}'}
    write_json(f'data/{NS}/recipe/{stairs}.json', {
        'type': 'minecraft:crafting_shaped', 'category': 'building', 'key': {'#': ingredient}, 'pattern': ['#  ', '## ', '###'],
        'result': {'count': 4, 'id': f'{NS}:{stairs}'}})
    write_json(f'data/{NS}/recipe/{stairs}_from_{base}_stonecutting.json', {
        'type': 'minecraft:stonecutting', 'ingredient': ingredient, 'result': {'count': 1, 'id': f'{NS}:{stairs}'}})

# Pickaxe blocks (data/minecraft/tags/block/mineable/pickaxe.json lists this tag).
write_json(f'data/{NS}/tags/block/stone_cut_blocks.json', {'values': stone_cut})
stone_walls = [f'{NS}:{wall}' for wall in STONE_WALLS]
for kind in ['block', 'item']:
    write_json(f'data/minecraft/tags/{kind}/stairs.json', {'replace': False, 'values': all_stairs + more_stairs})
    write_json(f'data/minecraft/tags/{kind}/walls.json', {'replace': False, 'values': stone_walls})

lang_file = os.path.join(ROOT, 'assets', NS, 'lang', 'en_us.json')
with open(lang_file, encoding='utf-8') as f:
    lang = json.load(f)
lang.update(names)
with open(lang_file, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(lang, f, indent='\t', ensure_ascii=False)
    f.write('\n')

# ---------------------------------------------------------------- layered carpets

CARPETS = [(f'{color}_carpet', f'minecraft:block/{color}_wool') for color in COLORS] + [('moss_carpet', 'minecraft:block/moss_block')]

for layers in range(2, MAX_LAYERS + 1):
    full = layers == MAX_LAYERS
    side = {'uv': [0, 16 - layers, 16, 16], 'texture': '#wool'}
    write_json(f'assets/{NS}/models/block/carpet_layers_{layers}.json', {
        'parent': 'minecraft:block/block',
        'textures': {'particle': '#wool'},
        'elements': [{
            'from': [0, 0, 0], 'to': [16, layers, 16],
            'faces': {
                'down': {'uv': [0, 0, 16, 16], 'texture': '#wool', 'cullface': 'down'},
                'up': dict({'uv': [0, 0, 16, 16], 'texture': '#wool'}, **({'cullface': 'up'} if full else {})),
                'north': dict(side, cullface='north'),
                'south': dict(side, cullface='south'),
                'west': dict(side, cullface='west'),
                'east': dict(side, cullface='east'),
            },
        }],
    })

for carpet, texture in CARPETS:
    variants = {'layers=1': {'model': f'minecraft:block/{carpet}'}}
    for layers in range(2, MAX_LAYERS + 1):
        model = f'{NS}:block/carpet/{carpet}_{layers}'
        write_json(f'assets/{NS}/models/block/carpet/{carpet}_{layers}.json', {'parent': f'{NS}:block/carpet_layers_{layers}', 'textures': {'wool': texture}})
        variants[f'layers={layers}'] = {'model': model}
    write_json(f'assets/minecraft/blockstates/{carpet}.json', {'variants': variants})
    block_id = f'minecraft:{carpet}'
    write_json(f'data/minecraft/loot_table/blocks/{carpet}.json', {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [{
            'type': 'minecraft:item', 'name': block_id,
            'functions': [{'function': 'minecraft:set_count', 'count': float(layers), 'add': False,
                           'conditions': [{'condition': 'minecraft:block_state_property', 'block': block_id, 'properties': {'layers': str(layers)}}]}
                          for layers in range(2, MAX_LAYERS + 1)]}],
            'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        'random_sequence': f'minecraft:blocks/{carpet}',
    })

print(f'wrote {len(COLORS) * len(MATERIALS)} stairs and slabs, {len(STONE_WALLS)} stone walls, {len(MORE_STAIRS)} more stairs '
      f'and {len(CARPETS)} layered carpets')
