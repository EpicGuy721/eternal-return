"""Builds the Eternal Return Moderner Beta preset, noise settings and world preset from Moderner Beta's
Release 1.6.4 preset (read from the Moderner Beta jar in the Gradle cache, so run a Gradle build first).

Usage: python tools/worldgen/build_preset.py [knobs.json]
knobs.json overrides the terrain knobs (see KNOBS below), so a tuning pass is one small file.
The knobs this repo ships are in tools/worldgen/knobs.json. See docs/worldgen.md for what each one does.
"""
import copy
import glob
import json
import os
import sys
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
MB_JARS = glob.glob(os.path.expanduser('~/.gradle/caches/modules-2/files-2.1/maven.modrinth/moderner-beta/5.0.0-alpha.2+1.21.1-fabric/*/*.jar'))
if not MB_JARS:
    sys.exit('Moderner Beta 5.0.0-alpha.2 jar not found in the Gradle cache; run ./gradlew build first')
MB_JAR = zipfile.ZipFile(MB_JARS[0])
NS = 'eternalreturn'
LOAD_IF_MB = [{'condition': 'fabric:all_mods_loaded', 'values': ['moderner_beta']}]

# Terrain knobs: forced biome heights give 1.6.4 continents and oceans; Beta-style sampled scale and
# depth noise add Beta 1.7.3 roughness on top. Height range is the full modern -64..320.
KNOBS = {
    'scale_sample': True,
    'scale_influence': 0.6,
    'scale_offset': 0.2,
    'depth_influence': 0.4,
    'depth_negative_flattening': False,
    'climate_height_scaling': False,
    'stretch_y': 12.0,
    'base_size': 8.5,
    'limit_blending': 10.0,
    'depth_weight': 1.0,
    'depth_offset': 0.0,
    'scale_weight': 1.0,
    'height_scale_offset': 0.0,
    'modify_only_positive_depth': False,
    'noise_top': 320,
    # Biome height overrides ("depth;scale", Moderner Beta's own format) on top of the preset's, by biome id
    # (a height variant after '*', as in 'minecraft:jungle*hills').
    'height_overrides': {},
    # Terrain shape from the noise, not the biome: "depth;scale" for every land biome the biome layers can
    # produce (with its hills, edge and other height variants), so they all share one height. None: each
    # biome keeps its own.
    'land_height': None,
    # Biomes (with their height variants) that keep their own height even with land_height set.
    'land_height_except': [],
    # Scale (the second half of "depth;scale") for oceans and rivers, whose depth stays as it is. Negative
    # enough, it cancels the sampled scale noise over water. None: as Moderner Beta has it.
    'water_scale': None,
    'scale_variation': 1.121,
    # Old growth taigas: one in this many taiga patches surrounded by taiga becomes old growth pine or
    # spruce taiga (half each). None: no old growth taigas.
    'old_growth_taiga_chance': None,
    # Moderner Beta's bottom slide, in 8-block noise cells: how far up from the bottom of the world the
    # terrain is pushed back to solid. Only shows where the ground would otherwise fall that low (sinkholes).
    # None: as Moderner Beta has it (3).
    'bottom_slide_size': None,
    # Vanilla's aquifers in the noise settings. Off: every open space below sea level fills with water down
    # to the lava level (y -54), so sinkholes are clean deep lakes. None: as Moderner Beta has it (on).
    'aquifers': None,
    # Biome stone: block id -> the biomes whose stone it replaces, from just under the topsoil down to
    # bedrock (see docs/worldgen.md). Biomes under minecraft:stone, and any biome not listed, keep stone.
    'biome_stone': {},
}
if len(sys.argv) > 1:
    KNOBS.update(json.load(open(sys.argv[1])))


def mb_json(path):
    return json.loads(MB_JAR.read('data/moderner_beta/' + path))


