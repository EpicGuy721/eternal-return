# Eternal Return (Fabric 1.21.1)

A total conversion for Minecraft. So far it covers three systems: a rework of enchanting built around capacity, material identity, and libraries of books instead of XP; tougher overworld mobs (baby skeletons and creepers, better-armed zombies and skeletons, new jockeys, cave-only creepers); and fishing as the emerald economy (bait, eleven new fish, lava fishing in the Nether, and fishermen as the only villagers who still pay emeralds). Worldgen has its own world type, Eternal Return: Release 1.6.4's continents and oceans with rolling, hilly Beta-style land and a new cave system, built on Moderner Beta.

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
| `runGametest` | flat | the mob, fishing and trade suites, a check that the Eternal Return world type loaded, the hardened blocks, and every ore variant against its vanilla ore (drops, Fortune, Silk Touch, experience, tool tier, smelting, tags, creative tab, redstone light, overlay textures) |
| `runGametestNoModernerBeta` | flat, Moderner Beta removed | the same suites, and a check that the world type is skipped cleanly |
| `runGametestModernerBeta` | Moderner Beta, Release 1.6.4 amplified | worldgen checks (debug tunnel on, trial chambers present), carving unchanged |
| `runGametestModernerBetaTunnelOff` | same | the same with the debug tunnel off |
| `runGametestModernerBeta164` | Moderner Beta, Release 1.6.4 | carving unchanged; cave density for comparison |
| `runGametestModernerBetaBeta` | Moderner Beta, Beta 1.7.3 | carving unchanged |
| `runGametestVanilla` | vanilla generator | worldgen checks (debug tunnel on, trial chambers present), carving unchanged |
| `runGametestEternalReturn` | Eternal Return, seed 173164 | mineshaft woods by biome (and real ones built from them), all nine oceans, cherry groves and ocean monuments present, no ocean biomes on dry land, overhangs without flat bottoms, world height, biome list, no cave biomes, no deepslate, badlands surface, no trial chambers; ore counts per ore over squares in ten biomes within 1% of the baseline recorded before the ore variants (`src/gametest/resources/ores/`), no stone-textured ore inside host stone, variants open to caves in every host, an emerald survey of the highest extreme hills, Moderner Beta hooks applied; debug tunnel on |
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

### Worldgen

The plan, status, terrain settings and maps are in [docs/worldgen.md](docs/worldgen.md). Code lives in `com.eternalreturn.worldgen`; the world type itself is data only.

