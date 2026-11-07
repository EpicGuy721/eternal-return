"""Sums a Java Flight Recording of the worldgen benchmark per worldgen step and per method.

Usage: python tools/bench/jfr_steps.py build/gametest/bench_eternal_return/bench.jfr [top]

Needs the JDK's `jfr` tool on the PATH. Counts execution samples of the server's worker and
server threads: per worldgen step (from the ChunkGenerating method on the stack), whether the
sample ran on the one-at-a-time "worldgen" queue (ChunkLoader on the stack) or in parallel, and
the methods of this mod and Moderner Beta that the time is spent under (inclusive), plus the
hottest methods themselves (self time).
"""
import collections
import re
import subprocess
import sys


def samples(path):
    out = subprocess.run(["jfr", "print", "--events", "jdk.ExecutionSample", "--stack-depth", "200", path],
                         capture_output=True, text=True, encoding="utf-8", errors="replace").stdout
    for block in out.split("jdk.ExecutionSample {")[1:]:
        thread = re.search(r'sampledThread = "([^"]+)"', block)
        frames = re.findall(r"^\s+([\w.$<>/]+)\(", block, re.M)
        if thread and frames:
            yield thread.group(1), frames


def step_of(frames):
    for f in frames:
        m = re.match(r"net\.minecraft\.world\.chunk\.ChunkGenerating\.(\w+)", f)
        if m:
            return m.group(1)
    for f in frames:
        if "LightingProvider" in f or "light." in f and "chunk.light" in f:
            return "light (lighting thread queue)"
        if "populateNoise" in f or "NoiseChunkGenerator" in f and "lambda" in f:
            return "noise (async)"
        if "populateBiomes" in f:
            return "biomes (async)"
    return "other: " + frames[min(len(frames) - 1, 0)]


def main():
    path = sys.argv[1]
    top = int(sys.argv[2]) if len(sys.argv) > 2 else 40
    steps = collections.Counter()
    serial = collections.Counter()
    inclusive = collections.Counter()
    selfc = collections.Counter()
    total = 0
    for thread, frames in samples(path):
        if not (thread.startswith("Worker-Main") or thread == "Server thread"):
            continue
        total += 1
        step = step_of(frames)
        queue = any("ChunkLoader.run" in f or "ServerChunkLoadingManager.method_60446" in f for f in frames)
        steps[step] += 1
        if queue:
            serial[step] += 1
        seen = set()
        for f in frames:
            if ("eternalreturn" in f or "modernerbeta" in f or "SurfaceBuilder" in f or "MaterialRules" in f
                    or "ChunkGenerator." in f or "StructureStart" in f or "Feature." in f) and f not in seen:
                seen.add(f)
                inclusive[f] += 1
        selfc[frames[0]] += 1
    print(f"{total} samples on worker and server threads")
    print("\nper step (samples, share, of which on the one-at-a-time worldgen queue):")
    for s, n in steps.most_common(30):
        print(f"  {n:7d} {100 * n / total:5.1f}%  queue {serial[s]:7d}  {s}")
    print(f"\ninclusive, top {top}:")
    for f, n in inclusive.most_common(top):
        print(f"  {n:7d} {100 * n / total:5.1f}%  {f}")
    print(f"\nself, top {top}:")
    for f, n in selfc.most_common(top):
        print(f"  {n:7d} {100 * n / total:5.1f}%  {f}")


if __name__ == "__main__":
    main()
