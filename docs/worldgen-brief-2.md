# Worldgen expansion brief 2

The owner's brief "Eternal Return: Worldgen expansion 2" (2026-11-08, from the PDF of that name), copied here so every session can read it. It follows the first brief ([worldgen-brief.md](worldgen-brief.md)); read that, [worldgen.md](worldgen.md) and [working-notes.md](working-notes.md) first. The owner's answers follow the brief.

## The brief

Six worldgen features so exploring keeps turning up something memorable: biome personality rolls, wild crop patches, hot springs with geysers, cacti that connect sideways, elder trees, and a larger ocean expansion.

**Already built and relevant:** biome stone, biome trees (saplings grow from each biome's tree table), peak, edge and river variants, sub-biomes in four batches, terrain landmarks, climate rules (snow depth, thick ice, fog tints), the stepped deep oceans and sinkholes, cold exposure, crop biomes, birds, bears, fishing.

**Constraints.**
- No new items, so no new blocks either. Changing how an existing block behaves (cactus connections) is allowed.
- Do not change terrain shaping. Anything that looks like terrain (seamounts, icebergs, atolls) is laid on as a landmark or feature, as batch D's islets and reefs were.
- Eternal Return worlds only; every other world must not change (CLAUDE.md; the flat runs and control fingerprints keep checking this).
- Data-driven: tables in the mod's data folder, tunable without code. Every feature gets its own on/off setting under `worldgen` in the config.
- Design manifesto: features connect to other systems, stay simple, and are learnable by playing, never by looking things up.
- One phase per session; ask design questions up front with a recommended option; check visuals in game; check in with the owner before the next phase. Ask rather than invent answers. Reports in plain language.

### 1. Biome personality rolls

Each patch of a biome rolls small variations so no two patches look identical, but every patch still reads at a glance as its biome. A roll only reweights or picks from what the biome already has; it never brings in anything foreign.

- **Identity (never rolled):** ground blocks, stone, climate, mob spawns, signature trees and signature plants. A redwood forest always has redwoods and a podzol floor; a desert always has sand and cactus; a meadow is always thick with flowers.
- **Personality (rolled):** small shifts inside that identity, from the options the table lists.

| Trait | What it changes | Limit that keeps the biome recognizable |
|---|---|---|
| Canopy mix | Reweights the biome's own tree table | Only species already in the table; the signature species stays at least half of the trees |
| Flower palette | Picks a subset of the biome's own flowers | Two to four of its flowers, never one it lacks; flower amount unchanged |
| Density | Shifts trees, grass and clutter | Within about 25% of normal; a forest never becomes a plain, a plain never a forest |
| Accent | One small extra from the biome's own accent list | Hand-written lists per biome: a pond or boulder ring in a forest, a fallen spruce in a taiga, a dry wash in a desert |
| Tint | Grass and foliage colour | A small shift; the colour stays clearly the biome's |
| Bird mix | Which of the biome's birds are common | Only birds the biome already has |

- **Rare rolls:** about one patch in twenty makes the biome more itself, never less (the densest, oldest-looking forest, a meadow at the top of its flower range, a desert with the most cactus).
- **Where:** one roll per 256-block cell (the cells the sub-biomes use). Peak, edge and river variants take their patch's roll; sub-biomes roll from their own tables.
- **Connections:** saplings follow the patch's canopy mix; crop pests come from the patch's bird mix; with hidden coordinates and random spawns, patches become how players name places ("the cornflower meadow").
- **Done when:** two patches of the same biome visibly differ, both are instantly recognizable, and no patch shows a tree, flower, bird or block its biome lacks.

### 2. Wild crop patches

Crops grow wild in small patches on farmland, only in each crop's ideal biome, so players learn the crop biome system by finding them. With villages gone, they are the main natural source of carrots, potatoes and beetroot.

| Crop | Grows wild in |
|---|---|
| Wheat | Plains, meadow, savanna |
| Carrots | Forest, birch forest, dark forest, cherry grove |
| Potatoes | Taiga, extreme hills |
| Beetroot | Ice plains, tundra |
| Pumpkins | Forest, dark forest, taiga, autumnal forest (pumpkin plains already has its own) |
| Melons | Jungle |
| Sweet berries | Taiga |

Each crop only in its ideal-biome tag (`eternalreturn:ideal_biomes/<crop>`, which sub-biomes and variants already join). Sugar cane and cocoa already grow naturally and need no patches.

- **A patch:** a loose clump of about 4 to 12 fully grown crops on farmland set into the ground, grass and flowers around it. About one per 300 to 500 blocks of a fitting biome (tunable per crop).
- **Farmland, not grass:** no new rule lets crops grow on grass. Farmland with a crop on it doesn't dry back, so the patch holds until harvested or trampled; bare farmland then reverts to dirt as usual. Players carry the seeds home.
- **Rare variant, overgrown field:** about one patch in ten is larger, in rough rows, with a few broken fence posts (plain fences), as if someone once farmed the right crop in the right place.
- **Done when:** each crop's patches appear only in its ideal biomes, stay put until harvested, and the farmland reverts after harvest.

### 3. Hot springs and geysers

Steaming terraced pools in the cold that keep players warm, give grazing animals open ground in winter, and turn arctic treks into routes from spring to spring. Some have a geyser that erupts on a cycle.

- **Where:** mainly cold land (tundra, arctic, Alps, icy mountains, glacier field, black spruce forest, taiga peaks), about one cluster per 800 to 1,000 blocks of cold land; a rarer, scenic version in the cherry grove and volcanic jungle. A landmark (`/locate structure` finds it) on the ground as it is.
- **Shape:** two to six pools stepping down a slope like terraces, calcite rims with tuff streaks, clear water 1 to 3 deep, steam rising constantly and visible from far off.
- **Warmth:** standing in a spring or within a few blocks counts as a warmth source for cold exposure.
- **Open ground:** the water never freezes and snow never settles within about 8 blocks, so grass stays exposed (snow layers block grazing, so animals gather at springs in deep cold). Climate rules and storms skip the clear ring.
- **The vent:** the deepest pool has magma at the bottom; standing on it hurts, the top is safe.
- **Geysers:** erupt on a cycle and launch what stands over them.
- **Done when:** springs appear in cold biomes at the set rarity, keep players warm, stay snow-free and unfrozen with grass around them.

### 4. Cacti that connect sideways

The vanilla cactus connects horizontally to other cactus and, like saplings, grows a different shape in each dry biome. No new block: the same cactus with connection states.

- **Connecting:** a cactus placed against another's side joins it as an arm, with a connecting piece as fences have. Cactus still breaks when a non-cactus block touches its side; cactus touching cactus is fine. Every piece traces back to a cactus standing on sand or red sand: break the trunk and everything it held breaks and drops. Contact damage unchanged, arms included.
- **Shapes by biome** (at generation and from growth or bone meal; no table means vanilla straight growth):

| Shape | Biomes | Form |
|---|---|---|
| Saguaro | Desert | Trunk 5 to 9 tall; one to four arms reach out one block, then turn up, at different heights |
| Organ pipe | Desert scrub, desert baobabs | Several upright stems joined at the base in a tight cluster |
| Cholla | Drylands | Short and scraggly; arms branch at several heights, sometimes reaching out two blocks |
| Barrel cluster | Badlands, Monument Valley | Low clumps of one- and two-tall cacti joined sideways into a mound |
| Candelabra | Savanna, Mesa Verde | Arms that branch, turn up, then branch again |

- **Growth:** a cactus grows taller as now and sprouts arms at its shape's arm heights; bone meal follows the table; the bone meal height cap of 3 rises to each shape's height.
- **Gameplay:** sideways cactus hedges are an early wall against torch-breaking zombies in dry country; they take time to grow, need sand under every trunk and hurt the player's own animals.
- **Textures:** the sideways piece is the cactus texture rotated.
- **Done when:** cactus joins sideways and breaks correctly when its support goes, each dry biome shows its own shape, and a planted cactus grows its biome's shape.

### 5. Elder trees

A single colossal version of a local species, about one per 2,000 x 2,000 blocks, standing far above the canopy as a landmark to navigate by.

- **Where:** any forested biome or sub-biome with a tree table. A landmark structure (spacing about 125 chunks, as the volcano and glacier), so `/locate structure` finds them and they generate the same in any chunk order.
- **Species:** a giant of a species from the biome's tree table (weighted by the patch's canopy mix). Each species gets its own elder shape, its normal shape scaled up: trunk 3x3 to 5x5, height 50 to 90, crown well past the canopy, its own wood and leaves; leaves decay normally when cut.
- **Surroundings:** a clearing of podzol and fallen leaves with young trees of the same species.
- **The hollow:** a natural room in the base of the trunk among the roots, sometimes with a spring of water, sometimes a bear's den.
- **Crown life:** bee nests in the branches and a bird roost; birds, bees and bears coming for honey.
- **Saplings:** elder leaves drop saplings much more often.
- **Done when:** elders appear about once per 2,000 x 2,000 blocks of forest, match their biome's species, are visible above the canopy from far off, and have their hollow and crown life.

