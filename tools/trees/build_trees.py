"""Writes the tree species of the biome tree tables (worldgen expansion phase 2):

- data/eternalreturn/worldgen/configured_feature/tree/*.json: shapes built from vanilla's trunk and foliage placers,
  one or more per wood that grows in Eternal Return worlds, each after a real tree;
- data/eternalreturn/structure/tree/*.nbt: hand-shaped trees no placer can make (a weeping willow, a baobab), with
  the trunk at the middle of the bottom layer. Every leaf is checked to be within 6 blocks of a log, so none decays.

Which biome grows what is not here: that is the tables in data/eternalreturn/eternalreturn/tree_tables/, which are
hand-written and can be tuned freely.

Run: python tools/trees/build_trees.py
"""
import gzip
import io
import json
import math
import os
import random
import struct
from collections import deque

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, 'src/main/resources/data/eternalreturn')
NS = 'eternalreturn'
DATA_VERSION = 3955  # Minecraft 1.21.1


def write_json(rel, obj):
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def uniform(lo, hi):
    return {'type': 'minecraft:uniform', 'min_inclusive': lo, 'max_inclusive': hi}


def state(block, **props):
    s = {'Name': block}
    if props:
        s['Properties'] = props
    return s


def log(block):
    return {'type': 'minecraft:simple_state_provider', 'state': state(block, axis='y')}


def leaves(*blocks):
    states = [state(b, distance='7', persistent='false', waterlogged='false') for b in blocks]
    if len(states) == 1:
        return {'type': 'minecraft:simple_state_provider', 'state': states[0]}
    return {'type': 'minecraft:weighted_state_provider', 'entries': [{'data': s, 'weight': w} for s, w in zip(states, [3, 1, 1])]}


def size(limit=1, lower=0, upper=1):
    return {'type': 'minecraft:two_layers_feature_size', 'limit': limit, 'lower_size': lower, 'upper_size': upper}


def tree(trunk, foliage, trunk_placer, foliage_placer, minimum_size=None, decorators=(), dirt='minecraft:dirt', force_dirt=False):
    return {'type': 'minecraft:tree', 'config': {
        'decorators': list(decorators),
        'dirt_provider': {'type': 'minecraft:simple_state_provider', 'state': state(dirt)},
        'foliage_placer': foliage_placer,
        'foliage_provider': foliage,
        'force_dirt': force_dirt,
        'ignore_vines': not decorators,
        'minimum_size': minimum_size or size(),
        'trunk_placer': trunk_placer,
        'trunk_provider': trunk,
    }}


def straight(base, a=0, b=0):
    return {'type': 'minecraft:straight_trunk_placer', 'base_height': base, 'height_rand_a': a, 'height_rand_b': b}


def blob(radius, height, offset=0):
    return {'type': 'minecraft:blob_foliage_placer', 'radius': radius, 'offset': offset, 'height': height}


def spruce_foliage(radius, offset, trunk_height):
    return {'type': 'minecraft:spruce_foliage_placer', 'radius': radius, 'offset': offset, 'trunk_height': trunk_height}


def cherry_foliage(radius, height, hanging, extension=0.33333334):
    return {'type': 'minecraft:cherry_foliage_placer', 'radius': radius, 'offset': 0, 'height': height,
            'wide_bottom_layer_hole_chance': 0.25, 'corner_hole_chance': 0.25,
            'hanging_leaves_chance': hanging, 'hanging_leaves_extension_chance': extension}


def bush(radius=2):
    return {'type': 'minecraft:bush_foliage_placer', 'radius': radius, 'offset': 1, 'height': 2}


def acacia_foliage(radius):
    return {'type': 'minecraft:acacia_foliage_placer', 'radius': radius, 'offset': 0}


def forking(base, a, b):
    return {'type': 'minecraft:forking_trunk_placer', 'base_height': base, 'height_rand_a': a, 'height_rand_b': b}


def bending(base, a, bend_lo, bend_hi, min_leaves=3):
    return {'type': 'minecraft:bending_trunk_placer', 'base_height': base, 'height_rand_a': a, 'height_rand_b': 0,
            'min_height_for_leaves': min_leaves, 'bend_length': uniform(bend_lo, bend_hi)}


