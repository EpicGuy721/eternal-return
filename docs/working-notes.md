# Working notes

How the owner wants work on this repo done, and pitfalls that each cost a failed run. Read this before starting a session (it used to live only in one machine's assistant memory).

## Working rules

- Reports in plain language: the owner doesn't read Java, so say what changed in the game, not in the code. Use they/them for the owner.
- Commit when the tests pass, with the owner's git identity (as on earlier commits); push only when asked.
- Never accept the Minecraft EULA for the owner; never run `gradle --stop`.
- Feature batches: ask the few design questions up front (with a recommended option), then implement everything, write gametests and run the two flat runs (`./gradlew runGametest`, `./gradlew runGametestNoModernerBeta`).
- Never run the full suite (`runAllGametests`, 25 to 50 minutes) or a whole multi-batch run such as `runGametestEternalReturn` without `-PgametestBatches` (about an hour) unless the owner says so. Pick the few batches a change can affect: `./gradlew runGametestEternalReturn -PgametestBatches=a,b`. The two flat runs are the only whole runs allowed unasked. Give new tests their own batchId; read failures from `build/gametest/<run>/report.xml`.
- New gametest batches that need a generated (Eternal Return) world must be added to `GameTestWorld.GENERATED_WORLD_BATCHES`, or they run (and fail) in the flat runs; add them to the Eternal Return run's batch list in build.gradle.
- Check visual changes in game before calling them done. On the owner's PC this is a temporary client class `DebugShots` (registered in EternalReturnClient, gated on `run/eternalreturn_shots.flag`) that makes or loads a world, teleports in spectator and saves screenshots, run with `-PhiddenWindow` so it never disturbs the owner (the window opens off screen; set `pauseOnLostFocus = false` and close any open screen every tick). Wait for the chunks before each shot (client `isChunkLoaded` over the view plus `worldRenderer.isTerrainRenderComplete()`): a fixed wait gives sky-only pictures while the server lags generating them. Delete the class, its register line, the flag, the save and the screenshots afterwards. A machine without a display (cloud) can't do this: give the owner `/tp` coordinates and ask them to look.
- `./gradlew runClientCompat` runs the released jar with Sodium, Nostalgic Tweaks, ColorLight and LambDynamicLights (from the local `run` folder). Sodium can't run in the Yarn dev client (`runClient`).
- Changing vanilla gameplay is fine for this modpack; prefer the owner's designs over keeping vanilla mechanics, but mention real costs (compat, saved worlds, performance). Non-Eternal Return worldgen must still not change (CLAUDE.md).
- Textures: old-Minecraft style (small ramps of 4 to 6 values, value shading with dithering, high contrast, colder colours); the `oldstyle-textures` skill has the details.
- The PC's system Python may lack Pillow or pypdf; `C:/Users/ryanh/AppData/Local/Programs/Python/Python313/python` has them.

## Gametest pitfalls

- The scripted test player (FishingTests.survivalPlayer) isn't ticked and loads no chunks where it is teleported: `ctx.runAtEveryTick(player::playerTick)` when ticking matters, `world.setChunkForced` before checking entities far away.
- Test areas are 8x8 with a barrier ceiling unless `@GameTest(skyAccess = true)`; barriers pass light. Barrier walls block mobs' sight and pathing.
- Rain fades in: `isRaining()` stays false until `world.setRainGradient(1.0F)`.
- mobGriefing: tests that need it on set it on and leave it. Tests that change config, time or weather go in their own batch (tests in a batch run side by side). Noise-making tests too (game events reach mobs in neighbouring tests).
- Client mixins never load in gametests. Mixin `@At(args = "classValue=...")` strings are not remapped (use class literals); never match Minecraft classes by name at runtime, never branch on `CallbackInfo.getId()`: production uses intermediary names. Only `runClientCompat` (the released jar) catches these.
- `ctx.addInstantFinalTask` is not a cleanup hook (it completes the test at once).
- In `eternalreturn_test:arena` the stone floor is at test y 1 (stand on y 2).
- `setAiDisabled(true)` freezes a mob entirely; clear its goals instead. Idle monsters may sit down (Lounging): keep them up with `Lounging.stand(mob)`. Wait for targeting rather than asserting at a fixed tick.
- Test runs save their config to `build/gametest/<run>/config/eternalreturn.json` and later runs read it.
- Only the test areas' own chunks tick entities; mobs don't despawn on the test server; a passing test removes its entities, a failing one leaves them ticking.
- `ctx.getRelativePos` returns x and z negated for unrotated tests: use `pos - ctx.getAbsolutePos(BlockPos.ORIGIN)`.
- Placed light takes a while to spread: wait for it rather than a fixed tick. An entity's first `baseTick()` sees no fluids: tick twice.
- A run stuck at "Saving worlds": the report is already written; stop only the gametest java process (its command line has `-Deternalreturn.gametest.batches`), never the Gradle daemon. A thread dump (jstack) shows deadlocks.
- Flaky tests: `-PgametestRepeat=50 -PgametestBatches=a,b` runs the batches 50 times in one server.
- The Eternal Return test world (seed 173164) has structures off (vanilla's test level): tests of structures start and place them by hand (LandmarkTests). Block ticks don't run high in its sky. `generator.getHeight` near coasts costs about 0.36 s (Moderner Beta's ocean distance), so check biomes before heights in anything that runs often.
- StructureTemplate mirroring flips across the template's corner: compute positions with `StructureTemplate.transform`.

## Block and render pitfalls

- Adding a property to vanilla blocks: WallBlock's shape maps are keyed by its original states (look up the bare state); a BooleanProperty's first value (true) becomes the default; code in Blocks' class init must not reach back into maps being filled; override getCullingShape and isTransparent when the outline grows; each added state costs about 2 KB of heap.
- Shape methods that read the world deadlock at shutdown (the lighting thread); overlay quads z-fight unless they have the face's exact corners; pull UVs in from sprite edges; CUTOUT, not CUTOUT_MIPPED, for overlays.
- Fabric attachment sync gives the client no redraw signal: send your own payload and call `worldRenderer.scheduleBlockRenders`.
- The owner plays with Nostalgic Tweaks' old lighting (no smooth light, round-robin relight) and "Full Block Outlines" on some blocks; ColorLight colours light per block id from its own thread.
