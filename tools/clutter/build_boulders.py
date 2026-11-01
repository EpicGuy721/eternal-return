"""Writes the starter boulder templates, data/eternalreturn/structure/boulder/*.nbt.

Each boulder is a lump of plain stone: one to three overlapping ellipsoids with a ragged surface, from a fixed
seed, so the shapes are the same on every run. The boulder feature (eternalreturn:boulder) places any template
in that folder, turned and mirrored at random, and swaps the stone for the biome's stone, so hand-built
templates (saved with a structure block into the same folder, stone only, no air) work alongside these.

Run: python tools/clutter/build_boulders.py
"""
import gzip
import io
import math
import os
import random
import struct

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, 'src/main/resources/data/eternalreturn/structure/boulder')
DATA_VERSION = 3955  # Minecraft 1.21.1

# name -> (seed, ellipsoids as (centre x, y, z, radius x, y, z)); y 0 is the layer sunk into the ground.
BOULDERS = {
    'small': (1, [(1.5, 1.0, 1.5, 1.6, 1.4, 1.5)]),
    'round': (2, [(2.5, 1.6, 2.5, 2.4, 2.2, 2.3)]),
    'flat': (3, [(3.0, 0.9, 2.2, 3.2, 1.5, 2.3)]),
    'standing': (4, [(1.6, 2.6, 1.6, 1.6, 3.0, 1.5)]),
    'pair': (5, [(2.3, 1.5, 2.3, 2.3, 2.1, 2.2), (5.0, 0.8, 3.2, 1.5, 1.4, 1.4)]),
}


def shape(seed, ellipsoids):
    rng = random.Random(seed)
    # A ragged surface: each ellipsoid's radius wobbles by direction, the same for every block.
    wobble = [[rng.uniform(-0.25, 0.25) for _ in range(3)] for _ in ellipsoids]
    blocks = set()
    for index, (cx, cy, cz, rx, ry, rz) in enumerate(ellipsoids):
        a, b, c = wobble[index]
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            for y in range(0, int(cy + ry) + 2):
                for z in range(int(cz - rz) - 1, int(cz + rz) + 2):
                    dx, dy, dz = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry, (z + 0.5 - cz) / rz
                    angle = math.atan2(dz, dx)
                    limit = 1 + a * math.sin(angle * 2) + b * math.cos(angle * 3) + c * dy
                    if dx * dx + dy * dy + dz * dz <= limit:
                        blocks.add((x, y, z))
    # Drop single blocks hanging by an edge or corner only: every block touches another by a face.
    blocks = {p for p in blocks if any(n in blocks for n in neighbours(p)) or len(blocks) == 1}
    mx = min(p[0] for p in blocks)
    mz = min(p[2] for p in blocks)
    return sorted((x - mx, y, z - mz) for x, y, z in blocks)


def neighbours(p):
    x, y, z = p
    return [(x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)]


# Minimal NBT writer: just the tags a structure file uses.
def tag_string(s):
    data = s.encode('utf-8')
    return struct.pack('>H', len(data)) + data


def named(tag_id, name, payload):
    return bytes([tag_id]) + tag_string(name) + payload


def int_list(values):
    return bytes([3]) + struct.pack('>i', len(values)) + b''.join(struct.pack('>i', v) for v in values)


def compound(entries):
    return b''.join(entries) + b'\x00'


def compound_list(items):
    return bytes([10]) + struct.pack('>i', len(items)) + b''.join(items)


def structure(blocks):
    size = [max(p[i] for p in blocks) + 1 for i in range(3)]
    palette = compound_list([compound([named(8, 'Name', tag_string('minecraft:stone'))])])
    entries = compound_list([compound([named(9, 'pos', int_list(list(p))), named(3, 'state', struct.pack('>i', 0))])
                             for p in blocks])
    root = compound([
        named(3, 'DataVersion', struct.pack('>i', DATA_VERSION)),
        named(9, 'size', int_list(size)),
        named(9, 'palette', palette),
        named(9, 'blocks', entries),
        named(9, 'entities', bytes([0]) + struct.pack('>i', 0)),
    ])
    return named(10, '', root), size


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, (seed, ellipsoids) in BOULDERS.items():
        blocks = shape(seed, ellipsoids)
        data, size = structure(blocks)
        buffer = io.BytesIO()
        # mtime 0: the same bytes on every run.
        with gzip.GzipFile(fileobj=buffer, mode='wb', mtime=0) as f:
            f.write(data)
        with open(os.path.join(OUT, name + '.nbt'), 'wb') as f:
            f.write(buffer.getvalue())
        print(f'{name}: {len(blocks)} blocks, {size[0]} x {size[1]} x {size[2]}')


if __name__ == '__main__':
    main()
