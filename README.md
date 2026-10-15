# Eternal Return (Fabric 1.21.1)

A total conversion for Minecraft. So far it covers three systems: a rework of enchanting built around capacity, material identity, and libraries of books instead of XP; tougher overworld mobs (baby skeletons and creepers, better-armed zombies and skeletons, new jockeys, cave-only creepers); and fishing as the emerald economy (bait, eleven new fish, lava fishing in the Nether, and fishermen as the only villagers who still pay emeralds); villagers with needs (sleep, gossip, exercise, sunlight, work-life balance) and shop hours; survival rules (beds that only set the respawn point and are remembered, beacons that keep monsters out and let animals return, animals that graze or starve and panic as a herd, spawner mobs that drop nothing); and building blocks (wool and concrete stairs and slabs, carpets that stack). Worldgen has its own world type, Eternal Return: Release 1.6.4's continents and oceans with rolling, hilly Beta-style land and a new cave system, built on Moderner Beta.

## Building

Requires JDK 21.

```
./gradlew build             # jar lands in build/libs/ (also compiles the tests, without running them)
./gradlew runClient         # dev client for testing
./gradlew runAllGametests   # every in-game test, on headless servers (a few minutes)
./gradlew generateWorldMaps # maps of the world types, caves and biome stone into docs/worldgen-maps/ (about 25 minutes)
```

Uses Yarn mappings (`1.21.1+build.3`), Fabric Loader 0.19.5, Fabric API `0.116.17+1.21.1`, and the current Loom from the official Fabric template. The Gradle wrapper is copied from that template.

Dev runs (client, server and tests) also load Moderner Beta `5.0.0-alpha.2+1.21.1`, the world generator this mod is built to sit on. It is a runtime-only dependency: nothing compiles against it, and the released jar doesn't require it. The Eternal Return world type only appears when Moderner Beta is installed; everything else works without it.

Every mixin was checked with a forced Mixin audit at startup. If a future mixin target is wrong, the game stops at launch with an error naming the mixin class.

### Tests

In-game tests live in `src/gametest`, a separate test mod that is never packed into the release jar. `runAllGametests` runs ten headless servers, each in a fresh world:

