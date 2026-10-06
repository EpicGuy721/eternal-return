# Worldgen vision

The worldgen phase of Eternal Return. This is the target; the status section further down tracks where things stand.

## Terrain

Overworld terrain with the weirdness of Beta 1.7.3 combined with the large continents and big oceans of Release 1.6.4.

## Biomes

A small biome list: mostly the Release 1.6.4 biomes, plus a few added in later versions. Which later biomes make the cut is the project owner's call. Chosen so far: birch forest, savanna, badlands and dark forest.

## Caves

Inspired by TheMasterCaver's World. Many cave types:

- spaghetti
- ravine
- maze
- zig-zag
- vertical
- ribbed
- random / combination
- large
- circular room
- spiral
- toroidal room

Cave stone depends on the biome (sandstone caves in deserts; granite, diorite, andesite and others elsewhere), and each stone has its own ore variants.

Built so far: spaghetti, ravine, large, vertical, zig-zag and ribbed (see The cave engine below).

No modern cave biomes (lush caves, dripstone caves, deep dark) and no deepslate. The full modern height (-64 to 320) is used.

## Scope

- Overworld only for now. The Nether and the End are left alone.
- Everything lives in this one mod, in its own worldgen package (`com.eternalreturn.worldgen`).
- Every feature gets its own on/off toggle in `config/eternalreturn.json`.
- Follow the repo's existing conventions: tags and data under `data/eternalreturn`, the existing config style, and README updates alongside each feature.

## Compatibility

Must keep working alongside Sodium, Nostalgic Tweaks and Moderner Beta.

## Current setup

Worldgen comes from Moderner Beta (`moderner-beta-fabric-5.0.0-alpha.2+1.21.1`) with this mod's own preset on top: World Type "Eternal Return" on the create-world screen (world preset `eternalreturn:eternal_return`, Moderner Beta settings preset `eternalreturn:eternal_return`). Moderner Beta is a runtime-only dependency in the dev environment, so the build never depends on it.

The preset is all data. Without Moderner Beta installed, the world type and its noise settings are skipped at load time (Fabric load conditions), the jar still launches, and worlds still load. The `gametestNoModernerBeta` run checks this.

## Status

- Phase 1 (investigation): done. Findings below.
- Phase 2 (preset, fish tags, terrain): done. Biome list, no cave biomes, no deepslate, full -64 to 320 height, terrain tuned in two passes (the second after playtesting).
- Phase 3, part 1 (cave engine): done. The shared engine, debug tools and six tunnel types; the old caves are off in Eternal Return worlds; trial chambers removed there. Ores are still vanilla's.
- Next: maze caves, combination caves and the room types (phase 3, part 2), then biome stone and ore variants.

## The Eternal Return preset

`tools/worldgen/build_preset.py` builds it from Moderner Beta's Release 1.6.4 preset (read straight from the Moderner Beta jar in the Gradle cache) and applies the changes below. The terrain knobs are in `tools/worldgen/knobs.json`: edit them, run `python tools/worldgen/build_preset.py tools/worldgen/knobs.json`, and the files under `src/main/resources` are rewritten.

| File | What it is |
|---|---|
| `data/eternalreturn/moderner_beta/settings_preset/eternal_return.json` | the Moderner Beta settings: biome layers, terrain knobs, no cave biomes, no deepslate |
| `data/eternalreturn/worldgen/noise_settings/eternal_return.json` | Moderner Beta's `overworld_256` noise settings with the height opened up to -64..320 |
| `data/eternalreturn/worldgen/world_preset/eternal_return.json` | the world type; vanilla Nether and End |
| `data/minecraft/tags/worldgen/world_preset/normal.json` | lists the world type on the create-world screen (optional entry) |
| `data/eternalreturn/moderner_beta/settings_preset_category/`, `data/eternalreturn/tags/moderner_beta/`, `data/moderner_beta/tags/moderner_beta/settings_preset_category/selectable.json` | lists the preset in Moderner Beta's own preset picker, with its icon (`assets/eternalreturn/textures/gui/moderner_beta_settings_preset/`) |
| `data/minecraft/tags/block/stone_ore_replaceables.json`, `data/minecraft/tags/block/deepslate_ore_replaceables.json` | move tuff from the deepslate ore tag to the stone one, so ores that land in tuff blobs use their stone variant |
| `structure_modifiers.removed` in the settings preset | `minecraft:trial_chambers`: no trial chambers in Eternal Return worlds |

