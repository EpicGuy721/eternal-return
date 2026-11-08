# World generation speed

How fast Eternal Return worlds generate, where the time goes, what was done about it (2026-11-07), and which mods help.

## The benchmark

`./gradlew runGametestBenchmark` (and `runGametestBenchmarkVanilla`, `runGametestBenchmarkModernerBeta` for comparison) starts a headless server on seed 173164 with structures on and asks for two squares of 24 x 24 chunks all at once, as a player exploring or a pre-generator does: one on a coast with islands (centre chunk -13, -19) and one inland (centre chunk -66, -66), after a small warm-up square. It logs `[bench]` lines with chunks per second, generated all the way to full chunks (`WorldgenBenchmarkTests`).

- `-PbenchSize=n`: square side in chunks (default 24).
- `-PbenchProfile`: also times every decoration feature and structure (`WorldgenProfile`) and records a Java Flight Recording to `build/gametest/bench_eternal_return/bench.jfr`; `python tools/bench/jfr_steps.py <file>` sums it per worldgen step and per method, and shows which part ran on the one-at-a-time worldgen queue.
- `-PgametestBatches=worldgen_bench,er_fingerprint` adds the Eternal Return fingerprint check (below); `-PrecordFingerprints` records it, `-PdumpBlocks=<file>` writes every block of its decorated chunks and `python tools/bench/diff_dumps.py a b` compares two such dumps block by block.
- `-PnoBiomeCache` turns this mod's speed-ups off, to measure them or record a baseline.
- `-Pc2me` loads C2ME into any run (never part of the release).

Numbers are from the owner's PC (Intel i5-13500H, 16 threads, 16 GB), chunks per second, coast / inland:

| World | Before | After |
|---|---|---|
| Vanilla | 37.9 / 40.0 | (unchanged) |
| Moderner Beta Release 1.6.4 | 23.7 / 24.0 | (unchanged) |
| Eternal Return | 5.7 / 3.5 | 15.7 / 12.5 (2.8x / 3.6x) |
| Eternal Return with C2ME | 13.2 / 10.9 (C2ME alone) | 43.2 / 37.9 (7.6x / 10.8x); 58 / 49 with the larger caches (10x / 14x) |
| Vanilla with C2ME | | 113.4 / 126.7 |

## Where the time went

Minecraft 1.21.1 runs most chunk generation steps (structure starts, surface, carving, decoration) one chunk at a time on a single queue; only the terrain noise and biome steps spread over the other cores. So a slow step on that queue holds everything up, however many cores the PC has: 15 of the 16 threads were idle most of the time.

The profile of the old code: 83% of all generation time was the surface step, and almost all of that was Moderner Beta working out the biome of each block. Eternal Return's biome stone rules ask "which biome is this block in?" for every block from the topsoil down to bedrock (about 50,000 blocks a chunk), and Moderner Beta answers by running its biome injection rules (cave biomes, oceans, beaches, with column heights) for the 4 x 4 x 4 cell the block falls in. It did that again for every block in the cell, about 20 times over, and each time also re-read ten column heights. Decoration was cheap (about 9 ms a chunk for every feature together: trees, ores, boulders, the climate pass, dripstone, mushrooms), and so were carving (the cave engine) and structures.

## What changed

- **Biome cell memory** (`ModernerBetaBiomeCacheMixin`, `QuartBiomeCache`, `ColumnHeightCache`): Moderner Beta's answer for a cell, and a column's heights, are worked out once and remembered (per worker thread, a few thousand recent cells). The answer only depends on the cell, so it is the same answer. Eternal Return worlds only; other worlds never touch it.
- **The same decoration on every start** (`FeatureOrder`): each decoration feature takes its random numbers from its place in the list of features the game builds when the world loads. Moderner Beta hands over its biomes in an order that changes each time the game starts, so that list, and with it where every ore, tree and plant lands, came out different on each start: the same seed never made the same world twice, and decoration couldn't be checked against a recording. Eternal Return worlds now list the biomes by name, so the order is fixed. This is not a speed-up, but it is what makes the decorated check below possible. Other worlds are untouched (their decoration still doesn't repeat in plain Moderner Beta worlds).