| Feature | Implementation |
|---|---|
| Eternal Return world type (needs Moderner Beta) | `data/eternalreturn/worldgen/world_preset/eternal_return.json`, settings preset `data/eternalreturn/moderner_beta/settings_preset/eternal_return.json`, noise settings `data/eternalreturn/worldgen/noise_settings/eternal_return.json`; all generated by `tools/worldgen/build_preset.py` from `tools/worldgen/knobs.json` |
| Biome list: Release 1.6.4 plus birch forest, savanna, badlands, dark forest, cherry grove and every modern ocean | biome layers in the settings preset |
| No lush caves, dripstone caves or deep dark | settings preset (`caveBiomeSettings`: none) |
| No deepslate | settings preset (`deepslate_generation` off); tuff moved from block tag `minecraft:deepslate_ore_replaceables` to `minecraft:stone_ore_replaceables`, so ores in tuff blobs stay stone ores |
| No trial chambers in Eternal Return worlds | settings preset (`structure_modifiers.removed`) |
| Cave engine: eight cave types, only in Eternal Return worlds | `com.eternalreturn.worldgen.caves` (`CaveSystemCarver`, `CaveTypes`, one class per type in `types/`); `worldgen/configured_carver/caves.json`; block tag `eternalreturn:carvable` |
| Old caves off in Eternal Return worlds | `CarverContextMixin` (which world), `ConfiguredCarverMixin` (skips other carvers) |
| Biome stone: each biome's stone from under the topsoil to bedrock, only in Eternal Return worlds | table `biome_stone` in `tools/worldgen/knobs.json`, written into the noise settings' surface rules by `build_preset.py`; `BiomeStoneCondition` (the on/off switch) |
| Hardened sandstone and hardened packed ice | `com.eternalreturn.worldgen.stone.HardenedBlocks` (one entry per block); client models `HardenedBlockModels`; block tag `eternalreturn:hardened_stones` |
| Ore variants: every ore in every host stone | table `src/main/resources/eternalreturn/ore_variants.json` (read at startup by `com.eternalreturn.worldgen.ores.OreVariants`); models, textures, loot tables, recipes and tags generated by `tools/ores/build_ores.py`; placed by `OrePlacement` through `OreFeatureMixin` and `ScatteredOreFeatureMixin`, Eternal Return worlds only; client `OreVariantRendering` |
| Cherry groves and every modern ocean (deep, warm, lukewarm, cold, frozen) | biome layers in the settings preset (`build_preset.py`; the ocean steps come from Moderner Beta's Release 1.17.1 preset) |
| Mineshafts built from their biome's wood; mineshafts in Moderner Beta's biomes | `com.eternalreturn.worldgen.structures.MineshaftWoods`; `MineshaftStructureMixin`, `MineshaftPartMixin`, `MineshaftPiecesMixin`, `StructureMixin`; biome tags `eternalreturn:mineshaft_wood/<wood>`, `eternalreturn:has_structure/mineshaft` |
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
| Thriftiness (rod keeps its bait) | `thriftiness.json` (effect `eternalreturn:bait_saving_chance`), `Thriftiness`, `Bait.consumeAfterCatch` |
| Eleven new fish | `FishingItems`; loot table `eternalreturn:gameplay/fishing/fish` |
| Worms from digging | `Worms`; block tag `drops_worms` |
| Chum, glow bait, infernal bait | recipes in `data/eternalreturn/recipe/` |
| Lava fishing in the Nether | `FishingBobberEntityMixin`, `FishingLoot`; loot table `eternalreturn:gameplay/lava_fishing` |
| Villagers stop paying emeralds, except fishermen for fish | `TradeRules`, `VillagerEntityMixin`, `BuyItemFactoryAccessor` |

## How the systems behave

### World type

Pick **World Type: Eternal Return** on the create-world screen (it is also in Moderner Beta's own preset list, in its own category). Oceans and continents are laid out like Release 1.6.4, but the land rolls and climbs like Beta 1.7.3 almost everywhere: mostly rolling and hilly ground around y 65-90, hills to about y 115, mountains up to about y 180, with Beta-style cliffs and the odd overhang (whose underside arches and bulges rather than lying flat, see [docs/worldgen.md](docs/worldgen.md)). Swamps stay flat and marshy, as in 1.6.4. Sea level is 63, bedrock is at -64, and below y=0 the stone simply carries on (the biome's stone, see below) with the usual ores: no deepslate layer and no lush caves, dripstone caves or deep dark. Biomes are Release 1.6.4's (with its hills and shores) plus birch forest, savanna, badlands, dark forest and cherry grove, and every modern ocean: deep oceans, and warm, lukewarm, cold and frozen oceans (with deep versions), as in 1.13 and later. Deep oceans bring ocean monuments, and Moderner Beta's smaller ocean shrines turn up too. There are no trial chambers. The Nether and the End are vanilla.

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

### Mobs

**Babies.** 5% of skeletons, strays and creepers spawn as babies: half size and 50% faster, otherwise the same as adults (same health, drops and XP). Skeletons and strays get the baby-zombie body proportions, and baby skeletons that freeze become baby strays. Baby creepers have half the fuse (0.75 s instead of 1.5 s) and about a third of the blast power: a zombie two blocks away takes a scratch where an adult's blast kills it. Saved as `IsBaby`, the same key baby zombies use.

**Armor.** Zombies, husks, zombie villagers, skeletons, strays and bogged get armor 25% of the time in a newly visited area, rising to 75% in long-inhabited ones (vanilla: 0% rising to 15%, so early-game mobs never had any). Each piece is enchanted 20% rising to 80% of the time (vanilla: 0% to 50%). Which pieces and materials they get is unchanged, and enchantments still roll inside each item's capacity.

**Cave-only creepers.** A natural creeper spawn needs a spot that no sky light reaches, so creepers never spawn on the surface but still spawn in caves (and in dark, fully enclosed builds). Spawners, spawn eggs and commands are unaffected. Vanilla does not replace a failed creeper spawn with another mob, so nights on the surface have roughly a fifth fewer hostile spawns overall.

**Jockeys.** Spiders keep vanilla's 1% jockey chance, and cave spiders (which vanilla never gives riders) get one 2% of the time, mostly from mineshaft spawners. The rider is picked from the biome's own spawn list: strays in snowy biomes, husks in deserts, skeletons elsewhere. A husk rider fights in melee from the spider's back. 5% of strays spawn riding a polar bear, which the stray steers. The bear despawns like a hostile mob, the way a chicken jockey's chicken does, so mounts don't pile up in snowy biomes.

**Fishing-rod zombies.** 5% of zombies spawn holding a fishing rod. When their target is 4-16 blocks away and in sight, they cast. A moment after the hook catches, they yank the target toward themselves, then recast every 3-5 seconds. Drowned that spawn with vanilla's decorative fishing rod use it too.

**Ender-pearl zombies.** 1% of zombies carry an ender pearl in their off hand. When their target is 12-40 blocks away and in sight, they throw it at the target and teleport there. It is a single use.

**Skeleton swords.** 10% of skeletons, strays and bogged carry a stone or iron sword in their off hand. When the target comes within 4 blocks they swap it into their main hand and fight in melee. Once the target is 7 blocks away they switch back to the bow.

**Boats.** Any monster stuck in a boat starts hitting it after a second and breaks it a moment later, dropping the boat. This includes zombie villagers, so trapping one in a boat to cure it no longer works. Use a cell instead.

### Fishing

**Bait.** Fish only bite if you carry bait. Without it you still reel in junk and treasure. The rod uses the first bait it finds in your off hand, then main hand, then inventory (the order a bow looks for arrows), so put the bait you want in your off hand. One bait is used each time you reel something in, never when you cast, and creative players don't use any. Every bait shortens the wait for a bite, stacking with Lure. However much the two add up to, the wait never drops below 1 second. Without that floor, vanilla stops fish biting entirely once the reduction passes 30 seconds, which Lure V with any bait reaches.

| Bait | Wait shortened by | Where it comes from | Special |
|---|---|---|---|
| Worm | 5 s (same as Lure I) | 4% chance when digging dirt, grass blocks or mud | |
| Chum | 10 s | 1 raw fish + 1 bone meal = 4 chum | Best all-round bait |
| Glow bait | 5 s by day, 15 s at night | 1 slime ball + 1 glowstone dust = 4 | Best bait at night; the only bait anglerfish take |
| Glistering melon slice | 5 s | vanilla recipe | Doubles uncommon fish, triples rare ones; the only bait koi take |
| Infernal bait | 5 s | 1 rotten flesh + 1 magma cream = 4 | The only bait that works in lava |

**Fish.** All eleven are eaten raw and fill like raw salmon. "Common" means the same odds as cod, "uncommon" a third of that, and "rare" a twelfth. Vanilla cod, salmon, tropical fish and pufferfish still bite at their usual odds. Fishermen buy all of them at double the emeralds they would normally pay: 2 per 15 cod, 2 per 13 salmon, 2 per 6 tropical fish, 2 per 4 pufferfish.

| Fish | Rarity | Where | Fisherman pays |
|---|---|---|---|
| Perch | common | anywhere | 2 emeralds per 15 (novice) |
| Pike | uncommon | anywhere | 2 per 6 (apprentice) |
| Catfish | uncommon | swamps and mangrove swamps, including Moderner Beta's swamps | 2 per 6 (apprentice) |
| Tuna | uncommon | ocean biomes | 2 per 5 (journeyman) |
| Icefish | uncommon | frozen rivers and oceans, snowy and icy biomes, peaks, and Moderner Beta's ice plains, tundra and cold taiga | 2 per 6 (journeyman) |
| Electric eel | rare | ocean biomes | 4 each (expert) |
| Anglerfish | rare | any ocean biome with at least 10 blocks of water under the hook; glow bait only | 6 each (expert) |
| Koi | rare | anywhere, glistering melon only | 10 each (master) |
| Magma eel | common | Nether lava | 2 per 10 (apprentice) |
| Ashfin | uncommon | lava in basalt deltas | 2 per 4 (journeyman) |
| Soulfin | rare | lava in soul sand valleys | 4 each (expert) |

"Ocean biomes" means vanilla's oceans plus Moderner Beta's own ocean biomes (biome tag `ocean_waters`). The water-depth rule counts straight down from the hook, and the minimum is the `anglerfishMinWaterDepth` config value.

Nether fish and infernal bait are fireproof. The new fish also count for the "fish caught" statistic and can be fed to dolphins.

**Thriftiness.** A new fishing rod enchantment, levels I to V. Each catch has a 10% chance per level (50% at V) to leave your bait unused, in water and lava alike. It turns up in enchanting tables, loot, and librarian trades like any other rod enchantment, and its levels count toward the rod's 7-point capacity, so Thriftiness V and Lure V can't share a rod.

**Lava fishing.** In the Nether, a hook cast into lava floats and gets bites like it does in water, with lava pops and flames instead of bubbles and splashes. A roof normally slows bites, but the Nether's ceiling doesn't. Catches are protected so they don't burn on the way to you. Without infernal bait you only get junk (magma blocks, soul sand, bones, blackstone, basalt, rotten flesh) and treasure. Treasure, from most to least common: gold nuggets and ingots, glowstone dust, quartz, crying obsidian, enchanted books, blaze rods, ghast tears, netherite scrap. Luck of the Sea and Lure work as usual. Outside the Nether, hooks sink in lava as in vanilla unless `lavaFishingOutsideNether` is on.

**Villagers.** When a villager unlocks new trades, any trade where it would pay you emeralds is left out of the pick, except fishermen buying raw fish. This also applies with the experimental Villager Trade Rebalance on. Villagers still get two trades per level where enough are left, but levels that only ever offered buying unlock nothing (the butcher from journeyman up, for example). Villagers that already rolled their trades keep them, so test with new ones.

## Configuration

`config/eternalreturn.json` is generated on first launch.

- `enchantCaps`: item tag to capacity, first match wins.
- `inherentEnchantments`: item tag to `{enchantment: level}`.
- `pointsRefundedPerCurseLevel`, `raiseMaxLevels`, `raisedMaxLevel`.
- `disabledEnchantments`: never rolled by tables, loot, mobs, or villagers.
- `bookshelfBias`: the multipliers above, plus `booksCanUnlockNonTableEnchantments` (off by default) to let shelved books make the table offer treasure enchantments like Frost Walker.
- `fortune`: toggles for hoes and shears.
- `fishing`: `fishRequireBait`; `baits` (item to `lureSeconds`, `nightLureSeconds`, `lava`); `wormDropChance`; `anglerfishMinWaterDepth` (default 10); `lavaFishingOutsideNether`.
- `villagers`: `onlyFishermenBuy`; `fishPriceMultiplier` (default 2, applied to every fish a fisherman buys); and `fishermanTrades` (item to `level`, `count`, base `emeralds`, `maxUses`, `experience`; needs a restart).
- `worldgen`: `debugTunnel` (off by default). Carves one straight 3x3 tunnel at y=20 along z=8 through every chunk on that row, to check that this mod's carvers run under the current world generator. Needs a restart, and only affects newly generated chunks.
- `worldgen.biomeStone` (on by default; Eternal Return worlds only, newly generated chunks only): each biome's stone under the topsoil. Off: plain stone everywhere.
- `worldgen.roundOverhangs` (on by default; Eternal Return worlds only, newly generated chunks only): overhang undersides arch and bulge instead of lying almost flat. Off: the terrain as Moderner Beta makes it.
- `worldgen.caves` (Eternal Return worlds only; newly generated chunks only): `enabled` (the new caves); `oldCaves` (off: vanilla's, Moderner Beta's and other mods' cave carvers don't run there); `density` (multiplies every weight); per type under `types` (`spaghetti`, `ravine`, `large`, `vertical`, `zigzag`, `ribbed`, `spiral`, `toroidal`): `enabled`, `weight` (caves starting per 100 chunks), `minY` and `maxY` (where a cave starts), `minRadius` and `maxRadius`, and for `large` a `typicalRadius` (the most common size; a few caverns reach toward `maxRadius`); `spiral` and `toroidal` also have `minSize` and `maxSize` (the spiral's coil radius, the room's ring radius). Config files from before the first cave tuning are reset to the new type defaults once (`typesVersion`). Debug: `debugForceCaveType` (a type id: only that type, at a high rate) and `debugLogCaveStarts` (writes `type,x,y,z` of every cave start to `eternalreturn-cave-starts.csv` in the game folder).
- `mobs`: baby chance, baby speed, and baby creeper fuse and blast multipliers; armor and armor-enchant chances, each written as `base + perDifficulty x clamped local difficulty`; the underground-creeper toggle and its sky-light limit; jockey, fishing rod, ender pearl and sword chances; fishing rod pull strength; the boat-breaking toggle.

Which items belong to each tier is controlled by tags in `data/eternalreturn/tags/item/`, so modded gear can be added with a datapack and no code changes. The same goes for mobs: `data/eternalreturn/tags/entity_type/` decides which mobs can be babies (`baby_variants`, which only works for skeleton-type mobs and creepers), which get the boosted armor (`armored_spawns`), and which can ride spiders (`jockey_riders`).

The block tag `eternalreturn:carvable` lists what caves can cut through (vanilla's `overworld_carver_replaceables` and anything added to it).

Fishing data lives in the same place. The item tag `raw_fish` is what fishermen buy and what chum accepts. The block tag `drops_worms` sets where worms come from. The biome tags `catfish_waters`, `icefish_waters` and `ocean_waters` set where those fish live. Their Moderner Beta entries are optional, so the tags load with or without it. Catch odds are in `data/eternalreturn/loot_table/gameplay/`.

## Compatibility notes

- Overrides `fortune`, `mending`, `quick_charge`, and replaces the `tradeable`, `on_random_loot`, and `exclusive_set/armor` enchantment tags. Another datapack replacing those same files will conflict.
- `generateEnchantments` is only taken over for capped items, items with inherent rules, or when shelves are biasing. Everything else uses vanilla's code untouched.
- The enchanting and anvil screen tweaks are cosmetic and set to `require = 0`, so a UI mod that touches the same screens cannot crash the game over them.
- Vanilla's 1% skeleton-only spider jockey roll is replaced by the mod's own roll (same default chance). A mod that edits that vanilla roll will see no effect.
- Adds one entity, `eternalreturn:zombie_hook`, 50 blocks (the two hardened blocks and 48 ore variants) and 65 items, so the mod must be installed on both client and server.
- Replaces the `minecraft:gameplay/fishing` loot table (fish now need bait and come from the extended fish table) and adds to the `minecraft:fishes` item tag. Mods that add fish to `minecraft:gameplay/fishing/fish` through Fabric's loot API still work, since that table is used unchanged inside the new one.
- The trade filter recognizes vanilla's two buy-trade types, including inside the trade rebalance's per-biome wrappers. Buy trades that other mods add with their own trade classes are not detected and stay.
- Sodium: no overlap.
- Moderner Beta (5.0.0-alpha.2): tested with its Release 1.6.4, 1.6.4 amplified and Beta 1.7.3 presets, and with the Eternal Return world type built on it. Its chunk generator runs carvers attached to biomes through Fabric's biome modifications, so this mod's carvers work in its worlds without any hook. The fish biome tags include its biomes as optional entries.
- Without Moderner Beta: the Eternal Return world type and its noise settings are skipped at load (Fabric load conditions) and the optional tag entries are dropped, so the game starts and existing non-Eternal Return worlds load normally. A world created with the Eternal Return type needs Moderner Beta to open.
- Adds tuff to the vanilla block tag `minecraft:stone_ore_replaceables` and replaces `minecraft:deepslate_ore_replaceables` with deepslate alone, so in every world, ores that generate inside tuff blobs use their stone variant instead of the deepslate one. A mod that adds blocks to `deepslate_ore_replaceables` may lose them, depending on load order. Adds the world type to `minecraft:normal` (the create-world list) and the preset to Moderner Beta's `selectable` category tag.
- Caves: in Eternal Return worlds, only this mod's carvers run (vanilla's, Moderner Beta's and other mods' carvers are skipped) unless `worldgen.caves.oldCaves` is on. Other worlds are untouched (checked against fingerprints of vanilla and Moderner Beta worlds). A world counts as Eternal Return by its noise settings, so an Eternal Return world whose generator settings were replaced by hand would carve normally.
- Ore variants: added to the vanilla tags `minecraft:<ore>_ores` (blocks and items; Fabric's `c:ores` tags include them through those), `minecraft:mineable/pickaxe`, `minecraft:needs_stone_tool` and `minecraft:needs_iron_tool`. The ore placement hook and the lake change only act in Eternal Return worlds with biome stone on; ores from other mods' ore features become variants there too when they land in a host stone, but only if their ore block is in the table. The vanilla ore tags `minecraft:stone_ore_replaceables` (vanilla plus tuff) and `minecraft:deepslate_ore_replaceables` (deepslate only) are otherwise as before.
- Mineshafts: in Eternal Return worlds each mineshaft piece saves its wood under the key `eternalreturn_wood`; without this mod such a piece falls back to vanilla's oak or dark oak. Other worlds' mineshafts are untouched.
- Biome stone: adds sandstone, red sandstone, granite, diorite and andesite to the item tags `minecraft:stone_tool_materials` and `minecraft:stone_crafting_materials`, and replaces vanilla's dispenser, dropper, lever, observer and piston recipes with versions taking that tag, in every world. A datapack replacing those recipes undoes this. Overrides the configured features `spring_water`, `spring_lava_overworld` and `glow_lichen` (only adding the two hardened blocks), and adds the hardened blocks to `minecraft:mineable/pickaxe`, `minecraft:base_stone_overworld` and `minecraft:stone_ore_replaceables`; none of this changes worlds without the hardened blocks. Red sandstone is not added to any ore tag: ores treat it as stone only in Eternal Return worlds. Uses an access widener for the surface-rule condition. With Moderner Beta installed, two small mixins into its surface pass (by class name, applied only when it is present) make that pass treat the host stones as stone in Eternal Return chunks; other presets are untouched.
- Moderner Beta's optional "Reduced Height" datapack moves the overworld floor to y=0 for every world, which cuts off the bottom of Eternal Return worlds. Its "Deepslate Blobs" datapack adds deepslate back. Leave both off.
- Nostalgic Tweaks: if you use its XP removal, this mod is already XP-free. If it has options that change the anvil or enchanting screens, check them alongside this.

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

17. Fish with no bait: only junk and treasure come up. Carry worms: fish bite, and one worm is used per catch (none when casting).
18. Dig dirt and grass for a while: now and then a worm drops.
19. Craft chum (any raw fish + bone meal), glow bait (slime ball + glowstone dust) and infernal bait (rotten flesh + magma cream): 4 of each.
20. `/give @s eternalreturn:koi` and the other fish names to see the new textures. Fish are in Food & Drinks after the pufferfish, and bait is in Tools & Utilities after the fishing rod.
21. Nether, with infernal bait: cast into a lava lake. The hook floats, lava pops trail toward it, and the catch flies out without burning. Magma eels are the common fish.
22. A new fisherman buys fish at double price (15 perch or 15 cod for 2 emeralds at novice). A master fisherman buys koi for 10 emeralds. New masons, farmers, librarians, etc. buy nothing. Villagers that rolled their trades before this update keep their old prices.
23. A Lure V rod with any bait still gets bites, fast.
24. `/enchant @s eternalreturn:thriftiness 5` on a rod, then fish with a stack of worms: roughly half the catches leave the stack unchanged.
25. Anglerfish: with glow bait, fish in open ocean (in the Eternal Return test world below, `/tp @s 24 63 0` has 13 blocks of water) and an anglerfish turns up now and then. Cast into shallow water near a shore: none.

World type (needs Moderner Beta): create a creative world with World Type "Eternal Return" and seed `173164`, then `/tp @s X 320 Z` to each spot and fly down. Heights are from the test world.

26. Anywhere on land: rolling hills and short climbs most of the way, not long flat stretches broken by sudden giant mountains. No 200-block spires, and floating rock is rare.
27. `2376 2344`: the highest ground within 3,000 blocks of spawn, a savanna peak at about y 161.
28. `968 -2296`: high extreme hills, about y 143 (where the emerald survey looks). `712 -568`: rolling extreme hills. `/tp @s 734 65 -592` puts you on the ground under an extreme hills overhang whose underside is about 30 blocks up: look up and around, it arches and bulges instead of lying flat (on worlds made before this update, overhang undersides are almost flat).
29. `-1208 584`: badlands with terracotta bands, at about y 71.
30. `-2040 1160`: a mushroom island. `-376 -376`: ice plains (a snowy overhang). `1416 264`: dark forest. `-608 192`: a cherry grove, a raised, hilly stretch of pink cherry trees and petals.
31. Anywhere: F3 never shows `lush_caves`, `dripstone_caves` or `deep_dark`. Dig down to y -60: the biome's stone all the way (with the usual granite, diorite, andesite and, below y 0, tuff blobs), bedrock at -64, ores like diamond and redstone in their normal stone texture, no deepslate.
32. With World Type "Moderner Beta", its Customize screen lists Eternal Return in its own category, with its icon.

Oceans and structures (same world; a new one if it was made before this update). `/tp @s X Y Z`, in spectator mode with night vision underwater:

33. Oceans, all at sea level (y 63): `392 63 -376` warm ocean (sand floor, coral and sea pickles); `200 63 -248` lukewarm; `-504 63 456` cold; `-632 63 1096` frozen (icebergs and ice); `136 63 136` deep ocean, deeper than the rest; `-568 63 968` deep frozen. F3 names each biome.
34. `/locate structure minecraft:monument`: the nearest ocean monument, in a deep ocean (the test world has one around `240 50 352`), with its guardians.
35. `/locate structure minecraft:mineshaft` from inside a biome, then `/tp` there in spectator mode: the supports and planks are that biome's wood. In the test world: oak `-401 26 -568` (under ice plains), birch `-401 -26 -759`, spruce `817 -33 1133`, jungle `3 -1 -1354`, acacia `1828 -46 -18`, dark oak `1304 -38 -302`, cherry `-539 12 -1664` (the centre of each mineshaft; look around for the corridors).

Caves (same world; a new world, since chunks generated before this update keep their old caves). `/gamemode spectator`, `/effect give @s night_vision infinite`, then `/tp @s X Y Z` puts you inside each cave, all within about 150 blocks of each other:

36. `-1283 -11 -1127`: spaghetti, 1.6.4-style tunnels that swell in the middle and fork; follow them up and down.
37. `-1286 15 -1140`: a ravine, a narrow, very tall canyon with stepped, jagged walls.
38. `-1200 -23 -1094`: a large cavern with uneven walls, a rolling floor and tunnels leading off. For a giant one, `/tp @s 356 -17 4290`: about 190 blocks across and 55 tall (far from the others).
39. `-1275 51 -1129`: the top of a vertical shaft; look down, then follow it to the tunnels at the bottom.
40. `-1338 37 -1196`: a zig-zag tunnel: straight runs with sharp alternating turns.
41. `-1304 -3 -1146`: a ribbed tunnel that bulges and pinches every few blocks.
42. `-1298 37 -1181`: the top of a spiral; follow it down as it winds two to four turns around a solid column of rock.
43. `-1166 -36 -1214`: inside a toroidal room; follow the ring round, with the pillar standing in the middle.
44. Anywhere: no water pours into caves from oceans or rivers, caves at y -56 and below hold lava, and there are no trial chambers (`/locate structure minecraft:trial_chambers` finds none).

Biome stone (same world, a new one if it was made before this update; survival for the drops, `/gamemode creative` to look around). `/tp @s X Y Z`, then dig straight down with a pickaxe; F3's "Targeted Block" names what you are looking at:

45. `-376 74 -952` (plains): grass, dirt, a coal ore, then plain stone from y 68 (with granite, gravel and dirt blobs further down). Stone drops cobblestone, as before.
46. `-632 76 392` (forest): dig down through the oak, then grass, dirt, then andesite from y 65, all the way down (a granite blob at y 32 to 37, a cave at y 12). Mining it drops andesite.
47. `-504 80 -696` (birch forest): grass, dirt, then diorite from y 76. Drops diorite.
48. `1032 90 328` (jungle, on the canopy): dig down through the tree, then grass, dirt, then granite from y 67. Drops granite.
49. `264 68 -696` (desert): sand, then ordinary sandstone (the desert's topsoil) down to y 54, an andesite blob, then hardened sandstone from y 51. It looks the same as the sandstone above, but F3 names it `eternalreturn:hardened_sandstone` and it takes about twice as long to mine. It drops plain sandstone, with or without Silk Touch.
50. `-1208 71 584` (badlands): terracotta bands, then red sandstone from y 48. Drops red sandstone.
51. `-376 90 -376` (ice plains, on a snowy overhang): dig through the overhang into the hollow under it, then grass and dirt from y 74, then hardened packed ice from y 68. It looks like packed ice, drops packed ice (Silk Touch too), isn't slippery to walk on, and doesn't melt next to a torch.
52. Hit hardened sandstone or hardened packed ice with your hand or a shovel: it breaks slowly and drops nothing. In the creative inventory, Natural Blocks shows Hardened Sandstone after Sandstone and Hardened Packed Ice after Packed Ice.
53. Crafting: a stone pickaxe from three sandstone (or red sandstone, granite, diorite, andesite) and two sticks; a furnace from eight andesite; a lever from a stick and a piece of granite; a piston from planks, an iron ingot, redstone and diorite. Packed ice makes none of them.
54. In any host stone, granite, diorite, andesite, dirt and gravel blobs turn up as before. The map at `docs/worldgen-maps/stone/stone_y30.png` shows a cut at y 30.

Ore variants (same world, made after this update; `/gamemode spectator` and `/effect give @s night_vision infinite`, then `/tp @s X Y Z` puts you in a cave with the ore in the wall next to you; `/gamemode survival` there to mine it):

55. `-657 -4 361` (forest): andesite iron ore under your feet. Mine it with a stone pickaxe: raw iron. A wooden pickaxe gets nothing.
56. `-482 -27 -724` (birch forest): diorite gold ore under your feet. An iron pickaxe gives raw gold; Silk Touch gives Diorite Gold Ore itself. `-526 12 -720`: diorite lapis ore just north of you.
57. `995 -45 288` (jungle): granite gold ore just west of you, at `994 -45 288`; `1013 -55 299`: granite redstone ore under your feet. Walk on it or right-click it and it lights up with red specks, like redstone ore.
58. `-1203 -4 599` (badlands): red sandstone gold ore under your feet. Badlands have about twice the gold of other biomes.
59. `237 20 -724` (desert): hardened sandstone iron ore just north of you, at `237 20 -725`; `229 -51 -734`: hardened sandstone diamond ore just west of you.
60. `-387 -2 -383` (ice plains): hardened packed ice iron ore west of you; `-354 -29 -391`: hardened packed ice diamond ore under your feet.
61. Emerald: andesite emerald ore turns up only in high extreme hills (y 95 and up), around `968 150 -2296` for example (fly around the peaks); it is rare.
62. Smelt or blast any variant: the same result, experience and time as its vanilla ore.
63. Creative inventory, Natural Blocks: after each deepslate ore come its six variants. Or `/give @s eternalreturn:granite_iron_ore` (any of `andesite`, `diorite`, `granite`, `red_sandstone`, `hardened_sandstone`, `hardened_packed_ice`, then `_`, then `coal`, `iron`, `copper`, `gold`, `redstone`, `lapis`, `diamond` or `emerald`, then `_ore`), for example `/give @s eternalreturn:hardened_sandstone_gold_ore 16` or `/give @s eternalreturn:hardened_packed_ice_lapis_ore 16`.
64. Old-style pack on and off: with Golden Days (or any old-style pack) on, the host stone under each ore shows the pack's texture and the ore blobs keep this mod's old-style overlays. Turn it off in Options, Resource Packs (move it back to Available, then Done): the host stones go back to vanilla's textures, the ore blobs stay the same, and vanilla's own stone ores show vanilla's modern textures again.
