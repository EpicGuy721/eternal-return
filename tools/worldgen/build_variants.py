"""Writes the biome variants of Eternal Return worlds (worldgen expansion phase 3) from tools/worldgen/variants.json:

- data/eternalreturn/worldgen/biome/<variant>.json: a copy of the base biome (from the Minecraft jar, the Moderner
  Beta jar or this mod) with the table's changes: temperature, downfall, colours, trees thinned to one in N (a
  copy of each tree feature with a rarity filter, worldgen/placed_feature/thinned/), features added or removed.
  Loaded only with Moderner Beta, like the preset;
- data/eternalreturn/worldgen/placed_feature/scrub_thorn_bush.json, the desert scrub's thorn bushes, and variant/*,
  copies of the vanilla features variants add (see own_copy);
- resourcepacks/biome_variants/: a built-in data pack, always on, that puts every variant in every biome tag its
  base is in (vanilla's, Moderner Beta's, the c: convention tags and this mod's), so fish, birds, mobs, ores,
  biome stone and everything else that goes by tag treat a variant as its base. A separate pack, so these lines
  never touch the tag files other scripts write;
- eternalreturn/biome_variants.json (read by the game, BiomeVariants): each variant's base, kind and boulders;
- the biome names in en_us.json (other lines kept).

build_preset.py reads the same table for the preset's side: the peak rules, the edge and river layers, heights
and surface rules. Run build_preset.py first, then this (it reads the biome stone tags that one writes).

Run: python tools/worldgen/build_variants.py
"""
import copy
import glob
import json
import os
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(ROOT, 'src/main/resources')
NS = 'eternalreturn'
TABLE = json.load(open(os.path.join(ROOT, 'tools/worldgen/variants.json'), encoding='utf-8'))
LOAD_IF_MB = [{'condition': 'fabric:all_mods_loaded', 'values': ['moderner_beta']}]
PACK = os.path.join(RES, 'resourcepacks', 'biome_variants')

MB_JAR = zipfile.ZipFile(glob.glob(os.path.expanduser(
    '~/.gradle/caches/modules-2/files-2.1/maven.modrinth/moderner-beta/5.0.0-alpha.2+1.21.1-fabric/*/*.jar'))[0])
LOOM = os.path.join(ROOT, '.gradle', 'loom-cache', 'minecraftMaven', 'net', 'minecraft')
MC_JAR = zipfile.ZipFile([j for j in glob.glob(os.path.join(LOOM, 'minecraft-common-*', '*', '*.jar')) if not j.endswith('-sources.jar')][0])
C_JAR = zipfile.ZipFile([j for j in glob.glob(os.path.expanduser(
    '~/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-convention-tags-v2/*/*/*.jar')) if 'sources' not in j][0])


def write_json(path, obj, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=indent, ensure_ascii=False)
        f.write('\n')


def read_data(kind, ident):
    """A data file (kind e.g. 'worldgen/biome') by id, from this mod, Moderner Beta or Minecraft."""
    ns, path = ident.split(':')
    own = os.path.join(RES, 'data', ns, kind, path + '.json')
    if os.path.exists(own):
        return json.load(open(own, encoding='utf-8'))
    for jar in (MB_JAR, MC_JAR):
        name = f'data/{ns}/{kind}/{path}.json'
        if name in jar.namelist():
            return json.loads(jar.read(name))
    raise KeyError(f'{kind} {ident} not found')


STEPS = ['raw_generation', 'lakes', 'local_modifications', 'underground_structures', 'surface_structures', 'strongholds',
         'underground_ores', 'underground_decoration', 'fluid_springs', 'vegetal_decoration', 'top_layer_modification']


def is_tree(feature):
    path = feature.split(':')[1]
    return 'tree' in path or path == 'dark_forest_vegetation'


def thinned(feature, keep_one_in):
    """A copy of a tree feature that keeps one tree in keep_one_in (a rarity filter on each spot)."""
    ns, path = feature.split(':')
    name = f'thinned/{ns}_{path}_{keep_one_in}'
    placed = copy.deepcopy(read_data('worldgen/placed_feature', feature))
    placement = placed['placement']
    filt = {'type': 'minecraft:rarity_filter', 'chance': keep_one_in}
    at = next((i for i, p in enumerate(placement) if p.get('type') == 'minecraft:biome'), len(placement))
    placement.insert(at, filt)
    out = {'fabric:load_conditions': LOAD_IF_MB, **placed} if ns == 'moderner_beta' else placed
    write_json(os.path.join(RES, 'data', NS, 'worldgen', 'placed_feature', name + '.json'), out)
    return f'{NS}:{name}'


def own_copy(feature):
    """A variant's added feature, as a copy of its own (worldgen/placed_feature/variant/): every biome must list the
    features it shares with others in one order, and a vanilla feature added at the end of a variant's list sits
    in the middle of another biome's (the game refuses such a cycle). A copy appears only at the ends of variants'
    lists, so it never conflicts."""
    ns, path = feature.split(':')
    if ns == NS:
        return feature
    name = f'variant/{ns}_{path}'
    placed = read_data('worldgen/placed_feature', feature)
    out = {'fabric:load_conditions': LOAD_IF_MB, **placed} if ns == 'moderner_beta' else placed
    write_json(os.path.join(RES, 'data', NS, 'worldgen', 'placed_feature', name + '.json'), out)
    return f'{NS}:{name}'


