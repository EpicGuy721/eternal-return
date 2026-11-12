package com.eternalreturn.worldgen.personality;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.worldgen.trees.BiomeTrees;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FlowerbedBlock;
import net.minecraft.block.KelpBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.SeaPickleBlock;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Accents (worldgen expansion 2, phase 7, batch 2: Personality): each patch rolls one small extra from its biome's list
 * (data/eternalreturn/eternalreturn/accents.json, hand-written and meant to be tuned: a biome family or biome id to its
 * accents, an accent written "name" or "name:block" for a block it's made of), and that accent turns up about
 * per_patch times across the patch (twice that in a rare patch), built only from what the biome already has. Peaks,
 * edges and rivers take their patch's (by family). The feature runs once a chunk, last of the plants, in Eternal Return
 * worlds only (its placed feature carries eternalreturn:eternal_return_only), with personality rolls on.
 */
public class AccentFeature extends Feature<DefaultFeatureConfig> {
	/** The roll's stream for the accent picked. */
	private static final int ACCENT = 400;
	private static final RegistryKey<ConfiguredFeature<?, ?>> BOULDER = RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, EternalReturn.id("boulder"));
	private static final Block[] CORAL_BLOCKS = {Blocks.TUBE_CORAL_BLOCK, Blocks.BRAIN_CORAL_BLOCK, Blocks.BUBBLE_CORAL_BLOCK, Blocks.FIRE_CORAL_BLOCK, Blocks.HORN_CORAL_BLOCK};
	private static final Block[] CORALS = {Blocks.TUBE_CORAL, Blocks.BRAIN_CORAL, Blocks.BUBBLE_CORAL, Blocks.FIRE_CORAL, Blocks.HORN_CORAL};
	private static final Block[] FANS = {Blocks.TUBE_CORAL_FAN, Blocks.BRAIN_CORAL_FAN, Blocks.BUBBLE_CORAL_FAN, Blocks.FIRE_CORAL_FAN, Blocks.HORN_CORAL_FAN};

	private static volatile float perPatch = 8;
	/** How many times as often accents turn up (for the tests: 1 in a world). */
	public static volatile float boost = 1;
	private static volatile Map<String, List<String>> lists = Map.of();
	/** Every accent this feature knows (the names the table may use). */
	public static final java.util.Set<String> NAMES = java.util.Set.of("pond", "frozen_pond", "boulder_ring", "mossy_boulders", "ice_boulders", "fallen_log", "snag",
			"mushroom_ring", "mossy_clearing", "fern_pocket", "flower_pocket", "berry_thicket", "petal_drift", "podzol_patch", "snow_drift", "stone_outcrop", "hoodoo",
			"dry_wash", "bleached_bones", "termite_mound", "melon_patch", "pumpkin_cluster", "kelp_ring", "sunken_log", "seagrass_bed", "sand_ripples", "clay_patch",
			"sea_boulders", "sea_pickles", "coral_head", "ice_floe");
	/** Where accents were placed as the world was made, "biome name x z", the latest few thousand (for the tests). */
	public static final java.util.concurrent.ConcurrentLinkedQueue<String> RECENT = new java.util.concurrent.ConcurrentLinkedQueue<>();

	/** The accent table as loaded: biome or family id to its accents, for the tests. */
	public static Map<String, List<String>> table() {
		return lists;
	}

	/** How many of each accent were placed as the world was made (for the tests and logs). */
	public static final Map<String, java.util.concurrent.atomic.AtomicInteger> PLACED = new java.util.concurrent.ConcurrentHashMap<>();

	public AccentFeature() {
		super(DefaultFeatureConfig.CODEC);
	}

	public static void register() {
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return EternalReturn.id("accents");
			}

			@Override
			public void reload(ResourceManager manager) {
				load(manager);
			}
		});
	}

	static void load(ResourceManager manager) {
		Map<String, List<String>> loaded = new HashMap<>();
		float per = 8;
		for (Map.Entry<Identifier, List<Resource>> file : manager.findAllResources("eternalreturn", id -> id.getPath().equals("eternalreturn/accents.json")).entrySet()) {
			for (Resource resource : file.getValue()) {
				try (Reader reader = resource.getReader()) {
					JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
					if (json.has("per_patch")) {
						per = json.get("per_patch").getAsFloat();
					}
					for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("biomes").entrySet()) {
						List<String> accents = new ArrayList<>();
						entry.getValue().getAsJsonArray().forEach(element -> accents.add(element.getAsString()));
						loaded.put(entry.getKey(), List.copyOf(accents));
					}
				} catch (Exception e) {
					EternalReturn.LOGGER.error("Couldn't read the accents {}", file.getKey(), e);
				}
			}
		}
		perPatch = per;
		lists = Map.copyOf(loaded);
	}

	/** A biome's accents: its family's (a peak, edge or river as its base), else its own; empty for none. */
	public static List<String> accentsOf(Identifier biome) {
		List<String> list = lists.get(Personality.family(biome).toString());
		return list != null ? list : lists.getOrDefault(biome.toString(), List.of());
	}

	/** The accent a patch rolled from its biome's list, or null. */
	@Nullable
	public static String accentOf(Personality.Roll roll, Identifier biome) {
		List<String> list = accentsOf(biome);
		return list.isEmpty() ? null : list.get(Math.min(list.size() - 1, (int) (roll.unit(ACCENT) * list.size())));
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		if (!EternalReturnConfig.get().worldgen.personalityRolls) {
			return false;
		}
		StructureWorldAccess world = context.getWorld();
		Random random = context.getRandom();
		ChunkPos chunk = new ChunkPos(context.getOrigin());
		int x = chunk.getStartX() + 4 + random.nextInt(8);
		int z = chunk.getStartZ() + 4 + random.nextInt(8);
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		RegistryEntry<Biome> biome = world.getBiomeForNoiseGen(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(top), BiomeCoords.fromBlock(z));
		Identifier id = biome.getKey().map(key -> key.getValue()).orElse(null);
		if (id == null) {
			return false;
		}
		Personality.Roll roll = Personality.at(Personality.salt(world.getSeed()), id, x, z);
		String accent = accentOf(roll, id);
		if (accent == null || random.nextFloat() >= perPatch / 256F * (roll.rare() ? 2 : 1) * boost) {
			return false;
		}
		Job job = new Job(world, context.getGenerator(), random, biome, roll, x, z);
		int colon = accent.indexOf(':');
		String name = colon < 0 ? accent : accent.substring(0, colon);
		Block block = colon < 0 ? null : Registries.BLOCK.get(Identifier.of(accent.substring(colon + 1)));
		boolean placed = switch (name) {
			case "pond" -> job.pond(false);
			case "frozen_pond" -> job.pond(true);
			case "boulder_ring" -> job.boulderRing();
			case "mossy_boulders" -> job.blobs(Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE, 3 + random.nextInt(3), false);
			case "ice_boulders" -> job.blobs(Blocks.PACKED_ICE, Blocks.PACKED_ICE, 2 + random.nextInt(3), false);
			case "fallen_log" -> job.fallenLog(block);
			case "snag" -> job.snag();
			case "mushroom_ring" -> job.mushroomRing();
			case "mossy_clearing" -> job.mossyClearing();
			case "fern_pocket" -> job.pocket(List.of(Blocks.FERN, Blocks.LARGE_FERN), 30);
			case "flower_pocket" -> job.flowerPocket();
			case "berry_thicket" -> job.pocket(List.of(Blocks.SWEET_BERRY_BUSH), 14);
			case "petal_drift" -> job.petalDrift();
			case "podzol_patch" -> job.groundPatch(Blocks.PODZOL, Blocks.COARSE_DIRT);
			case "snow_drift" -> job.snowDrift();
			case "stone_outcrop" -> job.outcrop(block);
			case "hoodoo" -> job.hoodoo();
			case "dry_wash" -> job.dryWash();
			case "bleached_bones" -> job.bones();
			case "termite_mound" -> job.termiteMound();
			case "melon_patch" -> job.pocket(List.of(Blocks.MELON), 6);
			case "pumpkin_cluster" -> job.pocket(List.of(Blocks.PUMPKIN), 6);
			case "kelp_ring" -> job.kelpRing();
			case "sunken_log" -> job.sunkenLog(block);
			case "seagrass_bed" -> job.seagrassBed();
			case "sand_ripples" -> job.sandRipples();
			case "clay_patch" -> job.clayPatch();
			case "sea_boulders" -> job.blobs(null, null, 2 + random.nextInt(3), true);
			case "sea_pickles" -> job.seaPickles();
			case "coral_head" -> job.coralHead();
			case "ice_floe" -> job.iceFloe();
			default -> {
				EternalReturn.LOGGER.warn("Accents: unknown accent {} for {}", accent, id);
				yield false;
			}
		};
		if (placed) {
			PLACED.computeIfAbsent(name, key -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
			RECENT.add(id + " " + accent + " " + x + " " + z);
			while (RECENT.size() > 4096) {
				RECENT.poll();
			}
		}
		return placed;
	}

	/** One accent being built round a spot. */
	static final class Job {
		final StructureWorldAccess world;
		final ChunkGenerator generator;
		final Random random;
		final RegistryEntry<Biome> biome;
		final Personality.Roll roll;
		final int x;
		final int z;
		final int sea;
		final BlockPos.Mutable pos = new BlockPos.Mutable();

		Job(StructureWorldAccess world, ChunkGenerator generator, Random random, RegistryEntry<Biome> biome, Personality.Roll roll, int x, int z) {
			this.world = world;
			this.generator = generator;
			this.random = random;
			this.biome = biome;
			this.roll = roll;
			this.x = x;
			this.z = z;
			this.sea = generator.getSeaLevel();
		}

		BlockState at(int x, int y, int z) {
			return this.world.getBlockState(this.pos.set(x, y, z));
		}

		void set(int x, int y, int z, BlockState state) {
			this.world.setBlockState(new BlockPos(x, y, z), state, Block.NOTIFY_LISTENERS);
		}

		/** Ground a plant, log or boulder can stand on (not wood, leaves or plants). */
		static boolean ground(BlockState state) {
			return state.isOpaqueFullCube(net.minecraft.world.EmptyBlockView.INSTANCE, BlockPos.ORIGIN) && !state.isIn(BlockTags.LOGS) && !state.isIn(BlockTags.LEAVES) || state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.MUD);
		}

		/** The land's ground block at a column (under plants, snow layers and trees' overhang), or Integer.MIN_VALUE in water or on wood. */
		int land(int x, int z) {
			int y = this.world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
			for (; y > this.world.getBottomY(); y--) {
				BlockState state = this.at(x, y, z);
				if (!state.getFluidState().isEmpty()) {
					return Integer.MIN_VALUE;
				}
				if (state.isIn(BlockTags.LOGS)) {
					return Integer.MIN_VALUE;
				}
				if (ground(state)) {
					return y;
				}
			}
			return Integer.MIN_VALUE;
		}

		/** The sea floor at a column (under water and water plants), or Integer.MIN_VALUE on land. */
		int floor(int x, int z) {
			int y = this.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) - 1;
			if (this.at(x, y, z).getFluidState().isEmpty() || !this.at(x, y, z).getFluidState().isIn(net.minecraft.registry.tag.FluidTags.WATER)) {
				if (!this.at(x, y, z).isOf(Blocks.ICE) && !this.at(x, y, z).isOf(Blocks.PACKED_ICE)) {
					return Integer.MIN_VALUE;
				}
			}
			for (; y > this.world.getBottomY(); y--) {
				BlockState state = this.at(x, y, z);
				if (state.isSolidBlock(this.world, this.pos) && state.getFluidState().isEmpty()) {
					return y;
				}
			}
			return Integer.MIN_VALUE;
		}

		/** Whether a spot above ground is open (air, snow layers or a small plant), clearing a plant there. */
		boolean open(int x, int y, int z) {
			BlockState state = this.at(x, y, z);
			if (state.isAir()) {
				return true;
			}
			if (state.isReplaceable() && state.getFluidState().isEmpty() || state.isIn(BlockTags.FLOWERS) && !state.isIn(BlockTags.LEAVES)) {
				if (state.getBlock() instanceof TallPlantBlock) {
					BlockState up = this.at(x, y + 1, z);
					if (up.isOf(state.getBlock())) {
						this.set(x, y + 1, z, Blocks.AIR.getDefaultState());
					}
				}
				this.set(x, y, z, Blocks.AIR.getDefaultState());
				return true;
			}
			return false;
		}

		boolean wet() {
			return this.biome.isIn(net.minecraft.registry.tag.TagKey.of(RegistryKeys.BIOME, Identifier.of("c", "is_swamp")))
					|| this.biome.isIn(net.minecraft.registry.tag.TagKey.of(RegistryKeys.BIOME, Identifier.of("c", "is_jungle")));
		}

		boolean snowy() {
			return this.biome.value().getTemperature() < 0.15F;
		}

		// --- Land ---

		/** A small pool edged with the biome's ground, lily pads and sugar cane where the biome has them; iced over if frozen. */
		boolean pond(boolean frozen) {
			int radiusX = 2 + this.random.nextInt(2);
			int radiusZ = 2 + this.random.nextInt(2);
			int base = this.land(this.x, this.z);
			if (base == Integer.MIN_VALUE || base < this.sea - 1) {
				return false;
			}
			// Flat: every column of the pond and its rim within a block of the middle.
			for (int dx = -radiusX - 1; dx <= radiusX + 1; dx++) {
				for (int dz = -radiusZ - 1; dz <= radiusZ + 1; dz++) {
					int y = this.land(this.x + dx, this.z + dz);
					if (y == Integer.MIN_VALUE || Math.abs(y - base) > 1) {
						return false;
					}
				}
			}
			boolean ice = frozen || this.snowy();
			java.util.Set<Block> plants = PersonalityFeatures.plantsOf(this.biome.value());
			for (int dx = -radiusX; dx <= radiusX; dx++) {
				for (int dz = -radiusZ; dz <= radiusZ; dz++) {
					double e = (dx * dx) / (double) (radiusX * radiusX) + (dz * dz) / (double) (radiusZ * radiusZ);
					if (e > 1) {
						continue;
					}
					int px = this.x + dx;
					int pz = this.z + dz;
					int depth = e < 0.35 ? 2 : 1;
					for (int y = base + 3; y > base; y--) {
						BlockState above = this.at(px, y, pz);
						if (!above.isAir()) {
							this.set(px, y, pz, Blocks.AIR.getDefaultState());
						}
					}
					for (int d = 0; d < depth; d++) {
						this.set(px, base - d, pz, (d == 0 && ice ? Blocks.ICE : Blocks.WATER).getDefaultState());
					}
					// The bed: the ground's own dirt (no grass under water).
					BlockState bed = this.at(px, base - depth, pz);
					if (bed.isOf(Blocks.GRASS_BLOCK) || bed.isOf(Blocks.PODZOL) || bed.isOf(Blocks.MYCELIUM)) {
						this.set(px, base - depth, pz, Blocks.DIRT.getDefaultState());
					}
					if (!ice && plants.contains(Blocks.LILY_PAD) && this.random.nextInt(6) == 0) {
						this.set(px, base + 1, pz, Blocks.LILY_PAD.getDefaultState());
					}
				}
			}
			// The rim: sugar cane by the water where the biome grows it; snow on the banks of a frozen pond.
			for (int dx = -radiusX - 1; dx <= radiusX + 1; dx++) {
				for (int dz = -radiusZ - 1; dz <= radiusZ + 1; dz++) {
					double e = (dx * dx) / (double) ((radiusX + 1) * (radiusX + 1)) + (dz * dz) / (double) ((radiusZ + 1) * (radiusZ + 1));
					int px = this.x + dx;
					int pz = this.z + dz;
					int y = this.land(px, pz);
					if (e > 1 || y == Integer.MIN_VALUE || this.at(px, y, pz).isOf(Blocks.WATER)) {
						continue;
					}
					if (frozen && this.open(px, y + 1, pz)) {
						this.set(px, y + 1, pz, Blocks.SNOW.getDefaultState());
					} else if (!ice && plants.contains(Blocks.SUGAR_CANE) && this.random.nextInt(4) == 0 && this.open(px, y + 1, pz)
							&& Blocks.SUGAR_CANE.getDefaultState().canPlaceAt(this.world, new BlockPos(px, y + 1, pz))) {
						int height = 1 + this.random.nextInt(3);
						for (int h = 1; h <= height && this.open(px, y + h, pz); h++) {
							this.set(px, y + h, pz, Blocks.SUGAR_CANE.getDefaultState());
						}
					}
				}
			}
			return true;
		}

		/** Five to nine of the biome's boulders in a loose ring 8 to 12 across. */
		boolean boulderRing() {
			ConfiguredFeature<?, ?> boulder = this.world.getRegistryManager().get(RegistryKeys.CONFIGURED_FEATURE).get(BOULDER);
			if (boulder == null) {
				return false;
			}
			int count = 5 + this.random.nextInt(5);
			double radius = 4 + this.random.nextDouble() * 2;
			boolean any = false;
			for (int i = 0; i < count; i++) {
				double angle = Math.PI * 2 * i / count + this.random.nextDouble() * 0.4;
				int bx = this.x + (int) Math.round(Math.cos(angle) * radius);
				int bz = this.z + (int) Math.round(Math.sin(angle) * radius);
				any |= boulder.generate(this.world, this.generator, this.random, new BlockPos(bx, 0, bz));
			}
			return any;
		}

		/**
		 * A few rounded blobs (as vanilla's forest rocks): of these blocks on land, or under water of the floor's own stone
		 * (sea boulders).
		 */
		boolean blobs(@Nullable Block main, @Nullable Block other, int count, boolean underwater) {
			boolean any = false;
			for (int i = 0; i < count; i++) {
				int bx = this.x + this.random.nextInt(9) - 4;
				int bz = this.z + this.random.nextInt(9) - 4;
				int y = underwater ? this.floor(bx, bz) : this.land(bx, bz);
				if (y == Integer.MIN_VALUE) {
					continue;
				}
				Block a = main;
				Block b = other;
				if (underwater) {
					a = this.stoneUnder(bx, y, bz, Blocks.STONE);
					b = a;
				}
				double radius = 1 + this.random.nextDouble() * 0.8;
				int r = (int) Math.ceil(radius);
				for (int dx = -r; dx <= r; dx++) {
					for (int dy = -r; dy <= r; dy++) {
						for (int dz = -r; dz <= r; dz++) {
							if (dx * dx + dy * dy * 1.4 + dz * dz > radius * radius + this.random.nextDouble() * 0.6) {
								continue;
							}
							int px = bx + dx;
							int py = y + dy + 1;
							int pz = bz + dz;
							BlockState there = this.at(px, py, pz);
							if (there.isIn(BlockTags.LOGS) || there.isIn(BlockTags.LEAVES) && !underwater) {
								continue;
							}
							if (underwater ? there.isOf(Blocks.WATER) || there.isReplaceable() || ground(there) : there.isAir() || there.isReplaceable() || ground(there)) {
								this.set(px, py, pz, (this.random.nextInt(3) == 0 ? b : a).getDefaultState());
								any = true;
							}
						}
					}
				}
			}
			return any;
		}

		/** The first stone under a spot (the ground's own: stone, a host stone, sandstone, blackstone...), or the fallback. */
		Block stoneUnder(int x, int y, int z, Block fallback) {
			for (int d = 0; d < 24; d++) {
				BlockState state = this.at(x, y - d, z);
				if (state.isIn(BlockTags.BASE_STONE_OVERWORLD) || state.isOf(Blocks.SANDSTONE) || state.isOf(Blocks.RED_SANDSTONE) || state.isOf(Blocks.BLACKSTONE)) {
					return state.getBlock();
				}
			}
			return fallback;
		}

		/** The log of the biome's own wood (its tree table's first sapling; inland wood on a beach), or oak. */
		Block log() {
			for (Identifier sapling : BiomeTrees.ownLists(this.biome).keySet()) {
				Block log = logOf(sapling);
				if (log != null) {
					return log;
				}
			}
			for (int distance = 16; distance <= 96; distance += 16) {
				for (int i = 0; i < 8; i++) {
					double angle = Math.PI * 2 * i / 8;
					int bx = this.x + (int) Math.round(Math.cos(angle) * distance);
					int bz = this.z + (int) Math.round(Math.sin(angle) * distance);
					RegistryEntry<Biome> other = this.generator.getBiomeSource().getBiome(BiomeCoords.fromBlock(bx), BiomeCoords.fromBlock(this.sea + 4), BiomeCoords.fromBlock(bz),
							this.world.toServerWorld().getChunkManager().getNoiseConfig().getMultiNoiseSampler());
					if (other.isIn(BiomeTags.IS_OCEAN) || other.isIn(BiomeTags.IS_BEACH) || other.isIn(BiomeTags.IS_RIVER)) {
						continue;
					}
					for (Identifier sapling : BiomeTrees.ownLists(other).keySet()) {
						Block log = logOf(sapling);
						if (log != null) {
							return log;
						}
					}
				}
			}
			return Blocks.OAK_LOG;
		}

		@Nullable
		static Block logOf(Identifier sapling) {
			String path = sapling.getPath();
			String base = path.endsWith("_sapling") ? path.substring(0, path.length() - "_sapling".length()) : path.equals("mangrove_propagule") ? "mangrove" : null;
			if (base == null) {
				return null;
			}
			Identifier log = Identifier.of(sapling.getNamespace(), base + "_log");
			return Registries.BLOCK.containsId(log) ? Registries.BLOCK.get(log) : null;
		}

		/** One of the biome's trees lying on its side, 4 to 9 long, mossy with mushrooms in wet biomes. */
		boolean fallenLog(@Nullable Block wood) {
			Block log = wood != null ? wood : this.log();
			Direction direction = Direction.Type.HORIZONTAL.random(this.random);
			int length = 4 + this.random.nextInt(6);
			int base = this.land(this.x, this.z);
			if (base == Integer.MIN_VALUE) {
				return false;
			}
			List<BlockPos> spots = new ArrayList<>();
			for (int i = 0; i < length; i++) {
				int px = this.x + direction.getOffsetX() * i;
				int pz = this.z + direction.getOffsetZ() * i;
				int y = this.land(px, pz);
				if (y == Integer.MIN_VALUE || Math.abs(y - base) > 1) {
					break;
				}
				spots.add(new BlockPos(px, y + 1, pz));
			}
			if (spots.size() < 3) {
				return false;
			}
			for (BlockPos spot : spots) {
				if (!this.open(spot.getX(), spot.getY(), spot.getZ())) {
					return false;
				}
			}
			for (BlockPos spot : spots) {
				this.set(spot.getX(), spot.getY(), spot.getZ(), log.getDefaultState().withIfExists(PillarBlock.AXIS, direction.getAxis()));
				if (this.wet() && this.random.nextInt(3) == 0 && this.at(spot.getX(), spot.getY() + 1, spot.getZ()).isAir()) {
					this.set(spot.getX(), spot.getY() + 1, spot.getZ(), (this.random.nextInt(3) == 0 ? Blocks.BROWN_MUSHROOM : Blocks.MOSS_CARPET).getDefaultState());
				}
			}
			return true;
		}

		/** A dead standing trunk of the biome's wood, 3 to 7 high, a stub of a branch or two. */
		boolean snag() {
			Block log = this.log();
			int base = this.land(this.x, this.z);
			if (base == Integer.MIN_VALUE) {
				return false;
			}
			int height = 3 + this.random.nextInt(5);
			for (int h = 1; h <= height; h++) {
				if (!this.open(this.x, base + h, this.z)) {
					return false;
				}
			}
			for (int h = 1; h <= height; h++) {
				this.set(this.x, base + h, this.z, log.getDefaultState());
			}
			int branches = this.random.nextInt(3);
			for (int i = 0; i < branches; i++) {
				Direction side = Direction.Type.HORIZONTAL.random(this.random);
				int y = base + 2 + this.random.nextInt(Math.max(1, height - 2));
				int bx = this.x + side.getOffsetX();
				int bz = this.z + side.getOffsetZ();
				if (this.at(bx, y, bz).isAir()) {
					this.set(bx, y, bz, log.getDefaultState().withIfExists(PillarBlock.AXIS, side.getAxis()));
				}
			}
			return true;
		}

		/** A fairy ring of brown and red mushrooms, 5 to 9 across. */
		boolean mushroomRing() {
			double radius = 2.5 + this.random.nextDouble() * 2;
			int count = (int) (radius * 5);
			boolean any = false;
			for (int i = 0; i < count; i++) {
				double angle = Math.PI * 2 * i / count;
				int px = this.x + (int) Math.round(Math.cos(angle) * radius);
				int pz = this.z + (int) Math.round(Math.sin(angle) * radius);
				int y = this.land(px, pz);
				if (y == Integer.MIN_VALUE || !this.open(px, y + 1, pz)) {
					continue;
				}
				BlockState mushroom = (this.random.nextInt(3) == 0 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).getDefaultState();
				if (mushroom.canPlaceAt(this.world, new BlockPos(px, y + 1, pz))) {
					this.set(px, y + 1, pz, mushroom);
					any = true;
				}
			}
			return any;
		}

		/** Moss blocks and moss carpet on the forest floor, 3 to 5 across from the middle. */
		boolean mossyClearing() {
			int radius = 3 + this.random.nextInt(3);
			boolean any = false;
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (dx * dx + dz * dz > radius * radius - this.random.nextInt(radius + 1)) {
						continue;
					}
					int px = this.x + dx;
					int pz = this.z + dz;
					int y = this.land(px, pz);
					if (y == Integer.MIN_VALUE) {
						continue;
					}
					BlockState ground = this.at(px, y, pz);
					if (ground.isIn(BlockTags.DIRT) && !ground.isOf(Blocks.MUD)) {
						this.set(px, y, pz, Blocks.MOSS_BLOCK.getDefaultState());
						if (this.random.nextInt(4) == 0 && this.open(px, y + 1, pz)) {
							this.set(px, y + 1, pz, Blocks.MOSS_CARPET.getDefaultState());
						}
						any = true;
					}
				}
			}
			return any;
		}

		/** A thick cluster of these plants on grass, dirt or podzol round the spot. */
		boolean pocket(List<Block> blocks, int tries) {
			boolean any = false;
			for (int i = 0; i < tries; i++) {
				int px = this.x + this.random.nextInt(7) - 3;
				int pz = this.z + this.random.nextInt(7) - 3;
				int y = this.land(px, pz);
				if (y == Integer.MIN_VALUE || !this.at(px, y, pz).isIn(BlockTags.DIRT) || !this.at(px, y + 1, pz).isAir()) {
					continue;
				}
				Block block = blocks.get(this.random.nextInt(blocks.size()));
				BlockState state = block.getDefaultState();
				if (block instanceof SweetBerryBushBlock) {
					state = state.with(SweetBerryBushBlock.AGE, 1 + this.random.nextInt(3));
				}
				BlockPos at = new BlockPos(px, y + 1, pz);
				if (block instanceof TallPlantBlock) {
					if (this.at(px, y + 2, pz).isAir() && state.canPlaceAt(this.world, at)) {
						TallPlantBlock.placeAt(this.world, state, at, Block.NOTIFY_LISTENERS);
						any = true;
					}
				} else if (block == Blocks.MELON || block == Blocks.PUMPKIN || state.canPlaceAt(this.world, at)) {
					this.set(px, y + 1, pz, state);
					any = true;
				}
			}
			return any;
		}

		/** A dense cluster of the patch's own flowers. */
		boolean flowerPocket() {
			List<String> palette = PersonalityFeatures.describeFlowers(this.biome.value(), this.roll).get(1);
			List<Block> flowers = palette.stream().map(id -> Registries.BLOCK.get(Identifier.of(id))).toList();
			return !flowers.isEmpty() && this.pocket(flowers, 28);
		}

		/** A carpet of the biome's petals (pink petals, maple petals: whichever it plants). */
		boolean petalDrift() {
			Block petals = null;
			for (Block block : PersonalityFeatures.plantsOf(this.biome.value())) {
				if (block instanceof FlowerbedBlock) {
					petals = block;
					break;
				}
			}
			if (petals == null) {
				return false;
			}
			boolean any = false;
			for (int i = 0; i < 40; i++) {
				int px = this.x + this.random.nextInt(9) - 4;
				int pz = this.z + this.random.nextInt(9) - 4;
				int y = this.land(px, pz);
				if (y == Integer.MIN_VALUE || !this.at(px, y + 1, pz).isAir()) {
					continue;
				}
				BlockState state = petals.getDefaultState().with(FlowerbedBlock.FLOWER_AMOUNT, 1 + this.random.nextInt(4))
						.with(FlowerbedBlock.FACING, Direction.Type.HORIZONTAL.random(this.random));
				if (state.canPlaceAt(this.world, new BlockPos(px, y + 1, pz))) {
					this.set(px, y + 1, pz, state);
					any = true;
				}
			}
			return any;
		}

		/** A spread of these ground blocks (podzol and coarse dirt) over grass and dirt. */
		boolean groundPatch(Block main, Block other) {
			int radius = 3 + this.random.nextInt(3);
			boolean any = false;
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (dx * dx + dz * dz > radius * radius - this.random.nextInt(radius + 1)) {
						continue;
					}
					int px = this.x + dx;
					int pz = this.z + dz;
					int y = this.land(px, pz);
					if (y != Integer.MIN_VALUE && (this.at(px, y, pz).isOf(Blocks.GRASS_BLOCK) || this.at(px, y, pz).isOf(Blocks.DIRT))) {
						this.set(px, y, pz, (this.random.nextInt(4) == 0 ? other : main).getDefaultState());
						any = true;
					}
				}
			}
			return any;
		}

		/** A long low bank of snow layers, highest along its middle. */
		boolean snowDrift() {
			double angle = this.random.nextDouble() * Math.PI;
			int length = 8 + this.random.nextInt(8);
			boolean any = false;
			for (int i = -length / 2; i <= length / 2; i++) {
				for (int w = -2; w <= 2; w++) {
					int px = this.x + (int) Math.round(Math.cos(angle) * i - Math.sin(angle) * w);
					int pz = this.z + (int) Math.round(Math.sin(angle) * i + Math.cos(angle) * w);
					int y = this.land(px, pz);
					if (y == Integer.MIN_VALUE) {
						continue;
					}
					double along = 1 - Math.abs(i) / (length / 2.0 + 1);
					int layers = Math.max(1, (int) Math.round((2 + this.random.nextInt(4)) * along * (1 - Math.abs(w) / 3.0)));
					BlockState above = this.at(px, y + 1, pz);
					if (above.isAir() || above.isOf(Blocks.SNOW)) {
						int now = above.isOf(Blocks.SNOW) ? above.get(SnowBlock.LAYERS) : 0;
						if (layers > now && Blocks.SNOW.getDefaultState().canPlaceAt(this.world, new BlockPos(px, y + 1, pz))) {
							this.set(px, y + 1, pz, Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, Math.min(8, layers)));
							any = true;
						}
					}
				}
			}
			return any;
		}

		/** A rough spire of the ground's own stone (or this block), 3 to 6 high, wider at its foot. */
		boolean outcrop(@Nullable Block block) {
			int base = this.land(this.x, this.z);
			if (base == Integer.MIN_VALUE) {
				return false;
			}
			Block stone = block != null ? block : this.stoneUnder(this.x, base, this.z, Blocks.STONE);
			int height = 3 + this.random.nextInt(4);
			for (int h = 0; h <= height; h++) {
				double radius = 1.6 * (1 - h / (double) (height + 1)) + 0.4;
				int r = (int) Math.ceil(radius);
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (dx * dx + dz * dz > radius * radius + this.random.nextDouble() * 0.5) {
							continue;
						}
						int px = this.x + dx;
						int pz = this.z + dz;
						int y = base + h;
						BlockState there = this.at(px, y, pz);
						if (there.isAir() || there.isReplaceable() || ground(there)) {
							this.set(px, y, pz, stone.getDefaultState());
						}
					}
				}
			}
			return true;
		}

		/** A thin spire of the land's terracotta bands (as the ground's own layers run), 4 to 8 high, with a wider cap. */
		boolean hoodoo() {
			int base = this.land(this.x, this.z);
			if (base == Integer.MIN_VALUE) {
				return false;
			}
			List<BlockState> bands = new ArrayList<>();
			for (int d = 0; d < 40 && bands.size() < 12; d++) {
				BlockState state = this.at(this.x, base - d, this.z);
				if (state.isIn(BlockTags.TERRACOTTA)) {
					bands.add(state);
				}
			}
			if (bands.isEmpty()) {
				return false;
			}
			int height = 4 + this.random.nextInt(5);
			for (int h = 1; h <= height; h++) {
				BlockState band = bands.get((h - 1) % bands.size());
				boolean cap = h >= height - 1;
				int r = cap ? 1 : 0;
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (cap && Math.abs(dx) + Math.abs(dz) == 2 && this.random.nextBoolean()) {
							continue;
						}
						int px = this.x + dx;
						int pz = this.z + dz;
						BlockState there = this.at(px, base + h, pz);
						if (there.isAir() || there.isReplaceable()) {
							this.set(px, base + h, pz, band);
						}
					}
				}
			}
			return true;
		}

		/** A winding dry streambed of the land's sand (red sand, or coarse dirt where there's no sand) and gravel, dead bushes along it. */
		boolean dryWash() {
			int base = this.land(this.x, this.z);
			if (base == Integer.MIN_VALUE) {
				return false;
			}
			BlockState top = this.at(this.x, base, this.z);
			Block bed = top.isOf(Blocks.RED_SAND) || top.isIn(BlockTags.TERRACOTTA) ? Blocks.RED_SAND : top.isOf(Blocks.SAND) || top.isOf(Blocks.SANDSTONE) ? Blocks.SAND : Blocks.COARSE_DIRT;
			double angle = this.random.nextDouble() * Math.PI * 2;
			double cx = this.x;
			double cz = this.z;
			int steps = 14 + this.random.nextInt(10);
			boolean any = false;
			for (int s = 0; s < steps; s++) {
				angle += (this.random.nextDouble() - 0.5) * 0.6;
				cx += Math.cos(angle);
				cz += Math.sin(angle);
				if (Math.abs(cx - this.x) > 11 || Math.abs(cz - this.z) > 11) {
					break;
				}
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						int px = (int) Math.floor(cx) + dx;
						int pz = (int) Math.floor(cz) + dz;
						int y = this.land(px, pz);
						if (y == Integer.MIN_VALUE) {
							continue;
						}
						BlockState ground = this.at(px, y, pz);
						if (!(ground.isIn(BlockTags.DIRT) || ground.isIn(BlockTags.SAND) || ground.isIn(BlockTags.TERRACOTTA) || ground.isOf(Blocks.SANDSTONE))) {
							continue;
						}
						boolean edge = Math.abs(dx) + Math.abs(dz) == 2;
						if (!edge || this.random.nextBoolean()) {
							this.open(px, y + 1, pz);
							this.set(px, y, pz, (this.random.nextInt(4) == 0 ? Blocks.GRAVEL : bed).getDefaultState());
							any = true;
						} else if (this.random.nextInt(5) == 0 && this.open(px, y + 1, pz)) {
							this.set(px, y + 1, pz, Blocks.DEAD_BUSH.getDefaultState());
						}
					}
				}
			}
			return any;
		}

		/** A rib cage and spine of bone blocks half buried in the sand. */
		boolean bones() {
			int base = this.land(this.x, this.z);
			if (base == Integer.MIN_VALUE) {
				return false;
			}
			Direction along = Direction.Type.HORIZONTAL.random(this.random);
			Direction side = along.rotateYClockwise();
			int length = 5 + this.random.nextInt(4);
			for (int i = 0; i < length; i++) {
				int px = this.x + along.getOffsetX() * i;
				int pz = this.z + along.getOffsetZ() * i;
				int y = this.land(px, pz);
				if (y == Integer.MIN_VALUE) {
					continue;
				}
				this.set(px, y, pz, Blocks.BONE_BLOCK.getDefaultState().with(PillarBlock.AXIS, along.getAxis()));
				if (i % 2 == 1 && i < length - 1) {
					for (int s : new int[]{-1, 1}) {
						for (int h = 0; h <= 1; h++) {
							int rx = px + side.getOffsetX() * s * (h == 0 ? 1 : 2);
							int rz = pz + side.getOffsetZ() * s * (h == 0 ? 1 : 2);
							int ry = y + h;
							BlockState there = this.at(rx, ry, rz);
							if (there.isAir() || there.isReplaceable() || ground(there)) {
								this.set(rx, ry, rz, Blocks.BONE_BLOCK.getDefaultState().with(PillarBlock.AXIS, h == 0 ? side.getAxis() : Direction.Axis.Y));
							}
						}
					}
				}
			}
			return true;
		}

		/** A tall lumpy mound of coarse dirt. */
		boolean termiteMound() {
			int base = this.land(this.x, this.z);
			if (base == Integer.MIN_VALUE) {
				return false;
			}
			int height = 3 + this.random.nextInt(4);
			for (int h = 1; h <= height; h++) {
				double radius = 1.3 * (1 - h / (double) (height + 1)) + 0.3;
				int r = (int) Math.ceil(radius);
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (dx * dx + dz * dz > radius * radius + this.random.nextDouble() * 0.8) {
							continue;
						}
						if (this.open(this.x + dx, base + h, this.z + dz)) {
							this.set(this.x + dx, base + h, this.z + dz, Blocks.COARSE_DIRT.getDefaultState());
						}
					}
				}
			}
			return true;
		}

		// --- Seas ---

		/** A ring of tall kelp round a bare patch of floor, 8 to 12 across. */
		boolean kelpRing() {
			double radius = 4 + this.random.nextDouble() * 2;
			int count = (int) (radius * 4);
			boolean any = false;
			for (int i = 0; i < count; i++) {
				double angle = Math.PI * 2 * i / count;
				int px = this.x + (int) Math.round(Math.cos(angle) * radius);
				int pz = this.z + (int) Math.round(Math.sin(angle) * radius);
				int y = this.floor(px, pz);
				if (y == Integer.MIN_VALUE || !this.at(px, y + 1, pz).isOf(Blocks.WATER)) {
					continue;
				}
				int room = 0;
				while (this.at(px, y + 1 + room, pz).isOf(Blocks.WATER) && this.at(px, y + 2 + room, pz).isOf(Blocks.WATER)) {
					room++;
				}
				int height = Math.min(room, 4 + this.random.nextInt(Math.max(1, room)));
				for (int h = 1; h <= height; h++) {
					this.set(px, y + h, pz, h == height ? Blocks.KELP.getDefaultState().with(KelpBlock.AGE, 20 + this.random.nextInt(4)) : Blocks.KELP_PLANT.getDefaultState());
				}
				any |= height > 0;
			}
			return any;
		}

		/** A log lying on the floor (the coast's wood, mangrove in the lagoon), seagrass round it. */
		boolean sunkenLog(@Nullable Block wood) {
			Block log = wood != null ? wood : this.log();
			Direction direction = Direction.Type.HORIZONTAL.random(this.random);
			int length = 4 + this.random.nextInt(4);
			int base = this.floor(this.x, this.z);
			if (base == Integer.MIN_VALUE) {
				return false;
			}
			boolean any = false;
			for (int i = 0; i < length; i++) {
				int px = this.x + direction.getOffsetX() * i;
				int pz = this.z + direction.getOffsetZ() * i;
				int y = this.floor(px, pz);
				if (y == Integer.MIN_VALUE || Math.abs(y - base) > 1 || !this.water(px, y + 1, pz)) {
					break;
				}
				this.set(px, y + 1, pz, log.getDefaultState().withIfExists(PillarBlock.AXIS, direction.getAxis()));
				any = true;
				Direction side = direction.rotateYClockwise();
				int sx = px + side.getOffsetX() * (this.random.nextBoolean() ? 1 : -1);
				int sz = pz + side.getOffsetZ() * (this.random.nextBoolean() ? 1 : -1);
				int sy = this.floor(sx, sz);
				if (sy != Integer.MIN_VALUE && this.water(sx, sy + 1, sz) && this.random.nextBoolean()) {
					this.set(sx, sy + 1, sz, Blocks.SEAGRASS.getDefaultState());
				}
			}
			return any;
		}

		/** Water (or a water plant) here. */
		boolean water(int x, int y, int z) {
			BlockState state = this.at(x, y, z);
			return state.isOf(Blocks.WATER) || state.isOf(Blocks.SEAGRASS) || state.isOf(Blocks.TALL_SEAGRASS);
		}

		/** A dense patch of tall seagrass. */
		boolean seagrassBed() {
			boolean any = false;
			for (int i = 0; i < 40; i++) {
				int px = this.x + this.random.nextInt(9) - 4;
				int pz = this.z + this.random.nextInt(9) - 4;
				int y = this.floor(px, pz);
				if (y == Integer.MIN_VALUE || !this.at(px, y + 1, pz).isOf(Blocks.WATER) && !this.at(px, y + 1, pz).isOf(Blocks.SEAGRASS)
						|| !this.at(px, y + 2, pz).isOf(Blocks.WATER)) {
					continue;
				}
				BlockPos at = new BlockPos(px, y + 1, pz);
				BlockState tall = Blocks.TALL_SEAGRASS.getDefaultState();
				if (tall.canPlaceAt(this.world, at)) {
					TallPlantBlock.placeAt(this.world, tall, at, Block.NOTIFY_LISTENERS);
					any = true;
				}
			}
			return any;
		}

		/** Bands of gravel across the sandy floor. */
		boolean sandRipples() {
			double angle = this.random.nextDouble() * Math.PI;
			boolean any = false;
			for (int dx = -6; dx <= 6; dx++) {
				for (int dz = -6; dz <= 6; dz++) {
					if (dx * dx + dz * dz > 36) {
						continue;
					}
					double across = dx * Math.cos(angle) + dz * Math.sin(angle);
					if (Math.floorMod((int) Math.floor(across), 3) != 0) {
						continue;
					}
					int px = this.x + dx;
					int pz = this.z + dz;
					int y = this.floor(px, pz);
					if (y != Integer.MIN_VALUE && this.at(px, y, pz).isIn(BlockTags.SAND)) {
						this.set(px, y, pz, Blocks.GRAVEL.getDefaultState());
						any = true;
					}
				}
			}
			return any;
		}

		/** A patch of clay in the floor. */
		boolean clayPatch() {
			int radius = 2 + this.random.nextInt(3);
			boolean any = false;
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (dx * dx + dz * dz > radius * radius) {
						continue;
					}
					int px = this.x + dx;
					int pz = this.z + dz;
					int y = this.floor(px, pz);
					if (y == Integer.MIN_VALUE) {
						continue;
					}
					for (int d = 0; d < 2; d++) {
						BlockState state = this.at(px, y - d, pz);
						if (state.isIn(BlockTags.SAND) || state.isOf(Blocks.GRAVEL) || state.isIn(BlockTags.DIRT)) {
							this.set(px, y - d, pz, Blocks.CLAY.getDefaultState());
							any = true;
						}
					}
				}
			}
			return any;
		}

		/** Sea pickles in groups of three and four on little rises of the floor. */
		boolean seaPickles() {
			boolean any = false;
			int groups = 3 + this.random.nextInt(3);
			for (int g = 0; g < groups; g++) {
				int px = this.x + this.random.nextInt(9) - 4;
				int pz = this.z + this.random.nextInt(9) - 4;
				int y = this.floor(px, pz);
				if (y == Integer.MIN_VALUE || !this.at(px, y + 1, pz).isOf(Blocks.WATER) || !this.at(px, y + 2, pz).isOf(Blocks.WATER)) {
					continue;
				}
				this.set(px, y + 1, pz, this.at(px, y, pz).getBlock().getDefaultState());
				this.set(px, y + 2, pz, Blocks.SEA_PICKLE.getDefaultState().with(SeaPickleBlock.PICKLES, 3 + this.random.nextInt(2)).with(Properties.WATERLOGGED, true));
				any = true;
			}
			return any;
		}

		/** A rounded mound of coral blocks with coral and fans on it. */
		boolean coralHead() {
			int base = this.floor(this.x, this.z);
			if (base == Integer.MIN_VALUE) {
				return false;
			}
			double radius = 1.5 + this.random.nextDouble() * 1.2;
			int r = (int) Math.ceil(radius);
			boolean any = false;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					for (int dy = 0; dy <= r; dy++) {
						if (dx * dx + dz * dz + dy * dy * 1.5 > radius * radius) {
							continue;
						}
						int px = this.x + dx;
						int py = base + 1 + dy;
						int pz = this.z + dz;
						if (this.water(px, py, pz) && this.at(px, py + 1, pz).getFluidState().isOf(Fluids.WATER)) {
							this.set(px, py, pz, CORAL_BLOCKS[this.random.nextInt(CORAL_BLOCKS.length)].getDefaultState());
							any = true;
						}
					}
				}
			}
			// Coral and fans on top.
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					int px = this.x + dx;
					int pz = this.z + dz;
					for (int py = base + r + 2; py > base; py--) {
						if (this.at(px, py - 1, pz).isIn(BlockTags.CORAL_BLOCKS) && this.at(px, py, pz).isOf(Blocks.WATER)) {
							if (this.random.nextInt(3) == 0) {
								Block[] kind = this.random.nextBoolean() ? CORALS : FANS;
								this.set(px, py, pz, kind[this.random.nextInt(kind.length)].getDefaultState());
							}
							break;
						}
					}
				}
			}
			return any;
		}

		/** A flat slab of packed ice floating on the surface. */
		boolean iceFloe() {
			int radius = 3 + this.random.nextInt(4);
			int surface = this.sea - 1;
			if (!this.at(this.x, surface, this.z).isOf(Blocks.WATER)) {
				return false;
			}
			boolean any = false;
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (dx * dx + dz * dz > radius * radius - this.random.nextInt(radius + 1)) {
						continue;
					}
					if (this.at(this.x + dx, surface, this.z + dz).isOf(Blocks.WATER) && this.at(this.x + dx, surface + 1, this.z + dz).isAir()) {
						this.set(this.x + dx, surface, this.z + dz, Blocks.PACKED_ICE.getDefaultState());
						any = true;
					}
				}
			}
			return any;
		}
	}
}