**Biomes.** The 1.6.4 land pool (desert, forest, extreme hills, swampland, plains, taiga, jungle, each listed twice) plus birch forest, savanna, badlands and dark forest (each listed once). Ocean, frozen ocean, river, frozen river, beach, ice plains, ice mountains, mushroom island, and the hills and shore rules are unchanged from 1.6.4. Birch forest, savanna and badlands get hills variants through the hills layer; dark forest has none, as in later versions.

All four later biomes work cleanly next to Moderner Beta's own: Moderner Beta ships height settings for them and a badlands surface (terracotta bands and red sand), and their vanilla grass and foliage colours and mob spawns apply unchanged. Nothing had to be skipped. Share of land on the test map: ice plains 19% (the same as plain 1.6.4, from its cold climate band), taiga 13%, swampland 11%, forest 11%, desert 8%, plains 7%, badlands 6%, jungle 6%, extreme hills 6%, dark forest 4%, savanna 4%, birch forest 4%, mushroom island 0.2%. The four later biomes together are about a fifth of the land.

**No cave biomes.** The cave biome provider is `moderner_beta:none`, so lush caves, dripstone caves and the deep dark never generate (and so no ancient cities either).

**No deepslate.** Moderner Beta's deepslate layer is off, so everything below y=0 is ordinary stone. Vanilla still scatters tuff blobs below y=0, and an ore that lands in tuff would take its deepslate variant. Tuff is added to `stone_ore_replaceables` and taken out of `deepslate_ore_replaceables` (that file replaces vanilla's, leaving only deepslate), so those ores are always the stone kind; the second change matters once caves expose tuff to air, because vanilla's ore placer then falls back to the deepslate target. Both tag changes apply to every world: in a vanilla world, ores inside tuff blobs use the stone variant too. Trial chambers, which have a little cobbled-deepslate rubble, no longer generate in Eternal Return worlds. Moderner Beta's optional "Deepslate Blobs" datapack would still add deepslate if someone turned it on.

**World height.** -64 to 320, all of it used. Bedrock at -64, solid stone with caves and ores from there to the surface, sea level 63, most land between 65 and 90, hills to about 115 and the tallest mountains around 180. Moderner Beta's own 1.6.4 presets stop terrain at y=128; this preset lets it go higher, with room to spare up to 320. Don't turn on Moderner Beta's "Reduced Height" datapack with this preset: it moves the floor to y=0 for every world.

## Terrain tuning

Goal: Beta 1.7.3's weird, dramatic land at Release 1.6.4's continent and ocean scale. All maps are seed 173164, 4096 x 4096 blocks centred on 0,0, in `docs/worldgen-maps/`.

| | Beta 1.7.3 | Release 1.6.4 | Eternal Return |
|---|---|---|---|
| Water (share of the map) | 42.1% | 64.7% | 61.1% |
| Ocean biomes | 0% (Beta has none) | 44.5% | 44.6% |
| Landmasses bigger than a chunk | 337 | 200 | 135 |
| Land height: median / 90th / 99th percentile / max | 71 / 84 / 105 / 122 | 68 / 78 / 104 / 124 | 71 / 91 / 113 / 177 |
| Land roughness (average height change between points 4 blocks apart) | 1.4 | 1.3 | 2.2 |
| Land mix: flat / rolling / hilly / mountains / extreme | 29 / 51 / 18 / 2 / 0% | 47 / 34 / 16 / 4 / 0% | 17 / 40 / 37 / 7 / 0.1% |
| Land with an overhang (576 sampled chunks) | 0.5% | 0.6% | 0.8% |

