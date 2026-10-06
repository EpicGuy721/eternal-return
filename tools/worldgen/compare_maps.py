"""Builds docs/worldgen-maps/overview.png and slices_compared.png from the three map runs
(run ./gradlew generateWorldMaps first). Needs Pillow.

Usage: python tools/worldgen/compare_maps.py [maps dir]   (default docs/worldgen-maps)
"""
import json
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
MAPS = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'docs', 'worldgen-maps')
RUNS = [('Beta 1.7.3', 'beta_1_7_3'), ('Release 1.6.4', 'release_1_6_4'), ('Eternal Return', 'eternal_return')]
TILE = 480
GAP = 20
CAPTION = 30


def pct(value):
    return f'{value * 100:.1f}%'


stats = {name: json.load(open(os.path.join(MAPS, name, 'stats.json'))) for _, name in RUNS}

# ---- overview: height map on top, land/water below, one column per preset
overview = Image.new('RGB', (3 * TILE + 2 * GAP, 2 * (TILE + CAPTION) + GAP // 2), 'white')
draw = ImageDraw.Draw(overview)
for i, (label, name) in enumerate(RUNS):
    s = stats[name]
    mix = s['terrain_mix']
    x = i * (TILE + GAP)
    draw.text((x + 4, 8), f"{label}  water {pct(s['water_share'])}  ocean biomes {pct(s['ocean_biome_share'])}", fill='black')
    overview.paste(Image.open(os.path.join(MAPS, name, 'height.png')).convert('RGB').resize((TILE, TILE)), (x, CAPTION))
    y = CAPTION + TILE + GAP // 2
    overview.paste(Image.open(os.path.join(MAPS, name, 'land.png')).convert('RGB').resize((TILE, TILE)), (x, y))
    draw.text((x + 4, y + TILE + 4),
              f"landmasses {s['landmasses']['count_over_1_chunk']}, tallest land {s['land_height']['max']}, "
              f"overhangs {pct(s['weirdness']['land_columns_with_overhang'])}", fill='black')
    draw.text((x + 4, y + TILE + 16),
              f"flat {pct(mix['flat'])} rolling {pct(mix['rolling'])} hilly {pct(mix['hilly'])} "
              f"mountains {pct(mix['mountains'])} extreme {pct(mix['extreme'])}", fill='black')
overview = overview.crop((0, 0, overview.width, CAPTION + TILE + GAP // 2 + TILE + 32))
overview.save(os.path.join(MAPS, 'overview.png'))

# ---- side views: y 40 to 320 (the slice image spans the whole world height, top row = top of the world)
width = 1000
rows = []
for label, name in RUNS:
    s = stats[name]
    image = Image.open(os.path.join(MAPS, name, 'slice.png')).convert('RGB')
    top = int(s['world_height'].split(' to ')[1])
    crop = image.crop((0, 0, width, top - 40))
    row = Image.new('RGB', (width, crop.height + 16), 'white')
    ImageDraw.Draw(row).text((4, 2), f"{label}: z={s['slice_z']}, {width} blocks from the west edge of the slice, y 40-{top}", fill='black')
    row.paste(crop, (0, 16))
    rows.append(row)
slices = Image.new('RGB', (width, sum(r.height for r in rows) + 8 * (len(rows) - 1)), 'white')
y = 0
for row in rows:
    slices.paste(row, (0, y))
    y += row.height + 8
slices.save(os.path.join(MAPS, 'slices_compared.png'))
print('wrote overview.png and slices_compared.png in', MAPS)