def random_spread(radius, height, attempts):
    return {'type': 'minecraft:random_spread_foliage_placer', 'radius': radius, 'offset': 0,
            'foliage_height': height, 'leaf_placement_attempts': attempts}


OAK = (log('minecraft:oak_log'), leaves('minecraft:oak_leaves'))
SPRUCE = (log('minecraft:spruce_log'), leaves('minecraft:spruce_leaves'))
BIRCH = (log('minecraft:birch_log'), leaves('minecraft:birch_leaves'))
JUNGLE = (log('minecraft:jungle_log'), leaves('minecraft:jungle_leaves'))
ACACIA = (log('minecraft:acacia_log'), leaves('minecraft:acacia_leaves'))
DARK_OAK = (log('minecraft:dark_oak_log'), leaves('minecraft:dark_oak_leaves'))
CHERRY = (log('minecraft:cherry_log'), leaves('minecraft:cherry_leaves'))
MAPLE = (log(f'{NS}:maple_log'), leaves(f'{NS}:maple_leaves'))
AZALEA = (log(f'{NS}:azalea_log'), leaves('minecraft:azalea_leaves', 'minecraft:flowering_azalea_leaves'))
PALE_OAK_COLOURS = ['red', 'orange', 'yellow']

# name -> (real tree it follows, configured feature). Mega trees grow from four saplings in a square.
SPECIES = {
    # Oak
    'white_oak': ('White oak: a great spreading crown on a tall, branching trunk',
                  tree(*OAK, {'type': 'minecraft:fancy_trunk_placer', 'base_height': 6, 'height_rand_a': 8, 'height_rand_b': 2},
                       {'type': 'minecraft:fancy_foliage_placer', 'radius': 3, 'offset': 4, 'height': 4},
                       {'type': 'minecraft:two_layers_feature_size', 'limit': 0, 'lower_size': 0, 'upper_size': 0, 'min_clipped_height': 4})),
    'bur_oak': ('Bur oak: a short stout trunk under a wide, low, round crown, the oak of open fields',
                tree(*OAK, straight(4, 1), blob(3, 3), size(1, 0, 2))),
    'bald_cypress': ('Bald cypress: a tall straight swamp trunk with a narrow pointed crown and hanging moss (vines)',
                     tree(*OAK, straight(10, 3, 2), spruce_foliage(uniform(1, 2), uniform(0, 1), uniform(4, 6)), size(2, 0, 2),
                          decorators=[{'type': 'minecraft:leave_vine', 'probability': 0.3}])),
    'scrub_oak': ('Scrub oak: a low, crooked oak of dry hills, as wide as it is tall',
                  tree(*OAK, bending(2, 1, 1, 2, 1), blob(2, 3, 1), size(1, 0, 1))),
    'shrub_oak': ('Shrub oak: a knee-high thicket of oak on a single short stem',
                  tree(*OAK, straight(1), bush(2), size(0, 0, 0))),
    # Spruce
    'grand_fir': ('Grand fir: a tall, dense, broad cone of a tree',
                  tree(*SPRUCE, straight(10, 3, 2), spruce_foliage(uniform(3, 4), uniform(0, 1), uniform(1, 2)), size(2, 0, 3))),
    'black_spruce': ('Black spruce: a thin spire of a tree, needles close to a spindly trunk, a tuft at the top',
                     tree(*SPRUCE, straight(8, 3), spruce_foliage(uniform(0, 1), uniform(1, 2), uniform(1, 2)), size(1, 0, 1))),
    'redwood': ('Coast redwood (mega): a colossal trunk, bare for most of its height, with a small crown far above',
                tree(*SPRUCE, {'type': 'minecraft:giant_trunk_placer', 'base_height': 26, 'height_rand_a': 6, 'height_rand_b': 8},
                     {'type': 'minecraft:mega_pine_foliage_placer', 'radius': 0, 'offset': 0, 'crown_height': uniform(6, 10)},
                     size(1, 1, 2),
                     decorators=[{'type': 'minecraft:alter_ground', 'provider': {'type': 'minecraft:simple_state_provider',
                                                                                 'state': state('minecraft:podzol', snowy='false')}}])),
    # Birch
    'poplar': ('Lombardy poplar: a tall, narrow column of leaves',
               tree(*BIRCH, straight(9, 3), spruce_foliage(1, uniform(0, 1), uniform(2, 3)), size(2, 0, 1))),
    'weeping_birch': ('Silver birch: a light crown with long hanging twigs',
                      tree(*BIRCH, straight(6, 2), cherry_foliage(3, 5, 0.45, 0.5), size(1, 0, 2))),
    'river_birch': ('River birch: two or three leaning stems from one root, each with its own crown',
                    tree(*BIRCH, forking(5, 2, 1), blob(2, 3), size(1, 0, 2))),
    # Jungle
    'kapok': ('Kapok (mega): an emergent giant, its flat crown spread above the jungle roof',
              tree(*JUNGLE, {'type': 'minecraft:mega_jungle_trunk_placer', 'base_height': 20, 'height_rand_a': 4, 'height_rand_b': 8},
                   acacia_foliage(4), size(1, 1, 2), decorators=[{'type': 'minecraft:trunk_vine'}])),
    'teak': ('Teak: a tall, clean, straight trunk with a round crown on top',
             tree(*JUNGLE, straight(13, 4), blob(3, 3), size(1, 0, 1),
                  decorators=[{'type': 'minecraft:cocoa', 'probability': 0.2}])),
    # Acacia
    'umbrella_thorn': ('Umbrella thorn acacia: a wide, flat crown on a forked trunk',
                       tree(*ACACIA, forking(4, 2, 1), acacia_foliage(3), size(1, 0, 2))),
    'thorn_bush': ('Whistling thorn: a low, spiky acacia bush',
                   tree(*ACACIA, straight(1, 1), bush(2), size(0, 0, 0))),
    # Dark oak
    'ancient_dark_oak': ('Ancient dark oak (mega): an old, tall dark oak with a heavy crown',
                         tree(*DARK_OAK, {'type': 'minecraft:dark_oak_trunk_placer', 'base_height': 9, 'height_rand_a': 3, 'height_rand_b': 2},
                              {'type': 'minecraft:dark_oak_foliage_placer', 'radius': 0, 'offset': 0},
                              {'type': 'minecraft:three_layers_feature_size', 'limit': 1, 'lower_size': 0, 'middle_size': 1, 'upper_limit': 1, 'upper_size': 2})),
    'hornbeam': ('Hornbeam: a dark, dense, round tree on a single fluted trunk; the dark oak sapling grows it on its own',
                 tree(*DARK_OAK, straight(5, 2), blob(3, 4), size(1, 0, 2))),
    # Cherry
    'weeping_cherry': ('Weeping cherry: long curtains of blossom hanging from wide branches',
                       tree(*CHERRY, {'type': 'minecraft:cherry_trunk_placer', 'base_height': 8, 'height_rand_a': 1, 'height_rand_b': 0,
                                      'branch_count': {'type': 'minecraft:weighted_list', 'distribution': [{'data': 2, 'weight': 1}, {'data': 3, 'weight': 1}]},
                                      'branch_horizontal_length': uniform(3, 4), 'branch_start_offset_from_top': {'min_inclusive': -4, 'max_inclusive': -3},
                                      'branch_end_offset_from_top': uniform(-1, 0)},
                            cherry_foliage(4, 5, 0.5, 0.6), size(1, 0, 2))),
    'mountain_cherry': ('Mountain cherry: a small, upright cherry with a round crown',
                        tree(*CHERRY, straight(4, 2), blob(2, 3), size(1, 0, 1))),
    # Maple
    'sugar_maple': ('Sugar maple: a tall trunk with a big, round, dense crown',
                    tree(*MAPLE, straight(7, 3), blob(3, 4), size(1, 0, 2))),
    'japanese_maple': ('Japanese maple: low and wide, a crooked trunk under a spreading, airy crown',
                       tree(*MAPLE, bending(3, 1, 1, 2, 2), random_spread(3, 3, 60), size(1, 0, 2))),
    # Azalea
    'tall_azalea': ('Tall azalea: a straight trunk with a loose, high crown of leaves and blossom',
                    tree(*AZALEA, straight(5, 2), random_spread(3, 3, 70), size(1, 0, 1), dirt='minecraft:rooted_dirt', force_dirt=True)),
    'azalea_shrub': ('Azalea shrub: a rounded bush of leaves and blossom on a stub of trunk',
                     tree(*AZALEA, straight(1), bush(2), size(0, 0, 0), dirt='minecraft:rooted_dirt', force_dirt=True)),
}

