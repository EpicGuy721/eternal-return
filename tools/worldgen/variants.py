"""Renders maps for several knob variants of the Eternal Return preset and prints a stats line for each.

Usage: python tools/worldgen/variants.py <variants.json>
variants.json maps a variant name to knob overrides, e.g. {"wilder": {"depth_weight": 2.0, "scale_weight": 3.0}}.
Each variant is rendered at half size (2048 x 2048 blocks, a few minutes each; set HALF=2048 for the full 4096)
into build/worldgen-variants/<name>/eternal_return/. The shipped preset is rebuilt from tools/worldgen/knobs.json at the end.
"""
import json
import os
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
TOOLS = os.path.join(ROOT, 'tools', 'worldgen')
GRADLEW = os.path.join(ROOT, 'gradlew.bat' if os.name == 'nt' else 'gradlew')
half = os.environ.get('HALF', '1024')
# Optional: render a land-rich area and pin the close-up and side view so variants show the same ground,
# e.g. MAP_CENTRE=-1024,-1024 MAP_CLOSEUP=942,-420 MAP_SLICE_Z=-1328
extra = [f'-P{prop}={os.environ[env]}' for env, prop in
         (('MAP_CENTRE', 'worldmapCentre'), ('MAP_CLOSEUP', 'worldmapCloseup'), ('MAP_SLICE_Z', 'worldmapSliceZ')) if env in os.environ]
shipped = json.load(open(os.path.join(TOOLS, 'knobs.json')))
variants = json.load(open(sys.argv[1]))
os.makedirs(os.path.join(ROOT, 'build', 'worldgen-variants'), exist_ok=True)

try:
    for name, overrides in variants.items():
        out = os.path.join(ROOT, 'build', 'worldgen-variants', name)
        knob_file = out + '.knobs.json'
        json.dump({**shipped, **overrides}, open(knob_file, 'w'))
        subprocess.run([sys.executable, os.path.join(TOOLS, 'build_preset.py'), knob_file], check=True, capture_output=True)
        with open(out + '.log', 'w') as log:
            result = subprocess.run([GRADLEW, 'runWorldmapEternalReturn', f'-PworldmapHalf={half}', f'-PworldmapOut={out}'] + extra,
                                    cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        out = os.path.join(out, 'eternal_return')
        try:
            stats = json.load(open(os.path.join(out, 'stats.json')))
            masses = stats['landmasses']
            mix = stats['terrain_mix']
            print(f"{name}: water={stats['water_share']} ocean_biome={stats['ocean_biome_share']} land_height={stats['land_height']} "
                  f"roughness={stats['land_roughness_blocks_per_4']} landmasses={masses['count_over_1_chunk']} "
                  f"largest_share={masses['largest_share_of_land']} weirdness={stats['weirdness']} "
                  f"mix={ {k: mix[k] for k in ('flat', 'rolling', 'hilly', 'mountains', 'extreme')} } "
                  f"relief_by_biome={mix['median_relief_by_biome']}", flush=True)
        except OSError:
            print(f'{name}: map run failed (exit {result.returncode}), see {out}.log', flush=True)
finally:
    subprocess.run([sys.executable, os.path.join(TOOLS, 'build_preset.py'), os.path.join(TOOLS, 'knobs.json')], check=True, capture_output=True)
