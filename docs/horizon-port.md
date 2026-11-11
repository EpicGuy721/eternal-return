# Porting Eternal Return to Horizon: feasibility (2026-11-07)

[Horizon](https://github.com/horizon-rust/horizon) is a from-scratch Rust remake of Minecraft Java 26.3 (MIT,
version 0.2). No Java runs in it: not this mod, not Moderner Beta, not Sodium, Nostalgic Tweaks, ColorLight or
LambDynamicLights. A port is a rewrite of the code; the data (models, textures, loot tables, recipes, tags,
worldgen JSON) mostly carries over after a 1.21.1 → 26.3 format update. The Fabric mod stays the main version.

The slice that proves the first steps lives in `horizon-slice/` (and as its own repo in
`../eternal-return-horizon`): see "The slice" below.

## Short answer

- **Can be ported now (data only):** the simple blocks and items (stairs, slabs, walls, gems, ores, wood sets,
  copper and rose gold gear, foods' items, the fish items), recipes, loot tables, tags, the enchantment JSON files,
  structures placed from templates, and simple worldgen features added to vanilla's biomes.
- **Can be ported now with Rust code (native mod):** block behaviour (icicles, mushrooms' gas, troughs, layered
  carpets), most gameplay rules that hook a game event (leaf distance, pistons, spawning rules, damage, fishing,
  villager trades, beds, beacons), mob behaviour as goals, config files, networking.
- **Needs Horizon to add things first:** Eternal Return's own biomes (Horizon drops new biomes from data; it is in
  review upstream, #3491), new mobs with their own bodies (a mod mob can only copy a zombie, a villager or a
  wandering trader), mod feature, carver and structure types in code (caves, landmarks, biome trees, the outer End),
  and a terrain generator that can also tell the game its biomes.
- **Biggest single job:** Moderner Beta's terrain. It is a Java mod; Eternal Return worlds would need its Beta
  terrain and biome layout rewritten in Rust behind Horizon's custom chunk generator hook.
- **Biggest risk:** Horizon is five weeks old and changes daily. Hook points are only ever added (safe), but most
  of this mod's 322 Mixins would have to become raw patches on Horizon's own functions, which can break on any
  update and pin the mod to one Nightly.

## What a Horizon mod can hook

