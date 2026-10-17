"""Draws each biome's terrain mix (flat / rolling / hilly / mountains, from terrain_mix.mix_by_biome in a map
run's stats.json) as one bar per biome, one panel per run. Needs Pillow.

Usage: python tools/worldgen/shape_by_biome.py <out.png> <label>=<map dir> [<label>=<map dir> ...]
"""
import json
import os
import sys

from PIL import Image, ImageDraw

COLOURS = [(126, 190, 92), (230, 200, 90), (205, 120, 60), (120, 90, 80)]
NAMES = ['flat', 'rolling', 'hilly', 'mountains']
BAR = 18
LABEL = 170
WIDTH = 360
SKIP = {'beach', 'river', 'frozen_river'}

runs = []
for arg in sys.argv[2:]:
    label, path = arg.split('=', 1)
    mixes = json.load(open(os.path.join(path, 'stats.json')))['terrain_mix']['mix_by_biome']
    rows = []
    for biome, text in sorted(mixes.items()):
        if biome in SKIP:
            continue
        shares, count = text.split('% of ')
        rows.append((biome.replace('early_release_', '').replace('late_beta_', ''), [float(v) for v in shares.split(' / ')], int(count)))
    runs.append((label, rows))

tallest = max(len(rows) for _, rows in runs)
panel = LABEL + WIDTH + 30
image = Image.new('RGB', (panel * len(runs), 40 + tallest * (BAR + 4) + 30), 'white')
draw = ImageDraw.Draw(image)
for i, (label, rows) in enumerate(runs):
    x0 = i * panel
    draw.text((x0 + 4, 6), label, fill='black')
    for j, (biome, shares, count) in enumerate(rows):
        y = 30 + j * (BAR + 4)
        draw.text((x0 + 4, y + 3), f'{biome} ({count})', fill='black')
        x = x0 + LABEL
        for share, colour in zip(shares, COLOURS):
            w = round(WIDTH * share / 100)
            draw.rectangle((x, y, x + w, y + BAR), fill=colour)
            x += w
y = 30 + tallest * (BAR + 4) + 6
x = 4
for name, colour in zip(NAMES, COLOURS):
    draw.rectangle((x, y, x + 14, y + 14), fill=colour)
    draw.text((x + 18, y + 1), name, fill='black')
    x += 110
draw.text((x, y + 1), 'share of each biome\'s 32 x 32 block squares (number of squares in brackets)', fill='black')
image.save(sys.argv[1])
print('wrote', sys.argv[1])