The land mix sorts every mostly-land 32 x 32 block square by its relief (highest minus lowest ground): flat under 8 blocks, rolling 8-19, hilly 20-39, mountains 40-79, extreme 80 or more.

Beta's world is a maze of lakes, inlets and islands with no real oceans, and most of its land rolls. 1.6.4 has real oceans and continents, but half its land is flat. Eternal Return keeps 1.6.4's oceans and coastlines (same ocean-biome share) and makes the land roll and climb almost everywhere: mostly rolling and hilly ground, mountains up to about y 180, Beta-like cliffs and the odd overhang.

**Second pass (after playtesting the first).** The first pass (below) gave mountains up to y 307, 200-block spires and many floating islands, and the change from plain to spire was sudden. That came from multiplying each biome's own height range by 2.5: extreme hills, hills variants and mushroom islands became far more rugged than deserts (median relief 64 blocks against 12 on the land-rich test area). The second pass cuts that multiplier to 0.45 and adds a fixed 0.85 to every land biome instead, so every biome gets a similar Beta-style roll and the hilly biomes are only somewhat hillier (median relief 10 to 27 blocks by biome; Beta 9 to 15). The land lift is roughly halved too.

Swamps stay flat (median relief 2): they sit below the "land only" cut-off, as in 1.6.4.