def write(rel, obj):
    path = os.path.join(ROOT, 'src/main/resources', rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def step(pipeline, step_id, step_type):
    for s in pipeline:
        if s.get('id') == step_id and s['type'] == step_type:
            return s
    raise KeyError((step_id, step_type))


base = mb_json('moderner_beta/settings_preset/release_1_6_4.json')
preset = copy.deepcopy(base)

# ---------------------------------------------------------------- biomes
pipeline = preset['biomeSettings']['moderner_beta:fractal_layers']['pipeline']

CLASSIC = ['minecraft:desert', 'minecraft:forest', 'moderner_beta:early_release_extreme_hills',
           'moderner_beta:early_release_swampland', 'moderner_beta:late_beta_plains',
           'moderner_beta:early_release_taiga', 'minecraft:jungle']
ADDED = ['minecraft:birch_forest', 'minecraft:savanna', 'minecraft:badlands', 'minecraft:dark_forest', 'minecraft:cherry_grove',
         'eternalreturn:autumnal_forest']
# Each 1.6.4 biome twice, each added biome once: the added six make up about a quarter of temperate land.
# (The autumnal forest has no hills variant: with one land height for every biome, a hills variant would change nothing.)
step(pipeline, 'biome_pool', 'moderner_beta:random_biome')['biomes'] = CLASSIC + CLASSIC + ADDED

WITH_HILLS = ['minecraft:birch_forest', 'minecraft:savanna', 'minecraft:badlands', 'minecraft:cherry_grove']
hills = step(pipeline, 'hills', 'moderner_beta:biome_replacement')['targets']
for biome in WITH_HILLS:
    hills[biome] = {'type': 'biome', 'value': biome + '*hills'}
for s in pipeline:
    if s['type'] == 'moderner_beta:conditional_overlay' and s.get('onMatch', {}).get('value') == 'hills':
        for term in s['predicate']['terms']:
            if term.get('condition') == 'moderner_beta:in_set':
                term['biomes'] += WITH_HILLS

# Old growth taigas, later versions' rarer taiga variants: right after the hills step, some taiga patches
# whose four neighbours are taiga become old growth pine or old growth spruce taiga (half each).
if KNOBS['old_growth_taiga_chance']:
    after_hills = next(i for i, s in enumerate(pipeline) if s['type'] == 'moderner_beta:conditional_overlay'
                       and s.get('onMatch', {}).get('value') == 'hills') + 1
    pipeline[after_hills:after_hills] = [
        {'type': 'moderner_beta:weighted_pool', 'id': 'old_growth_taiga', 'seed': 1001, 'targets': [
            {'data': {'type': 'biome', 'value': 'minecraft:old_growth_pine_taiga'}, 'weight': 1},
            {'data': {'type': 'biome', 'value': 'minecraft:old_growth_spruce_taiga'}, 'weight': 1}]},
        {'type': 'moderner_beta:conditional_overlay', 'parent': 'land', 'id': 'land',
         'onMatch': {'type': 'layer', 'value': 'old_growth_taiga'},
         'otherwise': {'type': 'biome', 'value': 'minecraft:the_void*null'},
         'predicate': {'condition': 'moderner_beta:all_of', 'terms': [
             {'biome': 'moderner_beta:early_release_taiga', 'condition': 'moderner_beta:single_match'},
             {'condition': 'moderner_beta:identical_neighbor', 'diagonal': False, 'requiredCount': 4},
             {'condition': 'moderner_beta:random_chance', 'denominator': KNOBS['old_growth_taiga_chance'], 'numerator': 1}]},
         'seed': 1002},
    ]

# ---------------------------------------------------------------- oceans: deep oceans and ocean temperatures
# Taken from Moderner Beta's Release 1.17.1 preset (the 1.7 deep ocean layer and the 1.13 ocean climate).
# Deep ocean: ocean whose four neighbours are ocean, right after the mushroom island layer, as in 1.7.
OCEANS = ['minecraft:ocean', 'minecraft:deep_ocean']
mushroom = next(i for i, s in enumerate(pipeline) if s['type'] == 'moderner_beta:conditional_overlay'
                and s.get('onMatch', {}).get('value') == 'minecraft:mushroom_fields')
pipeline.insert(mushroom + 1, {
    'type': 'moderner_beta:conditional_overlay', 'parent': 'land', 'id': 'land',
    'onMatch': {'type': 'biome', 'value': 'minecraft:deep_ocean'},
    'otherwise': {'type': 'biome', 'value': 'minecraft:the_void*null'},
    'predicate': {'condition': 'moderner_beta:all_of', 'terms': [
        {'biome': 'minecraft:ocean', 'condition': 'moderner_beta:single_match'},
        {'condition': 'moderner_beta:identical_neighbor', 'diagonal': False, 'requiredCount': 4}]},
    'seed': 0})


def ocean_means_any_ocean(node):
    """Later layers that look for ocean next to land (shores) see deep ocean as ocean too."""
    if isinstance(node, dict):
        for key, value in list(node.items()):
            if key == 'neighborPredicate' and value == {'biome': 'minecraft:ocean', 'condition': 'moderner_beta:single_match'}:
                node[key] = {'biomes': OCEANS, 'condition': 'moderner_beta:in_set'}
            elif key == 'biomes' and isinstance(value, list) and 'minecraft:ocean' in value and 'minecraft:deep_ocean' not in value:
                value.append('minecraft:deep_ocean')
            else:
                ocean_means_any_ocean(value)
    elif isinstance(node, list):
        for value in node:
            ocean_means_any_ocean(value)


for s in pipeline[mushroom + 2:]:
    ocean_means_any_ocean(s)
    if s['type'] == 'moderner_beta:mix_river' and 'minecraft:deep_ocean' not in s['ignoredBiomes']:
        s['ignoredBiomes'].append('minecraft:deep_ocean')
# Ocean temperature, last: an ocean noise picks frozen, cold, plain, lukewarm or warm for every ocean and
# deep ocean (deep warm becomes deep lukewarm, and warm and frozen soften next to land). The frozen oceans
# 1.6.4 puts beside its ice plains are left as they are.
pipeline += [
    {'type': 'moderner_beta:mapped_noise', 'id': 'ocean_climate', 'amplitudes': [1.0], 'scale': 8.0, 'seed': 2, 'useSaltedSeed': False,
     'values': [{'biome': 'minecraft:frozen_ocean', 'value': -0.4}, {'biome': 'minecraft:cold_ocean', 'value': -0.2},
                {'biome': 'minecraft:ocean', 'value': 0.0}, {'biome': 'minecraft:warm_ocean', 'value': 0.4},
                {'biome': 'minecraft:lukewarm_ocean', 'value': 0.2}]},
    {'type': 'moderner_beta:stacked_zoom', 'parent': 'ocean_climate', 'id': 'ocean_climate', 'level': 6, 'seed': 2001,
     'seedModifier': 1, 'zoomType': 'modal'},
    {'type': 'moderner_beta:apply_ocean_climate', 'parent': 'land', 'id': 'land', 'oceanClimate': 'ocean_climate',
     'applyCoasts': True, 'seed': 0},
]

# ---------------------------------------------------------------- biome variants (tools/worldgen/variants.json)
# Peaks: the biome on ground above its height is the peak variant, by Moderner Beta's biome injection, which
# rewrites a chunk's biomes after its terrain (so the terrain doesn't change). Only cells near the ground count
# (below_surface -8: no higher than 8 above the lowest ground around), so the sky and the floating islands keep
# their biome. Edges: a 1.6.4-style layer, where a base biome touches one of its neighbours at the land_0 step
# (cells of 32 blocks), so an edge band is about 32 blocks deep. Rivers: the river layer gives a river the
# variant of the land it runs through. The world's 1.6.4 layers have no climate noise, so edges come from the
# layers' own neighbour checks, as 1.6.4 makes beaches.
VARIANTS = json.load(open(os.path.join(ROOT, 'tools', 'worldgen', 'variants.json'), encoding='utf-8'))
WITH_HILLS_IDS = set(step(pipeline, 'hills', 'moderner_beta:biome_replacement')['targets'])


def with_height_variants(biomes):
    """The ids the layers use for these biomes: each with its hills (and the extreme hills' edge) variant."""
    out = []
    for b in biomes:
        b = b if ':' in b else f'{NS}:{b}'
        out.append(b)
        if b in WITH_HILLS_IDS:
            out.append(b + '*hills')
        if b == 'moderner_beta:early_release_extreme_hills':
            out.append(b + '*edge')
    return out


LAND_0_OCEANS = ['minecraft:ocean', 'minecraft:deep_ocean', 'minecraft:frozen_ocean']
edge_targets = []
for vid, spec in VARIANTS['edges'].items():
    if spec['neighbours'] == ['*land']:
        neighbour = {'condition': 'moderner_beta:all_of', 'terms': [
            {'condition': 'moderner_beta:inverted', 'term': {'biomes': with_height_variants([spec['base']]), 'condition': 'moderner_beta:in_set'}},
            {'condition': 'moderner_beta:inverted', 'term': {'biomes': LAND_0_OCEANS, 'condition': 'moderner_beta:in_set'}}]}
    else:
        neighbour = {'biomes': with_height_variants(spec['neighbours']), 'condition': 'moderner_beta:in_set'}
    edge_targets.append({'type': 'biome', 'result': f'{NS}:{vid}', 'predicate': {'condition': 'moderner_beta:all_of', 'terms': [
        {'biomes': with_height_variants([spec['base']]), 'condition': 'moderner_beta:in_set'},
        {'condition': 'moderner_beta:neighbor_match', 'diagonal': False, 'neighborPredicate': neighbour, 'requiredCount': 1}]}})
land_0 = next(i for i, s in enumerate(pipeline) if s['type'] == 'moderner_beta:add_land' and s.get('id') == 'land_0')
pipeline.insert(land_0 + 1, {'type': 'moderner_beta:predicate_overlay', 'parent': 'land_0', 'id': 'land_0', 'seed': 0, 'targets': edge_targets})
rivers = next(s for s in pipeline if s['type'] == 'moderner_beta:mix_river')
for vid, spec in VARIANTS['rivers'].items():
    for land in with_height_variants(spec['land']):
        rivers['biomeSpecificRivers'].setdefault(land, f'{NS}:{vid}')
preset['biomeSettings']['moderner_beta:biome_injection_rules'] = [
    {'type': 'moderner_beta:predicate', 'step_for': 'before_surface',
     'onMatch': {'type': 'moderner_beta:replace', 'replacements': {spec['base']: f'{NS}:{vid}'}},
     'predicate': {'condition': 'moderner_beta:all_of', 'terms': [
         {'condition': 'moderner_beta:biome_in_set', 'biomes': [spec['base']]},
         {'condition': 'moderner_beta:y_above', 'anchor': {'absolute': spec['y']}},
         {'condition': 'moderner_beta:below_surface', 'threshold': -8}]}}
    for vid, spec in VARIANTS['peaks'].items()]
VARIANT_BASES = {f'{NS}:{vid}': spec['base'] for kind in ('peaks', 'edges', 'subs') for vid, spec in VARIANTS[kind].items() if not vid.startswith('_')}
VARIANT_BASES.update({f'{NS}:{vid}': 'minecraft:river' for vid in VARIANTS['rivers']})

# ---------------------------------------------------------------- sub-biomes (worldgen expansion phase 6)
# A sub-biome takes over part of its parent's land: right after the 1.6.4 biome pool is laid out (cells of 256 blocks),
# a replacement step gives each cell of the parent a pick from a pool of its sub-biomes, with "nothing" (Moderner Beta's
# the_void*null, which keeps the parent) for the rest, so "share": 4 turns about one parent cell in four. The pool draws
# from its own seed per cell, so the parents, and every coordinate in them, stay where they were. Shore sub-biomes
# ("shore_of") replace the beach where one of those lands meets the sea. In the later layers a sub-biome acts as its
# parent (beaches or none, swamp lakes, frozen seas, rivers, its parent's edge, and as a neighbour for edges), except that
# it gets no hills, old growth or peak.
SUBS = {f'{NS}:{sid}': spec for sid, spec in VARIANTS['subs'].items() if not sid.startswith('_')}
LAND_SUBS = {sid: spec for sid, spec in SUBS.items() if 'share' in spec}
SHORE_SUBS = {sid: spec for sid, spec in SUBS.items() if 'shore_of' in spec}
subs_of = {}
for sid, spec in SUBS.items():
    subs_of.setdefault(spec['base'], []).append(sid)
pools = []
rounds = {}
# Rounds: a later batch's sub-biomes of a parent that already has some (variants.json "round": 2) are picked in a later
# replacement step, among the cells the earlier rounds left to the parent, so the earlier sub-biomes stay exactly where
# they were. Each one still takes its "share" of the whole parent: its weight is its share of what the earlier rounds left.
from fractions import Fraction
for parent in sorted({spec['base'] for spec in LAND_SUBS.values()}):
    mine = {sid: spec for sid, spec in LAND_SUBS.items() if spec['base'] == parent}
    left = Fraction(1)
    for rnd in sorted({spec.get('round', 1) for spec in mine.values()}):
        these = {sid: spec for sid, spec in mine.items() if spec.get('round', 1) == rnd}
        chances = {sid: Fraction(1, spec['share']) / left for sid, spec in these.items()}
        whole = 1
        for c in chances.values():
            whole = whole * c.denominator // __import__('math').gcd(whole, c.denominator)
        targets = [{'data': {'type': 'biome', 'value': sid}, 'weight': int(c * whole)} for sid, c in chances.items()]
        rest = whole - sum(t['weight'] for t in targets)
        if rest > 0:
            targets.append({'data': {'type': 'biome', 'value': 'minecraft:the_void*null'}, 'weight': rest})
        pool_id = 'sub_' + parent.split(':')[1] + ('' if rnd == 1 else f'_{rnd}')
        # Fixed per parent and round (variants.json "pool_seeds"), so adding a parent never moves another's sub-biomes.
        seed = VARIANTS['pool_seeds'][parent if rnd == 1 else f'{parent}#{rnd}']
        pools.append({'type': 'moderner_beta:weighted_pool', 'id': pool_id, 'seed': seed, 'targets': targets})
        rounds.setdefault(rnd, {})[parent] = {'type': 'layer', 'value': pool_id}
        left -= sum(Fraction(1, spec['share']) for spec in these.values())
replacements = rounds.get(1, {})
if replacements:
    laid_out = next(i for i, s in enumerate(pipeline) if s['type'] == 'moderner_beta:biome_replacement'
                    and s.get('targets', {}).get('minecraft:plains', {}).get('value') == 'biome_pool')
    steps = [{'type': 'moderner_beta:biome_replacement', 'parent': 'land', 'id': 'land', 'seed': 0, 'targets': rounds[r]} for r in sorted(rounds)]
    pipeline[laid_out + 1:laid_out + 1] = pools + steps
    after_subs = laid_out + len(pools) + len(steps) + 1


def with_subs(biomes):
    """These biome ids with the sub-biomes of each (height variants such as '*hills' count as their biome)."""
    out = list(biomes)
    for b in biomes:
        for sid in subs_of.get(b.lstrip('~').split('*')[0], []):
            if sid not in out and sid not in SHORE_SUBS:
                out.append(sid)
    return out


def subs_act_as_parent(node, in_neighbour=False):
    if isinstance(node, dict):
        for key, value in list(node.items()):
            if key == 'neighborPredicate':
                subs_act_as_parent(value, True)
            elif key == 'biomes' and isinstance(value, list) and in_neighbour:
                node[key] = with_subs(value)
            elif key in ('biomeSpecificRivers', 'biomeSpecificOceans') and isinstance(value, dict):
                for b, target in list(value.items()):
                    for sid in with_subs([b])[1:]:
                        value.setdefault(sid, target)
            else:
                subs_act_as_parent(value, in_neighbour)
    elif isinstance(node, list):
        for value in node:
            subs_act_as_parent(value, in_neighbour)


if SUBS:
    for s in pipeline[after_subs if replacements else 0:]:
        if s['type'] == 'moderner_beta:predicate_overlay':
            for target in s['targets']:
                subs_act_as_parent(target['predicate'].get('terms', []) if target['predicate'].get('condition') == 'moderner_beta:all_of' else [])
                terms = target['predicate'].get('terms', [])
                # Edges: where a sub-biome meets one of its parent's edge neighbours, it turns into the parent's edge, as the parent does.
                if target.get('result', '').removeprefix(f'{NS}:') in VARIANTS['edges'] and terms and terms[0].get('condition') == 'moderner_beta:in_set':
                    terms[0]['biomes'] = with_subs(terms[0]['biomes'])
                # The beach rule leaves out some lands (the extreme hills, swampland): their sub-biomes too.
                for term in terms:
                    if term.get('condition') == 'moderner_beta:inverted' and 'biomes' in term['term'] and target.get('result') == 'minecraft:beach':
                        term['term']['biomes'] = with_subs(term['term']['biomes'])
            # Swamp and jungle lakes: the parent's single match, for its sub-biomes too.
            extra = []
            for target in s['targets']:
                pred = target['predicate']
                if pred.get('condition') == 'moderner_beta:single_match' and target['type'] == 'layer':
                    for sid in with_subs([pred['biome']])[1:]:
                        extra.append({**target, 'predicate': {'biome': sid, 'condition': 'moderner_beta:single_match'}})
            s['targets'] += extra
        elif s['type'] in ('moderner_beta:mix_river', 'moderner_beta:add_land'):
            subs_act_as_parent(s)
    # Shore sub-biomes: before the beach rule, a cell of one of their lands next to the sea becomes the shore sub-biome.
    shore_step = next(s for s in pipeline if s['type'] == 'moderner_beta:predicate_overlay'
                      and any(t.get('result') == 'minecraft:beach' for t in s['targets']))
    beach_at = next(i for i, t in enumerate(shore_step['targets']) if t.get('result') == 'minecraft:beach')
    sea = next(t for t in shore_step['targets'][beach_at]['predicate']['terms'] if t.get('condition') == 'moderner_beta:neighbor_match')
    shore_step['targets'][beach_at:beach_at] = [
        {'type': 'biome', 'result': sid, 'predicate': {'condition': 'moderner_beta:all_of', 'terms': [
            {'biomes': with_subs(with_height_variants(spec['shore_of'])), 'condition': 'moderner_beta:in_set'}, copy.deepcopy(sea)]}}
        for sid, spec in SHORE_SUBS.items()]

# ---------------------------------------------------------------- caves: no modern cave biomes
preset['caveBiomeSettings'] = {'moderner_beta:provider': 'moderner_beta:none'}

# ---------------------------------------------------------------- structures (Eternal Return worlds only)
# No trial chambers and no ocean monuments (guardians spawn in the open ocean instead). Moderner Beta's
# ocean shrines exist only through this override in its presets, so dropping it drops them.
cs = preset['chunkSettings']
cs['moderner_beta:structure_modifiers']['removed'] = ['minecraft:trial_chambers', 'minecraft:ocean_monuments']
cs['moderner_beta:structure_modifiers']['overrides'].pop('moderner_beta:ocean_shrine')

# ---------------------------------------------------------------- terrain
cs['moderner_beta:deepslate_generation']['enabled'] = False
cs['moderner_beta:noise_generator_settings'] = f'{NS}:eternal_return'
land = cs['moderner_beta:noise_landmass']
land['scale']['sample'] = KNOBS['scale_sample']
land['scale']['influence'] = KNOBS['scale_influence']
land['scale']['offset'] = KNOBS['scale_offset']
land['scale']['variation'] = KNOBS['scale_variation']
land['depth']['influence'] = KNOBS['depth_influence']
land['depth']['negativeFlattening'] = KNOBS['depth_negative_flattening']
cs['moderner_beta:noise_3d_settings']['climateHeightScaling'] = KNOBS['climate_height_scaling']
cs['moderner_beta:noise_scale']['stretchY'] = KNOBS['stretch_y']
cs['moderner_beta:noise_scale']['baseSize'] = KNOBS['base_size']
cs['moderner_beta:noise_scale']['limitBlending'] = KNOBS['limit_blending']
if KNOBS['bottom_slide_size'] is not None:
    cs['moderner_beta:noise_slide']['bottomSize'] = KNOBS['bottom_slide_size']
forced = cs['moderner_beta:forced_biome_height']
forced['depthWeight'] = KNOBS['depth_weight']
forced['depthOffset'] = KNOBS['depth_offset']
forced['scaleWeight'] = KNOBS['scale_weight']
forced['scaleOffset'] = KNOBS['height_scale_offset']
forced['modifyOnlyPositiveDepth'] = KNOBS['modify_only_positive_depth']
# (With land_height set, below, every land biome shares one height and these per-biome heights give way to it.)
# Cherry groves: Moderner Beta gives them a plateau height (1.5;0.6), which these knobs turn into a sheer
# flat-topped block; a raised, rolling highland (and higher hills) suits them better, like vanilla's meadows.
forced['heightOverrides']['minecraft:cherry_grove'] = '0.35;0.6'
forced['heightOverrides']['minecraft:cherry_grove*hills'] = '0.6;0.7'
# The knobs' own overrides: the third terrain pass turns the plateau-height hills of jungles (1.6.4's 1.8;0.5),
# savannas and badlands (1.8;0.2 from Moderner Beta's tags) into raised rolling hills, like the cherry groves.
forced['heightOverrides'].update(KNOBS['height_overrides'])
# Ocean heights, as Moderner Beta's Release 1.17.1 preset sets them. Its height table only covers the
# biomes it believes a world can produce, which misses the deep cold, deep frozen and deep lukewarm
# oceans and the deep warm one (warm_ocean*deep) that the ocean climate step makes; without these lines
# they get the default land height and rise into hills that still report an ocean biome.
for ocean in ['ocean', 'cold_ocean', 'lukewarm_ocean', 'warm_ocean', 'frozen_ocean']:
    forced['heightOverrides']['minecraft:' + ocean] = '-1.0;0.2'
for ocean in ['deep_ocean', 'deep_cold_ocean', 'deep_lukewarm_ocean', 'deep_frozen_ocean']:
    forced['heightOverrides']['minecraft:' + ocean] = '-1.8;0.2'
forced['heightOverrides']['minecraft:warm_ocean*deep'] = '-1.0;0.2'


def pipeline_biomes(node, found):
    """Every biome id (with its height variant, as in 'minecraft:jungle*hills') the biome layers can produce."""
    if isinstance(node, dict):
        if node.get('type') == 'biome' and isinstance(node.get('value'), str):
            found.add(node['value'])
        for key, value in node.items():
            if key in ('biome', 'result') and isinstance(value, str):
                found.add(value)
            elif key == 'biomes' and isinstance(value, list):
                found.update(v for v in value if isinstance(v, str))
            elif key in ('targets', 'biomeSpecificRivers') and isinstance(value, dict):
                found.update(value.keys())
                found.update(v for v in value.values() if isinstance(v, str))
            pipeline_biomes(value, found)
    elif isinstance(node, list):
        for value in node:
            pipeline_biomes(value, found)


# Terrain shape from the noise, not the biome. Every land biome gets the same height (land_height), so flat,
# rolling and hilly ground come from Beta's sampled scale noise (scale_sample, scale_influence) wherever it
# falls, as in Beta 1.7.3. Oceans and rivers keep their depth, so seas, coastlines and rivers stay where they
# are; water_scale gives them a scale negative enough to cancel the scale noise, which would otherwise raise
# islets out of the sea and bars across rivers. Beaches and mushroom island shores (the coast) keep their own.
WATER = {b for b in forced['heightOverrides'] if 'ocean' in b} | {'minecraft:river', 'minecraft:frozen_river'}
WATER |= {f'{NS}:{vid}' for vid in VARIANTS['rivers']}
COAST = {'minecraft:beach', 'minecraft:snowy_beach', 'minecraft:stony_shore', 'minecraft:mushroom_fields*shore'} | set(SHORE_SUBS)
if KNOBS['water_scale'] is not None:
    for biome in sorted(WATER):
        depth = forced['heightOverrides'].get(biome, '-0.5;0.0').split(';')[0]
        forced['heightOverrides'][biome] = f"{depth};{KNOBS['water_scale']}"
if KNOBS['land_height'] is not None:
    produced = set()
    pipeline_biomes(pipeline, produced)
    for biome in sorted(produced):
        # (Skipping '~' and '#' entries: negated biomes and tags in the layers' conditions.)
        if (':' in biome and biome[0] not in '~#' and not biome.startswith('minecraft:the_void')
                and 'ocean' not in biome and 'river' not in biome and biome not in COAST
                and biome.split('*')[0] not in KNOBS['land_height_except']):
            forced['heightOverrides'][biome] = KNOBS['land_height']

preset['name'] = {'color': 'gold', 'translate': f'createWorld.customize.modern_beta.preset.name.{NS}.eternal_return'}
preset['description'] = {'translate': f'createWorld.customize.modern_beta.preset.desc.{NS}.eternal_return'}
write(f'data/{NS}/moderner_beta/settings_preset/eternal_return.json', preset)

# ---------------------------------------------------------------- noise settings: full -64..320 range
noise = mb_json('worldgen/noise_settings/overworld_256.json')
noise['noise']['min_y'] = -64
noise['noise']['height'] = KNOBS['noise_top'] + 64
if KNOBS['aquifers'] is not None:
    noise['aquifers_enabled'] = KNOBS['aquifers']
# Biome stone: after every topsoil, beach and badlands-band rule, so it only reaches what would stay
# stone. Switched by the config setting worldgen.biomeStone (the eternalreturn:biome_stone_enabled
# condition). No blending: each block takes the stone of the biome it is in.
for block, biomes in KNOBS['biome_stone'].items():
    biomes += [v for v, base in VARIANT_BASES.items() if base in biomes and v not in biomes]
stone_rules = [{'type': 'minecraft:condition',
                'if_true': {'type': 'minecraft:biome', 'biome_is': biomes},
                'then_run': {'type': 'minecraft:block', 'result_state': {'Name': block}}}
               for block, biomes in KNOBS['biome_stone'].items() if block != 'minecraft:stone' and biomes]
# The host stones, for Moderner Beta's own surface pass (BiomeStoneSurface): it must take them for stone.
write(f'data/{NS}/tags/block/biome_host_stones.json',
      {'values': [block for block, biomes in KNOBS['biome_stone'].items() if block != 'minecraft:stone' and biomes]})
# Each host stone's biomes as a biome tag, eternalreturn:biome_stone/<block path>, for blocks placed after the
# surface (boulders, BiomeStoneProcessor). Optional entries, so the tags load without Moderner Beta.
for block, biomes in KNOBS['biome_stone'].items():
    if block != 'minecraft:stone' and biomes:
        write(f'data/{NS}/tags/worldgen/biome/biome_stone/{block.split(":")[1]}.json',
              {'values': [{'id': biome, 'required': False} for biome in biomes]})
if stone_rules:
    noise['surface_rule']['sequence'].append({'type': 'minecraft:condition',
                                              'if_true': {'type': f'{NS}:biome_stone_enabled'},
                                              'then_run': {'type': 'minecraft:sequence', 'sequence': stone_rules}})
# Variant ground (tools/worldgen/variants.json "surface"): river banks and beds, coarse dirt on dry edges. First in the
# surface rules, so they win over the usual topsoil, and only on the ground surface (not cave floors).
def block_rule(name):
    return {'type': 'minecraft:block', 'result_state': {'Name': name}}


TOP = {'type': 'minecraft:stone_depth', 'offset': 0, 'surface_type': 'floor', 'add_surface_depth': False, 'secondary_depth_range': 0}
SHALLOW = {'type': 'minecraft:stone_depth', 'offset': 0, 'surface_type': 'floor', 'add_surface_depth': True, 'secondary_depth_range': 0}
DRY = {'type': 'minecraft:water', 'offset': -1, 'surface_depth_multiplier': 0, 'add_stone_depth': False}


def patches(threshold):
    return {'type': 'minecraft:noise_threshold', 'noise': 'minecraft:surface', 'min_threshold': threshold, 'max_threshold': 1.0e6}


def when(cond, then):
    return {'type': 'minecraft:condition', 'if_true': cond, 'then_run': then}


def below(threshold):
    return {'type': 'minecraft:noise_threshold', 'noise': 'minecraft:surface', 'min_threshold': -1.0e6, 'max_threshold': threshold}


def unless(cond, then):
    return when({'type': 'minecraft:not', 'invert': cond}, then)


SURFACES = {
    # Sand banks and bed, a few blocks deep.
    'sand': when(SHALLOW, block_rule('minecraft:sand')),
    # A gravel bed; on the banks, gravel in patches.
    'gravel': {'type': 'minecraft:sequence', 'sequence': [
        unless(DRY, when(SHALLOW, block_rule('minecraft:gravel'))),
        when(DRY, when(TOP, when(patches(0.3), block_rule('minecraft:gravel'))))]},
    # A clay bed; mud in patches on the banks.
    'clay': {'type': 'minecraft:sequence', 'sequence': [
        unless(DRY, when(SHALLOW, block_rule('minecraft:clay'))),
        when(DRY, when(TOP, when(patches(0.3), block_rule('minecraft:mud'))))]},
    # Podzol banks, most of the way along.
    'podzol': when(DRY, when(TOP, when(patches(-0.4), block_rule('minecraft:podzol')))),
    # Coarse dirt in patches (dry forest edges, desert scrub).
    'coarse_dirt_patches': when(DRY, when(TOP, when(patches(0.4), block_rule('minecraft:coarse_dirt')))),
    # Desert scrub: the desert's sand (its own surface rules name only the desert, so the scrub would be grass), with
    # coarse dirt in patches on top, and sandstone under the sand as in the desert.
    'scrub_sand': {'type': 'minecraft:sequence', 'sequence': [
        when(DRY, when(TOP, when(patches(0.4), block_rule('minecraft:coarse_dirt')))),
        when(SHALLOW, block_rule('minecraft:sand')),
        when({'type': 'minecraft:stone_depth', 'offset': 0, 'surface_type': 'floor', 'add_surface_depth': True, 'secondary_depth_range': 6},
             block_rule('minecraft:sandstone'))]},
    # Heathland: podzol and coarse dirt in patches between the grass.
    'heath': when(DRY, when(TOP, {'type': 'minecraft:sequence', 'sequence': [
        when(patches(0.25), block_rule('minecraft:podzol')),
        when({'type': 'minecraft:noise_threshold', 'noise': 'minecraft:surface', 'min_threshold': -1.0e6, 'max_threshold': -0.45},
             block_rule('minecraft:coarse_dirt'))]})),
    # Redwood forest: podzol over most of the floor.
    'podzol_floor': when(DRY, when(TOP, when(patches(-0.3), block_rule('minecraft:podzol')))),
    # Spooky swamp: mud in patches on the dry ground.
    'mud_patches': when(DRY, when(TOP, when(patches(0.2), block_rule('minecraft:mud')))),
    # Desert oasis: grass over most of the sand, the desert's sand under it.
    'oasis': when(DRY, when(TOP, when(patches(-0.3), block_rule('minecraft:grass_block')))),
    # Desert baobabs: coarse dirt and grass in patches on the sand.
    'baobab_ground': when(DRY, when(TOP, {'type': 'minecraft:sequence', 'sequence': [
        when(patches(0.3), block_rule('minecraft:coarse_dirt')),
        when(below(-0.45), block_rule('minecraft:grass_block'))]})),
    # Drylands: cracked ground, coarse dirt, packed mud and red sand over the sand.
    'drylands': when(DRY, when(TOP, {'type': 'minecraft:sequence', 'sequence': [
        when(patches(0.1), block_rule('minecraft:coarse_dirt')),
        when(below(-0.4), block_rule('minecraft:packed_mud')),
        when(below(-0.15), block_rule('minecraft:red_sand'))]})),
    # Monument Valley: red sand on the flats.
    'red_sand_floor': when(DRY, when(TOP, block_rule('minecraft:red_sand'))),
    # Mesa Verde: grass on top, coarse dirt in patches.
    'green_top': when(DRY, when(TOP, {'type': 'minecraft:sequence', 'sequence': [
        when(patches(0.35), block_rule('minecraft:coarse_dirt')), block_rule('minecraft:grass_block')]})),
    # Tundra: gravel, coarse dirt and moss in patches through the grass (under thin snow).
    'tundra': when(DRY, when(TOP, {'type': 'minecraft:sequence', 'sequence': [
        when(patches(0.35), block_rule('minecraft:gravel')),
        when(patches(0.1), block_rule('minecraft:coarse_dirt')),
        when(patches(-0.2), block_rule('minecraft:moss_block'))]})),
    # Arctic: snow blocks (the ice spikes stand on them), packed ice in patches.
    'arctic': when(DRY, when(TOP, {'type': 'minecraft:sequence', 'sequence': [
        when(patches(0.4), block_rule('minecraft:packed_ice')), block_rule('minecraft:snow_block')]})),
    # Alps: bare grey rock and scree in patches on the meadows.
    'alpine': when(DRY, when(TOP, {'type': 'minecraft:sequence', 'sequence': [
        when(patches(0.35), block_rule('minecraft:stone')),
        when(below(-0.5), block_rule('minecraft:gravel'))]})),
    # Icy mountains: snow blocks, packed ice and bare rock.
    'icy': when(DRY, when(TOP, {'type': 'minecraft:sequence', 'sequence': [
        when(patches(0.2), block_rule('minecraft:packed_ice')),
        when(below(-0.5), block_rule('minecraft:stone')), block_rule('minecraft:snow_block')]})),
    # Glacier field: moraine gravel, coarse dirt and packed ice.
    'moraine': when(DRY, when(TOP, {'type': 'minecraft:sequence', 'sequence': [
        when(patches(0.25), block_rule('minecraft:gravel')),
        when(below(-0.3), block_rule('minecraft:packed_ice')),
        when(patches(-0.05), block_rule('minecraft:coarse_dirt'))]})),
    # Bryce Mesa: red sand in patches, coarse dirt where pines take hold.
    'bryce_floor': when(DRY, when(TOP, {'type': 'minecraft:sequence', 'sequence': [
        when(patches(0.25), block_rule('minecraft:red_sand')),
        when(below(-0.35), block_rule('minecraft:coarse_dirt'))]})),
}
variant_surfaces = []
for kind in ('edges', 'rivers', 'subs'):
    for vid, spec in VARIANTS[kind].items():
        if not vid.startswith('_') and 'surface' in spec:
            variant_surfaces.append(when({'type': 'minecraft:biome', 'biome_is': [f'{NS}:{vid}']}, SURFACES[spec['surface']]))
# Sub-biomes take their parent's ground (the desert's sand, the badlands' red sand and terracotta, the beach's sand): each
# biome condition naming a parent names its sub-biomes too. Their own ground rules, below, come first.


def parents_ground(node):
    if isinstance(node, dict):
        if node.get('type') == 'minecraft:biome':
            node['biome_is'] = node['biome_is'] + [sid for b in node['biome_is'] for sid in subs_of.get(b, []) if sid not in node['biome_is']]
        for value in node.values():
            parents_ground(value)
    elif isinstance(node, list):
        for value in node:
            parents_ground(value)


parents_ground(noise['surface_rule'])
# Mesa styles: every block of ground above mesa_base_y is the style's band for its height (repeating upward), as the
# badlands' terracotta is; the buttes, tables and hoodoos built on it use the same bands (MesaBands), so their stripes
# line up with the land's.
band_rules = []
for sid, spec in SUBS.items():
    if 'bands' not in spec:
        continue
    layers = [block for block, thick in spec['bands'] for _ in range(thick)]
    runs = []
    for y in range(VARIANTS['mesa_base_y'], KNOBS['noise_top']):
        block = layers[(y - VARIANTS['mesa_base_y']) % len(layers)]
        if runs and runs[-1][1] == block:
            continue
        runs.append((y, block))
    band_rules.append(when({'type': 'minecraft:biome', 'biome_is': [sid]}, {'type': 'minecraft:sequence', 'sequence': [
        when({'type': 'minecraft:y_above', 'anchor': {'absolute': y}, 'surface_depth_multiplier': 0, 'add_stone_depth': False}, block_rule(block))
        for y, block in reversed(runs)]}))
noise['surface_rule']['sequence'][0:0] = [when({'type': 'minecraft:above_preliminary_surface'},
                                               {'type': 'minecraft:sequence', 'sequence': variant_surfaces})] + band_rules
write(f'data/{NS}/worldgen/noise_settings/eternal_return.json', {'fabric:load_conditions': LOAD_IF_MB, **noise})

# ---------------------------------------------------------------- the End
# Vanilla's End noise settings (from the Minecraft jar in the Loom cache) with two changes: the final density goes
# through eternalreturn:outer_end (vanilla's own density within 1,000 blocks of 0, 0 and below y 128, the outer islands
# past that) and the biome source's erosion through eternalreturn:outer_end_biome; and the noise reaches y 256, the
# top of the End, so islands can float high. See "The outer End" in docs/worldgen.md.
LOOM = os.path.join(ROOT, '.gradle', 'loom-cache', 'minecraftMaven', 'net', 'minecraft')
MC_JARS = [j for j in glob.glob(os.path.join(LOOM, 'minecraft-common-*', '*', '*.jar')) if not j.endswith('-sources.jar')]
if not MC_JARS:
    sys.exit('Minecraft jar not found in the Loom cache; run ./gradlew build first')
end = json.loads(zipfile.ZipFile(MC_JARS[0]).read('data/minecraft/worldgen/noise_settings/end.json'))
end['noise']['height'] = 256
end_noise = f'{NS}:outer_end'
end['noise_router']['final_density'] = {'type': f'{NS}:outer_end', 'noise': end_noise, 'inner': end['noise_router']['final_density']}
end['noise_router']['erosion'] = {'type': f'{NS}:outer_end_biome', 'noise': end_noise, 'inner': end['noise_router']['erosion']}
write(f'data/{NS}/worldgen/noise_settings/eternal_return_end.json', end)
# Only its seed is used (OuterEndDensity draws the outer End's seed from it).
write(f'data/{NS}/worldgen/noise/outer_end.json', {'firstOctave': -4, 'amplitudes': [1.0]})

# ---------------------------------------------------------------- world preset
settings = {'moderner_beta:preset': f'{NS}:eternal_return'}
write(f'data/{NS}/worldgen/world_preset/eternal_return.json', {
    'fabric:load_conditions': LOAD_IF_MB,
    'dimensions': {
        'minecraft:overworld': {'type': 'minecraft:overworld', 'generator': {
            'type': 'moderner_beta:moderner_beta',
            'biome_source': {'type': 'moderner_beta:moderner_beta', 'provider_settings': settings, 'cave_provider_settings': settings},
            'provider_settings': settings}},
        'minecraft:the_nether': {'type': 'minecraft:the_nether', 'generator': {
            'type': 'minecraft:noise', 'biome_source': {'type': 'minecraft:multi_noise', 'preset': 'minecraft:nether'}, 'settings': 'minecraft:nether'}},
        'minecraft:the_end': {'type': 'minecraft:the_end', 'generator': {
            'type': 'minecraft:noise', 'biome_source': {'type': 'minecraft:the_end'}, 'settings': f'{NS}:eternal_return_end'}},
    }})
# Shown in the create-world "World Type" list. Optional, so the tag still loads without Moderner Beta.
write('data/minecraft/tags/worldgen/world_preset/normal.json', {'replace': False, 'values': [{'id': f'{NS}:eternal_return', 'required': False}]})

# ---------------------------------------------------------------- Moderner Beta's own preset picker (MB registries; ignored without MB)
write(f'data/{NS}/moderner_beta/settings_preset_category/eternal_return.json', {
    'defaultIcon': f'{NS}:eternal_return',
    'name': {'color': 'gold', 'translate': f'createWorld.customize.modern_beta.preset_category.name.{NS}.eternal_return'},
    'description': {'translate': f'createWorld.customize.modern_beta.preset_category.desc.{NS}.eternal_return'},
    'presetTag': f'{NS}:eternal_return'})
write(f'data/{NS}/tags/moderner_beta/settings_preset/eternal_return.json', {'values': [f'{NS}:eternal_return']})
write('data/moderner_beta/tags/moderner_beta/settings_preset_category/selectable.json', {'replace': False, 'values': [f'{NS}:eternal_return']})
print('preset written with knobs', json.dumps(KNOBS))
