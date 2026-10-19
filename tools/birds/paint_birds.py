"""Paints the birds' texture layers, one set per shape, from each shape's model (tools/models/bird_<shape>.bbmodel):
the faces are found from the model's box-UV layout, and each part is painted by its role (its name).

Run from anywhere: python tools/birds/paint_birds.py [preview_dir]

Writes src/main/resources/assets/eternalreturn/textures/entity/bird/<shape>/:
- base.png: the bird's body, neck, head, wings and tail in light grays (shaded by face), tinted with its base colour;
- pattern_<name>.png for cap, mask, breast, wings, tail and head: the same grays where the pattern lies, transparent
  elsewhere, tinted with its pattern colour and drawn over the base;
- details.png: what is never tinted: the eyes (near black), the beak or bill, legs and feet. The palette's darkest
  colours stay a little lighter than the eyes, so a black-headed bird's eyes still show.
With a preview_dir, also writes preview_<shape>_<bird>.png there: birds with colours applied, for checking in Blockbench.
Hand edits to these textures are lost when this is rerun.
"""
import json
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', '..')
MODELS = os.path.join(ROOT, 'tools', 'models')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'eternalreturn', 'textures', 'entity', 'bird')

# Light grays by face: lit from above, darker underneath and behind. Wings and tails are flat, with grays of their own.
SHADE = {'up': 250, 'north': 240, 'east': 230, 'west': 230, 'south': 215, 'down': 205}
WING, TAIL = 226, 220

# Per shape: the untinted colours of its beak (or bill), legs and feet.
SHAPES = {
    'songbird': {'beak': (66, 58, 52), 'legs': (92, 72, 64)},
    'waterfowl': {'beak': (196, 170, 70), 'legs': (214, 120, 46)},
}

# Patterns by part role; ("head", "top") is the head's top row on its sides, ("head", "front") their front column.
PATTERNS = {
    'cap': [('head', 'up'), ('head', 'top')],
    'mask': [('head', 'north'), ('head', 'front')],
    'breast': [('body', 'north'), ('body', 'down'), ('neck', 'north'), ('head', 'chin')],
    'wings': [('wing', None)],
    'tail': [('tail', None)],
    'head': [('head', None), ('neck', None)],
}

# The natural palette (birds.json will hold it; here for the previews).
PALETTE = {
    'black': (70, 66, 70), 'charcoal': (96, 94, 96), 'slate_grey': (112, 120, 128), 'pale_grey': (182, 186, 188),
    'white': (238, 236, 230), 'cream': (234, 222, 186), 'buff': (214, 184, 132), 'tan': (176, 140, 96),
    'brown': (120, 86, 58), 'chestnut': (140, 64, 40), 'rust': (186, 94, 50), 'scarlet': (204, 40, 38),
    'orange': (232, 132, 40), 'yellow': (242, 202, 52), 'olive': (122, 126, 62), 'slate_blue': (78, 110, 172),
}
PREVIEWS = {
    'songbird': {'cardinal': ('scarlet', 'mask', 'black'), 'blue_jay': ('slate_blue', 'breast', 'white'),
                 'goldfinch': ('yellow', 'cap', 'black'), 'robin': ('brown', 'breast', 'rust')},
    'waterfowl': {'mallard_drake': ('pale_grey', 'head', 'olive'), 'mallard_hen': ('tan', 'wings', 'brown'),
                  'ruddy_duck': ('chestnut', 'cap', 'black')},
}
EYE = (18, 16, 20)


def role(name):
    for part in ('body', 'neck', 'head', 'wing', 'tail', 'beak', 'bill', 'leg', 'foot'):
        if name.startswith(part) or ('_' + part) in name:
            return 'beak' if part == 'bill' else part
    raise ValueError('no role for part ' + name)


def faces(element):
    """Each face's rectangle [x1, y1, x2, y2) on the texture, as Blockbench (and Minecraft) lay a box-UV cube out."""
    w, h, d = (round(b - a) for a, b in zip(element['from'], element['to']))
    u, v = element.get('uv_offset', [0, 0])
    return {'up': (u + d, v, u + d + w, v + d), 'down': (u + d + w, v, u + d + 2 * w, v + d),
            'east': (u, v + d, u + d, v + d + h), 'north': (u + d, v + d, u + d + w, v + d + h),
            'west': (u + d + w, v + d, u + 2 * d + w, v + d + h), 'south': (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h)}