def mangrove(base, rand_b, steps, branch_chance, radius, height, attempts, roots_lo, roots_hi):
    config = json.loads(json.dumps(VANILLA_MANGROVE))
    trunk = config['config']['trunk_placer']
    trunk['base_height'] = base
    trunk['height_rand_b'] = rand_b
    trunk['extra_branch_steps'] = uniform(*steps)
    trunk['place_branch_per_log_probability'] = branch_chance
    config['config']['foliage_placer'] = random_spread(radius, height, attempts)
    config['config']['root_placer']['trunk_offset_y'] = uniform(roots_lo, roots_hi)
    return config


# Vanilla 1.21.1's mangrove.json, trimmed of its bee nest decorator.
VANILLA_MANGROVE = {'type': 'minecraft:tree', 'config': {
    'decorators': [
        {'type': 'minecraft:leave_vine', 'probability': 0.125},
        {'type': 'minecraft:attached_to_leaves', 'block_provider': {
            'type': 'minecraft:randomized_int_state_provider', 'property': 'age',
            'source': {'type': 'minecraft:simple_state_provider', 'state': state('minecraft:mangrove_propagule', age='0', hanging='true', stage='0', waterlogged='false')},
            'values': uniform(0, 4)},
         'directions': ['down'], 'exclusion_radius_xz': 1, 'exclusion_radius_y': 0, 'probability': 0.14, 'required_empty_blocks': 2}],
    'dirt_provider': {'type': 'minecraft:simple_state_provider', 'state': state('minecraft:dirt')},
    'foliage_placer': random_spread(3, 2, 70),
    'foliage_provider': leaves('minecraft:mangrove_leaves'),
    'force_dirt': False,
    'ignore_vines': True,
    'minimum_size': size(2, 0, 2),
    'root_placer': {
        'type': 'minecraft:mangrove_root_placer',
        'above_root_placement': {'above_root_placement_chance': 0.5,
                                 'above_root_provider': {'type': 'minecraft:simple_state_provider', 'state': state('minecraft:moss_carpet')}},
        'mangrove_root_placement': {
            'can_grow_through': '#minecraft:mangrove_roots_can_grow_through', 'max_root_length': 15, 'max_root_width': 8,
            'muddy_roots_in': ['minecraft:mud', 'minecraft:muddy_mangrove_roots'],
            'muddy_roots_provider': {'type': 'minecraft:simple_state_provider', 'state': state('minecraft:muddy_mangrove_roots', axis='y')},
            'random_skew_chance': 0.2},
        'root_provider': {'type': 'minecraft:simple_state_provider', 'state': state('minecraft:mangrove_roots', waterlogged='false')},
        'trunk_offset_y': uniform(1, 3)},
    'trunk_placer': {'type': 'minecraft:upwards_branching_trunk_placer', 'base_height': 2,
                     'can_grow_through': '#minecraft:mangrove_logs_can_grow_through',
                     'extra_branch_length': uniform(0, 1), 'extra_branch_steps': uniform(1, 4),
                     'height_rand_a': 1, 'height_rand_b': 4, 'place_branch_per_log_probability': 0.5},
    'trunk_provider': log('minecraft:mangrove_log'),
}}

