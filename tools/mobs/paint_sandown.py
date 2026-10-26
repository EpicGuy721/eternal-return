"""Paints the Sandown's 128x128 box-UV texture (old-Minecraft style: small ramps, value shading, dithering).

The face layout follows the cubes' box-UV offsets in tools/models/sandown.bbmodel. Writes
src/main/resources/assets/eternalreturn/textures/entity/sandown.png (or the path given).
"""
import os
import random
import sys

from PIL import Image

W = H = 128
img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
px = img.load()
rng = random.Random(721)


def ramp(*cs):
    return [tuple(int(c.lstrip('#')[i:i + 2], 16) for i in (0, 2, 4)) + (255,) for c in cs]


GREEN = ramp('#1d2f27', '#2b4535', '#3c5d47', '#53785c', '#6f9576', '#93b597')
RED = ramp('#3a1312', '#5a1e1b', '#7e2a23', '#a1392d', '#bf5440', '#d77a5c')
BLUE = ramp('#1b252d', '#2c3c48', '#405664', '#5a7482', '#7b97a3', '#a3bcc3')
CREAM = ramp('#5b5646', '#7e7863', '#a29b83', '#c2bba1', '#dbd5bc', '#eeead5')
OCHRE = ramp('#3a2e14', '#584518', '#7a621f', '#9e8029', '#bf9e38', '#d8bb58')
IRON = ramp('#111113', '#1c1d20', '#2a2b2f', '#3b3d42', '#53565c', '#6f7278')

# Light by face: tops lit, undersides dark, sides a little darker than the front.
LIGHT = {'up': 0.8, 'north': 0.0, 'south': -0.2, 'east': -0.4, 'west': -0.4, 'down': -1.5}
BAYER = [[0, 2], [3, 1]]


def faces(off, size):
    u, v = off
    w, h, d = size
    return {
        'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d),
        'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
        'west': (u + d + w, v + d, d, h), 'south': (u + 2 * d + w, v + d, w, h),
    }


def pick(colours, t, x, y):
    i = int(t + (BAYER[y % 2][x % 2] + 0.5) / 4.0)
    return colours[max(0, min(len(colours) - 1, i))]


def paint(rect, face, colours, base, pattern=None, grad=0.9, noise=0.4):
    """Fills a face from a ramp: base value, lit by the face's direction, lighter at the top, noise, dithered."""
    x0, y0, fw, fh = rect
    for fy in range(fh):
        for fx in range(fw):
            t = base + LIGHT[face]
            if face not in ('up', 'down') and fh > 1:
                t += grad * (0.5 - (fy + 0.5) / fh)
            if pattern:
                t += pattern(fx, fy, fw, fh)
            t += (rng.random() - 0.5) * noise
            px[x0 + fx, y0 + fy] = pick(colours, t, x0 + fx, y0 + fy)


def dot(rect, fx, fy, colour):
    x0, y0, fw, fh = rect
    if 0 <= fx < fw and 0 <= fy < fh:
        px[x0 + fx, y0 + fy] = colour


def box(name, off, size, colours, base, pattern=None, **kw):
    f = faces(off, size)
    for face, rect in f.items():
        paint(rect, face, colours, base, pattern, **kw)
    return f


def harlequin(fx, fy, fw, fh):
    """Diamonds in two greens, a clown's costume."""
    dx = abs((fx % 6) - 2.5)
    dy = abs((fy % 6) - 2.5)
    return 1.15 if dx + dy <= 2.5 else -1.15


def pleats(fx, fy, fw, fh):
    return 0.6 if fx % 2 == 0 else -0.6


# Costume: chest, belly, waist.
chest = box('chest', (0, 0), (18, 8, 8), GREEN, 3.0, harlequin, grad=0.5, noise=0.2)
# The black triangle on the chest (from the mock-up).
for fy, (a, b) in zip(range(2, 6), [(8, 9), (7, 10), (6, 11), (5, 12)]):
    for fx in range(a, b + 1):
        dot(chest['north'], fx, fy, IRON[0] if fx not in (a, b) or fy == 2 else IRON[1])
belly = box('belly', (48, 16), (13, 5, 7), GREEN, 2.7, harlequin, grad=0.5, noise=0.2)
# A red plaque below it, outlined in black.
for fx in range(4, 9):
    for fy in range(1, 4):
        edge = fx in (4, 8) or fy in (1, 3)
        dot(belly['north'], fx, fy, IRON[0] if edge else pick(RED, 3.2 + (rng.random() - 0.5) * 0.4, fx, fy))
box('waist', (0, 28), (10, 4, 6), GREEN, 2.0, harlequin, grad=0.5, noise=0.2)

# Belt with a mustard buckle; mechanical hips below it.
belt = box('belt', (88, 16), (11, 3, 7), IRON, 2.2, grad=0.3)
for fx in range(4, 7):
    for fy in range(3):
        dot(belt['north'], fx, fy, IRON[0] if (fx, fy) == (5, 1) else pick(OCHRE, 4.0 - fy * 0.7, fx, fy))