def cells(rect, face, where=None):
    x1, y1, x2, y2 = rect
    for x in range(x1, x2):
        for y in range(y1, y2):
            if where in ('top', 'front') and face in ('north', 'south', 'up', 'down') and where == 'front':
                continue
            if where == 'top' and (face in ('up', 'down') or y != y1):
                continue
            if where == 'chin' and (face != 'north' or y != y2 - 1):
                continue
            # A side face's front column: the east face's right edge and the west face's left edge meet the front.
            if where == 'front' and not ((face == 'east' and x == x2 - 1) or (face == 'west' and x == x1)):
                continue
            yield x, y


def paint(shape):
    model = json.load(open(os.path.join(MODELS, f'bird_{shape}.bbmodel'), encoding='utf-8'))
    size = (model['resolution']['width'], model['resolution']['height'])
    parts = [(role(e['name']), faces(e)) for e in model['elements']]
    rng = random.Random(shape)
    base = Image.new('RGBA', size)
    for part, rects in parts:
        if part not in ('body', 'neck', 'head', 'wing', 'tail'):
            continue
        for face, rect in rects.items():
            x1, y1, x2, y2 = rect
            for x, y in cells(rect, face):
                g = (WING if part == 'wing' else TAIL if part == 'tail' else SHADE[face]) + rng.randint(-6, 6)
                if part == 'wing' and face in ('east', 'west') and y == y1:
                    g += 12  # the wing's top edge (its shoulder) catches the light
                if part == 'wing' and face in ('east', 'west') and x in (x1, x2 - 1):
                    g -= 18  # its tip and its join to the body
                g = max(0, min(255, g))
                base.putpixel((x, y), (g, g, g, 255))
    layers = {'base': base}
    for pattern, wanted in PATTERNS.items():
        image = Image.new('RGBA', size)
        for part, rects in parts:
            for want_part, how in wanted:
                if part != want_part:
                    continue
                for face, rect in rects.items():
                    if how in (None, 'top', 'front', 'chin') or how == face:
                        for x, y in cells(rect, face, how if how in ('top', 'front', 'chin') else None):
                            image.putpixel((x, y), base.getpixel((x, y)))
        layers['pattern_' + pattern] = image
    details = Image.new('RGBA', size)
    colours = SHAPES[shape]
    for part, rects in parts:
        if part == 'head':
            details.putpixel((rects['east'][2] - 1, rects['east'][1]), EYE + (255,))
            details.putpixel((rects['west'][0], rects['west'][1]), EYE + (255,))
        if part in ('beak', 'leg', 'foot'):
            colour = colours['beak' if part == 'beak' else 'legs']
            for face, rect in rects.items():
                for x, y in cells(rect, face):
                    shade = 0.82 if face in ('down', 'south') else 1.0
                    details.putpixel((x, y), tuple(int(c * shade) for c in colour) + (255,))
    layers['details'] = details
    return layers


def tint(image, colour):
    out = Image.new('RGBA', image.size)
    for x in range(image.width):
        for y in range(image.height):
            r, g, b, a = image.getpixel((x, y))
            if a:
                out.putpixel((x, y), (r * colour[0] // 255, g * colour[1] // 255, b * colour[2] // 255, a))
    return out


def main():
    for shape in SHAPES:
        layers = paint(shape)
        folder = os.path.join(OUT, shape)
        os.makedirs(folder, exist_ok=True)
        for name, image in layers.items():
            image.save(os.path.join(folder, name + '.png'))
        if len(sys.argv) > 1:
            for bird, (base, pattern, colour) in PREVIEWS[shape].items():
                image = tint(layers['base'], PALETTE[base])
                image.alpha_composite(tint(layers['pattern_' + pattern], PALETTE[colour]))
                image.alpha_composite(layers['details'])
                image.save(os.path.join(sys.argv[1], f'preview_{shape}_{bird}.png'))
        print(shape, 'layers written:', ', '.join(layers))


main()