SPECIES.update({
    'red_mangrove': ('Red mangrove: a tall crown standing high on a tangle of arching stilt roots',
                     mangrove(4, 4, (2, 5), 0.4, 3, 3, 80, 2, 3)),
    'black_mangrove': ('Black mangrove: low and spreading, many branches under a wide flat crown',
                       mangrove(1, 2, (2, 6), 0.75, 4, 2, 100, 1, 2)),
    'dwarf_mangrove': ('Dwarf mangrove: a knee-high, bushy mangrove on short roots',
                       mangrove(1, 1, (1, 2), 0.5, 2, 2, 40, 1, 1)),
})


# Pale oak shapes, each in the three leaf colours, one picked at random as the pale oak sapling does.
PALE_OAK_SHAPES = {
    'pale_aspen': ('Aspen: a slim, pale trunk with a narrow crown, in red, orange or yellow',
                   lambda colour: tree(log(f'{NS}:pale_oak_log'), leaves(f'{NS}:{colour}_pale_oak_leaves'), straight(8, 3), blob(2, 4), size(1, 0, 1))),
    'spreading_pale_oak': ('Spreading pale oak: a broad, branching crown, in red, orange or yellow',
                           lambda colour: tree(log(f'{NS}:pale_oak_log'), leaves(f'{NS}:{colour}_pale_oak_leaves'),
                                               {'type': 'minecraft:fancy_trunk_placer', 'base_height': 5, 'height_rand_a': 6, 'height_rand_b': 0},
                                               {'type': 'minecraft:fancy_foliage_placer', 'radius': 2, 'offset': 4, 'height': 4},
                                               {'type': 'minecraft:two_layers_feature_size', 'limit': 0, 'lower_size': 0, 'upper_size': 0, 'min_clipped_height': 4})),
}