- **Larger Moderner Beta caches** (`ModernerBetaChunkCacheMixin`, `ModernerBetaAuxChunkCacheMixin`): Moderner Beta keeps its per-chunk biome and height maps for the last 512 chunks. A render distance of 12 or more, a pre-generator or C2ME's threads work on more chunks than that at once, so the maps were thrown away and worked out again; caches of that size keep 2048 now (a few megabytes each). With C2ME: 44 / 39 to 58 / 49 chunks a second. Every Moderner Beta world (the caches only remember answers; the control fingerprints match).
- **The column's surface height, once** (`ModernerBetaSurfaceHeightMixin`): Moderner Beta's surface rules looked up the column's surface height (a locked cache lookup) for every block they tested; the last column's answer is kept. Every Moderner Beta world, same answers.
- **C2ME's thread count**: 10 (its default here), 12, 14 and 15 worker threads all came out between 45 and 59 chunks a second with no pattern (run-to-run noise); the i5-13500H's eight efficiency cores add little. Left at C2ME's default, which also leaves room for the game itself. `-Pc2me -Pc2meThreads=n` measures it.
- What's left (with C2ME): Moderner Beta working out each chunk's biome and height maps once (its design), and its per-block biome blending (about 10 to 15%, which would mean rewriting Moderner Beta's own maths line for line).

## Random respawns

A death without a bed (or a player's first join) puts the player at a random spot within 5,000 blocks (RandomSpawns), almost always somewhere no one has been, so the respawn waited 30 to 45 seconds while the spot was found and the land around it generated. `ReadySpawns` now picks the next spot ahead of time, while players are in the world, and generates the 11 x 11 chunks around it in the background (held as full chunks that don't tick, so nothing there moves or grows). The next respawn goes there at once and the rest of the view fills in around the player; then the next spot is prepared. Spots are picked exactly as before (same radius, dry land), so they are just as random. Checked by the game test `nextSpawnIsReady` (batch `ready_spawns`). Pre-generating the whole 5,000-block disc with Chunky (about 300,000 chunks) would make every respawn instant, for disk space.

## Proof that nothing looks different

- Eternal Return fingerprint (`src/gametest/resources/fingerprints/eternal_return.json` and `eternal_return_features.json`, batch `er_fingerprint` in `runGametestBenchmark`): 27 chunks hashed after carving and 27 other chunks fully decorated, one by one in a fixed order. Recorded with the speed-ups off (`-PnoBiomeCache`). A second run with them off matched 27 of 27 decorated chunks (decoration now repeats), and runs with them on matched 27 of 27 carved and 27 of 27 decorated chunks, with and without C2ME. (The carved chunks also matched a recording made at the original code before any change.)
- The control worlds (`runGametestVanilla`, `runGametestModernerBeta164`, `runGametestModernerBetaBeta`) still match their fingerprints. (`runGametestEternalReturnOldCaves` matched only 6 of 27 carved chunks before any of this work, the same with the speed-ups off: an earlier terrain change; its baseline was left for the owner.)

## Mods

- **C2ME** (Concurrent Chunk Management Engine, `c2me-fabric` 0.4.0-alpha.0.30+1.21.1): runs the one-at-a-time steps on many threads, plus faster chunk saving and loading. With this mod's changes it triples Eternal Return generation again (43 / 38 chunks a second). Eternal Return worlds came out identical with it (both fingerprints). In the vanilla world 2 of the 27 decorated fingerprint chunks differed with C2ME: it has a module that changes vanilla's own worldgen ("fixes worldgen vanilla bugs"), so vanilla worlds made with C2ME are not exactly vanilla's. It is an alpha and has broken determinism before; a world started with it should keep it. In the owner's pack since 2026-11-07 (the owner's choice; `runClientCompat` loads it, and the client starts cleanly with Sodium, Nostalgic Tweaks, ColorLight, LambDynamicLights and Moderner Beta).
- **Noisium** only speeds up vanilla's terrain noise code, which Moderner Beta doesn't use, and that step already runs on all cores: no gain here.
- **Chunky** (pre-generator) asks the server for chunks the normal way, so it gets both this mod's speed-up and C2ME's.

## Distant Horizons and Voxy

- **Distant Horizons** "Internal Server" (full generation) mode asks the server to generate real chunks through the normal chunk system, so it is exactly what the benchmark measures and gets every speed-up here (and C2ME's). Its lighter modes (surface, features) run the generator's steps on its own threads without saving chunks and leave structures out or broken. Nothing in this mod's generator depends on running on the server's queue: the biome cell memory is per thread, the coast distance fields and sinkhole plans are locked, so those modes are safe to run in parallel. Not tested in game here.
- The slow coastal height lookups (`generator.getHeight` near coasts, about 0.36 s the first time a 256-block region's coast distance is worked out) are only hit by landmark placement and spawn finding, a few times per region, and the region fields are remembered: they didn't show in the profile. Distant Horizons doesn't place structures in its lighter modes.
- **Voxy** builds its far terrain only from chunks that already exist (as you explore, or `/voxy import world` from a save); it doesn't generate any. Pair it with Chunky to pre-generate. Its Modrinth page lists 1.20.4 and 1.21.6 and later, not 1.21.1.