def variant_biome(vid, base, spec):
    biome = copy.deepcopy(read_data('worldgen/biome', base))
    if 'temperature' in spec:
        biome['temperature'] = round(biome['temperature'] + spec['temperature'], 3) if spec.get('_relative') else spec['temperature']
    if 'downfall' in spec:
        biome['downfall'] = spec['downfall']
    for key, value in spec.get('effects', {}).items():
        biome['effects'][key] = value
    features = [list(step) for step in biome['features']]
    while len(features) < len(STEPS):
        features.append([])
    if spec.get('thin_trees'):
        veg = STEPS.index('vegetal_decoration')
        features[veg] = [thinned(f, spec['thin_trees']) if is_tree(f) else f for f in features[veg]]
    removed = set(spec.get('remove_features', []))
    features = [[f for f in step if f not in removed] for step in features]
    for step, added in spec.get('add_features', {}).items():
        features[STEPS.index(step)] += [own_copy(f) for f in added]
    biome['features'] = features
    write_json(os.path.join(RES, 'data', NS, 'worldgen', 'biome', vid + '.json'), {'fabric:load_conditions': LOAD_IF_MB, **biome})


def all_variants():
    """variant id -> (base id, kind, spec), with peaks' temperatures relative to their base."""
    out = {}
    for vid, spec in TABLE['peaks'].items():
        out[vid] = (spec['base'], 'peak', {**spec, '_relative': True})
    for vid, spec in TABLE['edges'].items():
        out[vid] = (spec['base'], 'edge', spec)
    for vid, spec in TABLE['rivers'].items():
        out[vid] = ('minecraft:river', 'river', spec)
    return out


def tag_files():
    """Every biome tag file this mod, Moderner Beta, Minecraft and the c: convention tags ship: id -> values."""
    tags = {}
    for jar in (MC_JAR, MB_JAR, C_JAR):
        for name in jar.namelist():
            parts = name.split('/')
            if len(parts) > 4 and parts[0] == 'data' and parts[2:5] == ['tags', 'worldgen', 'biome'] and name.endswith('.json'):
                ident = parts[1] + ':' + '/'.join(parts[5:])[:-5]
                tags.setdefault(ident, []).extend(json.loads(jar.read(name)).get('values', []))
    data = os.path.join(RES, 'data')
    for path in glob.glob(os.path.join(data, '*', 'tags', 'worldgen', 'biome', '**', '*.json'), recursive=True):
        rel = os.path.relpath(path, data).replace('\\', '/').split('/')
        ident = rel[0] + ':' + '/'.join(rel[4:])[:-5]
        tags.setdefault(ident, []).extend(json.load(open(path, encoding='utf-8')).get('values', []))
    return tags


def main():
    variants = all_variants()
    for vid, (base, kind, spec) in variants.items():
        variant_biome(vid, base, spec)
    # The desert scrub's thorn bushes: a couple of tries a chunk, where an acacia sapling could grow (coarse dirt).
    write_json(os.path.join(RES, 'data', NS, 'worldgen', 'placed_feature', 'scrub_thorn_bush.json'), {
        'feature': f'{NS}:tree/thorn_bush',
        'placement': [{'type': 'minecraft:count', 'count': 2}, {'type': 'minecraft:in_square'},
                      {'type': 'minecraft:surface_water_depth_filter', 'max_water_depth': 0},
                      {'type': 'minecraft:heightmap', 'heightmap': 'OCEAN_FLOOR'},
                      {'type': 'minecraft:block_predicate_filter', 'predicate': {
                          'type': 'minecraft:would_survive', 'state': {'Name': 'minecraft:acacia_sapling', 'Properties': {'stage': '0'}}}},
                      {'type': 'minecraft:biome'}]})

    # Tags: every variant joins each tag its base is named in (directly; a tag that names another tag gets it through that one).
    for old in glob.glob(os.path.join(PACK, 'data', '**', '*.json'), recursive=True):
        os.remove(old)
    by_base = {}
    for vid, (base, kind, spec) in variants.items():
        by_base.setdefault(base, []).append(f'{NS}:{vid}')
    count = 0
    for tag, values in sorted(tag_files().items()):
        names = {v if isinstance(v, str) else v['id'] for v in values}
        added = sorted({vid for base, vids in by_base.items() if base in names for vid in vids})
        if added:
            ns, path = tag.split(':')
            write_json(os.path.join(PACK, 'data', ns, 'tags', 'worldgen', 'biome', path + '.json'),
                       {'replace': False, 'values': [{'id': v, 'required': False} for v in added]})
            count += 1
    write_json(os.path.join(PACK, 'pack.mcmeta'), {'pack': {'pack_format': 48, 'description': 'Eternal Return: biome variants in their base biomes\' tags'}})

    # What the game reads: each variant's base, kind and boulders.
    write_json(os.path.join(RES, NS, 'biome_variants.json'), {
        f'{NS}:{vid}': {k: v for k, v in {'base': base, 'kind': kind, 'boulders': spec.get('boulders')}.items() if v is not None}
        for vid, (base, kind, spec) in variants.items()})

    lang_file = os.path.join(RES, 'assets', NS, 'lang', 'en_us.json')
    lang = json.load(open(lang_file, encoding='utf-8'))
    for vid, (base, kind, spec) in variants.items():
        lang[f'biome.{NS}.{vid}'] = spec['name']
    write_json(lang_file, lang, indent='\t')
    print(f'{len(variants)} variants ({sum(1 for v in variants.values() if v[1] == "peak")} peaks, '
          f'{sum(1 for v in variants.values() if v[1] == "edge")} edges, {sum(1 for v in variants.values() if v[1] == "river")} rivers), '
          f'{count} tags extended')


if __name__ == '__main__':
    main()