# --- Templates ---------------------------------------------------------------------------------------------------

def leaf_distances(blocks):
    """Steps from each leaf to the nearest log through leaves and logs, as the game counts them."""
    dist = {p: 0 for p, b in blocks.items() if b.endswith('_log')}
    queue = deque(dist)
    while queue:
        p = queue.popleft()
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
            if n in blocks and n not in dist and blocks[n].endswith('_leaves'):
                dist[n] = dist[p] + 1
                queue.append(n)
    return dist


def willow():
    """Weeping willow (oak): a short thick trunk, branches arching out, a dome of leaves and curtains hanging from its rim."""
    rng = random.Random(11)
    b = {}
    for y in range(6):
        b[(0, y, 0)] = 'minecraft:oak_log'
    # Four branches climbing out to 3 blocks, then a short rise.
    tips = []
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        x, y, z = 0, 4, 0
        for step in range(3):
            x, z = x + dx, z + dz
            y += 1 if step == 1 else 0
            b[(x, y, z)] = 'minecraft:oak_log'
        tips.append((x, y, z))
    b[(0, 6, 0)] = 'minecraft:oak_log'
    # Dome: radius 4 at y 6, shrinking upward.
    for y, radius in ((5, 4.4), (6, 4.6), (7, 4.0), (8, 2.8)):
        for x in range(-5, 6):
            for z in range(-5, 6):
                if x * x + z * z <= radius * radius and (x, y, z) not in b:
                    b[(x, y, z)] = 'minecraft:oak_leaves'
    # Curtains: from the dome's rim, hanging 2 to 4 blocks.
    for x in range(-5, 6):
        for z in range(-5, 6):
            r = math.sqrt(x * x + z * z)
            if 2.8 <= r <= 4.4 and rng.random() < 0.6:
                for y in range(4, 4 - rng.randint(1, 3), -1):
                    b.setdefault((x, y, z), 'minecraft:oak_leaves')
    return b


def baobab():
    """Baobab (acacia): a fat bottle-shaped trunk, a ring of short thick branches on top, small tufts of leaves."""
    b = {}
    for y in range(8):
        for x in range(-1, 2):
            for z in range(-1, 2):
                corner = abs(x) == 1 and abs(z) == 1
                if not corner or y < 2:
                    b[(x, y, z)] = 'minecraft:acacia_log'
    for y in range(8, 10):
        b[(0, y, 0)] = 'minecraft:acacia_log'
    # Branches: out and up from the top of the trunk, in eight directions.
    tips = []
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        x, y, z = dx, 8, dz
        for step in range(2):
            x, z = x + dx, z + dz
            y += step
            b[(x, y, z)] = 'minecraft:acacia_log'
        tips.append((x, y, z))
    for tx, ty, tz in tips:
        for x in range(tx - 2, tx + 3):
            for z in range(tz - 2, tz + 3):
                for y in (ty + 1, ty + 2):
                    flat = abs(x - tx) + abs(z - tz)
                    if flat <= (2 if y == ty + 1 else 1):
                        b.setdefault((x, y, z), 'minecraft:acacia_leaves')
    return b