The knobs, most important first (Moderner Beta's names in brackets):

| Knob | Shipped (first pass; 1.6.4) | What it does |
|---|---|---|
| Boost land only (`forced_biome_height.modifyOnlyPositiveDepth`) | true (true; false) | The boosts below apply to land biomes only. Ocean floors keep their 1.6.4 depth, which is what keeps 1.6.4's coastlines and ocean sizes. |
| Biome contrast (`scaleWeight`) | 0.45 (2.5; 1) | Multiplies each biome's own ruggedness. High values make extreme hills and hills variants far wilder than plains; low values make every biome roll alike. |
| Base ruggedness (`scaleOffset`) | 0.85 (0.6; 0) | Ruggedness added to every land biome: rolling hills, cliffs, overhangs. Around 1.0 and above, floating rock becomes common. |
| Land height (`depthWeight`, `depthOffset`) | 1.2, 0.2 (1.6, 0.5; 1, 0) | How high land sits above the sea. Plains sit around y 70. |
| Terrain ceiling (noise settings height) | 320 (320; 128) | Where terrain has to stop. Not reached any more, but leaves room. |
| Depth noise strength (`noise_landmass.depth.influence`) | 0.2 (0.2; 0.2) | Beta's 1.0 breaks the coastline into islets; 0.35 made little difference. |
| Sampled scale noise (`noise_landmass.scale.sample`) | off (off; off) | Beta's random roughness. It also roughens ocean floors, so it scatters islets across the oceans. |
| Vertical stretch (`noise_scale.stretchY`) | 12 (12; 12) | Lower values (9) give taller, thinner spikes. |

Second-pass variants, on the 2048 x 2048 area centred on 0,-1024 (the land-rich part of the map; `MAP_CENTRE=0,-1024 MAP_CLOSEUP=942,-420 MAP_SLICE_Z=-1328`). Contrast is `scaleWeight`, base is `scaleOffset`, lift is `depthWeight` / `depthOffset`:

| Variant | Contrast, base, lift | Land: median / 99th pct / max | Flat / rolling / hilly / mountains / extreme | Overhangs | Relief: extreme hills vs desert |
|---|---|---|---|---|---|
| Beta 1.7.3 | (no biome heights) | 71 / 101 / 122 | 28 / 54 / 16 / 2 / 0% | 0.9% | 9-15 in every biome |
| First pass | 2.5, 0.6, 1.6 / 0.5 | 78 / 202 / 307 | 7 / 23 / 36 / 24 / 10% | 6.8% | 64 vs 12 |
| Even | 0.86, 0.98, 1.3 / 0.25 | 73 / 137 / 207 | 11 / 28 / 40 / 19 / 2% | 4.1% | 34 vs 13 |
| Even, lower | 0.8, 0.8, 1.3 / 0.25 | 72 / 125 / 202 | 14 / 34 / 38 / 12 / 1% | 1.9% | 30 vs 10 |
| Flatter contrast | 0.6, 0.9, 1.3 / 0.25 | 72 / 123 / 171 | 14 / 34 / 39 / 13 / 1% | 2.6% | 29 vs 11 |
| Even more | 0.45, 1.0, 1.3 / 0.25 | 72 / 123 / 175 | 13 / 33 / 40 / 14 / 0% | 3.0% | 28 vs 12 |
| Tame | 0.7, 0.7, 1.2 / 0.2 | 72 / 115 / 153 | 17 / 41 / 34 / 9 / 0% | 1.0% | 28 vs 9 |
| **Even more, tame (shipped)** | 0.45, 0.85, 1.2 / 0.2 | 72 / 115 / 162 | 15 / 39 / 37 / 9 / 0% | 1.0% | 26 vs 10 |

Also tried on the area centred on 0,0: Beta's sampled scale noise on top of "even" (influence 0.4) gave about the same land but 123 landmasses instead of 44 (islets across the oceans), and depth noise 0.35 changed almost nothing.

For a little more drama, the "even more" numbers (`depth_weight` 1.3, `depth_offset` 0.25, `height_scale_offset` 1.0) keep the same evenness with more cliffs and some floating rock.

**First pass (superseded).** Same goal, judged on the maps alone. Variants on the 2048 x 2048 area centred on 0,0:

| Variant | Change from 1.6.4 | Land height: median / 99th pct / max | Roughness | Overhangs | Landmasses | Verdict then |
|---|---|---|---|---|---|---|
| Beta noise on 1.6.4 | sampled scale on, no land boost | 70 / 119 / 202 | 2.2 | 0.5% | 171 | islets everywhere, little drama |
| Mild boost | land only; height 1.5 / 0.3, scale 2.0 / 0.4 | 72 / 203 / 253 | 3.0 | 5.1% | 36 | good but tame |
| Mild boost + Beta noise | as above, sampled scale 0.4 | 72 / 205 / 269 | 3.3 | 7.8% | 122 | more islets, same drama |
| First pass | land only; height 1.6 / 0.5, scale 2.5 / 0.6 | 76 / 227 / 307 | 4.0 | 8.6% | 33 | chosen; too extreme in game |
| First pass + Beta noise | sampled scale 0.2 | 76 / 227 / 307 | 4.1 | 8.5% | 68 | more islets, no gain |
| First pass, stretch 9 | stretchY 9 | 76 / 273 / 311 | 5.2 | 9.9% | 94 | spikier, more islets |
| Amplified-lite | land only; height 2.0 / 0.8, scale 3.0 / 0.8 | 80 / 263 / 310 | 5.2 | 8.5% | 28 | wilder |
| Amplified | land only; height 2.0 / 1.0, scale 4.0 / 1.0 | 86 / 298 / 314 | 6.7 | 13% | 29 | mountains everywhere |

(First-pass overhang numbers came from 144 sampled chunks and are noisier than the 576-chunk numbers used now.)

### Map tool

`./gradlew generateWorldMaps` renders all three presets (about 15 minutes; add `-PworldmapHalf=1024` for a 2048-block area in a few minutes). `./gradlew runWorldmapEternalReturn` (or `runWorldmapBeta`, `runWorldmapRelease164`) renders one. Each run generates a world headlessly at seed 173164 and writes to `docs/worldgen-maps/<preset>/`:

- `height.png`: shaded height (blue water, green lowland, brown hills, grey and white mountains)
- `biomes.png`: biomes, with a legend and each biome's share
- `land.png`: land (green) against water (blue)
- `slice.png`: a side view (full height) through the row with the most high ground
- `closeup.png`: a 512-block close-up of the area with the highest ground
- `stats.json`: the numbers in the tables above, plus each biome's median relief (`terrain_mix.median_relief_by_biome`)

Options: `-PworldmapOut=<dir>` writes to `<dir>/<preset>/` instead; `-PworldmapCentre=x,z` moves the mapped area; `-PworldmapCloseup=x,z` and `-PworldmapSliceZ=z` pin the close-up and side view, so different settings can be compared on the same ground.

`python tools/worldgen/compare_maps.py` rebuilds `overview.png` (height and land maps with the headline numbers) and `slices_compared.png` (the three side views). `python tools/worldgen/variants.py <variants.json>` renders a list of knob variants into `build/worldgen-variants/<name>/eternal_return/`, prints the stats for each, then restores the shipped preset; the `HALF`, `MAP_CENTRE`, `MAP_CLOSEUP` and `MAP_SLICE_Z` environment variables pass the options above.

## The cave engine (phase 3, part 1)

Six tunnel-style cave types, carved only in Eternal Return worlds. Maze caves, combination caves and the room types come next, then biome stone and ore variants. Code: `com.eternalreturn.worldgen.caves`.

### Which worlds, and the old caves

- **Only Eternal Return worlds.** A world counts as Eternal Return when its generator uses this mod's noise settings (`eternalreturn:eternal_return`), which only the Eternal Return preset references. Moderner Beta rebuilds the settings object when a preset overrides the sea level (ours does), but it keeps the noise router object, so the check compares that (`CaveWorlds`, told to every carver through `CarverContextMixin`).
- **The old caves are off there.** Every carver runs through `ConfiguredCarver.carve`, under vanilla's generator and Moderner Beta's alike (Moderner Beta swaps in its Beta-style caves and canyons just before that call). In Eternal Return worlds `ConfiguredCarverMixin` skips every carver that isn't this mod's: vanilla's cave and canyon carvers, Moderner Beta's replacements, and any other mod's carvers. Each carver is seeded on its own, so skipping one never moves another's caves.
- **Everything else is untouched.** In vanilla, other Moderner Beta presets and flat worlds the cave carver does nothing and nothing is skipped. The control fingerprints prove it (see Tests).
- **Toggles.** `worldgen.caves.enabled` turns the new caves off; `worldgen.caves.oldCaves` brings the old ones back in Eternal Return worlds. With the new caves off and the old ones on, an Eternal Return world comes out block for block as it did before the cave engine.

### How a cave is carved

- One carver, `eternalreturn:caves` (`CaveSystemCarver`), attached to every overworld biome after the biome's own carvers. The generator calls it for the chunk being carved once per nearby source chunk (up to 8 chunks away), with a random seeded from the world seed and that source chunk; Release 1.6.4's caves worked the same way.
- From that seed each type gets its own random (salted with the type's name), so turning a type off or changing its weight leaves the other types' caves where they were. The type decides how many caves start in the source chunk (weight per 100 chunks), places each start in the chunk at a y in its depth range, and simulates the whole cave. Only the parts inside the chunk being carved are written.
- Shapes stay within 120 blocks (per axis) of their source chunk's centre, so every chunk a cave touches also sees its source: caves line up across chunk borders, and the result doesn't depend on which chunks generate first.
- Shared building blocks (`CaveBuilder`, `Tunnel`, `ChunkCarver`): ellipsoids with an optional flat floor, any custom shape, and a winding tunnel walker using Release 1.6.4's wander maths. A new type is one class plus one line in `CaveTypes`.

`ChunkCarver` holds the rules every type follows:

- Only blocks in the block tag `eternalreturn:carvable` are replaced. It contains vanilla's `#minecraft:overworld_carver_replaceables` (all overworld stones, dirt, sand, terracotta, gravel, sandstone, snow, packed ice and more), so the biome stone variants planned next only need adding to the tag. Bedrock and anything outside the tag stay.
- Nothing is carved in the bottom layer or within 8 blocks of the top (vanilla's limits).
- At or below the lava level (vanilla's 8 blocks above the floor: y -56) carved blocks become lava, above it cave air.
- A shape is skipped in a chunk when water, or lava above the lava level, lies in or next to its box: Release 1.6.4's rule, which keeps caves from breaching oceans, rivers and lakes. Water just across the chunk's edge is read from the generator's height map (the ocean or river surface there), because the neighbouring chunk may not exist yet. Vanilla's carver tag contains water, but this rule means water is never carved.
- When grass or mycelium is carved, the dirt under it becomes the same block, as vanilla does.

### The types

Weight is caves starting per 100 chunks; spaghetti counts systems. Start y is where a cave begins (tunnels wander beyond it). Radius ranges are in blocks.

| Type | Looks like | Weight | Start y | Radius |
|---|---|---|---|---|
| `spaghetti` | Release 1.6.4's caves: systems of two to six long, thin tunnels leaving from points a few blocks apart, wandering in heading and height, with flat floors. One tunnel in five branches once; one in three keeps its slope for longer, so it climbs or drops a long way. Shallow ones can break out at the surface. | 24 | -58 to 85 | 1.3 to 2.0 (half-width) |
| `ravine` | Release 1.6.4's ravines: long, narrow canyons three to four times as tall as they are wide, with vertical, jagged walls (the width changes every one to three blocks of height). One in four is a large ravine, wider, taller and grown both ways from its start. Shallow ones open to the surface. | 2 | -30 to 45 | 2.5 to 4.5 (half-width; large ×1.5) |
| `large` | A big chamber: a flattened, stretched main body with five to ten lobes of different sizes and heights around it, so the walls are uneven and the ceiling lumpy, a mostly flat floor, and two to five winding tunnels leading out. The deepest can have lava on the floor. | 1 | -48 to 15 | 10 to 25 (main chamber) |
| `vertical` | Steep connections between levels. Three in five are shafts dropping 20 to 60 blocks with a slight drift and a bulging wall; the rest are steep tunnels at 50 to 75 degrees. Short side tunnels leave the top and bottom so they join the caves around them. | 8 | -20 to 60 (the top) | 1.5 to 3.5 |
| `zigzag` | Constant-width tunnels made of equal straight segments (6 to 14 blocks) turning the same sharp angle (70 to 110 degrees) left and right in turn, each segment with its own gentle slope. | 7 | -50 to 50 | 1.5 to 2.5 |
| `ribbed` | Gently curving tunnels whose width pulses 35 to 55 percent above and below the base every 5 to 9 blocks: wide bulges between rings of rock. | 7 | -50 to 50 | 2.0 to 3.5 (base) |

The settings that matter most for how much cave there is: `worldgen.caves.density` (multiplies every weight), the spaghetti weight (most of the cave volume) and its radius range, then the large-cave weight (rare but big).

### Density

Share of the underground (everything below the ground surface) that is open, over a land square of 24 x 24 chunks (from -1472, -1344; seed 173164):

| Depth | New caves | Old caves, same terrain | Release 1.6.4 world |
|---|---|---|---|
| y -64 to -49 | 2.1% | 3.4% | 3.2% |
| y -48 to -33 | 3.8% | 4.8% | 5.1% |
| y -32 to -17 | 5.2% | 5.8% | 7.4% |
| y -16 to -1 | 5.5% | 5.6% | 6.2% |
| y 0 to 15 | 6.7% | 6.8% | 5.4% |
| y 16 to 31 | 6.0% | 5.0% | 4.1% |
| y 32 to 47 | 4.6% | 3.7% | 3.4% |
| y 48 to 63 | 3.7% | 2.9% | 1.1% |
| y 64 to 79 | 2.8% | 3.1% | 0.2% |
| **All** | **4.46%** | **4.55%** | **4.53%** |

"Old caves" are Moderner Beta's 1.6.4-style caves on the identical terrain (the twin dimension with the engine off); the Release 1.6.4 world is Moderner Beta's own preset (its terrain is lower, hence the empty top rows). The new caves carry about the same total, a little thinner at the very bottom and a little more in the middle.

### Timing

Chunk generation up to the carving step, one chunk at a time, same squares with the engine on and off (overworld and twin dimension, swapped between rounds), 392 chunks each:

| | ms per chunk |
|---|---|
| No caves | 63.3 |
| New caves | 68.7 |
| Old caves (Moderner Beta's) | 70.6 |

Time spent inside the cave carver: 8.6 ms per chunk (each chunk simulates the caves of the 289 chunks around it). Three earlier runs gave 6.5 to 7.8 ms, so expect 6 to 9 ms depending on the machine's load. The new caves cost about the same as the old ones.

### Dev options

- `debugForceCaveType`: a type id, and only that type is carved, at a high rate (spaghetti, vertical, zigzag and ribbed 60 per 100 chunks; ravine 10; large 8).
- `debugLogCaveStarts`: appends `type,x,y,z` for every cave start to `eternalreturn-cave-starts.csv` in the game folder as chunks generate. Teleport to any line to stand inside that cave.

### Cave maps

`./gradlew runWorldmapCaves` (part of `generateWorldMaps`) writes to `docs/worldgen-maps/caves/`:

- `slices.png`: the land square cut at y -50, -20, 20 and 50, new caves next to the old ones on the same terrain (black = cave). `slice_y*.png` are the new-cave slices on their own.
- `gallery.png`: each type forced in its own 128 x 128 block square, seen from above (colour = height of the highest cave block) and from the side.
- `caves.json`: the density numbers and real cave starts in the land square (used for the README checklist).

### Trial chambers

Removed in Eternal Return worlds only: the preset lists `minecraft:trial_chambers` in Moderner Beta's `structure_modifiers.removed`, which drops the structure set from that world's placement. Nothing changes in other worlds. In Eternal Return worlds this also means no breezes, trial spawners, vaults, trial keys, heavy cores or maces. The structure test counts starts over an 8192-block square: none in Eternal Return, 222 in a vanilla world and 177 in a Moderner Beta 1.6.4 world.

### Tests

- `runGametestCaves` (Eternal Return with a twin dimension that has the same generator):
  - per type, forced, in a square of its own: caves exist; the open share is in a sane range; no gaps in the bedrock floor, no cave air at or below the lava level, no lava above it, nothing within 8 blocks of the top; no cave block touches an ocean, river or lake; cave borders line up across chunk edges as well as inside chunks; the same chunk generated first in one dimension and last in the other is identical; every logged start is of that type and in its depth range;
  - every block of `eternalreturn:carvable` is carved, bedrock and obsidian aren't, a shape beside water is skipped, lava at and below the lava level;
  - default density and the timing above.
- `runGametestEternalReturnOldCaves`: new caves off, old caves on: carved terrain matches the fingerprint recorded before the cave engine.
- Control fingerprints, unchanged: vanilla, Moderner Beta 1.6.4 amplified (debug tunnel on and off), Release 1.6.4 and Beta 1.7.3. Each hashes 27 chunks generated up to the carving step and compares with `src/gametest/resources/fingerprints/`; `-PrecordFingerprints` records new baselines.

### Known limits

- Water pockets inside the terrain (below sea level, not connected to an ocean) are only seen within the chunk being carved, so a cave can touch one across a chunk edge; the cave tests found none in their squares.
- Other mods' carvers don't run in Eternal Return worlds unless `oldCaves` is on.
- Ores: tuff is now in `stone_ore_replaceables` and no longer in `deepslate_ore_replaceables` (replaced), because caves exposing tuff to air let vanilla's ore placer fall back to the deepslate variant.

## Phase 1 findings (Moderner Beta 5.0.0-alpha.2)

Sources: the mod's shipped data and its source (Codeberg, tag `5.0.0-alpha.2`). The official docs site (moderner.nostalgica.net) has no DNS record and the Codeberg wiki is empty.

**Settings are data.** A world is three settings blocks (chunk, biome, cave biome), usually pointing at a settings preset such as `moderner_beta:release_1_6_4_amplified`. Presets are a datapack registry (`data/<namespace>/moderner_beta/settings_preset/*.json`), so this mod can ship its own preset and world preset as plain data, with no code dependency.

**Terrain.** Beta 1.7.3 and Release 1.6.4 use the same `noise_3d` terrain engine. 1.6.4 turns on `forced_biome_height` (each biome's depth and scale shape the land; this is where continents and oceans come from) and weakens the random depth noise (`noise_landmass.depth.influence` 0.2). Beta has no forced height and full-strength depth noise (influence 1.0). Phase 2 tested the blend; see Terrain tuning.

**Biomes.** Release presets use a `fractal_layers` pipeline, the old GenLayer stack, written out in the preset: `random_biome` pools, `biome_replacement`, `weighted_pool`, hills, shores, rivers. Trimming or adding biomes means editing those lists in our own preset. Beta-style presets also have `biome_injection_rules`. A 1.6.4 world uses vanilla `desert`, `forest`, `jungle`, `ocean`, `frozen_ocean`, `river`, `frozen_river`, `beach`, `mushroom_fields`, plus Moderner Beta's own `early_release_ice_plains`, `early_release_taiga`, `early_release_swampland`, `early_release_extreme_hills`, `late_beta_plains`. A cave-biome layer adds `lush_caves`, `dripstone_caves` and `deep_dark` underground. Moderner Beta's own biomes are not in vanilla tags such as `#minecraft:is_overworld`.

**Caves.** `chunkSettings.cave_generation` has `useCarvers` (master switch: off skips every carver, ours included), `forceBetaCaves` / `forceBetaCanyons` (swap vanilla's `cave`, `cave_extra_underground` and `canyon` carvers for Beta versions), `useNoiseCaves` and `fixCaveBorders`. Its `applyCarvers` loops over each nearby chunk's biome carvers like vanilla does, so carvers added through Fabric biome modifications run. Proven by the debug tunnel test (see README, Tests). Removing vanilla's cave carvers from biomes should also remove the Beta caves that replace them (from reading the code; untested). Phase 3 skips them at carve time instead, in Eternal Return worlds only (see The cave engine).

**World height.** Moderner Beta ships an optional "Reduced Height" datapack that moves the overworld floor to y=0, like old versions. It is off unless chosen at world creation. Without it, the world keeps the normal -64 floor. Moderner Beta's own presets cap terrain at y=128 even so; the Eternal Return preset uses the full range (see above).

## Existing features that depend on biomes

Checked against a Moderner Beta Release 1.6.4 world in phase 1, fixed in phase 2.

- Catfish (`catfish_waters`): now also bite in Moderner Beta's swamps (`early_release_swampland`, `late_beta_swampland`, `beta_swampland`, `pe_swampland`).
- Icefish (`icefish_waters`): now also Moderner Beta's ice plains, cold taiga, tundra, ice desert, and frozen and cold oceans (every generation), alongside vanilla's snowy biomes. 1.6.4 has no snowy beaches, ice spikes or 1.18 peaks, so those entries only matter in other world types.
- Anglerfish: no longer need a deep ocean biome. They bite in any ocean biome (`ocean_waters`: vanilla's oceans plus Moderner Beta's) when at least 10 blocks of water lie under the hook (`fishing.anglerfishMinWaterDepth`). On the test maps, half of all water is at least 13 blocks deep in Eternal Return (12 in 1.6.4, 2 in Beta).
- Tuna and electric eels: now use `ocean_waters` too, so Moderner Beta's Beta-style oceans count.
- Every Moderner Beta entry in these tags is optional, so the tags load without it.
- Spider jockey riders (weighted by the biome's monster spawn list): fine. Vanilla `desert` spawns husks, and Moderner Beta's ice plains spawn strays (weight 80), so strays on polar bears still happen there.
- Cave-only creepers (sky light 0): creepers only spawn where no sky light reaches. Cave types open to the sky (ravines, vertical shafts, big caverns with openings) won't get creepers near their openings; deep enclosed rooms will.
