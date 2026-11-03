# Worldgen expansion brief

The owner's brief "Eternal Return: Worldgen expansion" (2026-11-01, from the PDF "Worldgen Update ER"), copied here so every session can read it. The answers the owner has given so far follow it. Where things stand: the Status section of [worldgen.md](worldgen.md).

## The brief

**Project context.** Eternal Return, Fabric 1.21.1, one mod for all features. Worldgen runs on the Eternal Return world type built on Moderner Beta (1.6.4-style continents and biomes, no deepslate). Terrain shape is generated independently of biomes. Do not change terrain shaping. Already built: biome stone variants, six cave variants, enchanting, mobs and fishing.

**Constraints.**
- Vanilla blocks and existing Eternal Return blocks only. No new blocks or items for anything in this brief.
- Everything data-driven (JSON in the mod's data folder) so features can be tuned without code changes.
- Design manifesto: features must connect to other systems, stay simple, and be learnable by playing, not by looking things up.
- Before relying on any Moderner Beta internals (biome provider, biome injection), read its source and confirm how it works. Report back if something in the brief does not fit.
- When a design question is not answered here, ask instead of inventing an answer.

**World settings (ideas; phase 5 builds them).**

| Idea | What it does | Connects to |
|---|---|---|
| Snow depth by temperature | Colder spots get deeper snow, up to full layers, instead of one layer everywhere | Cold exposure (deep snow marks dangerous cold); snow layers blocking grazing |
| Snow falls through leaves | Snow drifts down through tree canopies and leaves a little on the way | Snowy forests look lived-in; snow on slabs, stairs and walls |
| Fully frozen lakes | Any cold lake with sky exposure freezes all the way down | Ice cracking under weight |
| Cooled lava block | Lava lakes in cold biomes can generate as obsidian | Early obsidian in tundra, a reason to brave the cold |
| Biome-specific fog color | Each world has its own day and night fog tint | The saturated mood without shaders |

One caveat on frozen lakes: the temperature determines how many layers are frozen.

**Terrain features and clutter (phases 1 and 4, done).** Rock arches (landmarks for navigation), craters (shallow bowls with exposed stone and ore at the rim), lava flows with charred land (coal blocks, gravel, dead bushes, burnt logs; warmth in cold regions, fire hazard near forests), volcanoes (obsidian and lava for the cold exposure system), glaciers and ice walls (home for hardened packed ice and ice crossings), underground rivers (rivers that tunnel underground; could be a seventh cave variant linking surface water to the caves), rock spires and overgrown cliffs, tropical pillars (tall stone columns). Many custom tree shapes per wood type; boulders per biome; most biomes carry their own objects.

**Biome ideas (phase 6).**

| Biome | What makes it distinct | Climate | Fits Eternal Return because |
|---|---|---|---|
| Pumpkin Plains | Plains thick with pumpkins | Temperate | Scarecrow material right there; crop pests and birds |
| Heathland and Meadow | Low shrubs, flowers, dark meadow variant | Temperate | Bird habitat; bees and bears |
| Webbed Forest | Cobwebbed trees, more spiders | Temperate | String source; danger zone with a reason to visit |
| Spooky Swamp and Swampland Ruins | Dead trees, ruined walls | Wet | Plain stone swamp; exploration without big structures |
| Bald Cypress, Redwood, Larch, Grand Fir, Poplar, Pinyon, Black Spruce | Tree shapes based on real species | Various | Custom trees from existing wood types; bird species habitats |
| Monument Valley, Mesa Verde, Bryce Mesa, Painted Canyon, White Mesa | Different mesa styles | Dry | Hardened sandstone, terracotta colour bands |
| Desert Oasis, Desert Baobabs, Drylands | Water and trees in deserts | Dry | Survival stops in dangerous heat |
| Tropical Pillars and Jungle Cliffs, Volcano | Stone columns, volcanic jungle | Tropical | Landmarks and lava |
| Alps, Glacier, Ice Wall, Icy Mountains, Snowy Mesa | Steep, extreme cold | Cold | Cold exposure endgame areas; hardened packed ice |
| Tundra and Arctic | Flat, sparse, deep snow | Cold | Long exposed treks that test leather armor |
| Mountain Steps | Terraced mountainsides | Mountain | Distinct silhouette, good base sites |
| Floating Islands and Floating Jungle | Islands hovering over land | Fantasy | Rare wonder; use sparingly |
| Roofed Fungi Forest and Giant Mushroom Island | Huge mushrooms under a canopy | Fantasy | Mushroom food niche |
| Archipelago and Coral Ocean | Island chains, shallow reefs | Ocean | Fishing overhaul habitats |

**Spec: terrain-detected biome variants (phase 3, done).** Variants describe the terrain, never create it: peak (surface above a per-biome height), edge (narrow band between two base biomes), river (only if the world type has rivers). Real registered biomes linked to their base, inheriting everything but what they override; one JSON per variant.

**Spec: biome-dependent sapling growth (phase 2, done).** A sapling grows a tree picked from its biome's weighted table (configured feature or NBT template), vanilla growth where there is no table, worldgen uses the same tables, a greenhouse hook left as an extension point. Template leaves non-persistent with correct distances; templates respect the sapling's space check.

**Build order.** Each phase depends on the ones before it. Finish a phase, test it in a new world, and check in with the owner before moving on.

| Phase | Work | Done when |
|---|---|---|
| 1 Clutter framework | Placed feature putting NBT templates on the surface (random rotation and mirroring, density per biome in JSON), stone swapped to the biome's; 3 to 5 boulder templates | Boulders appear in every biome at tunable density, made of that biome's stone |
| 2 Biome-dependent saplings | Growth lookup and fallback; the same tables drive worldgen trees; first species shapes | The same sapling grows different shapes in two biomes, vanilla growth still works where no table exists, cut trees' leaves decay |
| 3 Biome variants | Peak, then edge, then river | F3 shows variants in the right places, they inherit from base biomes, transitions look gradual |
| 4 Terrain landmarks | Arches, spires, tropical pillars; craters with ore at the rim; lava flows with charred ground; volcanoes, glaciers and ice walls | Each appears rarely enough to feel like a find, and each uses the local stone variant |
| 5 Climate rules | Snow depth by temperature, snow falling through leaves, top-layer freezing of cold lakes, obsidian in cold lava lakes, per-biome fog tint | Cold biomes show deeper snow than cool ones, and frozen lakes still have open water below the ice |
| 6 New biomes | The biomes from the table as climate sub-biomes, with spawn weights from the mob spawning section | Each biome is reachable in a normal world and has its own decoration and tree shapes |
| 7 Ruins and underground rivers | Swamp ruins, dungeon variants, small surface ruins; underground rivers as a cave variant | Ruins are rare and small, and underground rivers connect to surface water |

**Phase 5 contradictions to ask the owner about.** The world settings table says cold lakes freeze "all the way down"; the build order says "top-layer freezing" and "open water below the ice"; the caveat says the temperature decides how many layers freeze. The fog row says "each world has its own" tint while the build order says "per-biome fog tint".

## The owner's answers so far

- Process: one phase per session, then check in. Changing vanilla gameplay is fine (players aren't meant to play the normal game with this pack); keep the owner's designs over vanilla mechanics. Worlds other than Eternal Return must still not change (CLAUDE.md).
- Phase 1: boulder templates made by a script (the owner may add hand-built ones); land biomes only; boulders only plain stone, granite, diorite or andesite; none in desert, swamp, autumnal forest or badlands; cold biomes get plain stone boulders.
- Phase 2: sapling tables in Eternal Return worlds only; bone meal follows the table; every wood that generates in these worlds gets several species inspired by real trees (the brief's list is only examples); a sapling only ever grows its own wood. Palms (beaches, islands, archipelagos, tropical biomes), mangroves and pale oak get their biomes in phase 6: decide the placement then, don't ask again.
- Phase 3: peak height per base biome; edge band about 32 blocks; edges dry forest, taiga, jungle, desert scrub, swamp; rivers sandy, rocky, clay, leafy (the ice plains keep the frozen river). Moderner Beta's fractal biome layers have no climate noise, so edges come from the layers' neighbour check (the owner accepted this).
- Phase 4: rarity by size (small landmarks about one per 600 x 600 blocks, lava flows one per 1,000, volcanoes and glaciers one per 2,000); biomes "anywhere fitting"; real lava with a charred margin; volcanoes huge and rare. Checked in game by the owner on 2026-11-02: "looks good".
- Phase 5 (asked 2026-11-03, all four recommended options): cold water freezes thicker with the cold (1 to 3 layers) but the bottom 2 blocks of water never freeze, and cold lava lakes get an obsidian crust 1 to 2 thick over real lava; fog is per biome, a day and a night colour from one JSON table, blending across borders, unlisted biomes keep vanilla fog; everything in Eternal Return worlds only; snow depth is set at generation and storms pile snow back up to the local depth (snow also sifts through leaves in storms).
