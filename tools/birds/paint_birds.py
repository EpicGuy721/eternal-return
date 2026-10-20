"""Paints the birds' texture layers, one set per shape, from each shape's model (tools/models/bird_<shape>.bbmodel):
the faces are found from the model's box-UV layout, and each part is painted by its role (its name).

Run from anywhere: python tools/birds/paint_birds.py [preview_dir]

Writes src/main/resources/assets/eternalreturn/textures/entity/bird/<shape>/:
- base.png: the bird's body, neck, head, wings and tail in light grays (shaded by face, with feather markings: wing
  bars and dark flight feathers, barred tails, and per shape a duck's barred flanks, an owl's speckles and streaked
  breast, a corvid's sheen), tinted with its base colour;
- pattern_<name>.png for cap, mask, breast, wings, tail and head: the same grays where the pattern lies, transparent
  elsewhere, tinted with its pattern colour and drawn over the base (a bird wears up to two patterns, each in its
  own colour);
- details.png: what is never tinted: the eyes, the beak or bill, legs and feet.
Palette and presets come from src/main/resources/eternalreturn/birds.json. With a preview_dir, also writes
preview_<model>_<preset>.png there: each preset with its colours applied, for checking in Blockbench.
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

# Per shape: the untinted colours of its beak (or bill), legs and feet; where its eyes are (the head's sides, or its
# face for owls); and its wings' outline, row by row from the top, front to back ("#" feather, "." see-through).
SHAPES = {
    'songbird': {'beak': (66, 58, 52), 'legs': (92, 72, 64), 'eyes': 'side', 'wing': ['###', '##.']},
    'waterfowl': {'beak': (196, 170, 70), 'legs': (214, 120, 46), 'eyes': 'side', 'wing': ['####', '###.']},
    'owl': {'beak': (70, 64, 58), 'legs': (120, 112, 100), 'eyes': 'front', 'wing': ['###', '##.', '###']},
    'wader': {'beak': (190, 160, 70), 'legs': (100, 94, 78), 'eyes': 'side', 'wing': ['####', '##..']},
    'corvid': {'beak': (54, 50, 50), 'legs': (64, 60, 60), 'eyes': 'side', 'wing': ['###.', '####']},
    'woodpecker': {'beak': (62, 58, 56), 'legs': (84, 80, 76), 'eyes': 'side', 'wing': ['#####', '###..']},
    'seagull': {'beak': (226, 188, 58), 'legs': (214, 168, 150), 'eyes': 'side', 'wing': ['#####.', '######', '###...']},
}

# Patterns by part role; ("head", "top") is the head's top row on its sides, ("head", "front") their front column.
PATTERNS = {
    'cap': [('head', 'up'), ('head', 'top'), ('tuft', None)],
    'mask': [('head', 'north'), ('head', 'front')],
    'breast': [('body', 'north'), ('body', 'down'), ('neck', 'north'), ('head', 'chin')],
    'wings': [('wing', None)],
    'tail': [('tail', None)],
    'head': [('head', None), ('neck', None), ('tuft', None)],
}

# The palette, shapes and presets: the bird table, which the game reads too.
TABLE = json.load(open(os.path.join(ROOT, 'src', 'main', 'resources', 'eternalreturn', 'birds.json'), encoding='utf-8'))
PALETTE = {c['id']: tuple(int(c['rgb'][k:k + 2], 16) for k in (1, 3, 5)) for c in TABLE['palette']}
SHAPE_TABLE = {shape['id']: shape for shape in TABLE['shapes']}
EYE = (18, 16, 20)


def role(name):
    for part in ('body', 'neck', 'head', 'wing', 'tail', 'tuft', 'beak', 'bill', 'leg', 'foot'):
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


def feathered(outline, rect, face, x, y):
    """Whether a wing pixel is feather, by the shape's outline (front to back; the east face's front is its right edge)."""
    x1, y1, x2, y2 = rect
    if face not in ('east', 'west'):
        return True
    row = outline[min(y - y1, len(outline) - 1)]
    along = (x2 - 1 - x) if face == 'east' else (x - x1)
    return along < len(row) and row[along] == '#'


def plumage(part, face, rect, x, y):
    """The texture every feathered surface has: shading from the top of a side to its bottom, and rows of feathers
    (each row offset from the one above, a darker pixel at each feather's edge)."""
    x1, y1, x2, y2 = rect
    d = 0
    if face in ('east', 'west', 'north', 'south') and part in ('body', 'head', 'neck') and y2 - y1 > 1:
        d += round(10 - 22 * (y - y1) / (y2 - y1 - 1))  # lighter at the top, darker underneath
    if part in ('body', 'neck', 'head', 'tuft'):
        # The head's feathers are finer, so its rows are fainter.
        edge, light = (-14, 6) if part in ('body', 'neck') else (-8, 4)
        if (x - x1 + (y - y1) % 2 * 2) % 4 == 0:
            d += edge  # a feather's edge
        elif (x - x1 + (y - y1) % 2 * 2) % 4 == 1:
            d += light  # and the light catching the next feather
    return d


def markings(shape, part, face, rect, x, y, rng):
    """How much lighter (or darker) feather markings make this pixel of the gray base."""
    x1, y1, x2, y2 = rect
    side = face in ('east', 'west')
    if part == 'wing' and side:
        rows = y2 - y1
        along = (x2 - 1 - x) if face == 'east' else (x - x1)
        d = 0
        if y == y1:
            d += 14  # the coverts: a pale bar along the top
        elif y == y2 - 1 and rows > 1:
            d -= 16  # the flight feathers below, darker
            d += 6 if along % 2 == 0 else 0  # and their pale edges, feather by feather
        if along >= (x2 - x1) - 1:
            d -= 14  # the long feathers at the tip
        if shape == 'owl' and rng.random() < 0.25:
            d -= 26
        if shape == 'corvid' and y == y1:
            d += 10  # a glossy sheen
        return d
    if part == 'tail' and face in ('up', 'down'):
        return -12 if (y - y1) % 2 else 4  # barred
    if part == 'body':
        if shape == 'waterfowl' and side:
            return -9 if (y - y1) % 2 else 3  # finely barred flanks
        if shape == 'owl':
            if face == 'north':
                return -18 if (x - x1) % 2 == 0 and (y - y1) % 2 == 1 else 0  # a streaked breast
            if face in ('east', 'west', 'south', 'up') and rng.random() < 0.22:
                return -26  # speckles
        if shape == 'corvid' and face == 'up':
            return 12 if (x - x1) % 2 == 0 else 0  # a glossy sheen on the back
        if shape == 'songbird' and face == 'up':
            return -12 if rng.random() < 0.3 else 0  # a streaked back
        if shape == 'wader' and face == 'up':
            return 8 if (y - y1) % 2 == 0 else -6  # plumes along the back
    if part == 'neck' and shape == 'wader' and face == 'north':
        return 12  # a pale throat
    if part == 'head' and shape == 'owl' and face == 'north':
        return 8  # the facial disc catches the light
    return 0


def paint(shape):
    model = json.load(open(os.path.join(MODELS, f'bird_{shape}.bbmodel'), encoding='utf-8'))
    size = (model['resolution']['width'], model['resolution']['height'])
    parts = [(role(e['name']), faces(e)) for e in model['elements']]
    rng = random.Random(shape)
    base = Image.new('RGBA', size)
    for part, rects in parts:
        if part not in ('body', 'neck', 'head', 'wing', 'tail', 'tuft'):
            continue
        for face, rect in rects.items():
            x1, y1, x2, y2 = rect
            for x, y in cells(rect, face):
                if part == 'wing' and not feathered(SHAPES[shape]['wing'], rect, face, x, y):
                    continue
                g = (WING if part == 'wing' else TAIL if part == 'tail' else SHADE[face]) + rng.randint(-10, 10)
                g += plumage(part, face, rect, x, y) + markings(shape, part, face, rect, x, y, rng)
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
        if part == 'head' and colours['eyes'] == 'side':
            details.putpixel((rects['east'][2] - 1, rects['east'][1]), EYE + (255,))
            details.putpixel((rects['west'][0], rects['west'][1]), EYE + (255,))
        if part == 'head' and colours['eyes'] == 'front':
            # Owls: the eyes on the face, at its top corners, either side of the beak.
            details.putpixel((rects['north'][0], rects['north'][1]), EYE + (255,))
            details.putpixel((rects['north'][2] - 1, rects['north'][1]), EYE + (255,))
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
            for preset in TABLE['presets']:
                kind = SHAPE_TABLE[preset['shape']]
                if kind['model'] != shape:
                    continue
                image = tint(layers['base'], PALETTE[preset['base']])
                for layer in preset['patterns']:
                    image.alpha_composite(tint(layers['pattern_' + layer['pattern']], PALETTE[layer['colour']]))
                image.alpha_composite(layers['details'])
                if not kind['tufts']:
                    # In game the renderer hides the tufts; Blockbench can't, so the preview leaves them blank.
                    model = json.load(open(os.path.join(MODELS, f'bird_{shape}.bbmodel'), encoding='utf-8'))
                    for element in model['elements']:
                        if role(element['name']) == 'tuft':
                            for face, rect in faces(element).items():
                                for x, y in cells(rect, face):
                                    image.putpixel((x, y), (0, 0, 0, 0))
                image.save(os.path.join(sys.argv[1], f'preview_{shape}_{preset["id"]}.png'))
        print(shape, 'layers written:', ', '.join(layers))


main()
