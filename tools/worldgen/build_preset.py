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
    'depth_weight': 1.0,
    'depth_offset': 0.0,
    'scale_weight': 1.0,
    'height_scale_offset': 0.0,
    'modify_only_positive_depth': False,
    'noise_top': 320,
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
ADDED = ['minecraft:birch_forest', 'minecraft:savanna', 'minecraft:badlands', 'minecraft:dark_forest', 'minecraft:cherry_grove']
# Each 1.6.4 biome twice, each added biome once: the added five make up about a quarter of temperate land.
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

# ---------------------------------------------------------------- caves: no modern cave biomes
preset['caveBiomeSettings'] = {'moderner_beta:provider': 'moderner_beta:none'}

# ---------------------------------------------------------------- structures: no trial chambers (Eternal Return worlds only)
cs = preset['chunkSettings']
cs['moderner_beta:structure_modifiers']['removed'] = ['minecraft:trial_chambers']

# ---------------------------------------------------------------- terrain
cs['moderner_beta:deepslate_generation']['enabled'] = False
cs['moderner_beta:noise_generator_settings'] = f'{NS}:eternal_return'
land = cs['moderner_beta:noise_landmass']
land['scale']['sample'] = KNOBS['scale_sample']
land['scale']['influence'] = KNOBS['scale_influence']
land['scale']['offset'] = KNOBS['scale_offset']
land['depth']['influence'] = KNOBS['depth_influence']
land['depth']['negativeFlattening'] = KNOBS['depth_negative_flattening']
cs['moderner_beta:noise_3d_settings']['climateHeightScaling'] = KNOBS['climate_height_scaling']
cs['moderner_beta:noise_scale']['stretchY'] = KNOBS['stretch_y']
cs['moderner_beta:noise_scale']['baseSize'] = KNOBS['base_size']
forced = cs['moderner_beta:forced_biome_height']
forced['depthWeight'] = KNOBS['depth_weight']
forced['depthOffset'] = KNOBS['depth_offset']
forced['scaleWeight'] = KNOBS['scale_weight']
forced['scaleOffset'] = KNOBS['height_scale_offset']
forced['modifyOnlyPositiveDepth'] = KNOBS['modify_only_positive_depth']
# Cherry groves: Moderner Beta gives them a plateau height (1.5;0.6), which these knobs turn into a sheer
# flat-topped block; a raised, rolling highland (and higher hills) suits them better, like vanilla's meadows.
forced['heightOverrides']['minecraft:cherry_grove'] = '0.35;0.6'
forced['heightOverrides']['minecraft:cherry_grove*hills'] = '0.6;0.7'
# Ocean heights, as Moderner Beta's Release 1.17.1 preset sets them. Its height table only covers the
# biomes it believes a world can produce, which misses the deep cold, deep frozen and deep lukewarm
# oceans and the deep warm one (warm_ocean*deep) that the ocean climate step makes; without these lines
# they get the default land height and rise into hills that still report an ocean biome.
for ocean in ['ocean', 'cold_ocean', 'lukewarm_ocean', 'warm_ocean', 'frozen_ocean']:
    forced['heightOverrides']['minecraft:' + ocean] = '-1.0;0.2'
for ocean in ['deep_ocean', 'deep_cold_ocean', 'deep_lukewarm_ocean', 'deep_frozen_ocean']:
    forced['heightOverrides']['minecraft:' + ocean] = '-1.8;0.2'
forced['heightOverrides']['minecraft:warm_ocean*deep'] = '-1.0;0.2'

preset['name'] = {'color': 'gold', 'translate': f'createWorld.customize.modern_beta.preset.name.{NS}.eternal_return'}
preset['description'] = {'translate': f'createWorld.customize.modern_beta.preset.desc.{NS}.eternal_return'}
write(f'data/{NS}/moderner_beta/settings_preset/eternal_return.json', preset)

# ---------------------------------------------------------------- noise settings: full -64..320 range
noise = mb_json('worldgen/noise_settings/overworld_256.json')
noise['noise']['min_y'] = -64
noise['noise']['height'] = KNOBS['noise_top'] + 64
# Biome stone: after every topsoil, beach and badlands-band rule, so it only reaches what would stay
# stone. Switched by the config setting worldgen.biomeStone (the eternalreturn:biome_stone_enabled
# condition). No blending: each block takes the stone of the biome it is in.
stone_rules = [{'type': 'minecraft:condition',
                'if_true': {'type': 'minecraft:biome', 'biome_is': biomes},
                'then_run': {'type': 'minecraft:block', 'result_state': {'Name': block}}}
               for block, biomes in KNOBS['biome_stone'].items() if block != 'minecraft:stone' and biomes]
# The host stones, for Moderner Beta's own surface pass (BiomeStoneSurface): it must take them for stone.
write(f'data/{NS}/tags/block/biome_host_stones.json',
      {'values': [block for block, biomes in KNOBS['biome_stone'].items() if block != 'minecraft:stone' and biomes]})
if stone_rules:
    noise['surface_rule']['sequence'].append({'type': 'minecraft:condition',
                                              'if_true': {'type': f'{NS}:biome_stone_enabled'},
                                              'then_run': {'type': 'minecraft:sequence', 'sequence': stone_rules}})
write(f'data/{NS}/worldgen/noise_settings/eternal_return.json', {'fabric:load_conditions': LOAD_IF_MB, **noise})

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
            'type': 'minecraft:noise', 'biome_source': {'type': 'minecraft:the_end'}, 'settings': 'minecraft:end'}},
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