box('pelvis', (32, 28), (10, 3, 6), BLUE, 2.4)

# The ruff: pleated cream.
box('collar', (0, 16), (14, 2, 10), CREAM, 3.4, pleats, grad=0.5)

# Head: a white clown face.
head = box('head', (52, 0), (10, 8, 8), CREAM, 4.0, grad=1.0, noise=0.3)
face = head['north']
for fx in (2, 7):
    dot(face, fx, 1, RED[3])          # red marks above and below each eye
    dot(face, fx, 4, RED[3])
    dot(face, fx, 2, IRON[0])         # closed, smiling eyes: ^ ^
    dot(face, fx - 1, 3, IRON[0])
    dot(face, fx + 1, 3, IRON[0])
dot(face, 1, 5, IRON[0])              # a wide grin
dot(face, 8, 5, IRON[0])
for fx in range(2, 8):
    dot(face, fx, 6, RED[1] if fx in (4, 5) else IRON[0])

# Hat: a mustard cone on a black brim, red pom-pom, black antennae.
box('hat_brim', (88, 0), (10, 1, 9), IRON, 2.4, grad=0)
box('hat_2', (64, 28), (8, 2, 7), OCHRE, 3.0)
box('hat_3', (94, 28), (6, 2, 5), OCHRE, 3.3)
box('hat_4', (88, 38), (4, 2, 3), OCHRE, 3.6)
box('hat_stem', (114, 38), (2, 2, 2), IRON, 2.6)
box('hat_ball', (102, 38), (3, 3, 3), RED, 3.4)
antenna = box('antenna', (122, 38), (1, 8, 1), IRON, 3.0, grad=1.2)
for face_name in ('north', 'south', 'east', 'west'):
    dot(antenna[face_name], 0, 0, IRON[5])
dot(antenna['up'], 0, 0, IRON[5])

# Sleeves: costume, two red patches on the outside, a dark cuff with a red trim.
def sleeve_pattern(fx, fy, fw, fh):
    if fy >= 18:
        return -1.2
    return harlequin(fx, fy, fw, fh)


sleeve = box('sleeve', (0, 38), (5, 22, 5), GREEN, 3.0, sleeve_pattern, grad=0.7, noise=0.2)
for face_name in ('north', 'south', 'east', 'west'):
    for fx in range(5):
        dot(sleeve[face_name], fx, 17, pick(RED, 2.8 + (rng.random() - 0.5) * 0.5, fx, 17))
for top in (3, 10):
    for fx in range(1, 4):
        for fy in range(top, top + 4):
            edge = fx in (1, 3) or fy == top + 3
            dot(sleeve['east'], fx, fy, pick(RED, (2.0 if edge else 3.2) + (rng.random() - 0.5) * 0.4, fx, fy))
paint(faces((0, 38), (5, 22, 5))['down'], 'down', IRON, 0.8, noise=0.2)   # inside the sleeve

# Hands: steel, with dark gaps between three stubby fingers.
palm = box('palm', (56, 38), (4, 4, 3), BLUE, 3.0)
for face_name in ('north', 'south', 'east', 'west'):
    x0, y0, fw, fh = palm[face_name]
    for fx in range(1, fw, 2):
        for fy in (2, 3):
            dot(palm[face_name], fx, fy, BLUE[0])

# Legs: steel struts: a lit rail and a darker rail at the edges, a recessed gap with a piston in it, crossbars, a
# bolted knee plate and an ankle band.
def leg_pattern(fx, fy, fw, fh):
    knee = 10 <= fy <= 12
    if fy in (9, 13) or fy <= 1:
        return -1.4
    if knee or fy >= 20:
        return 0.5 if fy != 20 else -1.2
    if fx == 0:
        return 0.7
    if fx == fw - 1:
        return -0.3
    if fy in (5, 16):
        return 0.2
    return -1.6 if fx == 1 else 0.2


leg = box('leg', (20, 38), (4, 23, 4), BLUE, 3.0, leg_pattern, grad=0.8, noise=0.25)
for face_name in ('north', 'south', 'east', 'west'):
    dot(leg[face_name], 1, 11, IRON[1])   # the knee's bolt
    dot(leg[face_name], 2, 11, BLUE[5])

# Jester shoes: red, black soles, mustard bell tip.
def sole(fx, fy, fw, fh):
    return -2.4 if fy == fh - 1 else 0.0


box('shoe', (36, 38), (4, 4, 6), RED, 3.0, sole)
box('toe', (70, 38), (3, 2, 3), RED, 3.2, sole)
box('toe_tip', (82, 38), (1, 3, 2), OCHRE, 3.6)
paint(faces((36, 38), (4, 4, 6))['down'], 'down', IRON, 2.5, noise=0.3)
paint(faces((70, 38), (3, 2, 3))['down'], 'down', IRON, 2.5, noise=0.3)

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
img.save(sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'eternalreturn', 'textures', 'entity', 'sandown.png'))