def connect(blocks, a, b, block):
    """Fills a straight run from a to b one axis at a time (x, then z, then y), so every block touches the last by a face."""
    x, y, z = a
    blocks.setdefault((x, y, z), block)
    for axis, target in ((0, b[0]), (2, b[2]), (1, b[1])):
        while (x, y, z)[axis] != target:
            step = 1 if target > (x, y, z)[axis] else -1
            if axis == 0:
                x += step
            elif axis == 1:
                y += step
            else:
                z += step
            blocks.setdefault((x, y, z), block)


def prune(blocks):
    """Drops leaves more than 6 from a log (they would decay), and any left hanging by nothing."""
    dist = leaf_distances(blocks)
    return {p: b for p, b in blocks.items() if not b.endswith('_leaves') or dist.get(p, 99) <= 6}


def larch(seed, height):
    """Larch (spruce): a straight trunk with open tiers of short level branches, gaps of bare trunk between them,
    the tiers shrinking upward to a thin spire; needles in loose tufts along each branch, the tips drooping."""
    rng = random.Random(seed)
    b = {}
    for y in range(height):
        b[(0, y, 0)] = 'minecraft:spruce_log'
    tier = 3
    while tier < height - 2:
        reach = max(1, round(3.2 * (height - tier) / height + 0.4))
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if rng.random() < 0.15:
                continue
            length = max(1, reach - rng.randint(0, 1))
            for k in range(1, length + 1):
                b[(dx * k, tier, dz * k)] = 'minecraft:spruce_log'
            for k in range(1, length + 2):
                for side in (-1, 0, 1):
                    px, pz = dx * k + dz * side, dz * k + dx * side
                    if rng.random() < 0.8:
                        b.setdefault((px, tier, pz), 'minecraft:spruce_leaves')
                    if side == 0 or rng.random() < 0.4:
                        b.setdefault((px, tier + 1, pz), 'minecraft:spruce_leaves')
            b.setdefault((dx * (length + 1), tier - 1, dz * (length + 1)), 'minecraft:spruce_leaves')
        tier += rng.choice((2, 3))
    for y in range(height - 2, height + 2):
        for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            if (dx, dz) == (0, 0) or y < height:
                b.setdefault((dx, y, dz), 'minecraft:spruce_leaves')
    b[(0, height + 2, 0)] = 'minecraft:spruce_leaves'
    return prune(b)


def palm(seed, trunk, fronds=8, reach=5):
    """A palm (jungle wood): the trunk follows the given column path. On top, a knob of log stubs (the frond bases,
    two out in the main directions, one on the diagonals) under a dome of leaves, and fronds that run out from the
    stubs and fall away in steps, the long ones in the main directions, shorter ones between. Every leaf stays within
    6 of a log (the stubs carry the fronds), so a cut palm's leaves decay; reach trims the fronds (5 full, 4 short).
    The crown is open, a tuft and separate fronds with sky between them; a solid dome over the knob looked like an umbrella."""
    rng = random.Random(seed)
    b = {}
    for p in trunk:
        b[p] = 'minecraft:jungle_log'
    for prev, cur in zip(trunk, trunk[1:]):
        connect(b, prev, cur, 'minecraft:jungle_log')
    tx, ty, tz = trunk[-1]
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        b[(tx + dx, ty, tz + dz)] = 'minecraft:jungle_log'
        b[(tx + 2 * dx, ty, tz + 2 * dz)] = 'minecraft:jungle_log'
    for dx, dz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        b[(tx + dx, ty, tz + dz)] = 'minecraft:jungle_log'
    # A small tuft over the knob: the young fronds pointing up.
    for x, z in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        b.setdefault((tx + x, ty + 1, tz + z), 'minecraft:jungle_leaves')
    b.setdefault((tx, ty + 2, tz), 'minecraft:jungle_leaves')
    # A frond, out along (dx, dz) and down: each step out, then a step down (face to face, so leaves stay attached).
    main = [(2, 1), (3, 1), (3, 0), (4, 0), (4, -1), (5, -1), (5, -2), (6, -2), (6, -3)]
    side = [(2, 0), (3, 0), (3, -1), (4, -1), (4, -2), (5, -2)]
    for dx, dz in [(1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)][:fronds]:
        diagonal = dx != 0 and dz != 0
        steps = side if diagonal else main
        steps = steps[:len(steps) - (5 - reach) * 2 - rng.randint(0, 1)]
        for k, (out, down) in enumerate(steps):
            if diagonal:
                # Along a diagonal, out means one block on each axis; fill the corner so the run stays face to face.
                b.setdefault((tx + dx * out, ty + down, tz + dz * (out - 1)), 'minecraft:jungle_leaves')
                b.setdefault((tx + dx * out, ty + down, tz + dz * out), 'minecraft:jungle_leaves')
            else:
                b.setdefault((tx + dx * out, ty + down, tz + dz * out), 'minecraft:jungle_leaves')
                # Where it leaves the knob a main frond is three leaves wide, then one.
                if k < 2:
                    b.setdefault((tx + dx * out + dz, ty + down, tz + dz * out + dx), 'minecraft:jungle_leaves')
                    b.setdefault((tx + dx * out - dz, ty + down, tz + dz * out - dx), 'minecraft:jungle_leaves')
    return prune(b)


