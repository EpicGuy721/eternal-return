"""Paints the brown, black and sun bears' 128x64 coats (old-Minecraft style: one hue per coat, value shading, per-pixel
scatter, soft drifting gradients).

The bears are drawn with vanilla's polar bear model, so the face layout follows its box-UV offsets
(PolarBearEntityModel). Its body boxes are turned 90 degrees, so their south faces are the back, north the belly,
up the chest and down the rump. Writes src/main/resources/assets/eternalreturn/textures/entity/bear/<bear>.png (or
into the folder given).
"""
import math
import os
import random
import sys

from PIL import Image

W, H = 128, 64


STEPS = 3


def ramp(*cs):
    """A coat's shades: the given stops with STEPS - 1 close in-between shades after each."""
    stops = [tuple(int(c.lstrip('#')[i:i + 2], 16) for i in (0, 2, 4)) for c in cs]
    out = []
    for a, b in zip(stops, stops[1:]):
        out += [tuple(round(a[k] + (b[k] - a[k]) * j / STEPS) for k in range(3)) + (255,) for j in range(STEPS)]
    return out + [stops[-1] + (255,)]


# Light by the direction a face points in the world: tops lit, undersides dark.
LIGHT = {'up': 0.7, 'north': 0.0, 'south': -0.2, 'east': -0.4, 'west': -0.4, 'down': -1.4}
BODY_LIGHT = {'south': 0.7, 'up': 0.0, 'down': -0.2, 'east': -0.4, 'west': -0.4, 'north': -1.4}


def faces(off, size):
    u, v = off
    w, h, d = size
    return {
        'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d),
        'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
        'west': (u + d + w, v + d, d, h), 'south': (u + 2 * d + w, v + d, w, h),
    }


HEAD = faces((0, 0), (7, 7, 7))
MOUTH = faces((0, 44), (5, 3, 3))
EAR = faces((26, 0), (2, 2, 1))
HIP = faces((0, 19), (14, 14, 11))
SHOULDER = faces((39, 0), (12, 12, 10))
HIND_LEG = faces((50, 22), (4, 10, 8))
FORE_LEG = faces((50, 40), (4, 10, 6))


class Painter:
    def __init__(self, seed):
        self.img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        self.px = self.img.load()
        self.rng = random.Random(seed)
        # A soft drift across the whole sheet: a few long, gentle waves.
        self.waves = [(self.rng.uniform(0.15, 0.35), self.rng.uniform(0.15, 0.35), self.rng.uniform(0, 6.3))
                      for _ in range(3)]

    def drift(self, x, y):
        return sum(math.sin(x * a + y * b + p) for a, b, p in self.waves) / 3 * 0.45

    def pick(self, colours, t):
        """Scatters between the two nearest shades, pixel by pixel (t counts the stops given to ramp)."""
        i = int(math.floor(t * STEPS + self.rng.random()))
        return colours[max(0, min(len(colours) - 1, i))]

    def paint(self, rect, light, colours, base, grad=0.8, scatter=1.3, shade=None):
        x0, y0, fw, fh = rect
        for fy in range(fh):
            for fx in range(fw):
                x, y = x0 + fx, y0 + fy
                t = base + light + self.drift(x, y) + (self.rng.random() - 0.5) * scatter
                if grad and fh > 1:
                    t += grad * (0.5 - (fy + 0.5) / fh)
                if shade:
                    t += shade(fx, fy, fw, fh)
                self.px[x, y] = self.pick(colours, t)

    def box(self, f, colours, base, lights=LIGHT, **kw):
        for face, rect in f.items():
            self.paint(rect, lights[face], colours, base, grad=kw.get('grad', 0.8) if face not in ('up', 'down') else 0,
                       scatter=kw.get('scatter', 1.3), shade=kw.get('shade'))

    def dot(self, rect, fx, fy, colour):
        x0, y0, fw, fh = rect
        if 0 <= fx < fw and 0 <= fy < fh:
            self.px[x0 + fx, y0 + fy] = colour

    def save(self, path):
        self.img.save(path)


def paw(fx, fy, fw, fh):
    """Legs darken toward the paws."""
    return -0.9 * max(0, fy - (fh - 4)) / 3


