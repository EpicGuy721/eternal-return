"""Generates the resources for troughs (com.eternalreturn.survival.Troughs), one for each wood, and their contents,
from vanilla's textures (read from the Minecraft jars in the Loom cache, so run a Gradle build first).

Run from anywhere: python tools/troughs/build_troughs.py

A trough is a composter half as tall, its walls and floor the wood's planks; what's in it (water, hay, carrots or
seeds, block state contents) shows as a surface at one of four heights (block state level 1 to 4).

Writes (all under src/main/resources):
- block models: template_trough (the walls and floor, #planks), <wood>_trough on it, and trough_<contents>_<level>
  (the surface: vanilla's still water, tinted the biome's water colour; hay bale top; the carrot and seed piles);
- blockstates (multipart: the trough and its contents' surface) and item models;
- textures trough_carrots and trough_seeds: piles of carrots and of seeds, drawn in the colours of vanilla's carrot
  and wheat seeds icons;
- loot tables (the trough drops itself), recipes (five of the wood's slabs in a U: a trough), the tags
  eternalreturn:troughs (blocks and items), and minecraft:mineable/axe (#eternalreturn:troughs).

Do not hand-edit the generated files; change this script and rerun it.
"""
import glob
import io
import json
import os
import random
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', '..')
RES = os.path.join(ROOT, 'src', 'main', 'resources')
NS = 'eternalreturn'
LOOM = os.path.join(ROOT, '.gradle', 'loom-cache', 'minecraftMaven', 'net', 'minecraft')
CLIENT = [j for j in glob.glob(os.path.join(LOOM, 'minecraft-clientOnly-*', '*', '*.jar')) if not j.endswith('-sources.jar')]
if not CLIENT:
    raise SystemExit('Minecraft jars not found in the Loom cache; run ./gradlew build first')
JAR = zipfile.ZipFile(CLIENT[0])

# Each wood: its namespace (where its slab and planks are). Same order as Troughs.WOODS.
WOODS = [('oak', 'minecraft'), ('spruce', 'minecraft'), ('birch', 'minecraft'), ('jungle', 'minecraft'), ('acacia', 'minecraft'),
         ('dark_oak', 'minecraft'), ('mangrove', 'minecraft'), ('cherry', 'minecraft'), ('bamboo', 'minecraft'), ('crimson', 'minecraft'),
         ('warped', 'minecraft'), ('maple', NS), ('pale_oak', NS), ('azalea', NS)]
CONTENTS = ['water', 'hay', 'carrots', 'seeds']
# The surface's height (in pixels) at each level, 1 to 4: the floor is 2 high, the rim 8.
HEIGHTS = [3.5, 5.0, 6.5, 7.5]
SURFACES = {
    'water': ('minecraft:block/water_still', True),
    'hay': ('minecraft:block/hay_block_top', False),
    'carrots': (f'{NS}:block/trough_carrots', False),
    'seeds': (f'{NS}:block/trough_seeds', False),
}


def write_json(rel, data):
    full = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def vanilla_image(path):
    return Image.open(io.BytesIO(JAR.read(f'assets/minecraft/textures/{path}.png'))).convert('RGBA')


