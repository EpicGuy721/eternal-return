# Worldgen vision

The worldgen phase of Eternal Return. This is the target; the status section at the end tracks where things stand.

## Terrain

Overworld terrain with the weirdness of Beta 1.7.3 combined with the large continents and big oceans of Release 1.6.4.

## Biomes

A small biome list: mostly the Release 1.6.4 biomes, plus a few added in later versions. Which later biomes make the cut is the project owner's call.

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

## Scope

- Overworld only for now. The Nether and the End are left alone.
- Everything lives in this one mod, in its own worldgen package (`com.eternalreturn.worldgen`).
- Every feature gets its own on/off toggle in `config/eternalreturn.json`.
- Follow the repo's existing conventions: tags and data under `data/eternalreturn`, the existing config style, and README updates alongside each feature.

## Compatibility

Must keep working alongside Sodium, Nostalgic Tweaks and Moderner Beta.

## Current setup

Worldgen today comes from Moderner Beta (`moderner-beta-fabric-5.0.0-alpha.2+1.21.1`), world preset Release, version 1.6.4, amplified. It is a runtime-only dependency in the dev environment, so the build never depends on it.

## Status

- Phase 1 (investigation): done. Findings below. Nothing in terrain, biomes, caves or ores has been tuned yet.

## Phase 1 findings (Moderner Beta 5.0.0-alpha.2)

Sources: the mod's shipped data and its source (Codeberg, tag `5.0.0-alpha.2`). The official docs site (moderner.nostalgica.net) has no DNS record and the Codeberg wiki is empty.

**Settings are data.** A world is three settings blocks (chunk, biome, cave biome), usually pointing at a settings preset such as `moderner_beta:release_1_6_4_amplified`. Presets are a datapack registry (`data/<namespace>/moderner_beta/settings_preset/*.json`), so this mod can ship its own preset and world preset as plain data, with no code dependency.

**Terrain.** Beta 1.7.3 and Release 1.6.4 use the same `noise_3d` terrain engine. 1.6.4 turns on `forced_biome_height` (each biome's depth and scale shape the land; this is where continents and oceans come from) and weakens the random depth noise (`noise_landmass.depth.influence` 0.2). Beta has no forced height and full-strength depth noise (influence 1.0). A Beta-weird / 1.6.4-continent blend looks reachable by mixing these knobs (forced heights for oceans, stronger noise and per-biome `heightOverrides` for land). Unverified; that is terrain-tuning work.

**Biomes.** Release presets use a `fractal_layers` pipeline, the old GenLayer stack, written out in the preset: `random_biome` pools, `biome_replacement`, `weighted_pool`, hills, shores, rivers. Trimming or adding biomes means editing those lists in our own preset. Beta-style presets also have `biome_injection_rules`. A 1.6.4 world uses vanilla `desert`, `forest`, `jungle`, `ocean`, `frozen_ocean`, `river`, `frozen_river`, `beach`, `mushroom_fields`, plus Moderner Beta's own `early_release_ice_plains`, `early_release_taiga`, `early_release_swampland`, `early_release_extreme_hills`, `late_beta_plains`. A cave-biome layer adds `lush_caves`, `dripstone_caves` and `deep_dark` underground. Moderner Beta's own biomes are not in vanilla tags such as `#minecraft:is_overworld`.

**Caves.** `chunkSettings.cave_generation` has `useCarvers` (master switch: off skips every carver, ours included), `forceBetaCaves` / `forceBetaCanyons` (swap vanilla's `cave`, `cave_extra_underground` and `canyon` carvers for Beta versions), `useNoiseCaves` and `fixCaveBorders`. Its `applyCarvers` loops over each nearby chunk's biome carvers like vanilla does, so carvers added through Fabric biome modifications run. Proven by the debug tunnel test (see README, Tests). Removing vanilla's cave carvers from biomes should also remove the Beta caves that replace them (from reading the code; untested).

**World height.** Moderner Beta ships an optional "Reduced Height" datapack that moves the overworld floor to y=0, like old versions. It is off unless chosen at world creation. Without it, the world keeps the normal -64 floor.

## Existing features that depend on biomes

Checked against a Moderner Beta Release 1.6.4 world. Not changed yet.

- Catfish (`catfish_waters`: `swamp`, `mangrove_swamp`): never bite. 1.6.4 swamps are `moderner_beta:early_release_swampland`, and there are no mangroves.
- Icefish (`icefish_waters`): only frozen rivers and frozen-ocean edges. 1.6.4 ice plains and cold taiga are Moderner Beta biomes outside the tag; snowy beaches, ice spikes, cold and deep oceans, and the 1.18 peaks (`grove`, `snowy_slopes`, `frozen_peaks`, `jagged_peaks`) don't exist.
- Anglerfish (`#minecraft:is_deep_ocean`): never bite. 1.6.4 has no deep ocean biome, even where amplified oceans are deep.
- Tuna and electric eels (`#minecraft:is_ocean`): fine; `ocean` and `frozen_ocean` are in the tag.
- Spider jockey riders (weighted by the biome's monster spawn list): fine. Vanilla `desert` spawns husks, and Moderner Beta's ice plains spawn strays (weight 80), so strays on polar bears still happen there.
- Cave-only creepers (sky light 0): creepers only spawn where no sky light reaches. Cave types open to the sky (ravines, vertical shafts, big caverns with openings) won't get creepers near their openings; deep enclosed rooms will. The `deep_dark` cave biome spawns no monsters at all, so no creepers there either.