def bear(name, seed, coat, coat_base, muzzle, muzzle_base, eye_white, nose, claw, legs=0.0, ears=-0.6, mask=None,
         chest=None):
    p = Painter(seed)
    p.box(HEAD, coat, coat_base)
    p.box(EAR, coat, coat_base + ears, grad=0)
    p.box(HIP, coat, coat_base, BODY_LIGHT)
    p.box(SHOULDER, coat, coat_base + 0.1, BODY_LIGHT)
    p.box(HIND_LEG, coat, coat_base + legs, shade=paw)
    p.box(FORE_LEG, coat, coat_base + legs, shade=paw)

    # Muzzle: a lighter box, a black nose at its tip.
    p.box(MOUTH, muzzle, muzzle_base, grad=0.5, scatter=0.6)
    for fx in (1, 2, 3):
        p.dot(MOUTH['north'], fx, 0, nose)
        p.dot(MOUTH['up'], fx, 2, nose)
    p.dot(MOUTH['north'], 2, 1, nose)

    # A face mask (the sun bear's pale face) on the head's front, around and below the eyes.
    face = HEAD['north']
    if mask:
        for fy in range(2, 7):
            for fx in range(7):
                if fy >= 3 or 1 <= fx <= 5:
                    p.dot(face, fx, fy, p.pick(muzzle, muzzle_base + 0.3 - 0.4 * (fy - 2) / 4
                                               + (p.rng.random() - 0.5) * 0.6))
    # Eyes: one white and one black pixel each.
    for white, black in ((1, 2), (5, 4)):
        p.dot(face, white, 3, eye_white)
        p.dot(face, black, 3, nose)

    # Claws along the front of each paw.
    for leg in (HIND_LEG, FORE_LEG):
        for fx in (0, 2):
            p.dot(leg['north'], fx, leg['north'][3] - 1, claw)
        p.dot(leg['north'], 3, leg['north'][3] - 1, claw)

    # The sun bear's crescent: a U across the chest, scattered in one ochre ramp.
    if chest:
        colours, base = chest
        rect = SHOULDER['up']
        for fy in range(rect[3]):
            for fx in range(rect[2]):
                # Row 0 is at the back: the arms run down the chest's sides (beside the head) and meet low down.
                arm = 5.5 - 0.5 * fy
                if abs(abs(fx - 5.5) - arm) <= 1.2 and fy <= 9:
                    p.dot(rect, fx, fy, p.pick(colours, base - 0.15 * fy + (p.rng.random() - 0.5) * 0.7))
    return p


BEARS = {
    'brown_bear': dict(
        seed=11,
        coat=ramp('#2f1f13', '#41291a', '#533622', '#66442c', '#7a5437', '#8e6644', '#a17a55'),
        coat_base=3.4, legs=-0.6,
        muzzle=ramp('#6b4f37', '#7f6045', '#937254', '#a68564', '#b89876'), muzzle_base=2.4,
        eye_white=(222, 216, 204, 255), nose=(24, 18, 16, 255), claw=(52, 44, 38, 255)),
    'black_bear': dict(
        seed=12,
        coat=ramp('#0b0b0e', '#121217', '#1a1a20', '#23232a', '#2d2d35', '#383841'),
        coat_base=2.2, legs=-0.4,
        muzzle=ramp('#4d3a2a', '#614a36', '#765b43', '#8a6c52', '#9d7e62'), muzzle_base=2.3,
        eye_white=(200, 196, 190, 255), nose=(10, 10, 12, 255), claw=(96, 92, 88, 255)),
    'sun_bear': dict(
        seed=13,
        coat=ramp('#0c0b0c', '#131213', '#1b191a', '#242122', '#2e2a2b', '#393435'),
        coat_base=2.2, legs=-0.3,
        muzzle=ramp('#7a5c3c', '#8d6c49', '#a07d57', '#b28f67', '#c3a179'), muzzle_base=2.2, mask=True,
        eye_white=(222, 214, 198, 255), nose=(14, 12, 12, 255), claw=(104, 98, 92, 255),
        chest=(ramp('#7a4a1e', '#935a25', '#ab6c2f', '#c0803d', '#d0944f'), 3.0)),
}

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
out_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'eternalreturn',
                                                            'textures', 'entity', 'bear')
for bear_name, spec in BEARS.items():
    bear(bear_name, **spec).save(os.path.join(out_dir, bear_name + '.png'))