def luma(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


def palette(image, test=lambda p: True):
    """The distinct opaque colours of an icon passing the test, darkest first."""
    return sorted({p[:3] for p in image.getdata() if p[3] > 0 and test(p)}, key=luma)


def face(texture, cull=None, tint=False):
    f = {'texture': texture}
    if cull:
        f['cullface'] = cull
    if tint:
        f['tintindex'] = 0
    return f


def template():
    planks = '#planks'
    sides = ('north', 'south', 'west', 'east')
    # The floor, then the four walls (north and south whole, west and east between them).
    elements = [{'from': [0, 0, 0], 'to': [16, 2, 16],
                 'faces': {'down': face(planks, 'down'), 'up': face(planks), **{s: face(planks, s) for s in sides}}}]
    walls = [([0, 2, 0], [16, 8, 2], 'north'), ([0, 2, 14], [16, 8, 16], 'south'), ([0, 2, 2], [2, 8, 14], 'west'), ([14, 2, 2], [16, 8, 14], 'east')]
    for start, end, outside in walls:
        faces = {'up': face(planks)}
        for s in sides:
            faces[s] = face(planks, s if s == outside else None)
        elements.append({'from': start, 'to': end, 'faces': faces})
    return {'parent': 'minecraft:block/block', 'textures': {'particle': planks}, 'elements': elements}


def surface(texture, tint, height):
    return {'textures': {'particle': texture, 'surface': texture},
            'elements': [{'from': [2, height, 2], 'to': [14, height, 14], 'faces': {'up': face('#surface', tint=tint)}}]}


def pile(seed, base, draw):
    rng = random.Random(seed)
    image = Image.new('RGBA', (16, 16), base + (255,))
    draw(image, rng)
    return image


def carrots(image, rng):
    """Carrots lying every which way: a stroke of the orange ramp, light along its top, a green tuft at its head."""
    icon = vanilla_image('item/carrot')
    orange = palette(icon, lambda p: p[0] > p[1] + 40)
    green = palette(icon, lambda p: p[1] > p[0] + 10)
    spots = [(x, y) for y in range(0, 16, 3) for x in range(0, 16, 3)]
    rng.shuffle(spots)
    for x0, y0 in spots:
        dx, dy = rng.choice([(1, 1), (1, -1), (-1, 1), (-1, -1)])
        x, y = x0 + rng.randrange(4), y0 + rng.randrange(4)
        for i in range(4):
            shade = orange[min(len(orange) - 1, 1 + i // 2 + (1 if i == 1 else 0))]
            image.putpixel(((x + dx * i) % 16, (y + dy * i) % 16), shade + (255,))
            image.putpixel(((x + dx * i + 1) % 16, (y + dy * i) % 16), orange[max(0, len(orange) - 2 - i // 2)] + (255,))
        image.putpixel(((x - dx) % 16, (y - dy) % 16), green[-1] + (255,))
        image.putpixel(((x - dx * 2) % 16, (y - dy) % 16), green[len(green) // 2] + (255,))


def seeds(image, rng):
    """Seeds heaped: each pixel a seed's colour, the dark ones the gaps between, dithered."""
    icon = vanilla_image('item/wheat_seeds')
    ramp = palette(icon)
    for y in range(16):
        for x in range(16):
            r = rng.random()
            if r < 0.15:
                shade = ramp[0]
            elif r < 0.55:
                shade = ramp[len(ramp) // 3]
            elif r < 0.9:
                shade = ramp[2 * len(ramp) // 3]
            else:
                shade = ramp[-1]
            image.putpixel((x, y), shade + (255,))


def main():
    write_json(f'assets/{NS}/models/block/template_trough.json', template())
    for contents in CONTENTS:
        texture, tint = SURFACES[contents]
        for level, height in enumerate(HEIGHTS, 1):
            write_json(f'assets/{NS}/models/block/trough_{contents}_{level}.json', surface(texture, tint, height))
    carrot_base = palette(vanilla_image('item/carrot'), lambda p: p[0] > p[1] + 40)[0]
    pile(1, carrot_base, carrots).save(os.path.join(RES, 'assets', NS, 'textures', 'block', 'trough_carrots.png'))
    seed_base = palette(vanilla_image('item/wheat_seeds'))[0]
    pile(2, seed_base, seeds).save(os.path.join(RES, 'assets', NS, 'textures', 'block', 'trough_seeds.png'))

    troughs = []
    for wood, ns in WOODS:
        trough = f'{wood}_trough'
        troughs.append(f'{NS}:{trough}')
        planks = f'{ns}:block/{wood}_planks'
        write_json(f'assets/{NS}/models/block/{trough}.json', {'parent': f'{NS}:block/template_trough', 'textures': {'planks': planks, 'particle': planks}})
        parts = [{'apply': {'model': f'{NS}:block/{trough}'}}]
        for contents in CONTENTS:
            for level in range(1, len(HEIGHTS) + 1):
                parts.append({'when': {'contents': contents, 'level': str(level)}, 'apply': {'model': f'{NS}:block/trough_{contents}_{level}'}})
        write_json(f'assets/{NS}/blockstates/{trough}.json', {'multipart': parts})
        write_json(f'assets/{NS}/models/item/{trough}.json', {'parent': f'{NS}:block/{trough}'})
        write_json(f'data/{NS}/loot_table/blocks/{trough}.json', {
            'type': 'minecraft:block',
            'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{trough}'}],
                       'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
            'random_sequence': f'{NS}:blocks/{trough}'})
        write_json(f'data/{NS}/recipe/{trough}.json', {
            'type': 'minecraft:crafting_shaped', 'category': 'misc', 'group': 'trough',
            'pattern': ['S S', 'SSS'], 'key': {'S': {'item': f'{ns}:{wood}_slab'}},
            'result': {'count': 1, 'id': f'{NS}:{trough}'}})
    write_json(f'data/{NS}/tags/block/troughs.json', {'values': troughs})
    write_json(f'data/{NS}/tags/item/troughs.json', {'values': troughs})
    write_json('data/minecraft/tags/block/mineable/axe.json', {'replace': False, 'values': [f'#{NS}:troughs']})


if __name__ == '__main__':
    main()
