"""Builds a before/after picture of two map runs of the same ground: the two close-ups side by side, the two
side views one above the other, and the headline numbers of each. Needs Pillow.

Usage: python tools/worldgen/before_after.py <before dir> <after dir> <out.png> [before label] [after label]
Each dir is a map run's output (height.png, closeup.png, slice.png, stats.json), e.g. docs/worldgen-maps/eternal_return.
Pin the close-up and side view (-PworldmapCloseup, -PworldmapSliceZ) so both runs show the same ground.
"""
import json
import os
import sys

from PIL import Image, ImageDraw

before_dir, after_dir, out = sys.argv[1:4]
labels = sys.argv[4:6] if len(sys.argv) > 5 else ['Before', 'After']
CLOSEUP = 512
GAP = 10
SLICE_WIDTH = 2 * CLOSEUP + GAP
SLICE_LOW, SLICE_HIGH = 40, 200


def numbers(s):
    w = s['weirdness']
    m = s['terrain_mix']
    h = s['land_height']
    return [f"land height median {h['p50']}, 99th percentile {h['p99']}, highest {h['max']}",
            f"roughness {s['land_roughness_blocks_per_4']} blocks per 4",
            f"flat {m['flat'] * 100:.0f}%  rolling {m['rolling'] * 100:.0f}%  hilly {m['hilly'] * 100:.0f}%  "
            f"mountains {m['mountains'] * 100:.1f}%  extreme {m['extreme'] * 100:.1f}%",
            f"steps of 2+ blocks {w['steps_2_or_more'] * 100:.1f}%, 4+ {w['steps_4_or_more'] * 100:.2f}%, 8+ {w['steps_8_or_more'] * 100:.2f}%",
            f"columns with an overhang {w['land_columns_with_overhang'] * 100:.1f}%, floating pieces {w['floating_pieces']}"]


runs = []
for path in (before_dir, after_dir):
    stats = json.load(open(os.path.join(path, 'stats.json')))
    top = int(stats['world_height'].split(' to ')[1])
    side = Image.open(os.path.join(path, 'slice.png')).convert('RGB')
    side = side.crop((0, top - SLICE_HIGH, min(side.width, SLICE_WIDTH), top - SLICE_LOW))
    runs.append((stats, Image.open(os.path.join(path, 'closeup.png')).convert('RGB'), side))

line = 16
text_rows = 1 + len(numbers(runs[0][0]))
height = line + CLOSEUP + GAP + 2 * (line + (SLICE_HIGH - SLICE_LOW) + GAP) + text_rows * line + GAP
image = Image.new('RGB', (SLICE_WIDTH, height), 'white')
draw = ImageDraw.Draw(image)
for i, (stats, closeup, side) in enumerate(runs):
    x = i * (CLOSEUP + GAP)
    draw.text((x + 4, 2), f"{labels[i]}: close-up around {stats['closeup_centre']}, 1 pixel per block", fill='black')
    image.paste(closeup, (x, line))
y = line + CLOSEUP + GAP
for i, (stats, closeup, side) in enumerate(runs):
    draw.text((4, y), f"{labels[i]}: side view along z={stats['slice_z']}, y {SLICE_LOW}-{SLICE_HIGH}", fill='black')
    image.paste(side, (0, y + line))
    y += line + side.height + GAP
for i, (stats, closeup, side) in enumerate(runs):
    x = i * (CLOSEUP + GAP)
    draw.text((x + 4, y), labels[i], fill='black')
    for j, text in enumerate(numbers(stats)):
        draw.text((x + 4, y + (j + 1) * line), text, fill='black')
image.save(out)
print('wrote', out)
