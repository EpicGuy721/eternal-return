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
ADDED = ['minecraft:birch_forest', 'minecraft:savanna', 'minecraft:badlands', 'minecraft:dark_forest']
# Each 1.6.4 biome twice, each added biome once: the added four make up about a fifth of temperate land.
step(pipeline, 'biome_pool', 'moderner_beta:random_biome')['biomes'] = CLASSIC + CLASSIC + ADDED

hills = step(pipeline, 'hills', 'moderner_beta:biome_replacement')['targets']
for biome in ['minecraft:birch_forest', 'minecraft:savanna', 'minecraft:badlands']:
    hills[biome] = {'type': 'biome', 'value': biome + '*hills'}
for s in pipeline:
    if s['type'] == 'moderner_beta:conditional_overlay' and s.get('onMatch', {}).get('value') == 'hills':
        for term in s['predicate']['terms']:
            if term.get('condition') == 'moderner_beta:in_set':
                term['biomes'] += ['minecraft:birch_forest', 'minecraft:savanna', 'minecraft:badlands']

# ---------------------------------------------------------------- caves: no modern cave biomes
preset['caveBiomeSettings'] = {'moderner_beta:provider': 'moderner_beta:none'}

# ---------------------------------------------------------------- terrain
cs = preset['chunkSettings']
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

preset['name'] = {'color': 'gold', 'translate': f'createWorld.customize.modern_beta.preset.name.{NS}.eternal_return'}
preset['description'] = {'translate': f'createWorld.customize.modern_beta.preset.desc.{NS}.eternal_return'}
write(f'data/{NS}/moderner_beta/settings_preset/eternal_return.json', preset)

# ---------------------------------------------------------------- noise settings: full -64..320 range
noise = mb_json('worldgen/noise_settings/overworld_256.json')
noise['noise']['min_y'] = -64
noise['noise']['height'] = KNOBS['noise_top'] + 64
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