def column(height, shifts=()):
    """A trunk path: straight up, stepping one block sideways at each (y, dx, dz) in shifts."""
    path = []
    x = z = 0
    moves = {y: (dx, dz) for y, dx, dz in shifts}
    for y in range(height):
        if y in moves:
            x += moves[y][0]
            z += moves[y][1]
        path.append((x, y, z))
    return path


TEMPLATES = {
    'larch': ('Larch (spruce): open tiers of short level branches with bare trunk between, drooping tips, a thin spire', lambda: larch(21, 15)),
    'larch_young': ('Young larch (spruce): the same open tiers on a shorter trunk', lambda: larch(22, 11)),
    'palm': ('Coconut palm (jungle): a tall straight trunk under a knob of frond bases and arching, drooping fronds',
             lambda: palm(31, column(9))),
    'palm_leaning': ('Leaning palm (jungle): a trunk slanting steadily to one side, as palms lean out over a beach',
                     lambda: palm(32, column(10, [(2, 1, 0), (4, 1, 0), (6, 1, 0), (8, 1, 0)]))),
    'palm_curved': ('Curved palm (jungle): upright at the foot, bending ever further over toward the top',
                    lambda: palm(33, column(11, [(5, 1, 0), (7, 1, 0), (8, 1, 0), (9, 1, 0), (10, 1, 0)]))),
    'palm_tall': ('Tall palm (jungle): a very tall trunk with two kinks in it, a small crown far up',
                  lambda: palm(34, column(15, [(5, 1, 0), (10, -1, 0)]), reach=4)),
    'palm_dwarf': ('Dwarf palm (jungle): a short trunk under a full, low crown',
                   lambda: palm(35, column(4), reach=4)),
    'palm_twin': ('Twin palm (jungle): two trunks from one foot, leaning apart, each with its own crown', lambda: twin_palm(36)),

    'willow': ('Weeping willow (oak): a dome of leaves with long curtains hanging from its rim, by water', willow),
    'baobab': ('Baobab (acacia): a fat bottle-shaped trunk with a ring of short branches and small leaf tufts', baobab),
}


def tag_string(s):
    data = s.encode('utf-8')
    return struct.pack('>H', len(data)) + data


def named(tag_id, name, payload):
    return bytes([tag_id]) + tag_string(name) + payload


def int_list(values):
    return bytes([3]) + struct.pack('>i', len(values)) + b''.join(struct.pack('>i', v) for v in values)


def compound(entries):
    return b''.join(entries) + b'\x00'


def compound_list(items):
    return bytes([10]) + struct.pack('>i', len(items)) + b''.join(items)


def palette_entry(block):
    if block.endswith('_log'):
        props = {'axis': 'y'}
    else:
        props = {'distance': '7', 'persistent': 'false', 'waterlogged': 'false'}
    return compound([named(8, 'Name', tag_string(block)),
                     named(10, 'Properties', compound([named(8, k, tag_string(v)) for k, v in sorted(props.items())]))])