Two kinds of mod (Horizon's `docs/mods.md`, `docs/native-mods.md`):

- **Content mod** (`.hmod`): data and assets, no code. Blocks and items copy a vanilla *template* (its behaviour
  class) and change numbers (hardness, sounds, light, shapes, extra state properties, components). Also creative
  tabs, mob effects (no effect behaviour), entity types copied from a snowball, zombie, villager or wandering
  trader, menus, recipes, loot, tags, damage types, configured and placed features, structures, template pools,
  NeoForge biome modifiers (add or remove features and spawns in vanilla's biomes).
- **Native mod** (`.hnmod`): Rust compiled into the game by the launcher (it ships its own Rust toolchain, nothing
  is installed system-wide). Either *trait hooks* on Horizon's named hook points (609 of them in the catalog,
  modelled on NeoForge and Bukkit events), which stay stable, or *mixins* in the Sponge style (HEAD, RETURN,
  OVERWRITE, MODIFY_CONSTANT...) on **any** function of the game's source, which are pinned to one Horizon commit.

| Area | What exists | Verdict for this mod |
|---|---|---|
| Blocks with behaviour, states, ticking | template blocks with own state properties and shapes; hooks for random ticks, scheduled ticks, neighbour changes, shape updates, use, placement, survival, block entities (tick, container, sync, drawing), pistons, bone meal | yes |
| Entities and AI | entity types copied from zombie, villager, wandering trader or snowball; `register_goals` with Rust goals (vanilla's goals reusable); mobs drawn by the mod (`native_mob_submit`); spawn hooks (`potential_spawns`, `mob_spawned`) | partly: behaviour yes, but bears, birds, beholders, soldiers and the rest need their own size, movement (flying, swimming) and model, which templates don't give yet |
| Changing vanilla behaviour (our 322 Mixins) | named hooks for many events (`leaves_decay`, `piston_move`, `living_incoming_damage`, `living_knockback`, `use_item`, `player_fish`, `merchant_offer_traded`, `can_player_sleep`, `entity_tick_pre`...); mixins on any function for the rest | possible, but only the event-shaped ones are stable; the rest are raw patches that break on updates |
| World generation | data packs: noise settings, density functions, carvers, features, structures, biomes (changes to vanilla's), level stems; content mods: features and structures plus biome modifiers; `native_chunk_generate` (a mod builds whole chunks, as Bukkit's `ChunkGenerator`) and `native_lod_column` (far terrain) | features and structures from data: yes. New biomes: no (dropped, upstream #3491 in review). Own feature, carver, structure or density function types in code: no hook. Own terrain: yes, as a whole-chunk generator, but surface rules and structures keep reading vanilla's biome layout |
| Client rendering | resource packs and block/item models; render hook points (draw cubes, quads, blocks, items), block entity and mob drawing, fog colour and fog, biome colours, HUD, screens, particles sent from the server; Horizon has its own renderer, dynamic lights and shader packs built in | models and simple custom drawing yes; our client Mixins (snowlogging models, mixed slabs, moving block entities) need rewriting against Horizon's renderer |
| Networking, config, data packs | mod messages both ways, `register_configs` (files in `config/`), world data packs, `/reload` | yes |

**Stability.** The repository was created on 2026-10-03; on 2026-11-07 the launcher was at 0.2.50 (four releases
that day) and the hook API at version 413, about ten hook points added per day. Horizon's rules: a hook point's
contract is never changed, only new versions added, and a mod that uses only hook points declares the hook API
version it needs and keeps building on later Horizons. Content mods name an API line (`api = "0.2"`); a breaking
change starts a new line. A mixin on a raw function (as the slice's leaf rule needs) pins one Nightly: on a newer
Horizon the launcher leaves it out ("made for another Horizon version") until it is updated. No break of a named
hook has been recorded; raw targets move whenever Horizon's code does.

## Eternal Return, area by area

Ratings: **content** (data only), **native** (Rust with an existing hook), **needs Horizon** (a hook or feature
upstream must come first), **impossible today**.

| Area (Fabric size) | Rating | Notes |
|---|---|---|
| Moderner Beta terrain (a separate Java mod; Eternal Return worlds are built on it) | **needs Horizon**, very large | Rewrite Moderner Beta's Beta/Release terrain and biome layers in Rust behind `native_chunk_generate`; Horizon must also let a generator supply the biomes surface rules and structures read, and Eternal Return's own biomes must exist (#3491). Check Moderner Beta's licence before porting its code |
| Eternal Return world type, settings preset, noise settings, biome stone surface rules | needs Horizon | the JSON carries over only once the generator and mod biomes exist |
| Biomes: 1.6.4 list, autumnal forest, phase 6 biomes, peaks, edges, rivers, cave biomes | **needs Horizon** | new biomes are dropped today |
| Ores, ore variants, gems, hardened blocks, stone dripstone blocks, ice blocks (as blocks) | content | template blocks; textures and models carry over |
| Icicles, ice crystals, cave mushrooms (gas, growth), stone dripstone behaviour | native | random and scheduled tick hooks |
| Cave engine (8 cave types), floating islands, old caves off | needs Horizon | no hook for a mod carver; could live inside the custom chunk generator once it exists |
| Landmarks, biome trees, boulders, dungeons, mineshaft woods, strongholds at ground level, outer End | needs Horizon (a feature or structure type hook), partly content | boulders and simple features work as data today (the slice); template trees and landmark pieces need code hooks or the chunk generator |
| Climate (snow depth, ice, fog tints) | native, partly | weather and fog hooks exist; snow depth rules hook block placement and random ticks |
| Wood sets, copper and rose gold gear, rubies and sapphires, wool and concrete stairs and slabs, walls | content | the slice ports the stairs and slabs |
| Layered carpets, vertical and mixed slabs, snowlogging | native, large | extra state properties on vanilla blocks and custom models; snowlogging needs per-chunk data and client drawing |
| Enchanting (caps, table, anvil, new enchantments, curses, scrolls) | native + content | enchantment JSON carries over; the table and anvil rules need menu hooks (`prepare_anvil`, `prepare_item_enchant`, `enchant_effect`) |
| Mobs: new behaviour of vanilla mobs (jockeys, pearls, reeling, mining zombies, parkour, spawn rules) | native, large | goals and spawn hooks |
| New mobs (bears, birds, beholder, soldier, vampires, frostbite, parched, bone spider, mooblooms...) | **needs Horizon** | templates are only zombie, villager and wandering trader; no flying or water template, no own hitbox; models drawn by hand in Rust |
| Fishing (bait, 53 fish, lava fishing, monsters on the hook) | native + content | fish items and loot tables are data; `player_fish` and loot hooks |
| Villagers (needs, chores, trades, regions) | native, large | trade hooks exist; villager brains stay vanilla's, so chores need care |
| Survival (no hunger, foods, beds, beacons, animal hunger, armour weight, minecarts, crops) | native | many event hooks; each feature a small mod |
| Combat, potions, pistons crushing, random spawns, hidden coordinates | native | damage, knockback, piston and spawn hooks |
| Sword blocking (relies on Nostalgic Tweaks) | native, Horizon-specific | Nostalgic Tweaks doesn't exist there: blocking itself must be written too |
| Owner's pack: Sodium, Nostalgic Tweaks' old lighting, ColorLight, LambDynamicLights | impossible as they are | Horizon has its own fast renderer, dynamic lights and shader packs; old-style lighting and coloured light would need Horizon options or own client mods |
| Gametests, fingerprints, benchmark | needs rewriting | Horizon has Rust unit tests and its own harnesses, no GameTest |

## The slice

`horizon-slice/` (also its own git repo, `../eternal-return-horizon`):

1. **Content mod** (`content-mod/`, generated by `tools/build_content.py` from this repo's resources): the 64 wool
   and concrete stairs and slabs with their recipes (turned into 26.3's ingredient format), drops, tags, names and a
   creative tab. Blockstates and models are this repo's files unchanged.
2. **Native mod** (`native-mod/`): leaves decay at distance 10 instead of 7, as `LeafDistance`. Horizon's
   `leaves_decay` hook can only keep a leaf at vanilla's distance 7, and vanilla's distance stops at 7, so the mod
   searches through the leaves for a log within 9 steps itself. It reads block names from a game type, so it pins
   Nightly 2026-11-07.
3. **Boulder** (in the content mod): vanilla's rock feature (`block_blob` in 26.3, 1.21.1's `forest_rock`) in
   stone, granite, diorite and andesite, added to every overworld biome with a biome modifier. It is not the Fabric
   boulder (templates in each biome's stone): a mod's own feature type needs a hook Horizon lacks.

**Run in the game on 2026-11-07** (launcher 0.2.50, Nightly 2026-11-07-2ebf00a, a fresh creative world), with
screenshots in `horizon-slice/screenshots/`:

- `stairs_and_slabs.png`: all 64 stairs and slabs placed, drawn with this repo's models, the right colours.
- `leaves_distance_10.png`: a log with a line of 12 leaves, random ticks sped up: the leaves 1 to 9 blocks from the
  log stayed, 10 to 12 decayed (vanilla keeps only 1 to 6). The native mod applied and compiled on the first try.
- `boulder_granite.png`: a granite boulder the world generated on its own, on the grass of a forest.

What it took: the first native build is about 20 minutes (the launcher downloads Rust 1.99.1 and Horizon's source
and compiles the whole game); the version must first be prepared from Minecraft 26.3 (about a minute). Rebuilds
after a change to a mod are incremental. One format catch: 26.3 renamed `forest_rock` to `block_blob`, dropped
the `config` wrapper and writes the block as a plain name; the game logs "placed features cannot run" and skips a
feature in the old form, which is how it was found.

## Phased plan (rough sizes; a "session" is one working session like the feature batches)

| Phase | What | Size | Waits on Horizon? |
|---|---|---|---|
| 0 | Run the slice in game; set up a Horizon instance for testing | 1 session | no |
| 1 | All data-only content: blocks, items, wood sets, gear, gems, ores as blocks, recipes, loot, tags, enchantment JSON, fish items | 2 to 3 sessions | no |
| 2 | Gameplay rules as native mods on hook points: enchanting, survival, combat, fishing, villager trades, pistons, spawning, leaf distance | 8 to 12 sessions | some hooks will be missing; ask upstream |
| 3 | Block behaviour: icicles, mushrooms, troughs, carpets, slabs, snowlogging | 4 to 6 sessions | client drawing hooks for snowlogging and mixed slabs |
| 4 | Mobs: vanilla mob changes (goals), then new mobs | 6 to 10 sessions | yes for new mobs (templates, models, flying) |
| 5 | Terrain: Moderner Beta in Rust, Eternal Return biomes, caves, landmarks, trees, the outer End | 15 to 25 sessions | yes: mod biomes (#3491), generator biomes, feature and carver hooks |

Phases 1 to 3 give a playable "Eternal Return on vanilla terrain" in Horizon. Phase 5 is what makes it Eternal
Return worlds, and it is the part most likely to be blocked or reworked as Horizon changes.
