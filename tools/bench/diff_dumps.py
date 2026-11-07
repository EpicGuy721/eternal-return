"""Compares two block dumps of the Eternal Return fingerprint's decorated chunks, block by block.

Usage: python tools/bench/diff_dumps.py a.gz b.gz

The dumps come from ./gradlew runGametestBenchmark -PgametestBatches=er_fingerprint -PdumpBlocks=<file>.
Prints how many blocks differ, and which blocks became which (most common first), so a difference
that Moderner Beta's own unrepeatable decoration makes can be told from one a change made.
"""
import collections
import gzip
import struct
import sys


def read(path):
    with gzip.open(path, "rb") as f:
        data = f.read()
    pos = 0

    def take(fmt):
        nonlocal pos
        v = struct.unpack_from(fmt, data, pos)
        pos += struct.calcsize(fmt)
        return v

    def utf():
        nonlocal pos
        (n,) = take(">H")
        s = data[pos:pos + n].decode("utf-8", "replace")
        pos += n
        return s

    (bottom,) = take(">i")
    (count,) = take(">i")
    names = [utf() for _ in range(count)]
    (chunks,) = take(">i")
    out = {}
    for _ in range(chunks):
        key = utf()
        (n,) = take(">i")
        ids = struct.unpack_from(f">{n}h", data, pos)
        pos += 2 * n
        out[key] = [names[i] for i in ids]
    return bottom, out


def main():
    bottom, a = read(sys.argv[1])
    _, b = read(sys.argv[2])
    changes = collections.Counter()
    ys = collections.Counter()
    total = 0
    for key, blocks in a.items():
        other = b.get(key)
        diff = sum(1 for x, y in zip(blocks, other) if x != y)
        total += diff
        for i, (x, y) in enumerate(zip(blocks, other)):
            if x != y:
                changes[(x.split("[")[0], y.split("[")[0])] += 1
                ys[(bottom + i // 256) // 16 * 16] += 1
        print(f"{key}: {diff} blocks differ")
    print(f"total {total} blocks differ")
    for (x, y), n in changes.most_common(40):
        print(f"  {n:7d}  {x} -> {y}")
    print("by y (16-block bands):", sorted(ys.items()))


if __name__ == "__main__":
    main()