def write_template(name, blocks):
    dist = leaf_distances(blocks)
    far = [p for p, b in blocks.items() if b.endswith('_leaves') and dist.get(p, 99) > 6]
    assert not far, f'{name}: leaves too far from a log at {far[:5]}'
    # Horizontal branch logs lie along their direction: a log with no log above or below lies along x or z.
    states = {}
    for p, block in blocks.items():
        if block.endswith('_log'):
            x, y, z = p
            vertical = blocks.get((x, y - 1, z), '').endswith('_log') or blocks.get((x, y + 1, z), '').endswith('_log')
            if not vertical:
                along_x = blocks.get((x - 1, y, z), '').endswith('_log') or blocks.get((x + 1, y, z), '').endswith('_log')
                states[p] = (block, 'x' if along_x else 'z')
                continue
            states[p] = (block, 'y')
        else:
            states[p] = (block, None)
    mx = min(p[0] for p in blocks)
    mz = min(p[2] for p in blocks)
    # The trunk (x 0, z 0) must land on the middle of the bottom layer.
    half = max(-mx, max(p[0] for p in blocks), -mz, max(p[2] for p in blocks))
    shift = (half, 0, half)
    size = [2 * half + 1, max(p[1] for p in blocks) + 1, 2 * half + 1]
    palette = []
    index = {}
    entries = []
    for p in sorted(states):
        block, axis = states[p]
        key = (block, axis)
        if key not in index:
            index[key] = len(palette)
            if axis:
                palette.append(compound([named(8, 'Name', tag_string(block)),
                                         named(10, 'Properties', compound([named(8, 'axis', tag_string(axis))]))]))
            else:
                palette.append(palette_entry(block))
        pos = [p[0] + shift[0], p[1], p[2] + shift[2]]
        entries.append(compound([named(9, 'pos', int_list(pos)), named(3, 'state', struct.pack('>i', index[key]))]))
    root = compound([
        named(3, 'DataVersion', struct.pack('>i', DATA_VERSION)),
        named(9, 'size', int_list(size)),
        named(9, 'palette', compound_list(palette)),
        named(9, 'blocks', compound_list(entries)),
        named(9, 'entities', bytes([0]) + struct.pack('>i', 0)),
    ])
    buffer = io.BytesIO()
    with gzip.GzipFile(fileobj=buffer, mode='wb', mtime=0) as f:
        f.write(named(10, '', root))
    path = os.path.join(RES, 'structure/tree', name + '.nbt')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(buffer.getvalue())
    logs = sum(1 for b in blocks.values() if b.endswith('_log'))
    print(f'template {name}: {logs} logs, {len(blocks) - logs} leaves, {size[0]} x {size[1]} x {size[2]}')


def twin_palm(seed):
    a = palm(seed, column(9, [(3, 1, 0), (5, 1, 0), (7, 1, 0)]), reach=4)
    b = palm(seed + 100, [(0, 0, 0)] + [(x, y, z) for x, y, z in column(8, [(2, -1, 0), (4, -1, 0), (6, -1, 0)]) if y > 0], reach=4)
    for p, block in b.items():
        if block.endswith('_log') or p not in a:
            a[p] = block
    return prune(a)


def main():
    for name, (_, feature) in SPECIES.items():
        write_json(f'worldgen/configured_feature/tree/{name}.json', feature)
    for name, (_, make) in PALE_OAK_SHAPES.items():
        for colour in PALE_OAK_COLOURS:
            write_json(f'worldgen/configured_feature/tree/{colour}_{name}.json', make(colour))
        write_json(f'worldgen/configured_feature/tree/{name}.json', {'type': 'minecraft:random_selector', 'config': {
            'features': [{'chance': 1 / 3, 'feature': {'feature': f'{NS}:tree/red_{name}', 'placement': []}},
                         {'chance': 0.5, 'feature': {'feature': f'{NS}:tree/orange_{name}', 'placement': []}}],
            'default': {'feature': f'{NS}:tree/yellow_{name}', 'placement': []}}})
    for name, (_, make) in TEMPLATES.items():
        write_template(name, make())
    print(f'{len(SPECIES) + len(PALE_OAK_SHAPES)} species, {len(TEMPLATES)} templates')


if __name__ == '__main__':
    main()