### 6. Oceans

**6a. Depth zones in every ocean** (the stepped sea floor's bands):

| Zone | Depth | What's there | Why go there |
|---|---|---|---|
| Shelf | About y 30 up to the shore | Seagrass, sand ripples, kelp in cold seas, boulders, tide pools at the shoreline | Easy fishing, first dives |
| Slope walls | The steep drops between bands | Bare biome stone, sea caves in the walls, exposed ore, glow lichen | Ore visible from the water, caves |
| Middle plain | About y 0 | Sand, gravel and clay; scattered ship debris (planks and logs, not whole ships); occasional whale falls | Salvage, clay |
| Abyss | About y -30 | Dark gravel and clay; vents and seamounts in their sub-biomes | Rare fish, danger |

**6b. New sea sub-biomes** (batch D's sea replacement step, extended to every ocean temperature and the deep seas; each keeps its parent's depth; anything rising from floor or surface is a landmark at sea, `"at_sea": true`):

| Parent | Sub-biome | What makes it | Connects to |
|---|---|---|---|
| Frozen | Pack Ice | A sheet of ice over the sea, broken by open leads | Walkable, but heavy armor cracks brittle ice; ice fishing; polar bears along the leads; falling in means fast cold exposure |
| Frozen | Iceberg Sea | Bergs of packed ice with blue ice cores, mostly under water | Boat hazards; strays riding polar bears on large bergs |
| Cold | Kelp Forest | Kelp to the surface, dense enough to lose your bearings, dim green underwater fog | Schooling fish, cod-heavy fishing |
| Cold | Sea Stacks | Rocky stacks and arches from the sea, no trees | Seabird rookeries; farms near this coast suffer more crop pests |
| Plain | Seagrass Meadow | Shallow, flat, bright green seagrass floor | Gentle early fishing ground |
| Plain | Shoals | Sandbars just breaking the surface, shallow aquamarine channels | Boats can run aground; natural anchorages |
| Lukewarm | Mangrove Lagoon | Mangroves on shallow mud flats, a tangled swampy coast | Sheltered water; pillagers hiding in boats |
| Warm | Atolls | Coral reef rings around a calm lagoon, a palm islet on the rim | A protected base site at sea; tropical fish |
| Warm | Blue Holes | Existing sinkholes in shallow warm water, ringed with coral | A dive to the bottom of the world |
| Deep (any) | Seamounts | Undersea mountains from the abyss to just under the surface | Fish crowd round; guardians, now and then an elder guardian |
| Deep (any) | Hydrothermal Vents | Basalt chimneys with magma mouths and upward bubble columns on a warm patch of floor | The rarest fish; the deep sea's magma caves |
| Deep (any) | Whale Falls | Bone-block skeletons on the abyssal floor, several to a field | Drowned gather; prismarine and bones |

**6c. Coasts and seas:** driftwood (logs of the wood growing inland nearby washed up on beaches); sea caves (openings in coastal cliffs at sea level leading into the caves); sunken forests (stumps and dead trunks on the shelf); water colour by sub-biome (kelp forest dark green, shoals aquamarine, abyss near black), fog tints following the same groups.

**6d. Fishing:** every new sea is a habitat for existing fish (no new fish). Ice fishing in the pack ice; the rarest fish bite most at vents and seamounts; fishermen's regional buy lists cover the new seas through their parents' tags.

**Done when:** each depth zone looks different in every ocean, each new sea sub-biome appears in its parent at a set share without moving the sea floor, and the coastal features appear along the shores.

### Build order

Start after the owner has checked phase 6 batch D in game. Ask whether this brief comes before or after the first brief's phase 7 (ruins and underground rivers).

| Phase | Work | Done when |
|---|---|---|
| 1 | Wild crop patches, with the overgrown field | Patches only in each crop's ideal biomes, on farmland, holding until harvested |
| 2 | Cactus connections, then the five biome shapes (generation, growth, bone meal) | Cactus joins and breaks correctly; each dry biome shows its shape; planted cacti grow their biome's shape |
| 3 | Hot springs, then geysers | Springs warm players and stay green and unfrozen; geysers erupt on a cycle and launch what stands over them |
| 4 | Elder trees, one shape per species | One per about 2,000 x 2,000 blocks of forest, matching the species, with hollow and crown life |
| 5 | Ocean depth zones and coastal features (6a, 6c) | Each zone looks different in every ocean; driftwood matches the land behind it |
| 6 | Sea sub-biomes in batches: frozen and cold, then plain and lukewarm, then warm and deep (6b, 6d) | Each appears at its share without moving the sea floor; fish habitats work |
| 7 | Biome personality rolls, per biome family | Patches visibly differ, each stays recognizable, nothing foreign appears |

Personality rolls come last on purpose: by then every new biome, crop patch, cactus shape and accent exists, so each biome's identity and accent lists can be written once, complete.

### Open questions (ask at the start of their phase, each with a recommended option)

1. Cactus connections in other worlds: everywhere, or only Eternal Return worlds? Recommended: everywhere.
2. Cholla in the drylands (no cactus there now)? Recommended: yes, sparse.
3. Seamounts the only place elder guardians spawn? Recommended: yes.
4. Sea sub-biome shares? Recommended: about 1 in 3 of each parent, like the coral ocean and archipelago.
5. Accent lists for phase 7: recommended, Claude Code drafts them and the owner approves.

## The owner's answers so far

- Order (asked 2026-11-08): this brief comes before the first brief's phase 7 (ruins and underground rivers).
- Phase 1 (asked 2026-11-08): no sweet berry patches (taiga's existing bushes are enough); pumpkin and melon patches are loose fruit on grass, no stems or farmland; the overgrown field's broken fence posts are the fence of a wood that grows in that biome.
- Phase 1, after the in-game check (2026-11-08, "everything looks good"): patches no longer turn the ground to farmland; a fully grown crop can stand on grass or dirt (so wild patches stay), but seeds still need farmland to be planted and to grow.
- Phase 2 (asked 2026-11-08): cactus connections work in every world (the shapes by biome generate only in Eternal Return worlds); sparse cholla in the drylands.
- Phase 2, after the in-game check (2026-11-08): a new block, the cactus arm, replaces sideways cactus. Arms are thinner (10 x 10 pixels), go only on top of another arm or on the side of a cactus, and join the cactus beside them unless an arm is under them; top and bottom use the same texture; no z-fighting. Cactus keeps its vanilla rules and can't be placed sideways any more. Arms come only from wild cacti (they drop themselves; no recipe). The shapes are rebuilt with arms: saguaro arms out one then up, organ pipe a short trunk with arms at its foot rising as stems, cholla short arms at several heights, barrel a 1 to 2 tall cactus ringed by low arms, candelabra two tiers of arms near the top.
- Phase 3 (asked 2026-11-08, all four recommended options): a geyser erupts every 1 to 2 minutes, a rumble and bubbles a few seconds ahead, then a 3-second plume of spray and steam launching what stands over the vent about 10 blocks up; the plume doesn't hurt (only the fall does); about 1 spring cluster in 3 has a geyser; spring water gives warmth only.
- Phase 4 (asked 2026-11-08): elder shapes from about 7 shared giant forms (broadleaf dome, conifer spire, tall column, flat umbrella, weeping, palm, bottle trunk), each species mapped to one with its own wood, leaves and proportions, shrubs never elders; elders in any biome with a tree table (savannas, palm beaches and baobab groves too); elder leaves drop saplings no more often than normal (the owner's change to the brief).
- After the owner's in-game check of phases 3 and 4 (2026-11-08): no tuff in the game (the owner doesn't want it): hot spring rims are calcite only; the volcano landmark's streaks and the volcanic jungle's ground patches are blackstone instead; the tuff dripstone block is removed. Geysers are a block rule instead of a spring timer: a magma block with lava directly below and water directly above now and then erupts (so springs whose vent sits on lava have geysers; any such spot in any world does). The acacia elder was "super ugly": it gets a form of its own.