| Task | World | What runs |
|---|---|---|
| `runGametest` | flat | the mob, fishing and trade suites (with guardian spawn lists and limits, prismarine, monsters on the hook and overfishing), villager needs (how each is met, price rises on emerald trades only, refusals, shop hours, sleep privacy, healing, saving), survival (no night skip, remembered beds and respawning at the one before, beacon areas through the real spawn rules, grazing and starving, feeding by hand, herd panic, blind follow range, a real spawner's mobs dropping nothing, Growth and Slayer, every stairs and slab with its tags and recipes, stacking and dropping carpets), armor weight and Weightless, drowned prismarine, baby fish and squids, bigger and rain slimes, blazes, falling snow, crop biomes, scrolls and book recipes, spider webs, eating times and raw food, mining and hungry zombies, the newer jockeys, Soulbound, dispensers, the obsidian plate, pistons moving chests and chains, cut vines, floating petals, pig litters, stunted babies, glass and dyed frames, beehive and TNT heads, zombies breaking lights, glowing seen through walls, Nether wither skeletons and their skulls, Launch, Reeling, Spread of Ailments, Curse of Ascension, what endermen carry, the philosopher's stone, a check that the Eternal Return world type loaded, the hardened blocks, and every ore variant against its vanilla ore (drops, Fortune, Silk Touch, experience, tool tier, smelting, tags, creative tab, redstone light, overlay textures) |
| `runGametestNoModernerBeta` | flat, Moderner Beta removed | the same suites, and a check that the world type is skipped cleanly |
| `runGametestModernerBeta` | Moderner Beta, Release 1.6.4 amplified | worldgen checks (debug tunnel on, trial chambers present), carving unchanged |
| `runGametestModernerBetaTunnelOff` | same | the same with the debug tunnel off |
| `runGametestModernerBeta164` | Moderner Beta, Release 1.6.4 | carving unchanged; cave density for comparison |
| `runGametestModernerBetaBeta` | Moderner Beta, Beta 1.7.3 | carving unchanged |
| `runGametestVanilla` | vanilla generator | worldgen checks (debug tunnel on, trial chambers present), carving unchanged |
| `runGametestEternalReturn` | Eternal Return, seed 173164 | only essential structures in every dimension, blazes outside fortresses, mineshaft woods by biome (and real ones built from them), all nine oceans and cherry groves present, no ocean monuments or ocean shrines, no ocean biomes on dry land, overhangs without flat bottoms, world height, biome list, no cave biomes, no deepslate, badlands surface, no trial chambers; ore counts per ore over squares in ten biomes within 1% of the recorded baseline (`src/gametest/resources/ores/`), no stone-textured ore inside host stone, variants open to caves in every host, an emerald survey of the highest extreme hills, Moderner Beta hooks applied; debug tunnel on |
| `runGametestEternalReturnOldCaves` | Eternal Return, new caves off, old caves on, biome stone off | carving matches the world from before the cave engine; cave density for comparison |
| `runGametestCaves` | Eternal Return plus a twin dimension | every cave type, seams, chunk order, the carvable tag, density, timing; biome stone in every biome against the same chunks without it |

"Carving unchanged" hashes 27 chunks generated up to the carving step and compares them with a baseline recorded before the cave engine; the same runs also decorate 27 other chunks (ores, plants, structures): the vanilla world compares them with a baseline recorded before the ore variants, and the Moderner Beta worlds (whose decoration doesn't repeat from run to run) check that none of this mod's blocks appear in them (`src/gametest/resources/fingerprints/`; `./gradlew runAllGametests -PrecordFingerprints` records new ones). Each run writes `build/gametest/<world>/report.xml`. Test worlds enable only the datapacks a normal new world would, so Moderner Beta's optional "Reduced Height" and "Deepslate Blobs" packs stay off.

The map tool runs the same way: `runWorldmapEternalReturn`, `runWorldmapBeta` and `runWorldmapRelease164` each generate one world at seed 173164 and save height, biome, land and side-view images plus `stats.json` to `docs/worldgen-maps/<preset>/`. Add `-PworldmapHalf=1024` for a 2048-block area instead of 4096. `runWorldmapCaves` saves cave slices at four depths (new caves next to the old ones on the same terrain), a gallery of every cave type, and real cave starts to `docs/worldgen-maps/caves/`. `runWorldmapStone` saves a cut through the stone at y=30, coloured by block, plus one checklist spot per host stone, to `docs/worldgen-maps/stone/`. Details in [docs/worldgen.md](docs/worldgen.md).

## Features and where they live

### Enchanting

| Feature | Implementation |
|---|---|
| Max level 2-4 raised to V | `EnchantmentMixin` (level-1 enchants untouched) |
| Enchant capacity per material | `EnchantCaps`, enforced in `EnchantGenerator` and `AnvilScreenHandlerMixin` |
| Curses refund capacity | `EnchantCaps.cost` (-1 point per curse level) |
| No XP for tables or anvils | `EnchantmentScreenHandlerMixin`, `PlayerEntityMixin`, `AnvilScreenHandlerMixin`, client screen mixins |
| Chiseled bookshelf bias | `ShelfBias` (scan) + `EnchantGenerator` (weights and levels) |
| Protections no longer conflict | `data/minecraft/tags/enchantment/exclusive_set/armor.json` emptied |
| Inherent material enchants | `InherentEnchantments` + item tags under `tags/item/inherent/` |
| Mending removed | `mending.json` neutered, removed from loot and trade tags, blocked in the roller |
| Fortune on hoes multiplies crops | `BlockMixin` + `FortuneHelper` |
| Fortune on shears multiplies wool | `fortune.json` + `SheepEntityMixin` + `ShearsDispenserBehaviorMixin` |
| Curse of Invisibility | `invisibility_curse.json`; hiding in `ItemStackMixin`, `ItemMixin`, tooltip code |
| Growth (armor: +1 max health per level) | `growth.json` (vanilla attribute effect) |
| Soulbound (kept through death) | `soulbound.json`, `Soulbound`, `PlayerEntitySurvivalMixin` and `survival/ServerPlayerEntityMixin` |
| Weightless (armor weighs nothing) | `weightless.json`, read by `ArmorWeight` |
| Launch, Reeling, Spread of Ailments, Curse of Ascension | `launch.json`, `reeling.json`, `spread_of_ailments.json`, `ascension_curse.json`; `NewEnchantments`; `mixin/combat`; item tag `eternalreturn:enchantable/ranged` |
| Curses of Visibility, Blindness, Heaviness, Slipperiness and Frailness | `visibility_curse.json` and the rest; `Curses`; `LivingEntityCurseMixin`, `ItemStackFrailMixin`, `ArmorWeight`; item tag `eternalreturn:enchantable/tools` |
| Librarians sell scrolls; scrolls in stronghold and end city chests; scrolls teach enchanted book recipes | `com.eternalreturn.scrolls` (`Scrolls`, `ScrollItem`, `ScrollLoot`); `EnchantBookFactoryMixin`, `ServerPlayNetworkHandlerMixin` (left click), `CraftingScreenHandlerMixin` and `CrafterBlockMixin` (locked recipes); recipes generated by `tools/scrolls/build_scrolls.py` from `tools/scrolls/scrolls.json` |

### Mobs

| Feature | Implementation |
|---|---|
| Baby skeletons, strays, creepers | `AbstractSkeletonEntityMixin`, `CreeperEntityMixin`, `BabyVariants`; creeper render scale in `CreeperEntityRendererMixin` |
| More armor, more often enchanted | `SpawnGear`, hooked in `MobEntitySpawnMixin`; tag `armored_spawns` |
| Creepers only spawn underground | `SpawnRestrictionMixin` |
| Cave spider jockeys, biome-matched riders | `SpiderEntityMixin`, `CaveSpiderEntityMixin`, `Jockeys`; tag `jockey_riders` |
| Strays riding polar bears | `Jockeys.tryPolarBearMount`, `PolarBearEntityMixin` (despawning) |
| Zombies reel targets in with fishing rods | `ReelInTargetGoal`, `ZombieHookEntity`, client `ZombieHookEntityRenderer` |
| Zombies throw ender pearls | `ThrowPearlGoal`, `Ballistics` (aiming) |
| Skeletons draw a sword up close | `SkeletonSidearm`, `AbstractSkeletonEntityMixin` |
| Monsters break out of boats | `MobEntitySpawnMixin` |
| Beehives and TNT on zombies' and skeletons' heads | `HeadGear`, `ZombieEntityMixin`, `AbstractSkeletonEntityMixin`, `LivingEntitySurvivalMixin`; biome tag `beehive_mob_biomes` |
| Zombies break torches (and, with a pickaxe or shovel, other lights and beds) | `BreakLightsGoal`, `ZombieEntityMixin`; block tags `zombie_torches`, `zombie_tool_targets` |
| Wither skeletons in the Nether; skulls always drop | `WitherSkeletons`; loot table `minecraft:entities/wither_skeleton` |
| Endermen only carry cactus, pumpkins and melons | block tag `minecraft:enderman_holdable` (replaced) |
| Large zombies, skeletons and creepers | `LargeMobs` (the scale attribute), `ActiveTargetGoalMixin`, `CreeperEntityAccessor` |
| More zombie villagers; witches in the Nether | `SpawnTweaks` |
| Pillagers in boats at sea | `PillagerBoats`, `BoatEntityPillagerMixin`, `MobEntitySpawnMixin` (they keep their boat) |
| Undead servants (the Necronomicon) | `Necronomicon`, `Servants`, `MobEntityServantMixin`, `MobEntityAccessor`, `LivingEntitySurvivalMixin` (no drops) |
| Spiders fling webs | `SpiderWebGoal`, `WebEntity` (`eternalreturn:web`), `SpiderGoalsMixin` |
| Mining zombies; zombies hunt animals and eat raw meat | `ZombieHabits`, `MineTowardTargetGoal`, `ZombieEatMeatGoal`, `ZombieEntityMixin`; tags `zombie_prey` (entity), `zombie_meat` (item) |
| Baby zombies on cows, pigs and sheep; skeletons on phantoms and slimes | `ZombieHabits`, `ZombieEntityMixin`, `PhantomEntityMixin`, `SlimeEntityMixin`, `AbstractSkeletonEntityMixin` (doesn't steer) |
| Baby fish and squids | `BabyVariants`, `WaterCreatureEntityMixin`; client `LivingEntityRendererMixin`; tag `baby_variants` |
| Bigger slimes; slimes in the rain at night | `Slimes`, `SlimeEntityMixin`, `SpawnRestrictionMixin` |
| Blazes outside fortresses (Eternal Return worlds) | `Blazes`, `SpawnRestrictionMixin` |
| Drowned drop prismarine | loot table `eternalreturn:entities/drowned_prismarine`, added to the drowned's by `FishingLoot` |
| Guardians and elder guardians in every ocean | `OceanSpawns` (biome tag `ocean_waters`); `SpawnRestrictionMixin` (elder guardian limits), `MobEntitySpawnMixin` and `MobEntityAccessor` (they despawn) |

### Worldgen

The plan, status, terrain settings and maps are in [docs/worldgen.md](docs/worldgen.md). Code lives in `com.eternalreturn.worldgen`; the world type itself is data only.

| Feature | Implementation |
|---|---|
| Eternal Return world type (needs Moderner Beta) | `data/eternalreturn/worldgen/world_preset/eternal_return.json`, settings preset `data/eternalreturn/moderner_beta/settings_preset/eternal_return.json`, noise settings `data/eternalreturn/worldgen/noise_settings/eternal_return.json`; all generated by `tools/worldgen/build_preset.py` from `tools/worldgen/knobs.json` |
| Biome list: Release 1.6.4 plus birch forest, savanna, badlands, dark forest, cherry grove and every modern ocean | biome layers in the settings preset |
| No lush caves, dripstone caves or deep dark | settings preset (`caveBiomeSettings`: none) |
| No deepslate | settings preset (`deepslate_generation` off); tuff moved from block tag `minecraft:deepslate_ore_replaceables` to `minecraft:stone_ore_replaceables`, so ores in tuff blobs stay stone ores |
| Only strongholds, mineshafts, nether fossils and end cities in Eternal Return worlds (all three dimensions) | `EternalReturnServer`, `MinecraftServerMixin`, `StructurePlacementCalculatorMixin` |
| No trial chambers, ocean monuments or ocean shrines in Eternal Return worlds | settings preset (`structure_modifiers`: trial chambers and monuments removed, Moderner Beta's ocean shrine override dropped) |
| Cave engine: eight cave types, only in Eternal Return worlds | `com.eternalreturn.worldgen.caves` (`CaveSystemCarver`, `CaveTypes`, one class per type in `types/`); `worldgen/configured_carver/caves.json`; block tag `eternalreturn:carvable` |
| Old caves off in Eternal Return worlds | `CarverContextMixin` (which world), `ConfiguredCarverMixin` (skips other carvers) |
| Biome stone: each biome's stone from under the topsoil to bedrock, only in Eternal Return worlds | table `biome_stone` in `tools/worldgen/knobs.json`, written into the noise settings' surface rules by `build_preset.py`; `BiomeStoneCondition` (the on/off switch) |
| Hardened sandstone and hardened packed ice | `com.eternalreturn.worldgen.stone.HardenedBlocks` (one entry per block); client models `HardenedBlockModels`; block tag `eternalreturn:hardened_stones` |
| Ore variants: every ore in every host stone | table `src/main/resources/eternalreturn/ore_variants.json` (read at startup by `com.eternalreturn.worldgen.ores.OreVariants`); models, textures, loot tables, recipes and tags generated by `tools/ores/build_ores.py`; placed by `OrePlacement` through `OreFeatureMixin` and `ScatteredOreFeatureMixin`, Eternal Return worlds only; client `OreVariantRendering` |
| Cherry groves and every modern ocean (deep, warm, lukewarm, cold, frozen) | biome layers in the settings preset (`build_preset.py`; the ocean steps come from Moderner Beta's Release 1.17.1 preset) |
| Mineshafts built from their biome's wood; mineshafts in Moderner Beta's biomes | `com.eternalreturn.worldgen.structures.MineshaftWoods`; `MineshaftStructureMixin`, `MineshaftPartMixin`, `MineshaftPiecesMixin`, `StructureMixin`; biome tags `eternalreturn:mineshaft_wood/<wood>`, `eternalreturn:has_structure/mineshaft` |
| Twice as many dungeons, in cobblestone, stone brick and brick; Nether dungeons; wild nether wart (Eternal Return worlds) | `Dungeons`, `DungeonFeatureMixin`, `ChunkGeneratorExtraMixin` |
| A third of strongholds at ground level; illagers and an illusioner in strongholds (Eternal Return worlds) | `Strongholds`, `StrongholdStructureMixin`, `StructureWeightSamplerMixin`, `StructureStartMixin` |
| Lava lakes keep host stone instead of lining it with stone | `LakeFeatureMixin`, Eternal Return worlds with biome stone only |
| Springs and glow lichen on the hardened blocks | `data/minecraft/worldgen/configured_feature/` (`spring_water`, `spring_lava_overworld`, `glow_lichen`) |
| Host stones as stone materials | item tags `minecraft:stone_tool_materials`, `minecraft:stone_crafting_materials`; recipes `dispenser`, `dropper`, `lever`, `observer`, `piston` in `data/minecraft/recipe/` |
| Full -64 to 320 height | noise settings |
| Debug tunnel (off by default) | `DebugTunnelCarver`, `worldgen/configured_carver/debug_tunnel.json`, attached to overworld biomes in `WorldgenFeatures` |
| World map tool (dev only) | `WorldmapTests`, `CaveMapTests`, `StoneMapTests` in `src/gametest` |

### Fishing and villagers

| Feature | Implementation |
|---|---|
| Fish only bite with bait | `Bait`, `BaitLootCondition`, `FishingBobberEntityMixin`; `minecraft:gameplay/fishing` loot table replaced |
| Slayer (rod: more monsters on the hook) | `slayer.json` (effect `eternalreturn:hostile_catch_chance`), `Slayer`, used by `HostileCatches` |
| Thriftiness (rod keeps its bait) | `thriftiness.json` (effect `eternalreturn:bait_saving_chance`), `Thriftiness`, `Bait.consumeAfterCatch` |
| 53 fish, each with its rarity, habitat, time, weather, moon and price | table `eternalreturn/fish.json`, read by `FishTable` and `FishingItems`; resources (textures, names, tooltips, habitat biome tags `fish_habitat/*`, loot tables `eternalreturn:gameplay/fishing/fish` and `.../lava_fishing/fish`, `docs/fish.md`) generated by `tools/fish/build_fish.py`; loot conditions `MoonPhaseLootCondition`, `DeepWaterLootCondition` |
| Chum | recipe `data/eternalreturn/recipe/chum.json` |
| Lava fishing in the Nether | `FishingBobberEntityMixin`, `FishingLoot`; loot table `eternalreturn:gameplay/lava_fishing` |
| Prismarine shards and crystals from fishing; rarely prismarine, dark prismarine and sea lanterns | loot tables `eternalreturn:gameplay/fishing/prismarine`, `.../prismarine_blocks` |
| Monsters on the hook: drowned, guardians, elder guardians, drowned riding them | `HostileCatches`, hooked in `FishingBobberEntityMixin`; `DrownedEntityMixin` (the guardian swims, the drowned throws) |
| Overfished spots bite slower | `Overfishing`, hooked in `FishingBobberEntityMixin` |
| Villagers stop paying emeralds, except fishermen for fish | `TradeRules`, `VillagerEntityMixin`, `BuyItemFactoryAccessor` |
| Fishermen buy every fish of their region from the level of its rarity, and sell bait | `FishermanTrades`, `VillagerEntityMixin` |
| Wandering traders: only near villages, nothing the world can't give, always asylum papers | `WanderingTraders`, `WanderingTraderEntityMixin`, `WanderingTraderManagerMixin`; item tag `not_from_wandering_traders`; `AsylumPapers` |
| Villagers in Moderner Beta's biomes get the right region (snow, taiga, swamp...) | `VillagerRegions` |
| Villager needs, shop hours, healing with food | `VillagerNeeds`; `VillagerEntityMixin` (ticking, saving, gossip, trading, prices), `SleepTaskMixin` (privacy); messages in `lang/en_us.json` |

### Survival and blocks

| Feature | Implementation |
|---|---|
| Beds don't skip the night; the last 3 beds are remembered for respawning | `BedMemory`; `SleepManagerMixin`, `ServerWorldSleepMixin`, `survival/ServerPlayerEntityMixin` |
| Beacons keep monsters out and let animals spawn; animals spawn nowhere else | `BeaconZones`; `BeaconBlockEntityMixin`, `SpawnRestrictionMixin`, `PhantomSpawnerMixin`, `PatrolSpawnerMixin` |
| Animals get hungry, graze, take food by hand, starve | `AnimalHunger`; `AnimalEntityMixin`, `SheepGrazingMixin`, `HorseFoodMixin`, `MobEntitySurvivalMixin`; tags `grazers` (entity), `grazing_plants` (block) |
| Herds panic together | `HerdPanic`, `LivingEntitySurvivalMixin`; entity tag `herd_animals` |
| Blindness cuts a mob's follow range | `BlindMobs`, `LivingEntitySurvivalMixin` |
| Glowing creatures are seen through walls | `LivingEntitySurvivalMixin` |
| No hunger; food heals, each food with its own stack size, eating time, healing and effects | table `src/main/resources/eternalreturn/foods.json`, read by `Meals`; `HungerManagerMixin`, `PlayerEntityMealsMixin`, `CakeBlockMixin`, `SaturationEffectMixin`, `HoneyBottleMixin`, `LivingEntitySurvivalMixin`; client `HungerBarMixin`, tooltip in `EternalReturnClient` |
| Drinks take a second, hits stop eating, raw food poisons | `Food`, `DrinkTimeMixin`, `LivingEntitySurvivalMixin`; item tag `raw_foods` |
| Cut vines, petals on water, pig litters, stunted babies | `Farming`, `VineBlockMixin`, `PlantBlockMixin`, `PinkPetalsItemMixin`, `PassiveEntityMixin`, `survival/AnimalEntityMixin` |
| Dispensers place blocks; obsidian pressure plate; pistons move chests; chains link blocks | `com.eternalreturn.blocks.Redstone`, `ChainLinks`; `mixin/redstone`; client `MovingBlockEntities`, `PistonBlockEntityRendererMixin` (moving chests stay drawn) |
| Glass item frames; dyed item frames | `com.eternalreturn.blocks.Frames`; `mixin/frames`; client `FrameModels`, `ItemFrameEntityRendererMixin`; item tag `minecraft:dyeable`; vanilla frame models with a tintable back panel (`assets/minecraft/models/block/template_item_frame*.json`) and two-layer item icons |
| Armor weight | `ArmorWeight`, `PlayerEntitySurvivalMixin`; tooltip in the client |
| Snow falls | `SnowFall`, `SnowBlockMixin`, `AbstractBlockSnowMixin` |
| Crops grow fastest in their ideal biomes | `CropBiomes`, `CropGrowthMixin`; biome tags `eternalreturn:ideal_biomes/<crop>` |
| Spawner mobs drop no items | `SpawnerMobs`; `MobSpawnerLogicMixin`, `MobEntitySurvivalMixin`, `LivingEntitySurvivalMixin` |
| Philosopher's stone | `com.eternalreturn.items.PhilosophersStone`, `CreeperEntityAccessor`; recipe `philosophers_stone.json` |
| Necronomicon | `com.eternalreturn.items.Necronomicon`; recipe `necronomicon.json` |
| Dynamite | `com.eternalreturn.items.Dynamite` (entity `eternalreturn:dynamite`), recipe `dynamite.json` |
| Faster minecarts; copper rails | `MinecartSpeedMixin`; `com.eternalreturn.blocks.CopperRail` |
| Netherite gear crafted from ingots, not upgraded | recipes `data/eternalreturn/recipe/netherite_*.json`; vanilla's smithing upgrades switched off (`data/minecraft/recipe/netherite_*_smithing.json`) |
| Wool and concrete stairs and slabs | `com.eternalreturn.blocks.CutBlocks`; resources generated by `tools/blocks/build_blocks.py` |
| Carpets stack up to 16 layers | `CarpetLayers`, `CarpetBlockMixin`; vanilla carpet blockstates and loot tables replaced by `tools/blocks/build_blocks.py` |

## How the systems behave

### World type

Pick **World Type: Eternal Return** on the create-world screen (it is also in Moderner Beta's own preset list, in its own category). Oceans and continents are laid out like Release 1.6.4, but the land rolls like Beta 1.7.3: mostly gently rolling ground around y 65-85, hills to about y 105 and mountains, mostly in the extreme hills, up to about y 170. Sheer cliffs and overhangs turn up about as often as in Beta, and an overhang's underside arches and bulges rather than lying flat (see [docs/worldgen.md](docs/worldgen.md)). Swamps stay flat and marshy, as in 1.6.4. Sea level is 63, bedrock is at -64, and below y=0 the stone simply carries on (the biome's stone, see below) with the usual ores: no deepslate layer and no lush caves, dripstone caves or deep dark. Biomes are Release 1.6.4's (with its hills and shores) plus birch forest, savanna, badlands, dark forest and cherry grove, and every modern ocean: deep oceans, and warm, lukewarm, cold and frozen oceans (with deep versions), as in 1.13 and later. The only structures are strongholds, mineshafts, nether fossils and end cities, in the overworld, the Nether and the End alike: no villages, temples, outposts, ruins, shipwrecks, monuments, mansions, ancient cities, trail ruins, igloos, ruined portals, trial chambers, nether fortresses or bastions. Structure-like features (dungeons, geodes, fossils, desert wells) stay. So there are no villagers except cured zombie villagers; guardians roam the open sea and blazes the soul sand valleys and basalt deltas instead (see Mobs). The Nether and the End are vanilla.

**Caves.** Eight types, a little more cave in all than Release 1.6.4 (5.3% of the underground open, against 4.5%):

- spaghetti: Release 1.6.4's caves, same sizes and forks: clusters of winding tunnels that swell in the middle and split in two, with round rooms;
- ravines: tall, narrow canyons with jagged walls, some large, some open to the sky;
- large caverns: rare big chambers with uneven walls, rolling floors and tunnels leading out, most 20 to 35 blocks in radius and a few far bigger (up to 80; one of radius 76 is about 190 blocks across);
- vertical shafts (2.5 to 4.5 in radius) and steep tunnels joining one level to the next;
- zig-zag tunnels of straight segments with sharp turns;
- ribbed tunnels that bulge and pinch every few blocks;
- spirals: a tunnel winding two to four turns down 30 to 70 blocks around a solid column (rare);
- toroidal rooms: donut-shaped rooms around a pillar of rock, usually lying flat, sometimes tilted (rare).

**Biome stone.** Under the topsoil, all the way down to bedrock, the stone matches the biome above it: andesite under forests, taiga and extreme hills; diorite under birch forests; granite under jungles and savannas; hardened sandstone under deserts; red sandstone under the badlands' terracotta bands; hardened packed ice under ice plains, frozen oceans and frozen rivers. Plains, oceans, rivers, beaches, mushroom islands, swamps and dark forests keep plain stone. Borders are sharp, with no blending. Hardened sandstone and hardened packed ice look like sandstone and packed ice but mine like stone (pickaxe needed, same hardness, no melting or sliding) and drop plain sandstone or packed ice, Silk Touch or not; you can only get the hardened blocks themselves from the creative inventory. Since deserts and ice plains drop no cobblestone, sandstone, red sandstone, granite, diorite and andesite all work for stone tools, furnaces, brewing stands, dispensers, droppers, levers, observers and pistons (in every world).

**Ore variants.** Every ore has its own version in each host stone: granite iron ore, hardened sandstone gold ore, andesite emerald ore and so on (6 host stones x 8 ores, 48 blocks). They mine, drop, smelt and give experience exactly like the vanilla ore (raw iron from granite iron ore, Fortune works the same), Silk Touch gives the variant itself, and they are in the Natural Blocks tab after each ore's deepslate version. Plain stone and tuff blobs keep vanilla's ores. They look like the old, pre-1.14 ores (coal, iron, copper, gold, redstone and diamond share one blob shape; lapis and emerald have their own), drawn over the host stone's texture. Emerald comes from Moderner Beta's own feature, only in high ground (y 95 and up) in extreme hills, plains, swamps and ice plains, so it is rare; badlands still have their extra gold. With an old-style resource pack such as Golden Days, the host stone under each ore takes the pack's texture; the ore pattern is this mod's own and stays the same with the pack on or off.

**Mineshafts.** Built from the wood of the biome they start in: birch in birch forests, spruce in taiga, jungle wood in jungles, acacia in savannas, dark oak in dark forests, cherry in cherry groves, and oak everywhere else (badlands too). They also generate under Moderner Beta's extreme hills, ice plains, swamps and plains, which vanilla's mineshaft list left out. Eternal Return worlds only; elsewhere mineshafts are vanilla's.

Caves stay out of oceans and rivers, and turn to lava at y -56 and below. Vanilla's and Moderner Beta's own caves and ravines no longer generate in Eternal Return worlds; `worldgen.caves.oldCaves` brings them back. Other world types keep their normal caves.

Leave Moderner Beta's "Reduced Height" datapack off for this world type: it moves the floor of every world to y=0.

### Enchanting

**Capacity.** Points used = sum of enchantment levels, minus inherent levels, minus curse levels. Iron 6, gold 10, diamond 7, netherite 4, chainmail 5, bows and crossbows 6, fishing rods 7. Anything not in a cap tag (wood, stone, leather, modded copper, tridents, shears, maces...) is uncapped. The tooltip shows `Enchant capacity: used / cap`.

- The enchanting table, villager gear, mob gear and `enchant_with_levels` loot all roll inside the remaining capacity. If an enchantment would overflow, its level is trimmed to fit rather than dropped.
- The anvil refuses any result that adds points past the cap and shows `Over capacity: X / Y` in place of the old cost label. Renaming, repairing, and adding curses always work, even on an item that is already over its cap.

**Inherent enchantments** are real enchantments stored on the item, so vanilla damage, loot, and tooltips all read them normally. They act as a free baseline: on an iron sword, Unbreaking III costs 2 points, not 3. They are added when an item enters a player's inventory, when a mob equips it, and on smithing upgrades. Gear that only has its inherent enchantments does not glint, can still go in an enchanting table, and keeps them through the grindstone.

- Diamond to netherite: the diamond's free Efficiency I is removed before netherite's free Unbreaking V is added, so you keep exactly the levels you paid for.
- Golden and diamond swords get nothing, since Fortune and Efficiency do nothing on a sword. Add them to the tags if you want it anyway.
- Golden tools' free Fortune I means they can never take Silk Touch (vanilla makes the two exclusive).

**Bookshelf bias.** Chiseled bookshelves are read from the same ring of positions as regular bookshelves, with the same "air between" rule. They add no enchanting power, so a table needs regular shelves for power and chiseled ones for steering. The ring has 32 spots, so 15 regular shelves still leave room for 17 chiseled ones. For each enchantment found on shelved books:

- its weight is multiplied by `1 + 1.0 x (total book levels)`, capped at 16x
- it is rolled with `+3 power x (total book levels)`, capped at +30, which lets it reach higher levels than the table normally allows
- it can never exceed the highest-level book of it on the shelves

A single Sharpness V book turns the bottom slot's typical Sharpness I into III and makes Sharpness V reachable in the top slot. Books are not consumed. The same bias applies when enchanting plain books, so a library can grow itself.

**XP.** Tables still cost lapis (1/2/3) but no levels, and the option buttons never grey out for level. The table still rerolls its options after each use. Anvils have no cost and no "Too Expensive!" limit.

**Max level V.** Applies to every enchantment whose max was 2 to 4, modded ones included. Two had to be handled specially:

- Quick Charge V would make crossbows charge in 0 ticks under vanilla scaling, so it was re-curved: I-III unchanged, IV = 0.35 s, V = 0.25 s.
- Depth Strider already hits the water movement ceiling at III, so IV and V add nothing.

**Fortune.** Hoes with Fortune apply the ore formula (multiply by up to Fortune + 1) to fully grown wheat, beetroot, and cocoa. Carrots, potatoes, and nether wart already scale with Fortune in vanilla, so they are left out to avoid double scaling; add them to `fortune_crop_drops` if you want it. Shears accept Fortune through the anvil and apply the ore formula to wool, from players and dispensers.

**Curse of Invisibility.** Hides the enchantment list, the glint, and the capacity line. The book itself still shows the curse so you know what you are applying. Found in loot and trades like the other curses.

**Scrolls.** Librarians no longer sell enchanted books: where they would, they sell a scroll of that enchantment (a level I book's price in emeralds, plus a piece of paper). Left-click with a scroll in your main hand to read it: it's used up and you learn the recipe for that enchantment's book. The recipe makes a level I book from a book in the middle, ringed by diamonds, amethyst shards and one to three items that suit the enchantment (a blaze rod for Fire Aspect, blue ice for Frost Walker, three arrows for Multishot). Higher levels come from combining books in the anvil, as before. Book recipes only craft for a player who has learned them (crafters never make them), and a scroll you have already read stays in your hand. Every enchantment has one, this mod's included, except the disabled ones (Mending). The table of themed items is `tools/scrolls/scrolls.json`. Scrolls are also rare chest loot: about one stronghold library chest in five holds a scroll of a random enchantment, one corridor or crossing chest in twenty, and one end city chest in ten (loot table `eternalreturn:chests/scroll`, loot function `eternalreturn:random_scroll`).

**Soulbound.** A new enchantment for anything enchantable, one level (it can't share an item with Curse of Vanishing): the item stays with you when you die and is back in the same slot when you respawn.

**Weightless.** A new armor enchantment, one level: the piece weighs nothing (see Armor weight).

**Launch.** A new melee weapon enchantment (swords, axes and the mace), I to V: a hit throws the target straight up instead of back, higher each level (about a block and a half at I, 6 blocks at V). It can't share a weapon with Knockback. Mobs holding a Launch weapon use it too.

**Reeling.** A new bow and crossbow enchantment, I to V: an arrow that hits a mob or player pulls it toward the shooter with a short hop, a fifth of the way per level, so at V it lands about a block and a half from you. It can't share a bow with Punch.

**Spread of Ailments.** A new bow and crossbow enchantment, one level: every arrow carries the effects the shooter has at that moment, good or bad, with the time they have left, and gives them to whatever it hits, as a tipped arrow would. Skeletons with such a bow pass theirs on too.

**Curse of Ascension.** A new curse for anything enchantable: dropped, the item floats up into the sky, about a block every two seconds, until it despawns or someone grabs it. Found like other curses (loot, trades), never from the table.

All four have scrolls and book recipes like the other enchantments.

**Five more curses**, found like Curse of Ascension (loot and trades, never the table), each with its scroll:
- Curse of Visibility (chestplates): whoever wears it glows, for as long as they wear it, and glowing shows through walls.
- Curse of Blindness (helmets): whoever wears it is blind while they wear it.
- Curse of Heaviness (any armor): the piece weighs twice as much. It can't share a piece with Weightless.
- Curse of Slipperiness (tools and weapons): each time you use it (a hit, starting to dig, using it on a block or in the air) there's a 0.5% chance it slips out of your hand onto the ground.
- Curse of Frailness (anything with durability): whenever it takes damage, half the time it takes twice as much. It can't share an item with Unbreaking.

**Growth.** A new armor enchantment, levels I to V: each level adds half a heart (1 point) of max health, on each piece that has it, so Growth V on all four pieces is ten extra hearts. Enchanting tables, loot and librarians offer it like other armor enchantments, and its levels count toward the piece's capacity.

### Mobs

**Babies.** 5% of skeletons, strays and creepers spawn as babies: half size and 50% faster, otherwise the same as adults (same health, drops and XP). Skeletons and strays get the baby-zombie body proportions, and baby skeletons that freeze become baby strays. Baby creepers have half the fuse (0.75 s instead of 1.5 s) and about a third of the blast power: a zombie two blocks away takes a scratch where an adult's blast kills it. Saved as `IsBaby`, the same key baby zombies use.

**Armor.** Zombies, husks, zombie villagers, skeletons, strays and bogged get armor 25% of the time in a newly visited area, rising to 75% in long-inhabited ones (vanilla: 0% rising to 15%, so early-game mobs never had any). Each piece is enchanted 20% rising to 80% of the time (vanilla: 0% to 50%). Which pieces and materials they get is unchanged, and enchantments still roll inside each item's capacity.

**Cave-only creepers.** A natural creeper spawn needs a spot that no sky light reaches, so creepers never spawn on the surface but still spawn in caves (and in dark, fully enclosed builds). Spawners, spawn eggs and commands are unaffected. Vanilla does not replace a failed creeper spawn with another mob, so nights on the surface have roughly a fifth fewer hostile spawns overall.

**Jockeys.** Spiders keep vanilla's 1% jockey chance, and cave spiders (which vanilla never gives riders) get one 2% of the time, mostly from mineshaft spawners. The rider is picked from the biome's own spawn list: strays in snowy biomes, husks in deserts, skeletons elsewhere. A husk rider fights in melee from the spider's back. 5% of strays spawn riding a polar bear, which the stray steers. The bear despawns like a hostile mob, the way a chicken jockey's chicken does, so mounts don't pile up in snowy biomes.

**Fishing-rod zombies.** 5% of zombies spawn holding a fishing rod. When their target is 4-16 blocks away and in sight, they cast. A moment after the hook catches, they yank the target toward themselves, then recast every 3-5 seconds. Drowned that spawn with vanilla's decorative fishing rod use it too.

**Ender-pearl zombies.** 1% of zombies carry an ender pearl in their off hand. When their target is 12-40 blocks away and in sight, they throw it at the target and teleport there. It is a single use.

**Skeleton swords.** 10% of skeletons, strays and bogged carry a stone or iron sword in their off hand. When the target comes within 4 blocks they swap it into their main hand and fight in melee. Once the target is 7 blocks away they switch back to the bow.

**Guardians.** Ocean monuments are gone from Eternal Return worlds, so guardians live in the open sea instead. Every ocean biome, in any world type (vanilla's oceans and Moderner Beta's), spawns them now and then as monsters, day and night, and very rarely an elder guardian. As in vanilla, they spawn far more readily where the sky is hidden (under ice or overhangs) than under open sky, where only one try in 20 passes. An elder guardian spawn also needs a one-in-four roll and no other elder guardian within 96 blocks. Ones that spawn this way despawn like other monsters (the ones in monuments, in other worlds, stay put), but while one is near, it gives Mining Fatigue to players within 50 blocks as usual. Guardians are now where prismarine, sponges (from elder guardians) and fish come from in the sea, along with fishing.

**Boats.** Any monster stuck in a boat starts hitting it after a second and breaks it a moment later, dropping the boat. This includes zombie villagers, so trapping one in a boat to cure it no longer works. Use a cell instead.

### Fishing

**Bait.** Fish only bite if you carry bait. Without it you still reel in junk and treasure. The rod uses the first bait it finds in your off hand, then main hand, then inventory (the order a bow looks for arrows), so put the bait you want in your off hand. One bait is used each time you reel something in, never when you cast, and creative players don't use any. Every bait shortens the wait for a bite, stacking with Lure. However much the two add up to, the wait never drops below 1 second. Without that floor, vanilla stops fish biting entirely once the reduction passes 30 seconds, which Lure V with any bait reaches.

| Bait | Wait shortened by | Where it comes from | Special |
|---|---|---|---|
| Chum | 10 s | 1 raw fish + 1 bone meal = 4 chum; fishermen sell 16 for an emerald | Best all-round bait |
| Glistering melon slice | 5 s | vanilla recipe; fishermen sell 4 for an emerald | Doubles the odds of rare, epic and legendary fish |
| Magma cream | 5 s | slimes, magma cubes; fishermen sell 4 for an emerald | The only bait that works in lava |

**Fish.** 53 fish, each with its own rarity (common, uncommon, rare, epic, legendary), habitat, and the conditions it bites under: time of day, weather, moon phase, water depth, height, or under a roof underground. 45 come from water; 8 (bonefish, eyeball fish, glowdine, magma fish, obsidian fish, searing cod, soulsucker and wither bonefish) from lava in the Nether, with magma cream. All of them are eaten raw, fill like raw salmon, and can't be cooked. Nether fish are fireproof. Each fish's tooltip gives its rarity, where and when it bites, and which fishermen buy it; its name is coloured by rarity. Vanilla cod, salmon, tropical fish and pufferfish still bite wherever fish do. The full list, with every fish's habitat, conditions and price, is in [docs/fish.md](docs/fish.md).

A fish's odds come from its rarity, among whatever else bites in that spot at that moment, so a legendary fish might come up once in about a hundred catches where it lives, and the same spot gives different fish by day and night or in rain. Some examples: the bluegill (common, plains and forests or rivers, by day), the european eel (rare, rivers or swamps, on rainy nights), the opah (epic, oceans, under a full moon), the oarfish (legendary, oceans over 12 or more blocks of water, in thunderstorms), the echo fish (legendary, underground water below y 0, under a new moon), the wither bonefish (legendary, lava in soul sand valleys or basalt deltas).

**Fishermen.** Every fisherman buys fish, and sells chum, glistering melon slices and magma cream for an emerald, from novice on. He buys every fish of a rarity at once when he reaches its level (common at novice, uncommon at apprentice, rare at journeyman, epic at expert, legendary at master), not a random two, at the price in the fish table: from 8 common fish for an emerald to 12 emeralds for a legendary one. Which fish depends on his region, the biome he was born or cured in, which also sets his clothes: he buys the freshwater fish of his own region only (plains: plains, forests, rivers and beaches; desert and savanna: dry lands; snow: snowy lands; taiga: taigas and mountains; jungle; swamp), and every fisherman buys fish from the sea, cherry groves, underground water and the Nether. A plains fisherman buys 41 of the fish, the others 31 to 35. Vanilla's own fish trades (cod, salmon, tropical fish, pufferfish) still turn up now and then, at double the emeralds. Fishermen from before this get the new trades the next time you open their trade screen, and trades for fish that no longer exist are dropped.

Villagers in Moderner Beta's biomes, which Eternal Return worlds use for taigas, ice plains, swamps and extreme hills, now get the region vanilla gives its own biome of that kind (ice plains snow, taiga and extreme hills taiga, swampland swamp) instead of plains.

**Slayer.** A new fishing rod enchantment, levels I to V: each level adds 5% to the chance that a bite is a monster (3% without it; Slayer V makes it 28%). It turns up like Thriftiness and also counts toward the rod's capacity.

**Thriftiness.** A new fishing rod enchantment, levels I to V. Each catch has a 10% chance per level (50% at V) to leave your bait unused, in water and lava alike. It turns up in enchanting tables, loot, and librarian trades like any other rod enchantment, and its levels count toward the rod's 7-point capacity, so Thriftiness V and Lure V can't share a rod.

**Prismarine.** Prismarine shards (1-3) and crystals (1-2) can come up anywhere you fish in water, bait or not: about 12% of catches without bait, 2% with (Luck of the Sea makes them a little more likely). With ocean monuments gone from Eternal Return worlds, fishing and guardians are where prismarine comes from.

**Monsters on the line.** 3% of bites in water are a monster instead of items (never on Peaceful). It comes up at the hook with a splash and goes for you, and it uses up your bait like any catch. Anywhere: a drowned, yanked out of the water toward you. In ocean biomes only: a guardian, a drowned with a trident riding a guardian, an elder guardian (very rare), or a drowned with a trident riding an elder guardian (rarest). The mount swims by itself while the drowned throws its trident. Out of 100 monster catches at sea, about 64 are drowned, 21 guardians, 11 drowned guardian jockeys, 3 elder guardians and 1 drowned elder guardian jockey; elsewhere they are all drowned. Elder guardians caught this way despawn like other monsters. If there's no room for the monster at the hook (a big elder guardian in a narrow pool, say), the catch is ordinary items instead.

**Overfishing.** Fishing the same spot over and over makes the fish shy. Each catch counts against the spot it came from (anywhere within 6 blocks of it) and fades over 5 minutes. The first 3 catches' worth are free; each one over that makes the wait for a bite there half as long again, up to 4 times as long. Six catches in quick succession make bites take about 2.5 times as long. Move along the shore or come back later and the fish bite as usual. Lure, bait and the roof rule still apply first; the slowdown multiplies what is left. This applies to lava fishing too. The game remembers recent catches only while it runs.

**Lava fishing.** In the Nether, a hook cast into lava floats and gets bites like it does in water, with lava pops and flames instead of bubbles and splashes. A roof normally slows bites, but the Nether's ceiling doesn't. Catches are protected so they don't burn on the way to you. Without magma cream you only get junk (magma blocks, soul sand, bones, blackstone, basalt, rotten flesh) and treasure. Treasure, from most to least common: gold nuggets and ingots, glowstone dust, quartz, crying obsidian, enchanted books, blaze rods, ghast tears, netherite scrap. Luck of the Sea and Lure work as usual. Outside the Nether, hooks sink in lava as in vanilla unless `lavaFishingOutsideNether` is on.

**Wandering traders** only come to a player near a village: at least 3 villagers within 64 blocks. In Eternal Return worlds, which have no villages, that means villagers you have cured, bred or brought with asylum papers. They never sell what an Eternal Return world can't give (allium, moss blocks, small dripleaf, pointed dripstone, rooted dirt, mangrove propagules), picking other trades instead, and they always sell asylum papers.

**Villagers.** When a villager unlocks new trades, any trade where it would pay you emeralds is left out of the pick, except fishermen buying raw fish. This also applies with the experimental Villager Trade Rebalance on. Villagers still get two trades per level where enough are left, but levels that only ever offered buying unlock nothing (the butcher from journeyman up, for example). Villagers that already rolled their trades keep them, so test with new ones.

**Villager needs.** Every villager has five needs, and remembers when each was last met:

| Need | Met when | What it takes |
|---|---|---|
| Sleep | it sleeps in its bed | Privacy: a villager won't lie down while it can see more than one other adult villager, so it needs a house (two to a room is fine, a dormitory isn't) |
| Gossip | it gossips with another villager | A gathering place where villagers meet (a bell does it); two villagers share gossip at most once a minute |
| Exercise | it walks 8 blocks in one go | Room to roam: the 8 blocks are a straight line from where the walk began, so pacing around a small pen doesn't count |
| Sunlight | it sees the sky in the daytime | Getting outside: not kept underground or under a roof all day |
| Work-life balance | its workstation is at least 8 blocks from its bed (checked every second) | Work and home apart: a workstation right next to the bed breaks it for as long as it stays there |

A need missed for 2 days adds 5 emeralds to every trade it sells for emeralds, per need (two missed needs: +10). A need missed for 7 days and the villager won't trade at all. Trades where the villager pays you (fishermen buying fish) don't change. When you try to trade, the villager tells you in chat about every need it has missed for a day or more, what it takes, and whether prices are up or it refuses; it shakes its head when it refuses. Days are the day clock's, so sleeping through a night counts as a night passing.

Villagers that existed before this update, and babies until they grow up, start with every need met, so nothing changes for 2 days. Wandering traders have no needs.

**Shop hours.** Villagers don't trade at night (from dusk, 13000 on the day clock, to dawn, 23000): "Sorry, we're closed for the night."

**Healing.** A hurt villager with food in its inventory (bread, carrots, potatoes or beetroot, which farmers gather and share) now and then eats one, about one chance in four every 2 seconds, healing by the food's value (bread 5, a carrot 3, a potato or beetroot 1).

### Survival

**Beds.** Sleeping never skips the night, however many players sleep: a bed only sets your respawn point (by day too, as in vanilla), and you get up when you like (morning wakes you as usual). Lying down still resets the phantom timer. Each player remembers the last 3 beds that set their spawn point. If you die and your bed is gone (or blocked), you respawn at the bed before it, then the one before that; only if none of the three works do you go to world spawn. Respawn anchors and spawn points set by command are left alone.

**Beacons.** A working beacon (a pyramid and a clear beam) covers the same area its effects reach: 20, 30, 40 or 50 blocks each way for a 1 to 4 tier pyramid, from that far below the beacon up to the sky. No monster spawns naturally inside it, and no phantoms or pillager patrols come there. Spawners still work.

**Animals.** Animals (cows, pigs, sheep, chickens, horses, rabbits, wolves, foxes and the rest of the creature spawn group) no longer spawn naturally once the land has been generated, except inside a beacon's area. New land still comes with its animals, and breeding works everywhere: once a herd is gone, it's gone.

**Hunger.** Farm animals (cows, mooshrooms, sheep, pigs, chickens, rabbits, goats, horses, donkeys, mules, llamas and camels; entity tag `eternalreturn:grazers`) remember when they last ate. Half a day after a meal they graze when they can: a plant where they stand (grass, ferns, flowers) or the grass block under them, which turns to dirt, as sheep already do (with mobGriefing off they eat without changing the block). Feeding one its breeding food counts as a meal, and a hungry animal takes it even when it wouldn't for breeding (already in love, resting after breeding, or a horse at full health). After 3 days without food an animal loses a point of health every 30 seconds until it eats or dies. So a pen needs grass, or someone to feed it. Animals that existed before this update start fed.

**Herd panic.** Hurt a cow and every cow within 16 blocks runs, as if you had hit it too. The same goes for pigs, sheep, chickens, rabbits, horses, donkeys and mules (entity tag `eternalreturn:herd_animals`), each with its own kind.

**Blindness.** A blinded mob only notices and follows a target within 2 blocks (vanilla ignores blindness on mobs). Its follow range also limits how far it plans a path, so it shuffles about.

**Armor weight.** Armor slows you down. A full set of diamond takes 30% off your walking speed, as much as Slowness II; iron and netherite 15% (Slowness I); gold 20%; chainmail 8%; leather and turtle shell 3%. The chestplate carries 40% of a set's weight, leggings 30%, helmet and boots 15% each, and the tooltip shows each piece's share. Weightless takes a piece's weight off.

**Heads.** In birch forests and cherry groves, 10% of zombies and skeletons (any kind) wear a beehive over their heads. Kill one and the hive breaks open: 3 angry bees come out after whoever killed it (or the nearest player). Anywhere, one zombie or skeleton in a thousand wears TNT instead, and explodes like TNT when it dies. Neither drops, and like any helmet they keep the mob from burning in daylight.

**Zombies and lights.** With nothing to chase, a zombie looks for a torch within 8 blocks (soul and redstone torches too), walks over and knocks it off. A zombie with a pickaxe or shovel also digs out lanterns, glowstone, sea lanterns, jack o'lanterns, shroomlights, froglights, redstone lamps, end rods, candles and beds, taking as long as a player with that tool would. Needs mobGriefing.

**Dungeons and the Nether** (Eternal Return worlds only). Dungeons are twice as common, and come in three kinds: cobblestone as in vanilla, stone bricks, and bricks. The Nether has dungeons too, of nether bricks or red nether bricks, whose spawners raise wither skeletons, blazes or witches (spawner mobs drop nothing, as everywhere). Nether wart grows wild in patches on soul sand.

**Strongholds** (Eternal Return worlds only). A third of them are built at ground level instead of deep underground, where the ground is above sea level: the ground is raised to meet them and cleared above, the way villages sit, so they stand out in the open where they rise above the hills. Illagers live in strongholds: about half the rooms have one or two (pillagers, vindicators, witches, now and then an evoker), and the portal room always has an illusioner. They are there from the start and never despawn.

**Wither skeletons** now and then spawn anywhere in the Nether, not only in fortresses, and always drop their skull, however they die.

**Endermen** only pick up cactus, pumpkins and melons.

**Large mobs.** One zombie, skeleton or creeper (any kind) in 200 spawns twice the size, hitbox and all, where there's room. It never holds anything (a large skeleton fights with its fists) but can wear armor. A large zombie only goes for players and iron golems: it leaves animals, villagers and turtles alone, doesn't eat raw meat and doesn't break lights. A large creeper's fuse lasts twice as long (3 seconds) and its blast is twice as big.

**Zombie villagers** are 15% of zombies (vanilla 5%), so curing them is a real way to get villagers in Eternal Return worlds, which have no villages. **Witches** spawn now and then in the Nether.

**Pillagers at sea.** Out in an ocean, now and then (about once every 7 minutes on average) a dark oak boat with two pillagers turns up on the water 24 to 40 blocks away. The front one steers toward you (or whoever they're after) and stops about 6 blocks off so both can shoot. They don't break out of their boat like other monsters; an empty boat sinks away after 5 seconds. Never on Peaceful or inside a beacon's area.

**Spiders** fling a ball of web at you from 4 to 16 blocks away every 6 seconds or so; if it hits, you're stuck in a cobweb where you stand (it stays until broken). Cave spiders too.

**Zombies.** Zombies that spawn deep underground (below y 0) sometimes (15%) carry a pickaxe or shovel, and when they can't reach you they dig toward you, a block at a time, as fast as a player with that tool would (cracks and all; mobGriefing must be on; never bedrock, obsidian, containers, or into water or lava). Adult zombies hunt farm animals (cows, pigs, sheep, chickens, rabbits, goats, horses, llamas, camels) when there's no player or villager to chase, but never one a mob is riding (a baby zombie's cow, a chicken jockey's chicken), and a hurt zombie eats raw meat lying nearby to heal (twice the meat's food value). Zombies from monster spawners (dungeons) always hold a stone or iron pickaxe, at any depth, so they dig too (and, being spawner mobs, never drop it). 5% of baby zombies ride a cow, pig or sheep and steer it, as they do chickens; the mount despawns with them.

**Jockeys.** 10% of phantoms carry a skeleton, and 5% of slimes (size 2 and up) have one on top; the mount moves as usual while the skeleton shoots.

**Baby fish and squids.** 15% of cod, salmon, tropical fish, pufferfish, squids and glow squids spawn as babies: half the size, a little faster, and they stay small.

**Slimes.** A new slime is sometimes size 8 (8%) or even 16 (2%), on top of vanilla's 1, 2 and 4; they split down through the sizes as usual. While it rains at night, slimes spawn on the surface in any overworld biome, besides swamps and slime chunks as before.

**Blazes.** In Eternal Return worlds there are no nether fortresses, so blazes spawn in soul sand valleys and basalt deltas instead; blaze rods (and so eyes of ender and the End) stay within reach. Wither skeletons came only from fortresses, so they (and the Wither) are gone there. Other worlds are unchanged.

**Drowned** drop prismarine shards and crystals now and then (Looting helps).

**Glowing.** A creature with the Glowing effect can be seen through blocks: mobs that would only notice you in plain sight spot you behind walls, up to 128 blocks.

**No hunger.** The food bar is gone: it stays full, so you can always sprint, and you never starve. Health doesn't come back by itself either (except on Peaceful): food heals you, each kind its own way. Snacks heal a little at once and are quick to eat; cooked food and meals heal more, spread over a few seconds, and most give a perk; healing from several foods runs side by side. Ordinary food can only be eaten when you're hurt; food with a perk (the "any time" column) whenever you like. The Hunger effect (rotten flesh, raw chicken, pufferfish) halves food healing while it lasts; the Saturation effect heals. A slice of cake heals 2 ❤ over 3 s. Each food's healing and effects are in its tooltip. The mod's fish are left as they were (64 a stack, 1.6 s) and heal like raw salmon. Raw meat and fish still poison you 70% of the time. Potions, milk and ominous bottles take a second to drink; getting hurt still stops you eating. The table is `src/main/resources/eternalreturn/foods.json` (❤ = a heart):

| Food | Stack | Eating time | Heals | Over | Any time | Effects |
|---|---|---|---|---|---|---|
| Sweet berries | 64 | 0.6 s | 0.5 ❤ | at once |  |  |
| Glow berries | 64 | 0.6 s | 0.5 ❤ | at once | yes | Night vision (30 s) |
| Melon slice | 64 | 0.6 s | 1 ❤ | at once |  |  |
| Dried kelp | 64 | 0.4 s | 0.5 ❤ | at once |  |  |
| Cookie | 64 | 0.6 s | 1 ❤ | at once | yes | Speed (10 s) |
| Carrot | 64 | 0.8 s | 1 ❤ | at once |  |  |
| Beetroot | 64 | 0.8 s | 0.5 ❤ | at once |  |  |
| Potato | 64 | 1 s | 0.5 ❤ | at once |  |  |
| Poisonous potato | 64 | 1 s | - | at once |  | its own (as vanilla) |
| Apple | 32 | 1.2 s | 2 ❤ | 2 s |  |  |
| Chorus fruit | 64 | 1 s | 1 ❤ | at once | yes |  |
| Spider eye | 64 | 1 s | - | at once |  | its own (as vanilla) |
| Rotten flesh | 64 | 1.6 s | 1 ❤ | at once |  | its own (as vanilla) |
| Raw beef | 32 | 1.6 s | 1.5 ❤ | at once |  |  |
| Raw porkchop | 32 | 1.6 s | 1.5 ❤ | at once |  |  |
| Raw mutton | 32 | 1.6 s | 1 ❤ | at once |  |  |
| Raw chicken | 32 | 1.6 s | 1 ❤ | at once |  | its own (as vanilla) |
| Raw rabbit | 32 | 1.2 s | 1 ❤ | at once |  |  |
| Raw cod | 32 | 1.2 s | 0.5 ❤ | at once |  |  |
| Raw salmon | 32 | 1.2 s | 1 ❤ | at once |  |  |
| Tropical fish | 32 | 1 s | 0.5 ❤ | at once |  |  |
| Pufferfish | 16 | 1.6 s | - | at once |  | its own (as vanilla) |
| Cooked cod | 32 | 1.2 s | 2.5 ❤ | 3 s | yes | Water breathing (30 s) |
| Cooked salmon | 32 | 1.6 s | 3.5 ❤ | 4 s | yes | Dolphins grace (20 s) |
| Cooked rabbit | 32 | 1.2 s | 2.5 ❤ | 3 s | yes | Jump boost (30 s) |
| Cooked chicken | 32 | 1.6 s | 3.5 ❤ | 5 s | yes | Speed (20 s) |
| Cooked mutton | 16 | 2 s | 4.5 ❤ | 6 s | yes | Resistance (20 s) |
| Cooked porkchop | 16 | 2.4 s | 5.5 ❤ | 8 s | yes | Absorption (1:00) |
| Steak | 16 | 2.4 s | 6 ❤ | 10 s | yes | Strength (20 s) |
| Baked potato | 32 | 1.6 s | 3 ❤ | 5 s |  |  |
| Bread | 32 | 1.6 s | 3.5 ❤ | 6 s |  |  |
| Pumpkin pie | 8 | 2.4 s | 6 ❤ | 8 s | yes | Luck (3:00) |
| Mushroom stew | 1 | 2.4 s | 5 ❤ | 10 s | yes | Resistance (30 s) |
| Beetroot soup | 1 | 2.4 s | 4 ❤ | 6 s | yes | Haste (1:00) |
| Rabbit stew | 1 | 3.2 s | 9 ❤ | 15 s | yes | Speed (1:00), Jump boost (1:00) |
| Suspicious stew | 1 | 2.4 s | 3 ❤ | 6 s | yes |  |
| Golden carrot | 32 | 1.2 s | 3 ❤ | 3 s | yes | Night vision (3:00) |
| Golden apple | 16 | 1.6 s | 4 ❤ | 4 s | yes | its own (as vanilla) |
| Enchanted golden apple | 8 | 1.6 s | 10 ❤ | 5 s | yes | its own (as vanilla) |
| Honey bottle | 16 | 2 s | 2 ❤ | 4 s | yes |  |

Stacks bigger than a food's new stack size (from before this change) stay as they are until split.

**Farming.** Right-click a vine with shears and it stops growing. Pink petals can be placed on still water, like lily pads: right-click the water, from any angle. Pigs have litters of 2 or 3 piglets, or 2 to 5 if a parent was fed a golden carrot (golden carrots are pig food now). A baby animal fed a poisonous potato is poisoned half the time, and one poisoned that way never grows up.

**Snow** falls like sand when nothing holds it up: a snow block placed in the air, or a snow layer whose block is mined away, drops to the ground (a layer keeps its layers) instead of vanishing. Snow that lands on a snow layer adds to the pile: the layers add up (a snow block counts as 8) to a full layer, and any left over pile on top.

**Crops.** Each crop grows at full speed only in its ideal biomes; elsewhere it grows four times slower (bone meal works as usual): wheat in plains, meadows and savannas; carrots in forests and cherry groves; potatoes in taigas and windswept hills; beetroots in snowy and icy biomes; melons and cocoa in jungles; pumpkins in forests and taigas; sugar cane in swamps and along rivers and beaches; cactus in deserts and badlands; sweet berries in taigas and groves; nether wart anywhere in the Nether. Moderner Beta's matching biomes count too. The lists are the biome tags `eternalreturn:ideal_biomes/<crop>`.

**Spawners.** Mobs from monster spawners drop no items: no loot and none of the gear they spawned with. They still drop experience, and anything they picked up (a lost sword, say).

### Redstone and frames

**Dispensers** place blocks: a block item with no dispenser behaviour of its own (TNT, shulker boxes, pumpkins and the like keep theirs) is placed in front, facing away from the dispenser; seeds and potatoes are planted on farmland. If it can't be placed, it is dropped as before. Droppers still drop.

**Obsidian pressure plate:** two obsidian side by side. Only players set it off (not mobs, items or spectators). Needs a pickaxe; as tough as obsidian.

**Pistons** move blocks that hold things (chests, barrels, furnaces, hoppers, signs, banners, jukeboxes...) with everything inside. Spawners, beacons, enchanting tables, conduits, end portals and command blocks still stop a piston (block tag `eternalreturn:immovable_block_entities`). A moved block that can't stand where it lands breaks and drops its contents. Chests, signs and the like stay visible as they slide.

**Chains** link blocks when pistons move them. A chain is attached to the blocks at its two ends, along its length: when a piston moves either of them, or the chain, all three move together, whether or not the piston touches the chain itself. Chains stick to slime and honey only where they visibly join them, end-on; a chain lying alongside a slime block doesn't stick. The 12-block limit still applies.

**Glass item frames:** 8 glass panes around a leather. Empty, it shows a thin glass frame; holding something, the frame vanishes and only the item shows, flat against the wall. Right-click one holding an item to open the chest (barrel, furnace...) it hangs on; sneak to turn the item. A glass item frame and a glow ink sac make a glowing one.

**Dyed item frames:** put an item frame (glass, glowing or plain) in a crafting grid with any dyes, as with leather armour. Only the inside takes the colour (the leather backing; the wooden border stays as it is), on the placed frame and on the item; a glass frame, which has no backing, colours its glass. It keeps its colour when placed and picked up again.

### The philosopher's stone

Crafted from a diamond in the middle, lapis lazuli on the four sides and amethyst shards in the four corners. It lasts 777 uses. Right-click:

- a block, to transmute it: glass and sand, grass and dirt, cobblestone and stone, stone bricks and cracked stone bricks, mossy cobblestone and mossy stone bricks, ice and packed ice, clay and terracotta, melon and pumpkin, obsidian and crying obsidian, sandstone and red sandstone, soul sand and soul soil, nether bricks and red nether bricks, coarse dirt and podzol, concrete and concrete powder of the same colour, gravel and tuff, netherrack and blackstone, mud and packed mud, snow and powder snow, each into the other and back; granite to diorite to andesite and round again; logs, planks and leaves to the next wood (oak, spruce, birch, jungle, acacia, dark oak, cherry); wool to the next colour. A log keeps the way it lies. It only makes blocks an Eternal Return world can give you: no mangrove or bamboo wood, and no moss. Concrete powder made next to water sets straight back into concrete, so nothing happens and no use is spent;
- a creature: a pig into a zombified piglin and back, a cow into a mooshroom, a villager into a witch, a squid into a glow squid, a cod into a salmon, a slime into a magma cube (same size), a spider into a cave spider, a donkey into a mule, and each back again; a zombie into a husk, a husk into a drowned, a drowned into a zombie; a skeleton into a stray, a stray into a bogged, a bogged into a skeleton. A sheep gets random wool, a horse gets a new coat colour or new markings (one or the other, at random), a creeper is charged (or uncharged), and rabbits, foxes, axolotls, llamas and frogs change colour.

Creatures keep their gear and name. The stone works before a creature's own right-click: a villager doesn't open its trades and a horse isn't mounted.

### Items

**Necronomicon.** A philosopher's stone in the middle, a book below it, and 7 wither skeleton skulls around them. It lasts 666 uses. Right-click a block (or the air) to raise a random undead servant there: a zombie, husk, drowned, skeleton, stray, bogged, wither skeleton or zombified piglin, with the gear it would spawn with. A servant follows you (and catches up if you get more than 24 blocks ahead), goes for whatever attacks you and whatever you attack, and fights back when something hurts it (not when you do); otherwise it leaves every mob and player alone. It never despawns, burns in daylight like any undead (a helmet helps), and drops nothing when it dies, not even experience. You can have 3 at once; once one dies, you can raise another.

**Dynamite.** 2 gunpowder, a paper and a string make one. Throw it (or fire it from a dispenser): it bounces to a stop and goes off 2 seconds after it was thrown, a little weaker than TNT, breaking blocks as TNT does.

**Asylum papers.** Every wandering trader sells them, one for 16 emeralds. Right-click a block (or the air) and a villager arrives: an adult with no job yet, dressed for the biome.

**Netherite** gear is crafted like diamond gear, from netherite ingots (8 for a chestplate, 2 and a stick for a sword); diamond gear can no longer be upgraded at a smithing table.

**Minecarts** can go up to three times as fast (24 blocks a second at top speed, on any rail). **Copper rails**: 6 copper ingots and a stick make 16. They work and curve like ordinary rails.

### Blocks

**Stairs and slabs.** Every color of wool and concrete has stairs and a slab (`eternalreturn:white_wool_stairs`, `eternalreturn:red_concrete_slab` and so on), after the full block in the Colored Blocks tab. Crafted like any stairs (6 blocks make 4) and slab (3 make 6); concrete ones also come from the stonecutter (1 block makes 1 stairs or 2 slabs). They behave like their block: wool ones burn, are quick to cut with shears and muffle sculk; concrete ones need a pickaxe.

**Carpets.** Placing a carpet on top of the same carpet adds a layer, like snow: up to 16 layers, a full block. Each layer is 1/16 of a block high and breaking it gives back one carpet per layer. A different color doesn't stack. Moss carpet stacks too.

## Configuration

`config/eternalreturn.json` is generated on first launch.

- `enchantCaps`: item tag to capacity, first match wins.
- `inherentEnchantments`: item tag to `{enchantment: level}`.
- `pointsRefundedPerCurseLevel`, `raiseMaxLevels`, `raisedMaxLevel`.
- `disabledEnchantments`: never rolled by tables, loot, mobs, or villagers.
- `bookshelfBias`: the multipliers above, plus `booksCanUnlockNonTableEnchantments` (off by default) to let shelved books make the table offer treasure enchantments like Frost Walker.
- `fortune`: toggles for hoes and shears.
- `fishing`: `fishRequireBait`; `baits` (item to `lureSeconds`, `nightLureSeconds`, `lava`; config files with the old worms, glow bait and infernal bait are reset to the new list once); `lavaFishingOutsideNether`; `hostileCatchChance` (default 0.03) and `hostileCatches` (mob to `weight` and `oceanOnly`; the ids are mob ids, or `drowned_guardian_jockey` / `drowned_elder_guardian_jockey`); `overfishing` (`enabled`, `radius` 6, `freeCatches` 3, `slowdownPerCatch` 0.5, `maxSlowdown` 4, `recoveryMinutes` 5).
- `survival`: `bedsSkipNight` (off), `rememberedBeds` (3), `beaconsBlockMonsters`, `animalsOnlySpawnNearBeacons`, `animalsGetHungry`, `hungryAfterDays` (0.5), `grazeChance` (per second, 0.125), `daysBeforeStarving` (3), `starveDamageSeconds` (30), `herdPanic` and `herdPanicRadius` (16), `blindFollowRange` (2), `spawnerMobsDropNothing`; `armorWeight` (material to a full set's slowdown; gold is lighter than iron now, and config files with the old gold weight are moved to the new one once), `snowFalls`, `cropGrowthOutsideIdealBiome` (0.25); `removeHunger` (on: no hunger, food heals), `hitsInterruptEating`, `rawFoodPoisonChance` (0.7), `pigLitters`, `poisonousPotatoStuntChance` (0.5); `glowingSeenThroughWalls`.
- `enchantmentsExtra`: `ascensionCurseRises`.
- `redstone`: `dispensersPlaceBlocks`, `pistonsMoveBlockEntities`, `chainsLinkBlocks`, `minecartSpeedMultiplier` (3).
- `villagers`: `onlyFishermenBuy`; `fishPriceMultiplier` (default 2, applied to vanilla's own fish trades; the mod's fish pay what `eternalreturn/fish.json` says); `traderVillageVillagers` (3) and `traderVillageRadius` (64), `asylumPapersPrice` (16, 0 for none); `needs`: `enabled`, `daysBeforePriceRise` (2), `emeraldsPerMissingNeed` (5), `daysBeforeRefusal` (7), `maxOtherAdultsInSight` (1), `exerciseBlocks` (8), `minWorkstationDistance` (8), `closedAtNight`, `nightStart` (13000) and `nightEnd` (23000), `healWithFood` and `eatChance` (0.25).
- `worldgen`: `debugTunnel` (off by default). Carves one straight 3x3 tunnel at y=20 along z=8 through every chunk on that row, to check that this mod's carvers run under the current world generator. Needs a restart, and only affects newly generated chunks.
- `worldgen.biomeStone` (on by default; Eternal Return worlds only, newly generated chunks only): each biome's stone under the topsoil. Off: plain stone everywhere.
- `worldgen.moreDungeons`, `netherDungeonAttempts` (6 per chunk), `netherWartPatchChance` (0.3), `surfaceStrongholdChance` (a third), `strongholdIllagers` (Eternal Return worlds only, newly generated chunks only).
- `worldgen.onlyEssentialStructures` (on by default; Eternal Return worlds only): only strongholds, mineshafts, nether fossils and end cities, in all three dimensions, and blazes outside fortresses.
- `worldgen.roundOverhangs` (on by default; Eternal Return worlds only, newly generated chunks only): overhang undersides arch and bulge instead of lying almost flat, and floating sheets of rock too thin to arch (3 blocks or less) are removed. Off: the terrain as Moderner Beta makes it.
- `worldgen.caves` (Eternal Return worlds only; newly generated chunks only): `enabled` (the new caves); `oldCaves` (off: vanilla's, Moderner Beta's and other mods' cave carvers don't run there); `density` (multiplies every weight); per type under `types` (`spaghetti`, `ravine`, `large`, `vertical`, `zigzag`, `ribbed`, `spiral`, `toroidal`): `enabled`, `weight` (caves starting per 100 chunks), `minY` and `maxY` (where a cave starts), `minRadius` and `maxRadius`, and for `large` a `typicalRadius` (the most common size; a few caverns reach toward `maxRadius`); `spiral` and `toroidal` also have `minSize` and `maxSize` (the spiral's coil radius, the room's ring radius). Config files from before the first cave tuning are reset to the new type defaults once (`typesVersion`). Debug: `debugForceCaveType` (a type id: only that type, at a high rate) and `debugLogCaveStarts` (writes `type,x,y,z` of every cave start to `eternalreturn-cave-starts.csv` in the game folder).
- `mobs`: baby chance, baby speed, and baby creeper fuse and blast multipliers; armor and armor-enchant chances, each written as `base + perDifficulty x clamped local difficulty`; the underground-creeper toggle and its sky-light limit; jockey, fishing rod, ender pearl and sword chances; fishing rod pull strength; the boat-breaking toggle; `guardianSpawnWeight` (4) and `elderGuardianSpawnWeight` (1) in ocean biomes (0 turns them off; read when the world loads), `elderGuardianSpawnChance` (0.25) and `elderGuardianSpacing` (96 blocks); `slimeSize8Chance` (0.08), `slimeSize16Chance` (0.02), `slimesInNightRain`; `waterBabyChance` (0.15); `spiderWebCooldownSeconds` (6), `miningZombieChance` (0.15), `spawnerZombiesDig`, `zombiesHuntAnimals`, `babyZombieAnimalJockeyChance` (0.05), `phantomSkeletonChance` (0.1), `slimeSkeletonChance` (0.05); `beehiveHeadChance` (0.1), `tntHeadChance` (0.001), `zombiesBreakLights`, `netherWitherSkeletonWeight` (2; 0 for fortresses only, read when the world loads); `zombieVillagerShare` (0.15), `netherWitchWeight` (2), `largeVariantChance` (0.005), `pillagerBoatChance` (0.15 a minute at sea).

Which items belong to each tier is controlled by tags in `data/eternalreturn/tags/item/`, so modded gear can be added with a datapack and no code changes. The same goes for mobs: `data/eternalreturn/tags/entity_type/` decides which mobs can be babies (`baby_variants`, which only works for skeleton-type mobs and creepers), which get the boosted armor (`armored_spawns`), and which can ride spiders (`jockey_riders`).

The block tag `eternalreturn:carvable` lists what caves can cut through (vanilla's `overworld_carver_replaceables` and anything added to it).

Fishing data lives in the same place. The item tag `raw_fish` is what chum accepts. Every fish (rarity, habitats, conditions, price, which fishermen buy it) is in `src/main/resources/eternalreturn/fish.json`; `tools/fish/build_fish.py` turns it into the habitat biome tags (`fish_habitat/<habitat>`, whose Moderner Beta entries are optional, so they load with or without it), the catch tables in `data/eternalreturn/loot_table/gameplay/`, the textures, names and tooltips, and `docs/fish.md`.

## Compatibility notes

- Overrides `fortune`, `mending`, `quick_charge`, and replaces the `tradeable`, `on_random_loot`, and `exclusive_set/armor` enchantment tags. Another datapack replacing those same files will conflict.
- `generateEnchantments` is only taken over for capped items, items with inherent rules, or when shelves are biasing. Everything else uses vanilla's code untouched.
- The enchanting and anvil screen tweaks are cosmetic and set to `require = 0`, so a UI mod that touches the same screens cannot crash the game over them.
- Vanilla's 1% skeleton-only spider jockey roll is replaced by the mod's own roll (same default chance). A mod that edits that vanilla roll will see no effect.
- Adds one entity, `eternalreturn:zombie_hook`, 50 blocks (the two hardened blocks and 48 ore variants) and 65 items, so the mod must be installed on both client and server.
- Replaces the `minecraft:gameplay/fishing` loot table (fish now need bait and come from the extended fish table) and adds to the `minecraft:fishes` item tag. Mods that add fish to `minecraft:gameplay/fishing/fish` through Fabric's loot API still work, since that table is used unchanged inside the new one.
- The trade filter recognizes vanilla's two buy-trade types, including inside the trade rebalance's per-biome wrappers. Buy trades that other mods add with their own trade classes are not detected and stay.
- Sodium: no overlap.
- Villager regions: villagers born, cured or spawned in Moderner Beta's biomes get vanilla's region for that kind of biome (`VillagerRegions`), which changes their clothes and a fisherman's fish.
- Moderner Beta (5.0.0-alpha.2): tested with its Release 1.6.4, 1.6.4 amplified and Beta 1.7.3 presets, and with the Eternal Return world type built on it. Its chunk generator runs carvers attached to biomes through Fabric's biome modifications, so this mod's carvers work in its worlds without any hook. The fish biome tags include its biomes as optional entries.
- Without Moderner Beta: the Eternal Return world type and its noise settings are skipped at load (Fabric load conditions) and the optional tag entries are dropped, so the game starts and existing non-Eternal Return worlds load normally. A world created with the Eternal Return type needs Moderner Beta to open.
- Adds tuff to the vanilla block tag `minecraft:stone_ore_replaceables` and replaces `minecraft:deepslate_ore_replaceables` with deepslate alone, so in every world, ores that generate inside tuff blobs use their stone variant instead of the deepslate one. A mod that adds blocks to `deepslate_ore_replaceables` may lose them, depending on load order. Adds the world type to `minecraft:normal` (the create-world list) and the preset to Moderner Beta's `selectable` category tag.
- Caves: in Eternal Return worlds, only this mod's carvers run (vanilla's, Moderner Beta's and other mods' carvers are skipped) unless `worldgen.caves.oldCaves` is on. Other worlds are untouched (checked against fingerprints of vanilla and Moderner Beta worlds). A world counts as Eternal Return by its noise settings, so an Eternal Return world whose generator settings were replaced by hand would carve normally.
- Ore variants: added to the vanilla tags `minecraft:<ore>_ores` (blocks and items; Fabric's `c:ores` tags include them through those), `minecraft:mineable/pickaxe`, `minecraft:needs_stone_tool` and `minecraft:needs_iron_tool`. The ore placement hook and the lake change only act in Eternal Return worlds with biome stone on; ores from other mods' ore features become variants there too when they land in a host stone, but only if their ore block is in the table. The vanilla ore tags `minecraft:stone_ore_replaceables` (vanilla plus tuff) and `minecraft:deepslate_ore_replaceables` (deepslate only) are otherwise as before.
- Mineshafts: in Eternal Return worlds each mineshaft piece saves its wood under the key `eternalreturn_wood`; without this mod such a piece falls back to vanilla's oak or dark oak. Other worlds' mineshafts are untouched.
- Biome stone: adds sandstone, red sandstone, granite, diorite and andesite to the item tags `minecraft:stone_tool_materials` and `minecraft:stone_crafting_materials`, and replaces vanilla's dispenser, dropper, lever, observer and piston recipes with versions taking that tag, in every world. A datapack replacing those recipes undoes this. Overrides the configured features `spring_water`, `spring_lava_overworld` and `glow_lichen` (only adding the two hardened blocks), and adds the hardened blocks to `minecraft:mineable/pickaxe`, `minecraft:base_stone_overworld` and `minecraft:stone_ore_replaceables`; none of this changes worlds without the hardened blocks. Red sandstone is not added to any ore tag: ores treat it as stone only in Eternal Return worlds. Uses an access widener for the surface-rule condition. With Moderner Beta installed, two small mixins into its surface pass (by class name, applied only when it is present) make that pass treat the host stones as stone in Eternal Return chunks; other presets are untouched.
- Moderner Beta's optional "Reduced Height" datapack moves the overworld floor to y=0 for every world, which cuts off the bottom of Eternal Return worlds. Its "Deepslate Blobs" datapack adds deepslate back. Leave both off.
- Nostalgic Tweaks: if you use its XP removal, this mod is already XP-free. If it has options that change the anvil or enchanting screens, check them alongside this.

- Carpets: every carpet block gets a `layers` property (1 to 16). A one-layer carpet is exactly vanilla's, so worlds, structures and commands that place carpets are unchanged. A resource pack that replaces a carpet's blockstate file draws stacked carpets with its own model, one layer high; this mod's own files cover vanilla and old-style packs that only change textures.
- Vines: vines get a `cut` property (false until sheared), so worlds and structures that place vines are unchanged. Like carpets, a resource pack replacing the vine blockstate file still works (its multipart conditions ignore the new property).
- Item frames: the mod replaces vanilla's item frame models (the template block models, to mark the backing tintable, and the item models, as two layers). A resource pack with its own item frame models wins over them, and dyed frames then show undyed.
- Pistons: other mods' blocks with data become movable too unless added to `eternalreturn:immovable_block_entities`.
- Endermen: the vanilla block tag `minecraft:enderman_holdable` is replaced, so blocks other mods add to it are dropped too.
- Netherite: vanilla's nine netherite smithing upgrade recipes are switched off (replaced with recipes that never load) and crafting recipes added. A datapack or mod that brings the smithing upgrades back would make both work.
- Wandering traders: their trade pools are filtered as they pick (the item tag `eternalreturn:not_from_wandering_traders`), so trades other mods add still turn up unless their item is in the tag.
- Minecarts: the speed multiplier applies to every minecart that uses vanilla's top speed (not the furnace minecart, which has its own).
- Wither skeletons: the vanilla loot table `minecraft:entities/wither_skeleton` is replaced (the skull no longer needs a player kill or luck). A datapack replacing it wins.
- Beds: with night skipping off, the "players sleeping" counter isn't shown on servers.

## Quick test checklist

1. Iron sword in inventory shows Unbreaking I, no glint, `Enchant capacity: 0 / 6`.
2. Table with 15 bookshelves: options are clickable at level 0, only lapis is spent.
3. Add a chiseled bookshelf with a Sharpness V book: Sharpness shows up far more often and higher.
4. Anvil: stack books on a diamond sword past 7 points, output disappears with the over-capacity message.
5. Protection IV + Fire Protection III combine on one chestplate.
6. Golden hoe on mature wheat drops extra wheat. Fortune shears on a sheep drop extra wool.
7. Diamond pickaxe with Efficiency III upgraded to netherite: Efficiency II, Unbreaking V.
8. Apply Curse of Invisibility: the enchantment list, glint, and capacity line vanish, and the anvil now accepts one more point.

Mobs (in survival, at night; the summons skip the random rolls):

9. `/summon skeleton ~ ~ ~ {IsBaby:1b}` and `/summon creeper ~ ~ ~ {IsBaby:1b}`: half-size and fast. The baby creeper goes off in about 0.75 s and barely marks the ground.
10. On the surface at night no creepers appear. In a dark cave they still do.
11. Zombies and skeletons wear armor far more often, even early on, and it is often enchanted.
12. `/summon zombie ~ ~ ~ {HandItems:[{id:"minecraft:fishing_rod",count:1},{}]}` and stand about 8 blocks away: a line appears from its hand and you get yanked toward it.
13. `/summon zombie ~ ~ ~ {HandItems:[{},{id:"minecraft:ender_pearl",count:1}]}` and stand about 20 blocks away: it throws the pearl and lands next to you.
14. `/summon skeleton ~ ~ ~ {HandItems:[{id:"minecraft:bow",count:1},{id:"minecraft:iron_sword",count:1}]}`: walk up to it and it switches to the sword. Back off and it switches back to the bow.
15. Push a zombie into a boat: it smashes its way out within about two seconds. A pig in a boat stays put.
16. Rare spawns: set `caveSpiderJockeyChance` and `strayPolarBearJockeyChance` to 1.0 in the config and restart. A cave spider egg then gives a jockey and a stray egg gives a stray on a polar bear. Spider eggs give husk riders in a desert and stray riders in a snowy biome.

Fishing and villagers (in survival):

17. Fish with no bait: only junk and treasure come up, plus now and then prismarine shards or crystals. Carry chum: fish bite, and one chum is used per catch (none when casting).
18. Hover over a fish (`/give @s eternalreturn:bluegill`): its rarity, where and when it bites, and who buys it. Fish are in Food & Drinks after the pufferfish, chum in Tools & Utilities after the fishing rod.
19. Fish a river with chum by day, then at night: bluegill and striped perch by day, crappie and walleye at night. In rain, muskellunge turns up; on a rainy night, european eel.
20. Craft chum (any raw fish + bone meal): 4 of it.
21. Nether, with magma cream: cast into a lava lake. The hook floats, lava pops trail toward it, and the catch flies out without burning. Magma fish bite in any lava; each Nether biome has its own fish (bonefish and soulsucker in soul sand valleys, obsidian fish in basalt deltas...).
22. A new fisherman (`/summon villager ~ ~ ~ {VillagerData:{profession:"minecraft:fisherman",level:5,type:"minecraft:plains"}}`): he sells chum, glistering melon and magma cream for an emerald, and buys every fish a plains master fisherman buys, bluegill (6 for 1) to oarfish (12 emeralds each). Make the same with `type:"minecraft:snow"`: no bluegill, but alaska blackfish, and every ocean and Nether fish. New masons, farmers, librarians, etc. buy nothing.
23. Villager needs: right-click a villager around midday. A content one just trades. `/time add 50000` (two days and a bit) with it shut in a dark 1 x 2 hole: right-click it and chat lists its missed needs (sleep, gossip, exercise, sunlight) and says its prices are up; emerald prices are 20 higher. `/time add 120000` more and it shakes its head and refuses. Let it out into a village with a bell, a house of its own and a workstation 8 or more blocks from its bed: once it has slept, gossiped, had a walk and seen the sun, it is happy again.
24. Shop hours: right-click any villager at night: "Sorry, we're closed for the night." Same villager in the morning: it trades.
25. Healing: punch a villager once or twice and throw it some bread: it picks it up, and every few seconds while hurt it eats a piece and heals.
26. A Lure V rod with any bait still gets bites, fast.
27. `/enchant @s eternalreturn:thriftiness 5` on a rod, then fish with a stack of chum: roughly half the catches leave the stack unchanged.
28. Deep water: with chum, fish in open ocean at night (in the Eternal Return test world below, `/tp @s 24 63 0` has 13 blocks of water): monkfish and skates turn up now and then. Cast into shallow water near a shore: none.
29. Monsters on the line: set `hostileCatchChance` to 1.0 in the config's `fishing` section and restart. In a river or lake every bite is a drowned, yanked out toward you. In an ocean it can also be a guardian, a drowned with a trident riding a guardian, or (rarely) an elder guardian, alone or ridden; the guardian swims around while the drowned throws its trident. A bait is used up each time.
30. Overfishing: with chum, fish one spot and reel in six fish in a row. Bites take visibly longer (about 2.5 times as long by the sixth). Walk 10 blocks along the shore and they come quickly again; or wait 5 minutes at the first spot.
31. Prismarine: fish without bait for a while: about one catch in eight is prismarine shards or crystals.

World type (needs Moderner Beta): create a creative world with World Type "Eternal Return" and seed `173164`, then `/tp @s X 320 Z` to each spot and fly down. Heights are from the test world.

32. Anywhere on land: gently rolling hills most of the way, few sheer steps or cliffs, and mountains only here and there (mostly extreme hills). Jungle, savanna and badlands hills are raised rolling hills, not flat-topped blocks with sheer sides. No spires, and floating rock is very rare.
33. `372 -1912`: the highest ground within 2,000 blocks of spawn, an extreme hills peak at about y 170.
34. `968 -2296`: high extreme hills, about y 143 (where the emerald survey looks). `712 -568`: rolling extreme hills. `/tp @s -394 66 -448` puts you on the ground under an ice plains overhang whose underside is about 40 blocks up: look up and around, it arches and bulges instead of lying flat (on worlds made before this update, overhang undersides are almost flat).
35. `-1208 584`: badlands with terracotta bands, at about y 71.
36. `-2040 1160`: a mushroom island. `-376 -376`: ice plains. `1416 264`: dark forest. `-608 192`: a cherry grove, a raised, hilly stretch of pink cherry trees and petals.
37. Anywhere: F3 never shows `lush_caves`, `dripstone_caves` or `deep_dark`. Dig down to y -60: the biome's stone all the way (with the usual granite, diorite, andesite and, below y 0, tuff blobs), bedrock at -64, ores like diamond and redstone in their normal stone texture, no deepslate.
38. With World Type "Moderner Beta", its Customize screen lists Eternal Return in its own category, with its icon.

Oceans and structures (same world; a new one if it was made before this update). `/tp @s X Y Z`, in spectator mode with night vision underwater:

39. Oceans, all at sea level (y 63): `392 63 -376` warm ocean (sand floor, coral and sea pickles); `200 63 -248` lukewarm; `-504 63 456` cold; `-632 63 1096` frozen (icebergs and ice); `136 63 136` deep ocean, deeper than the rest; `-568 63 968` deep frozen. F3 names each biome.
40. `/locate structure minecraft:monument` and `/locate structure moderner_beta:ocean_shrine`: neither finds anything. Fly low over a deep ocean (`136 63 136` in the test world) for a while, best at night or under ice: now and then a guardian (very rarely an elder guardian, with Mining Fatigue when you get near). In other world types, monuments are still there.
41. `/locate structure minecraft:mineshaft` from inside a biome, then `/tp` there in spectator mode: the supports and planks are that biome's wood. In the test world: oak `-401 26 -568` (under ice plains), birch `-401 -26 -759`, spruce `817 -33 1133`, jungle `3 -1 -1354`, acacia `1828 -46 -18`, dark oak `1304 -38 -302`, cherry `-539 12 -1664` (the centre of each mineshaft; look around for the corridors).

Caves (same world; a new world, since chunks generated before this update keep their old caves). `/gamemode spectator`, `/effect give @s night_vision infinite`, then `/tp @s X Y Z` puts you inside each cave, all within about 150 blocks of each other:

42. `-1283 -11 -1127`: spaghetti, 1.6.4-style tunnels that swell in the middle and fork; follow them up and down.
43. `-1286 15 -1140`: a ravine, a narrow, very tall canyon with stepped, jagged walls.
44. `-1200 -23 -1094`: a large cavern with uneven walls, a rolling floor and tunnels leading off. For a giant one, `/tp @s 356 -17 4290`: about 190 blocks across and 55 tall (far from the others).
45. `-1275 51 -1129`: the top of a vertical shaft; look down, then follow it to the tunnels at the bottom.
46. `-1338 37 -1196`: a zig-zag tunnel: straight runs with sharp alternating turns.
47. `-1304 -3 -1146`: a ribbed tunnel that bulges and pinches every few blocks.
48. `-1298 37 -1181`: the top of a spiral; follow it down as it winds two to four turns around a solid column of rock.
49. `-1166 -36 -1214`: inside a toroidal room; follow the ring round, with the pillar standing in the middle.
50. Anywhere: no water pours into caves from oceans or rivers, caves at y -56 and below hold lava, and there are no trial chambers (`/locate structure minecraft:trial_chambers` finds none).

Biome stone (same world, a new one if it was made before this update; survival for the drops, `/gamemode creative` to look around). `/tp @s X Y Z`, then dig straight down with a pickaxe; F3's "Targeted Block" names what you are looking at:

51. `-376 73 -952` (plains): grass, dirt, a coal ore, a small cave just under it, then plain stone from y 64 (with granite, gravel and dirt blobs further down). Stone drops cobblestone, as before.
52. `-632 76 392` (forest): dig down through any tree, then grass, dirt, then andesite from y 65, all the way down (a granite blob at y 32 to 37, a cave at y 12). Mining it drops andesite.
53. `-504 85 -696` (birch forest): dig down through any tree, then grass, dirt, then diorite from y 74. Drops diorite.
54. `1032 100 328` (jungle, on the canopy): dig down through the tree, then grass, dirt, then granite from y 68. Drops granite.
55. `264 69 -696` (desert): sand, then ordinary sandstone (the desert's topsoil) down to y 55, an andesite blob, then hardened sandstone from y 51. It looks the same as the sandstone above, but F3 names it `eternalreturn:hardened_sandstone` and it takes about twice as long to mine. It drops plain sandstone, with or without Silk Touch.
56. `-1208 72 584` (badlands): terracotta bands, then red sandstone from y 48. Drops red sandstone.
57. `-376 76 -376` (ice plains): grass and dirt from y 74, a hardened packed ice iron ore at y 70, then hardened packed ice from y 68. It looks like packed ice, drops packed ice (Silk Touch too), isn't slippery to walk on, and doesn't melt next to a torch.
58. Hit hardened sandstone or hardened packed ice with your hand or a shovel: it breaks slowly and drops nothing. In the creative inventory, Natural Blocks shows Hardened Sandstone after Sandstone and Hardened Packed Ice after Packed Ice.
59. Crafting: a stone pickaxe from three sandstone (or red sandstone, granite, diorite, andesite) and two sticks; a furnace from eight andesite; a lever from a stick and a piece of granite; a piston from planks, an iron ingot, redstone and diorite. Packed ice makes none of them.
60. In any host stone, granite, diorite, andesite, dirt and gravel blobs turn up as before. The map at `docs/worldgen-maps/stone/stone_y30.png` shows a cut at y 30.

Ore variants (same world, made after this update; `/gamemode spectator` and `/effect give @s night_vision infinite`, then `/tp @s X Y Z` puts you in a cave with the ore in the wall next to you; `/gamemode survival` there to mine it):

61. `-657 -4 361` (forest): andesite iron ore under your feet. Mine it with a stone pickaxe: raw iron. A wooden pickaxe gets nothing.
62. `-482 -27 -724` (birch forest): diorite gold ore under your feet. An iron pickaxe gives raw gold; Silk Touch gives Diorite Gold Ore itself. `-526 12 -720`: diorite lapis ore just north of you.
63. `995 -45 288` (jungle): granite gold ore just west of you, at `994 -45 288`; `1013 -55 299`: granite redstone ore under your feet. Walk on it or right-click it and it lights up with red specks, like redstone ore.
64. `-1203 -4 599` (badlands): red sandstone gold ore under your feet. Badlands have about twice the gold of other biomes.
65. `237 20 -724` (desert): hardened sandstone iron ore just north of you, at `237 20 -725`; `229 -51 -734`: hardened sandstone diamond ore just west of you.
66. `-387 -2 -383` (ice plains): hardened packed ice iron ore west of you; `-354 -29 -391`: hardened packed ice diamond ore under your feet.
67. Emerald: andesite emerald ore turns up only in high extreme hills (y 95 and up), around `968 150 -2296` for example (fly around the peaks); it is rare.
68. Smelt or blast any variant: the same result, experience and time as its vanilla ore.
69. Creative inventory, Natural Blocks: after each deepslate ore come its six variants. Or `/give @s eternalreturn:granite_iron_ore` (any of `andesite`, `diorite`, `granite`, `red_sandstone`, `hardened_sandstone`, `hardened_packed_ice`, then `_`, then `coal`, `iron`, `copper`, `gold`, `redstone`, `lapis`, `diamond` or `emerald`, then `_ore`), for example `/give @s eternalreturn:hardened_sandstone_gold_ore 16` or `/give @s eternalreturn:hardened_packed_ice_lapis_ore 16`.
70. Old-style pack on and off: with Golden Days (or any old-style pack) on, the host stone under each ore shows the pack's texture and the ore blobs keep this mod's old-style overlays. Turn it off in Options, Resource Packs (move it back to Available, then Done): the host stones go back to vanilla's textures, the ore blobs stay the same, and vanilla's own stone ores show vanilla's modern textures again.

Survival and blocks (any world, survival mode):

71. Beds: sleep in a bed at night: the night goes on (get up whenever). Right-click three more beds by day ("You can sleep only at night", but each sets your spawn). Break the newest bed, then `/kill @s`: you wake at the bed before it. Break that one too and die again: the one before.
72. Beacons: build a working beacon (a 3 x 3 iron pyramid is enough). At night around it nothing hostile spawns within 20 blocks; outside, monsters do. Phantoms leave you alone inside it.
73. Animals: kill every cow around: none come back (breeding still works). Inside a beacon's area on grass, animals turn up again now and then.
74. Hunger: pen a cow on stone and `/time add 80000`: it loses health every 30 seconds until you feed it wheat (it eats even right after breeding). A cow penned on grass grazes the grass to dirt and stays healthy while there's grass left.
75. Herd panic: hit a sheep in a flock: the whole flock bolts. Hit a pig next to them: only pigs run.
76. Blindness: `/effect give` a zombie blindness (or throw a splash potion of Blindness): it ignores you unless you are within 2 blocks.
77. Spawners: kill zombies from a dungeon spawner: experience but no rotten flesh, armor or other items. Zombies that spawn naturally still drop theirs.
78. Growth: `/enchant @s eternalreturn:growth 5` holding a helmet, then wear it: five extra half-hearts (two and a half hearts).
79. Slayer: `/enchant @s eternalreturn:slayer 5` on a rod, fish in the ocean: about one bite in four is a monster.
80. Stairs and slabs: craft white wool stairs (6 wool) and slabs (3 wool); put white concrete in a stonecutter: stairs and slabs. Shears cut wool stairs fast; concrete slabs need a pickaxe to drop.
81. Carpets: place a carpet, then keep placing the same carpet on top: it thickens a sixteenth at a time to a full block. Break it: one carpet per layer.
82. Armor weight: put on a full diamond set and sprint beside a friend in leather: you fall behind (as with Slowness II). The tooltip says how much each piece slows you. `/enchant @s eternalreturn:weightless` on the chestplate: noticeably quicker.
83. Scrolls: trade with a librarian (cure a zombie villager in an Eternal Return world): it sells a Scroll of something, not a book. Left-click with it: "You learned to craft ... books", and the scroll is gone. Put a book in the middle of a crafting table, ring it with diamonds, amethyst and the themed items (the recipe book shows it now): a level I book. Before reading the scroll, the same layout gives nothing.
84. Snow: place a snow block in the air: it falls. Mine the block under a snow layer: the layer drops instead of vanishing. Drop snow onto a snow layer: the pile grows.
85. Crops: plant wheat in a plains and in a desert (water both): the desert field takes about four times as long.
86. Slimes: `/weather rain` at night in plains: slimes turn up on the surface. Now and then a huge one.
87. Fish: watch a school of cod or a squid: some are half-size babies.
88. Drowned: kill a few: now and then a prismarine shard or crystal. Fishing: prismarine, dark prismarine and sea lanterns come up rarely.
89. Structures (new Eternal Return world): `/locate structure minecraft:village_plains`, `minecraft:fortress` or `minecraft:bastion_remnant` find nothing; `/locate structure minecraft:stronghold` and `minecraft:nether_fossil` (in the Nether) do. Blazes roam soul sand valleys and basalt deltas.
90. Spiders: let a spider see you from 8 blocks: a web flies at you and you're stuck in a cobweb.
91. Eating: eat a cookie (quick), bread (normal), steak (slow); drink milk (1 second). Get hit mid-bite: you stop. Eat raw beef a few times: usually poisoned.
92. Mining zombies: `/summon zombie ~ ~ ~ {HandItems:[{id:"minecraft:iron_pickaxe",count:1},{}]}` behind a stone wall from you (mobGriefing on): it digs through to you.
93. Hungry zombies: a zombie near a cow chases it. Hurt a zombie and drop raw beef near it: it eats and heals.
94. Jockeys: `/summon zombie ~ ~ ~ {IsBaby:1b}` a few times near cows... or set `babyZombieAnimalJockeyChance` to 1: babies arrive riding cows, pigs or sheep. `phantomSkeletonChance` 1: phantoms carry skeletons.
95. Soulbound: `/enchant @s eternalreturn:soulbound` on your sword, then die: it's back in the same slot.
96. Dispensers: put stairs in a dispenser and power it: the stairs appear in front, facing away.
97. Obsidian pressure plate: craft one (2 obsidian), wire a lamp: walking on it lights the lamp, a mob on it doesn't.
98. Pistons: push a chest full of items with a piston: it moves with everything inside. A spawner still stops the piston.
99. Chains: stand a chain upright on a block, a block on top of the chain; push the bottom block with a piston: all three move.
100. Glass item frames: craft one, put it on a chest and an item in it: only the item shows; right-click opens the chest. Dye an item frame red in a crafting grid and place it.
101. Farming: shear a vine: it stops growing. Place pink petals on a pond. Breed pigs with golden carrots: big litters. Feed a piglet a poisonous potato: sometimes it stays small for good.
102. Heads: set `tntHeadChance` to 1, `/summon zombie` a few times naturally (or wait for night): they wear TNT; kill one: it explodes. In a birch forest, find a zombie in a beehive and kill it: 3 bees come after you.
103. Lights: place a torch near a zombie (mobGriefing on): it walks over and knocks it off. Give a zombie an iron pickaxe (`/item replace entity @e[type=zombie,limit=1,sort=nearest] weapon.mainhand with iron_pickaxe`) near a lantern or a bed: it digs it out.
104. Glowing: `/effect give @s glowing`, stand behind a wall from a skeleton: it comes for you.
105. Wither skeletons: in the Nether, `/kill @e[type=wither_skeleton]` near a fortress: every one drops a skull.
106. Launch: `/enchant @s eternalreturn:launch 5` on a sword, hit a pig: it flies straight up.
107. Reeling: `/enchant @s eternalreturn:reeling 5` on a bow, shoot a cow 10 blocks away: it hops right up to you.
108. Spread of Ailments: `/enchant @s eternalreturn:spread_of_ailments`, `/effect give @s speed 30`, shoot a zombie: it gets Speed.
109. Curse of Ascension: `/enchant @s eternalreturn:ascension_curse` on anything, drop it: it floats up.
110. Endermen: watch an enderman in a plains: it leaves the grass alone.
111. Philosopher's stone: craft one, right-click glass (sand), again (glass), a pig (zombified piglin), a sheep (new colour), a creeper (charged).
112. Curses: `/enchant @s eternalreturn:visibility_curse` on a chestplate and put it on: you glow. `blindness_curse` on a helmet: blind. `heaviness_curse` on a chestplate: its tooltip weight doubles. `slipperiness_curse` on a pickaxe: dig for a while and it eventually drops out of your hand. `frailness_curse` on a sword: it wears out about half again as fast.
113. Netherite: 8 netherite ingots make a chestplate; a smithing table no longer upgrades diamond gear.
114. Minecarts: a long straight track with powered rails: a cart gets much faster than before. Craft copper rails (6 copper ingots and a stick) and lay a curve: carts take it.
115. Dynamite: craft it (2 gunpowder, paper, string), throw it at some dirt: it lands, fizzes, and blows up 2 seconds after the throw.
116. Wandering traders: none come out in the wild. Gather 3 villagers (cure zombie villagers, or `/summon villager` three times) and wait near them: a trader comes, selling asylum papers for 16 emeralds and no moss, dripstone, dripleaf, rooted dirt, allium or mangrove propagules. Right-click the papers on the ground: a villager arrives.
117. Necronomicon: craft it (philosopher's stone, a book, 7 wither skeleton skulls). Right-click the ground three times: three undead servants follow you. Hit a cow: they go for it. A fourth click: "You already have 3 servants".
118. Large mobs: set `largeVariantChance` to 1 and restart: every zombie, skeleton and creeper comes out twice the size, empty-handed. A large zombie ignores a villager next to it; a large creeper takes twice as long to blow and the crater is far bigger.
119. Pillagers at sea: set `pillagerBoatChance` to 1, restart and sail out into an ocean: within a minute a boat of two pillagers rows toward you and starts shooting.
120. Dungeons (Eternal Return world): explore caves: dungeons turn up twice as often, some of stone bricks, some of bricks. In the Nether: dungeons of nether bricks with wither skeleton, blaze or witch spawners, and nether wart growing wild on soul sand.
121. Strongholds (Eternal Return world): `/locate structure minecraft:stronghold` a few times from different places: about a third stand at ground level. Inside: illagers in the rooms, and an illusioner in the portal room.
122. Baby squid: `/summon squid ~ ~ ~ {IsBaby:1b}` in water: the small squid is drawn right where it is (it used to float off to one side).
123. No hunger: in survival there's no food bar; sprint as long as you like. Take some fall damage: your health stays down. Eat a melon slice (much quicker than bread): 1 ❤ back at once. Eat a steak: 6 ❤ come back over 10 seconds, and you get Strength for 20 s. At full health, bread won't be eaten but a cooked chicken will (it gives Speed). Hover any food to see what it heals.
