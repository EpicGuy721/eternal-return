package com.eternalreturn.worldgen.ocean;

import com.eternalreturn.worldgen.terrain.TerrainShape;
import com.eternalreturn.worldgen.trees.BiomeTrees;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MultifaceGrowthBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.SeaPickleBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.loot.LootTables;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The oceans' depth zones and the coasts (worldgen expansion 2, phase 5; docs/worldgen.md, Oceans). One feature, run once
 * per chunk in every overworld biome (Eternal Return worlds only), each placed feature doing one part (its "part"),
 * one chunk in "rarity" where the part is rare:
 * <ul>
 * <li>floors: the shelf (floor y 15 and up) sand with ripple rows, the middle plain clay patches, the slope walls between
 * the steps bare stone (the stone under them) with glow lichen; the abyss (below y -15) as it is.
 * <li>boulders: rounded rocks of the stone under them on the shelf.
 * <li>grottoes: flooded dead-end grottoes running into the slope walls, glow lichen inside.
 * <li>sea_caves: an opening at sea level in a coastal cliff, a tunnel into a dry grotto; one in "extra" goes on to the
 * nearest cave.
 * <li>tide_pools: little stone-rimmed pools of sea water on the beach, sea pickles or seagrass in them.
 * <li>driftwood: logs of the wood growing inland lying washed up on the beach, often stripped (bleached).
 * <li>sunken_forest: stumps and fallen trunks of the inland wood on the shelf.
 * <li>ship_debris: a mast, planks, slabs, stairs, fences and trapdoors strewn on the middle plain; one in "extra" with a
 * barrel of shipwreck supplies.
 * <li>whale_fall: a whale's skeleton of bone blocks on the deep floor.
 * <li>pack_ice (phase 6, batch 1; in the biomes of eternalreturn:pack_ice_seas): the sea's surface one sheet of ice,
 * broken by long open leads of water, with ridges of packed ice and snow along their edges and snow on the sheet.
 * <li>kelp_forest (phase 6, batch 1; in eternalreturn:kelp_forests): kelp from the floor to the surface, in dense stands.
 * <li>vents (phase 6, batch 3; in eternalreturn:vent_fields): one to three basalt chimneys on a patch of blackstone and
 * basalt, each with a magma mouth, columns of bubbles rising from soul sand beside them to the surface.
 * <li>whale_field (phase 6, batch 3): a whale's skeleton, as whale_fall, with prismarine strewn round it.
 * <li>blue_hole_rim (phase 6, batch 3): round each sinkhole (a blue hole) a ring of coral blocks, coral and sea pickles on
 * the floor just outside its edge, where there's water.
 * <li>lagoon_mud (phase 6, batch 2; in eternalreturn:mangrove_lagoons): where the water is 6 deep or less, the loose floor
 * (sand, gravel, dirt, clay) turns to mud, a little with moss, so the lagoon's mangroves take root along the shallows.
 * </ul>
 */
public final class OceanFeature extends Feature<OceanFeature.Config> {
	public record Config(String part, int rarity, int extra) implements FeatureConfig {
		public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("part").forGetter(Config::part),
				Codec.intRange(1, 100000).optionalFieldOf("rarity", 1).forGetter(Config::rarity),
				Codec.intRange(1, 1000).optionalFieldOf("extra", 1).forGetter(Config::extra)).apply(instance, Config::new));
	}

	/** Floors at this y and up are the shelf; below ABYSS the abyss; between them the middle plain. */
	public static final int SHELF = (int) ((TerrainShape.SHELF_Y + TerrainShape.MIDDLE_Y) / 2);
	public static final int ABYSS = (int) ((TerrainShape.MIDDLE_Y + TerrainShape.DEEP_Y) / 2);
	/** A floor that changes this much over 3 blocks is a slope wall. */
	public static final int STEEP = 4;
	private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

	public static final net.minecraft.registry.tag.TagKey<Biome> PACK_ICE_SEAS = net.minecraft.registry.tag.TagKey.of(
			net.minecraft.registry.RegistryKeys.BIOME, com.eternalreturn.EternalReturn.id("pack_ice_seas"));
	public static final net.minecraft.registry.tag.TagKey<Biome> ICEBERG_SEAS = net.minecraft.registry.tag.TagKey.of(
			net.minecraft.registry.RegistryKeys.BIOME, com.eternalreturn.EternalReturn.id("iceberg_seas"));
	public static final net.minecraft.registry.tag.TagKey<Biome> VENT_FIELDS = net.minecraft.registry.tag.TagKey.of(
			net.minecraft.registry.RegistryKeys.BIOME, com.eternalreturn.EternalReturn.id("vent_fields"));
	public static final net.minecraft.registry.tag.TagKey<Biome> MANGROVE_LAGOONS = net.minecraft.registry.tag.TagKey.of(
			net.minecraft.registry.RegistryKeys.BIOME, com.eternalreturn.EternalReturn.id("mangrove_lagoons"));
	public static final net.minecraft.registry.tag.TagKey<Biome> KELP_FORESTS = net.minecraft.registry.tag.TagKey.of(
			net.minecraft.registry.RegistryKeys.BIOME, com.eternalreturn.EternalReturn.id("kelp_forests"));
	private static volatile long noiseSeed = Long.MIN_VALUE;
	private static volatile SimplexNoiseSampler[] noises;

	public OceanFeature() {
		super(Config.CODEC);
	}

	@Override
	public boolean generate(FeatureContext<Config> context) {
		Config config = context.getConfig();
		Random random = context.getRandom();
		if (config.rarity() > 1 && random.nextInt(config.rarity()) != 0) {
			return false;
		}
		Job job = new Job(context.getWorld(), context.getGenerator(), new ChunkPos(context.getOrigin()), random, config.extra());
		return switch (config.part()) {
			case "floors" -> job.floors();
			case "boulders" -> job.boulders();
			case "grottoes" -> job.grotto();
			case "sea_caves" -> job.seaCave();
			case "tide_pools" -> job.tidePools();
			case "driftwood" -> job.driftwood();
			case "sunken_forest" -> job.sunkenForest();
			case "ship_debris" -> job.shipDebris();
			case "whale_fall" -> job.whaleFall();
			case "pack_ice" -> job.packIce();
			case "kelp_forest" -> job.kelpForest();
			case "lagoon_mud" -> job.lagoonMud();
			case "vents" -> job.vents();
			case "whale_field" -> job.whaleField();
			case "blue_hole_rim" -> job.blueHoleRim();
			default -> false;
		};
	}

	/** Noises from the world seed: ripple patches, ripple rows, clay patches. */
	static SimplexNoiseSampler noise(long seed, int which) {
		if (noiseSeed != seed || noises == null) {
			SimplexNoiseSampler[] made = new SimplexNoiseSampler[6];
			for (int i = 0; i < made.length; i++) {
				made[i] = new SimplexNoiseSampler(Random.create(seed ^ (0x0CEA17L * (i + 1))));
			}
			noises = made;
			noiseSeed = seed;
		}
		return noises[which];
	}

	/** One run of the feature in one chunk. */
	private static final class Job {
		final StructureWorldAccess world;
		final ChunkGenerator generator;
		final ChunkPos chunk;
		final Random random;
		final int extra;
		final int sea;

		Job(StructureWorldAccess world, ChunkGenerator generator, ChunkPos chunk, Random random, int extra) {
			this.world = world;
			this.generator = generator;
			this.chunk = chunk;
			this.random = random;
			this.extra = extra;
			this.sea = generator.getSeaLevel();
		}

		// ------------------------------------------------------------------ helpers

		/** Whether a block can be written: this chunk and the ring of chunks round it. */
		boolean writable(int x, int z) {
			return x >= this.chunk.getStartX() - 16 && x <= this.chunk.getEndX() + 16 && z >= this.chunk.getStartZ() - 16 && z <= this.chunk.getEndZ() + 16;
		}

		boolean writable(BlockPos pos) {
			return this.writable(pos.getX(), pos.getZ());
		}

		/** The top solid block under the water (or the ground's top on land). */
		int floor(int x, int z) {
			return this.world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z) - 1;
		}

		int surface(int x, int z) {
			return this.world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z) - 1;
		}

		void set(BlockPos pos, BlockState state) {
			if (this.writable(pos)) {
				this.world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
			}
		}

		boolean water(BlockPos pos) {
			BlockState state = this.world.getBlockState(pos);
			return state.isOf(Blocks.WATER);
		}

		/** Sea water over the floor here, in an ocean biome. */
		boolean sea(int x, int z) {
			int floor = this.floor(x, z);
			return floor < this.sea - 1 && this.water(new BlockPos(x, floor + 1, z)) && this.biomeAt(x, this.sea - 1, z).isIn(BiomeTags.IS_OCEAN);
		}

		/** How much the floor changes over 3 blocks round here, the most of the four ways. */
		int steepness(int x, int z) {
			int floor = this.floor(x, z);
			int most = 0;
			for (Direction side : SIDES) {
				most = Math.max(most, Math.abs(this.floor(x + side.getOffsetX() * 3, z + side.getOffsetZ() * 3) - floor));
			}
			return most;
		}

		static boolean loose(BlockState state) {
			return state.isOf(Blocks.SAND) || state.isOf(Blocks.RED_SAND) || state.isOf(Blocks.GRAVEL) || state.isOf(Blocks.DIRT) || state.isOf(Blocks.CLAY)
					|| state.isOf(Blocks.COARSE_DIRT) || state.isOf(Blocks.MUD);
		}

		/** The stone under this spot: the first block of the overworld's stones within 12 down (the biome's stone), else stone. */
		BlockState stoneUnder(int x, int y, int z) {
			BlockPos.Mutable pos = new BlockPos.Mutable(x, y, z);
			for (int i = 0; i < 12; i++) {
				BlockState state = this.world.getBlockState(pos);
				if (state.isIn(BlockTags.BASE_STONE_OVERWORLD)) {
					return state.getBlock().getDefaultState();
				}
				pos.move(Direction.DOWN);
			}
			return Blocks.STONE.getDefaultState();
		}

		/** Glow lichen in the water at pos, on the face of the block on the given side, if that face is solid. */
		void lichen(BlockPos pos, Direction side) {
			if (!this.writable(pos) || !this.writable(pos.offset(side))) {
				return;
			}
			BlockState here = this.world.getBlockState(pos);
			boolean wet = here.isOf(Blocks.WATER);
			boolean dry = here.isAir();
			boolean merge = here.isOf(Blocks.GLOW_LICHEN);
			if (!wet && !dry && !merge) {
				return;
			}
			BlockPos on = pos.offset(side);
			if (!this.world.getBlockState(on).isSideSolidFullSquare(this.world, on, side.getOpposite())) {
				return;
			}
			BlockState lichen = merge ? here : Blocks.GLOW_LICHEN.getDefaultState().with(Properties.WATERLOGGED, wet);
			this.set(pos, lichen.with(MultifaceGrowthBlock.getProperty(side), true));
		}

		/** The biome at any spot, generated or not (from the generator's biome source). */
		RegistryEntry<Biome> biomeAt(int x, int y, int z) {
			if (this.writable(x, z)) {
				// The biome stored in the chunk (no blending with the neighbours', which may lie out of reach).
				return this.world.getBiomeForNoiseGen(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(y), BiomeCoords.fromBlock(z));
			}
			NoiseConfig noise = this.world.toServerWorld().getChunkManager().getNoiseConfig();
			return this.generator.getBiomeSource().getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(y), BiomeCoords.fromBlock(z), noise.getMultiNoiseSampler());
		}

		/**
		 * The log of the wood growing inland of here: the nearest land biome (not sea, beach or river) within 96 blocks
		 * that has a tree table of its own, one of its saplings' woods. Null where no such land is near.
		 */
		@Nullable
		Block inlandLog(int x, int z) {
			for (int distance = 16; distance <= 96; distance += 16) {
				for (int i = 0; i < 8; i++) {
					double angle = Math.PI * 2 * i / 8;
					int bx = x + (int) Math.round(Math.cos(angle) * distance);
					int bz = z + (int) Math.round(Math.sin(angle) * distance);
					RegistryEntry<Biome> biome = this.biomeAt(bx, this.sea + 4, bz);
					if (biome.isIn(BiomeTags.IS_OCEAN) || biome.isIn(BiomeTags.IS_BEACH) || biome.isIn(BiomeTags.IS_RIVER)) {
						continue;
					}
					List<Identifier> saplings = new ArrayList<>(BiomeTrees.ownLists(biome).keySet());
					for (int tries = 0; tries < saplings.size() * 2 && !saplings.isEmpty(); tries++) {
						Block log = logOf(saplings.get(this.random.nextInt(saplings.size())));
						if (log != null) {
							return log;
						}
					}
				}
			}
			return null;
		}

		@Nullable
		static Block logOf(Identifier sapling) {
			String path = sapling.getPath();
			String base = path.endsWith("_sapling") ? path.substring(0, path.length() - "_sapling".length())
					: path.equals("mangrove_propagule") ? "mangrove" : null;
			if (base == null) {
				return null;
			}
			Identifier log = Identifier.of(sapling.getNamespace(), base + "_log");
			return Registries.BLOCK.containsId(log) ? Registries.BLOCK.get(log) : null;
		}

		/** The stripped (bleached) log of a log, or the log itself where there is none. */
		static Block stripped(Block log) {
			Identifier id = Registries.BLOCK.getId(log);
			Identifier strippedId = Identifier.of(id.getNamespace(), "stripped_" + id.getPath());
			return Registries.BLOCK.containsId(strippedId) ? Registries.BLOCK.get(strippedId) : log;
		}

		BlockPos column(int spread) {
			return new BlockPos(this.chunk.getStartX() + 8 + this.random.nextInt(spread * 2 + 1) - spread, 0,
					this.chunk.getStartZ() + 8 + this.random.nextInt(spread * 2 + 1) - spread);
		}

		// ------------------------------------------------------------------ the depth zones

		boolean floors() {
			long seed = this.world.getSeed();
			SimplexNoiseSampler patches = noise(seed, 0);
			SimplexNoiseSampler rows = noise(seed, 1);
			SimplexNoiseSampler clay = noise(seed, 2);
			boolean any = false;
			BlockPos.Mutable pos = new BlockPos.Mutable();
			for (int x = this.chunk.getStartX(); x <= this.chunk.getEndX(); x++) {
				for (int z = this.chunk.getStartZ(); z <= this.chunk.getEndZ(); z++) {
					if (!this.sea(x, z)) {
						continue;
					}
					int floor = this.floor(x, z);
					int steep = this.steepness(x, z);
					any = true;
					if (steep >= STEEP) {
						this.bareWall(x, floor, z);
					} else if (floor >= SHELF) {
						// The shelf: sand, with rows of ripples in patches.
						for (int y = floor; y > floor - 4; y--) {
							BlockState state = this.world.getBlockState(pos.set(x, y, z));
							if (!state.isOf(Blocks.GRAVEL) && !state.isOf(Blocks.DIRT)) {
								break;
							}
							this.world.setBlockState(pos, Blocks.SAND.getDefaultState(), Block.NOTIFY_LISTENERS);
						}
						if (steep <= 1 && floor < this.sea - 3 && patches.sample(x / 48.0, z / 48.0) > 0.1) {
							double angle = patches.sample(x / 300.0 + 50, z / 300.0) * Math.PI;
							double across = x * Math.cos(angle) + z * Math.sin(angle) + rows.sample(x / 24.0, z / 24.0) * 1.5;
							// 1.4 wide, so a row running on the slant still joins up from block to block.
							if (MathHelper.floorMod(across, 4.5) < 1.4 && this.world.getBlockState(pos.set(x, floor, z)).isOf(Blocks.SAND)
									&& this.water(pos.set(x, floor + 1, z))) {
								this.world.setBlockState(pos, Blocks.SAND.getDefaultState(), Block.NOTIFY_LISTENERS);
							}
						}
					} else if (floor >= ABYSS) {
						// The middle plain: clay in patches among its sand and gravel.
						if (clay.sample(x / 20.0, z / 20.0) > 0.45) {
							for (int y = floor; y > floor - 2; y--) {
								if (loose(this.world.getBlockState(pos.set(x, y, z)))) {
									this.world.setBlockState(pos, Blocks.CLAY.getDefaultState(), Block.NOTIFY_LISTENERS);
								}
							}
						}
					}
				}
			}
			return any;
		}

		/** A slope wall: its loose cover gives way to the stone under it, glow lichen on its face now and then. */
		void bareWall(int x, int floor, int z) {
			BlockPos.Mutable pos = new BlockPos.Mutable();
			int y = floor;
			while (y > floor - 7 && loose(this.world.getBlockState(pos.set(x, y, z)))) {
				y--;
			}
			if (y < floor) {
				BlockState stone = this.stoneUnder(x, y, z);
				for (int fill = y + 1; fill <= floor; fill++) {
					this.world.setBlockState(pos.set(x, fill, z), stone, Block.NOTIFY_LISTENERS);
				}
			}
			if (this.random.nextInt(6) == 0) {
				for (Direction side : SIDES) {
					int nx = x + side.getOffsetX();
					int nz = z + side.getOffsetZ();
					if (this.floor(nx, nz) <= floor - 2) {
						this.lichen(new BlockPos(nx, floor - 1 - this.random.nextInt(Math.max(1, floor - 1 - this.floor(nx, nz))), nz), side.getOpposite());
						return;
					}
				}
			}
		}

		/** One to three rounded rocks of the stone under them, half sunk into the shelf. */
		boolean boulders() {
			boolean any = false;
			for (int n = 1 + this.random.nextInt(3); n > 0; n--) {
				BlockPos column = this.column(6);
				if (!this.sea(column.getX(), column.getZ())) {
					continue;
				}
				int floor = this.floor(column.getX(), column.getZ());
				if (floor < SHELF || floor > this.sea - 5 || this.steepness(column.getX(), column.getZ()) > 2) {
					continue;
				}
				double radius = 1.3 + this.random.nextDouble() * 1.4;
				double tall = radius * (0.7 + this.random.nextDouble() * 0.3);
				BlockState stone = this.stoneUnder(column.getX(), floor, column.getZ());
				int reach = MathHelper.ceil(radius);
				for (int dx = -reach; dx <= reach; dx++) {
					for (int dz = -reach; dz <= reach; dz++) {
						for (int dy = -reach; dy <= reach; dy++) {
							double d = (dx * dx + dz * dz) / (radius * radius) + dy * dy / (tall * tall);
							if (d > 1 + this.random.nextDouble() * 0.15) {
								continue;
							}
							BlockPos pos = column.add(dx, floor + dy, dz);
							BlockState there = this.world.getBlockState(pos);
							if (there.isOf(Blocks.WATER) || loose(there)) {
								int roll = this.random.nextInt(10);
								this.set(pos, roll == 0 ? Blocks.COBBLESTONE.getDefaultState() : roll == 1 ? Blocks.MOSSY_COBBLESTONE.getDefaultState() : stone);
							}
						}
					}
				}
				any = true;
			}
			return any;
		}

		// ------------------------------------------------------------------ grottoes and sea caves

		/** A flooded dead-end grotto running 10 to 25 blocks into a slope wall. */
		boolean grotto() {
			for (int tries = 0; tries < 16; tries++) {
				BlockPos column = this.column(6);
				int x = column.getX();
				int z = column.getZ();
				if (!this.sea(x, z) || this.steepness(x, z) < STEEP) {
					continue;
				}
				int floor = this.floor(x, z);
				double gx = this.floor(x + 3, z) - this.floor(x - 3, z);
				double gz = this.floor(x, z + 3) - this.floor(x, z - 3);
				double length = Math.sqrt(gx * gx + gz * gz);
				if (length < 3) {
					continue;
				}
				double heading = Math.atan2(gz / length, gx / length);
				double px = x + 0.5 - Math.cos(heading) * 2;
				double py = floor - 2;
				double pz = z + 0.5 - Math.sin(heading) * 2;
				int steps = 10 + this.random.nextInt(16);
				List<BlockPos> carved = new ArrayList<>();
				for (int step = 0; step < steps; step++) {
					double radius = 1.6 + this.random.nextDouble() * 0.8;
					if (!this.carve(px, py, pz, radius, radius * 0.85, Blocks.WATER.getDefaultState(), Integer.MIN_VALUE, carved, false)) {
						break;
					}
					heading += (this.random.nextDouble() - 0.5) * 0.5;
					px += Math.cos(heading);
					pz += Math.sin(heading);
					py += (this.random.nextDouble() - 0.5) * 0.6;
				}
				if (carved.size() < 20) {
					continue;
				}
				this.decorate(carved, true);
				return true;
			}
			return false;
		}

		/**
		 * Carves an ellipsoid with this fill (water or cave air), not below floorY; false, carving nothing, where it would
		 * break into something it mustn't: air or lava (for water), any fluid (for air), bedrock, or outside the writable area.
		 * A dry carve also keeps 2 blocks of ground over it unless "open" (the mouth).
		 */
		boolean carve(double cx, double cy, double cz, double radius, double tall, BlockState fill, int floorY, List<BlockPos> carved, boolean open) {
			boolean wet = fill.isOf(Blocks.WATER);
			int reach = MathHelper.ceil(Math.max(radius, tall)) + 1;
			List<BlockPos> inside = new ArrayList<>();
			BlockPos.Mutable near = new BlockPos.Mutable();
			for (int dx = -reach; dx <= reach; dx++) {
				for (int dy = -reach; dy <= reach; dy++) {
					for (int dz = -reach; dz <= reach; dz++) {
						BlockPos pos = BlockPos.ofFloored(cx + dx, cy + dy, cz + dz);
						double ex = pos.getX() + 0.5 - cx;
						double ey = pos.getY() + 0.5 - cy;
						double ez = pos.getZ() + 0.5 - cz;
						double d = (ex * ex + ez * ez) / (radius * radius) + ey * ey / (tall * tall);
						if (d > 1 || pos.getY() < floorY) {
							continue;
						}
						if (!this.writable(pos) || pos.getY() <= this.world.getBottomY() + 4) {
							return false;
						}
						BlockState state = this.world.getBlockState(pos);
						if (state.isOf(Blocks.BEDROCK) || wet && state.isAir()) {
							return false;
						}
						if (!wet && !open && pos.getY() > this.surface(pos.getX(), pos.getZ()) - 3) {
							return false;
						}
						for (Direction side : Direction.values()) {
							BlockState next = this.world.getBlockState(near.set(pos, side));
							// Water must not meet air or lava; a dry carve must not open beside or under a fluid (one under it stays put).
							if (wet ? (next.isAir() || next.getFluidState().isIn(FluidTags.LAVA)) : side != Direction.DOWN && !next.getFluidState().isEmpty()) {
								return false;
							}
						}
						if (!state.isAir() && state.getFluidState().isEmpty()) {
							inside.add(pos);
						}
					}
				}
			}
			for (BlockPos pos : inside) {
				this.world.setBlockState(pos, fill, Block.NOTIFY_LISTENERS);
				carved.add(pos);
				// A loose ceiling would fall in: it becomes the stone round it.
				BlockPos up = pos.up();
				if (loose(this.world.getBlockState(up)) || this.world.getBlockState(up).isOf(Blocks.SAND) || this.world.getBlockState(up).isOf(Blocks.GRAVEL)) {
					this.world.setBlockState(up, this.stoneUnder(pos.getX(), pos.getY() - 1, pos.getZ()), Block.NOTIFY_LISTENERS);
				}
			}
			return true;
		}

		/** Glow lichen on a grotto's walls and ceiling, now and then sea pickles (flooded) on its floor. */
		void decorate(List<BlockPos> carved, boolean flooded) {
			for (BlockPos pos : carved) {
				BlockState here = this.world.getBlockState(pos);
				if (!(flooded ? here.isOf(Blocks.WATER) : here.isAir())) {
					continue;
				}
				if (flooded && this.random.nextInt(40) == 0 && this.world.getBlockState(pos.down()).isSideSolidFullSquare(this.world, pos.down(), Direction.UP)) {
					this.set(pos, Blocks.SEA_PICKLE.getDefaultState().with(SeaPickleBlock.PICKLES, 1 + this.random.nextInt(3)).with(Properties.WATERLOGGED, true));
					continue;
				}
				if (this.random.nextInt(flooded ? 5 : 9) == 0) {
					Direction side = Direction.values()[this.random.nextInt(6)];
					if (side != Direction.DOWN) {
						this.lichen(pos, side);
					}
				}
			}
		}

		/** A sea cave: an opening at sea level in a coastal cliff, a tunnel inland to a dry grotto, sometimes on to a cave. */
		boolean seaCave() {
			for (int tries = 0; tries < 24; tries++) {
				BlockPos column = this.column(5);
				int x = column.getX();
				int z = column.getZ();
				int top = this.surface(x, z);
				if (top < this.sea + 9 || !this.world.getBlockState(new BlockPos(x, top, z)).getFluidState().isEmpty()
						|| this.biomeAt(x, this.sea, z).isIn(BiomeTags.IS_OCEAN)) {
					continue;
				}
				Direction toSea = null;
				for (Direction side : SIDES) {
					for (int d = 1; d <= 3 && toSea == null; d++) {
						int sx = x + side.getOffsetX() * d;
						int sz = z + side.getOffsetZ() * d;
						if (this.water(new BlockPos(sx, this.sea - 1, sz)) && this.surface(sx, sz) <= this.sea - 1
								&& this.biomeAt(sx, this.sea, sz).isIn(BiomeTags.IS_OCEAN)) {
							toSea = side;
						}
					}
				}
				if (toSea == null) {
					continue;
				}
				double heading = Math.atan2(-toSea.getOffsetZ(), -toSea.getOffsetX());
				double px = x + 0.5 + toSea.getOffsetX();
				double pz = z + 0.5 + toSea.getOffsetZ();
				int floorY = this.sea;
				int steps = 8 + this.random.nextInt(9);
				List<BlockPos> carved = new ArrayList<>();
				int done = 0;
				for (int step = 0; step < steps; step++) {
					if (step >= 4 && step % 4 == 0 && floorY < this.sea + 3) {
						floorY++;
					}
					if (!this.carve(px, floorY + 1.6, pz, 1.7, 2.1, Blocks.CAVE_AIR.getDefaultState(), floorY, carved, step < 3)) {
						break;
					}
					done++;
					heading += (this.random.nextDouble() - 0.5) * 0.4;
					px += Math.cos(heading);
					pz += Math.sin(heading);
				}
				if (done < 6) {
					continue;
				}
				// The grotto at its end.
				double roomRadius = 4 + this.random.nextDouble() * 2;
				double rx = px + Math.cos(heading) * (roomRadius - 1);
				double rz = pz + Math.sin(heading) * (roomRadius - 1);
				List<BlockPos> room = new ArrayList<>();
				boolean grotto = this.carve(rx, floorY + 2.5, rz, roomRadius, 3.2, Blocks.CAVE_AIR.getDefaultState(), floorY, room, false);
				if (!grotto) {
					// A smaller one, or none: the tunnel then ends where it is.
					grotto = this.carve(rx, floorY + 1.8, rz, roomRadius * 0.6, 2.2, Blocks.CAVE_AIR.getDefaultState(), floorY, room, false);
				}
				carved.addAll(room);
				if (grotto && this.random.nextInt(this.extra) == 0) {
					this.toCave(rx, floorY + 1.5, rz, carved);
				}
				this.decorate(carved, false);
				return true;
			}
			return false;
		}

		/** On from a grotto toward the nearest cave below in reach, until it opens into it. */
		void toCave(double x, double y, double z, List<BlockPos> carved) {
			BlockPos target = null;
			double best = Double.MAX_VALUE;
			BlockPos.Mutable pos = new BlockPos.Mutable();
			for (int bx = this.chunk.getStartX() - 14; bx <= this.chunk.getEndX() + 14; bx += 2) {
				for (int bz = this.chunk.getStartZ() - 14; bz <= this.chunk.getEndZ() + 14; bz += 2) {
					for (int by = (int) y - 4; by > y - 50 && by > this.world.getBottomY() + 8; by -= 2) {
						BlockState state = this.world.getBlockState(pos.set(bx, by, bz));
						if (state.isOf(Blocks.CAVE_AIR) || state.isAir() && by < this.surface(bx, bz) - 8) {
							double d = Math.pow(bx - x, 2) + Math.pow(by - y, 2) * 2 + Math.pow(bz - z, 2);
							if (d < best && d > 64) {
								best = d;
								target = pos.toImmutable();
							}
						}
					}
				}
			}
			if (target == null) {
				return;
			}
			double px = x;
			double py = y;
			double pz = z;
			for (int step = 0; step < 80; step++) {
				double dx = target.getX() + 0.5 - px;
				double dy = target.getY() + 0.5 - py;
				double dz = target.getZ() + 0.5 - pz;
				double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
				if (length < 1.5) {
					return;
				}
				px += dx / length;
				py += dy / length;
				pz += dz / length;
				// The last steps open into the cave, which is air: carve each block alone there.
				if (!this.carve(px, py, pz, 1.5, 1.6, Blocks.CAVE_AIR.getDefaultState(), Integer.MIN_VALUE, carved, false)) {
					BlockPos at = BlockPos.ofFloored(px, py, pz);
					if (this.world.getBlockState(at).isAir()) {
						return;
					}
					if (!this.writable(at)) {
						return;
					}
					BlockState state = this.world.getBlockState(at);
					if (!state.getFluidState().isEmpty() || state.isOf(Blocks.BEDROCK)) {
						return;
					}
					boolean wet = false;
					for (Direction side : Direction.values()) {
						wet |= !this.world.getFluidState(at.offset(side)).isEmpty();
					}
					if (wet) {
						return;
					}
					this.world.setBlockState(at, Blocks.CAVE_AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					this.world.setBlockState(at.up(), this.world.getBlockState(at.up()).getFluidState().isEmpty() && !this.world.getBlockState(at.up()).isOf(Blocks.BEDROCK)
							? Blocks.CAVE_AIR.getDefaultState() : this.world.getBlockState(at.up()), Block.NOTIFY_LISTENERS);
					carved.add(at);
				}
			}
		}

		// ------------------------------------------------------------------ the beach

		/** A spot of dry beach sand or gravel at about sea level with open water between near and far blocks away. */
		@Nullable
		BlockPos beachSpot(int near, int far) {
			for (int tries = 0; tries < 16; tries++) {
				BlockPos column = this.column(6);
				int top = this.surface(column.getX(), column.getZ());
				BlockPos ground = new BlockPos(column.getX(), top, column.getZ());
				BlockState state = this.world.getBlockState(ground);
				if (top < this.sea - 1 || top > this.sea + 2 || !(state.isOf(Blocks.SAND) || state.isOf(Blocks.GRAVEL)) || !this.world.isAir(ground.up())) {
					continue;
				}
				boolean tooClose = false;
				for (Direction side : SIDES) {
					for (int d = 1; d < near; d++) {
						tooClose |= this.world.getBlockState(new BlockPos(column.getX() + side.getOffsetX() * d, top, column.getZ() + side.getOffsetZ() * d)).getFluidState().isIn(FluidTags.WATER)
								|| this.surface(column.getX() + side.getOffsetX() * d, column.getZ() + side.getOffsetZ() * d) < this.sea - 1;
					}
				}
				if (tooClose) {
					continue;
				}
				for (Direction side : SIDES) {
					for (int d = near; d <= far; d++) {
						BlockPos at = new BlockPos(column.getX() + side.getOffsetX() * d, this.sea - 1, column.getZ() + side.getOffsetZ() * d);
						// Open water at least 2 deep, not a river's (the water by a beach is often still the beach's biome).
						if (this.water(at) && this.floor(at.getX(), at.getZ()) <= this.sea - 3
								&& !this.biomeAt(at.getX(), at.getY(), at.getZ()).isIn(BiomeTags.IS_RIVER)) {
							return ground;
						}
					}
				}
			}
			return null;
		}

		/** One or two little pools of sea water on the beach, rimmed with stone, sea pickles or seagrass in them. */
		boolean tidePools() {
			boolean any = false;
			int wanted = 1 + this.random.nextInt(2);
			for (int attempt = 0; attempt < 12 && wanted > 0; attempt++) {
				BlockPos ground = this.beachSpot(4, 9);
				if (ground == null) {
					break;
				}
				int level = ground.getY();
				double radius = 1.2 + this.random.nextDouble() * 1.3;
				int reach = MathHelper.ceil(radius + 1.5);
				List<BlockPos> pool = new ArrayList<>();
				List<BlockPos> rim = new ArrayList<>();
				boolean ok = true;
				for (int dx = -reach; dx <= reach && ok; dx++) {
					for (int dz = -reach; dz <= reach && ok; dz++) {
						double d = Math.sqrt(dx * dx + dz * dz) + (this.random.nextDouble() - 0.5) * 0.4;
						if (d > radius + 1.3) {
							continue;
						}
						int x = ground.getX() + dx;
						int z = ground.getZ() + dz;
						int surface = this.writable(x, z) ? this.surface(x, z) : Integer.MIN_VALUE;
						boolean pooled = d <= radius;
						// The pool sinks into the beach as it slopes: at most 3 dug down for the pool (higher ground round it holds
						// the water as well as a rim), a dip of 1 under the pool or 2 under the rim made up with stone; never where
						// the sea's water stands.
						if (surface == Integer.MIN_VALUE || pooled && surface > level + 3 || surface < level - (pooled ? 1 : 2)
								|| !this.world.getBlockState(new BlockPos(x, surface, z)).getFluidState().isEmpty()) {
							ok = false;
						} else if (pooled) {
							pool.add(new BlockPos(x, level, z));
						} else if (surface <= level + 1) {
							rim.add(new BlockPos(x, level, z));
							for (int y = surface + 1; y < level; y++) {
								rim.add(new BlockPos(x, y, z));
							}
						}
					}
				}
				if (!ok || pool.size() < 3) {
					continue;
				}
				BlockState stone = this.stoneUnder(ground.getX(), level - 1, ground.getZ());
				for (BlockPos pos : rim) {
					if (this.world.getBlockState(pos.up()).isAir() || this.world.getBlockState(pos.up()).isReplaceable()) {
						this.set(pos.up(), Blocks.AIR.getDefaultState());
					}
					this.set(pos, stone);
					if (this.random.nextInt(4) == 0) {
						this.set(pos.up(), this.random.nextInt(3) == 0 ? Blocks.MOSSY_COBBLESTONE.getDefaultState() : stone);
					}
				}
				for (BlockPos pos : pool) {
					this.set(pos.up(), Blocks.AIR.getDefaultState());
					this.set(pos.down(), stone);
					this.set(pos, Blocks.WATER.getDefaultState());
				}
				for (BlockPos pos : pool) {
					int roll = this.random.nextInt(6);
					if (roll == 0) {
						this.set(pos, Blocks.SEA_PICKLE.getDefaultState().with(SeaPickleBlock.PICKLES, 1 + this.random.nextInt(3)).with(Properties.WATERLOGGED, true));
					} else if (roll == 1) {
						this.set(pos, Blocks.SEAGRASS.getDefaultState());
					}
				}
				any = true;
				wanted--;
			}
			return any;
		}

		/** One or two logs of the inland wood washed up on the beach, half of them stripped (bleached by the sea). */
		boolean driftwood() {
			BlockPos ground = this.beachSpot(2, 6);
			if (ground == null) {
				return false;
			}
			Block log = this.inlandLog(ground.getX(), ground.getZ());
			if (log == null) {
				log = Blocks.OAK_LOG;
			}
			boolean any = this.log(ground, log, 2 + this.random.nextInt(4));
			if (any && this.random.nextInt(3) == 0) {
				BlockPos second = ground.add(this.random.nextInt(5) - 2, 0, this.random.nextInt(5) - 2);
				this.log(new BlockPos(second.getX(), this.surface(second.getX(), second.getZ()), second.getZ()), log, 2 + this.random.nextInt(2));
			}
			return any;
		}

		/** A log lying on the beach at this ground block, if the beach under it is level and dry. */
		boolean log(BlockPos ground, Block log, int length) {
			Direction.Axis axis = this.random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
			Direction along = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
			List<BlockPos> spots = new ArrayList<>();
			for (int i = 0; i < length; i++) {
				BlockPos at = ground.offset(along, i);
				BlockState under = this.world.getBlockState(at);
				if (!this.writable(at) || this.surface(at.getX(), at.getZ()) != ground.getY() || !under.isSideSolidFullSquare(this.world, at, Direction.UP)
						|| !under.getFluidState().isEmpty() || !this.world.isAir(at.up())) {
					return false;
				}
				spots.add(at.up());
			}
			BlockState state = (this.random.nextBoolean() ? stripped(log) : log).getDefaultState();
			if (state.contains(PillarBlock.AXIS)) {
				state = state.with(PillarBlock.AXIS, axis);
			}
			for (BlockPos spot : spots) {
				this.set(spot, state);
			}
			return true;
		}

		// ------------------------------------------------------------------ on the sea floor

		/** A flat spot of sea floor in this depth range near the chunk's middle, or null. */
		@Nullable
		BlockPos flatFloor(int low, int high, int spread, int slope) {
			for (int tries = 0; tries < 12; tries++) {
				BlockPos column = this.column(spread);
				if (!this.sea(column.getX(), column.getZ())) {
					continue;
				}
				int floor = this.floor(column.getX(), column.getZ());
				if (floor >= low && floor <= high && this.steepness(column.getX(), column.getZ()) <= slope) {
					return new BlockPos(column.getX(), floor, column.getZ());
				}
			}
			return null;
		}

		/** Stumps and fallen trunks of the inland wood on the shelf near a coast with trees. */
		boolean sunkenForest() {
			BlockPos centre = this.flatFloor(SHELF, this.sea - 6, 4, 2);
			if (centre == null) {
				return false;
			}
			Block log = this.inlandLog(centre.getX(), centre.getZ());
			if (log == null) {
				return false;
			}
			BlockState upright = log.getDefaultState();
			boolean any = false;
			for (int n = 5 + this.random.nextInt(6); n > 0; n--) {
				int x = centre.getX() + this.random.nextInt(21) - 10;
				int z = centre.getZ() + this.random.nextInt(21) - 10;
				if (!this.writable(x, z) || !this.sea(x, z)) {
					continue;
				}
				int floor = this.floor(x, z);
				if (floor < SHELF - 4 || floor > this.sea - 4) {
					continue;
				}
				int height = 1 + this.random.nextInt(this.random.nextInt(4) == 0 ? 5 : 3);
				for (int y = 1; y <= height && y < this.sea - 1 - floor; y++) {
					this.set(new BlockPos(x, floor + y, z), upright);
				}
				any = true;
			}
			for (int n = 1 + this.random.nextInt(3); n > 0; n--) {
				int x = centre.getX() + this.random.nextInt(15) - 7;
				int z = centre.getZ() + this.random.nextInt(15) - 7;
				Direction.Axis axis = this.random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
				Direction along = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
				BlockState lying = upright.contains(PillarBlock.AXIS) ? upright.with(PillarBlock.AXIS, axis) : upright;
				for (int i = 0, length = 4 + this.random.nextInt(4); i < length; i++) {
					int bx = x + along.getOffsetX() * i;
					int bz = z + along.getOffsetZ() * i;
					if (!this.writable(bx, bz) || !this.sea(bx, bz)) {
						break;
					}
					BlockPos at = new BlockPos(bx, this.floor(bx, bz) + 1, bz);
					if (this.water(at)) {
						this.set(at, lying);
					}
				}
			}
			return any;
		}

		static BlockPos level(BlockPos pos, int y) {
			return new BlockPos(pos.getX(), y, pos.getZ());
		}

		/** Water-loggable pieces keep the sea in them. */
		static BlockState wet(BlockState state) {
			return state.contains(Properties.WATERLOGGED) ? state.with(Properties.WATERLOGGED, true) : state;
		}

		/** A broken ship's wood strewn over the middle plain: a mast, planks and pieces, sometimes a barrel of supplies. */
		boolean shipDebris() {
			BlockPos centre = this.flatFloor(ABYSS, SHELF - 1, 3, 2);
			if (centre == null) {
				return false;
			}
			String[] woods = {"oak", "spruce", "dark_oak"};
			String wood = woods[this.random.nextInt(woods.length)];
			Block log = Registries.BLOCK.get(Identifier.ofVanilla(wood + "_log"));
			Block planks = Registries.BLOCK.get(Identifier.ofVanilla(wood + "_planks"));
			Block slab = Registries.BLOCK.get(Identifier.ofVanilla(wood + "_slab"));
			Block stairs = Registries.BLOCK.get(Identifier.ofVanilla(wood + "_stairs"));
			Block fence = Registries.BLOCK.get(Identifier.ofVanilla(wood + "_fence"));
			Block trapdoor = Registries.BLOCK.get(Identifier.ofVanilla(wood + "_trapdoor"));
			// The mast, lying along the floor.
			Direction along = this.random.nextBoolean() ? Direction.EAST : Direction.SOUTH;
			BlockState mast = log.getDefaultState().with(PillarBlock.AXIS, along.getAxis());
			BlockPos start = centre.offset(along, -4);
			for (int i = 0, length = 6 + this.random.nextInt(5); i < length; i++) {
				BlockPos column = start.offset(along, i);
				if (!this.writable(column) || !this.sea(column.getX(), column.getZ())) {
					continue;
				}
				BlockPos at = new BlockPos(column.getX(), this.floor(column.getX(), column.getZ()) + 1, column.getZ());
				if (Math.abs(at.getY() - centre.getY() - 1) <= 1 && this.water(at)) {
					this.set(at, mast);
				}
			}
			// The pieces round it.
			for (int n = 10 + this.random.nextInt(8); n > 0; n--) {
				int x = centre.getX() + this.random.nextInt(15) - 7;
				int z = centre.getZ() + this.random.nextInt(15) - 7;
				if (!this.writable(x, z) || !this.sea(x, z)) {
					continue;
				}
				BlockPos at = new BlockPos(x, this.floor(x, z) + 1, z);
				if (!this.water(at)) {
					continue;
				}
				Direction facing = SIDES[this.random.nextInt(4)];
				BlockState piece = switch (this.random.nextInt(6)) {
					case 0, 1 -> planks.getDefaultState();
					case 2 -> slab.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
					case 3 -> stairs.getDefaultState().with(StairsBlock.FACING, facing).with(StairsBlock.HALF, this.random.nextBoolean() ? BlockHalf.BOTTOM : BlockHalf.TOP);
					case 4 -> fence.getDefaultState();
					default -> trapdoor.getDefaultState().with(TrapdoorBlock.FACING, facing).with(TrapdoorBlock.HALF, BlockHalf.BOTTOM);
				};
				this.set(at, wet(piece));
				// Now and then a plank half buried, or a second piece on top.
				if (this.random.nextInt(5) == 0 && this.water(at.up())) {
					this.set(at.up(), wet(slab.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM)));
				}
			}
			// One field in "extra": a barrel of the ship's supplies.
			if (this.random.nextInt(this.extra) == 0) {
				for (int tries = 0; tries < 8; tries++) {
					int x = centre.getX() + this.random.nextInt(7) - 3;
					int z = centre.getZ() + this.random.nextInt(7) - 3;
					if (!this.writable(x, z) || !this.sea(x, z)) {
						continue;
					}
					BlockPos at = new BlockPos(x, this.floor(x, z) + 1, z);
					if (this.water(at)) {
						this.set(at, Blocks.BARREL.getDefaultState().with(BarrelBlock.FACING, this.random.nextInt(3) == 0 ? Direction.UP : SIDES[this.random.nextInt(4)]));
						if (this.world.getBlockEntity(at) instanceof BarrelBlockEntity barrel) {
							barrel.setLootTable(LootTables.SHIPWRECK_SUPPLY_CHEST, this.random.nextLong());
						}
						break;
					}
				}
			}
			return true;
		}

		/** Whether this column's sea is in a biome of the tag (by the biome at the sea's surface). */
		boolean seaOf(int x, int z, net.minecraft.registry.tag.TagKey<Biome> tag) {
			return this.biomeAt(x, this.sea - 1, z).isIn(tag) && this.water(new BlockPos(x, this.sea - 1, z));
		}

		/**
		 * Pack ice: every column of the sea's top block becomes ice, but for the leads (long winding cracks of open water
		 * where a noise crosses zero, about 4 blocks wide, and thinner branches), and the ice by a lead now and then rides
		 * up into a ridge of packed ice or snow; snow lies on most of the sheet.
		 */
		boolean packIce() {
			long seed = this.world.getSeed();
			SimplexNoiseSampler leads = noise(seed, 4);
			SimplexNoiseSampler branches = noise(seed, 5);
			boolean any = false;
			BlockPos.Mutable pos = new BlockPos.Mutable();
			int top = this.sea - 1;
			for (int x = this.chunk.getStartX(); x <= this.chunk.getEndX(); x++) {
				for (int z = this.chunk.getStartZ(); z <= this.chunk.getEndZ(); z++) {
					BlockState surface = this.world.getBlockState(pos.set(x, top, z));
					if (!(surface.isOf(Blocks.WATER) || surface.isOf(Blocks.ICE)) || !this.biomeAt(x, top, z).isIn(PACK_ICE_SEAS)
							|| this.floor(x, z) >= top) {
						continue;
					}
					any = true;
					double lead = Math.min(Math.abs(leads.sample(x / 110.0, z / 110.0)) / 0.04, Math.abs(branches.sample(x / 45.0, z / 45.0)) / 0.025);
					BlockPos above = new BlockPos(x, top + 1, z);
					if (lead < 1) {
						this.world.setBlockState(pos, Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
						if (!this.world.isAir(above) && this.world.getBlockState(above).isReplaceable()) {
							this.world.setBlockState(above, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
						}
						continue;
					}
					this.world.setBlockState(pos, Blocks.ICE.getDefaultState(), Block.NOTIFY_LISTENERS);
					if (!this.world.isAir(above)) {
						continue;
					}
					if (lead < 1.8 && this.random.nextInt(3) == 0) {
						// A pressure ridge by the lead.
						this.world.setBlockState(above, (this.random.nextInt(3) == 0 ? Blocks.SNOW_BLOCK : Blocks.PACKED_ICE).getDefaultState(), Block.NOTIFY_LISTENERS);
						if (this.random.nextInt(3) == 0) {
							this.world.setBlockState(above.up(), Blocks.SNOW.getDefaultState(), Block.NOTIFY_LISTENERS);
						}
					} else if (lead > 2.5 || this.random.nextBoolean()) {
						// Snow over the sheet; bare ice in patches only along the leads.
						this.world.setBlockState(above, Blocks.SNOW.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
			}
			return any;
		}

		/** One to three chimneys of a vent field, each on its patch of dark rock, bubbles rising beside it. */
		boolean vents() {
			boolean any = false;
			for (int n = 1 + this.random.nextInt(3); n > 0; n--) {
				BlockPos column = this.column(5);
				int x = column.getX();
				int z = column.getZ();
				if (!this.seaOf(x, z, VENT_FIELDS)) {
					continue;
				}
				int floor = this.floor(x, z);
				if (floor > this.sea - 12) {
					continue;
				}
				// The patch: blackstone, basalt and now and then magma in the floor round it.
				for (int dx = -5; dx <= 5; dx++) {
					for (int dz = -5; dz <= 5; dz++) {
						if (dx * dx + dz * dz > 25 - this.random.nextInt(8)) {
							continue;
						}
						int fx = x + dx;
						int fz = z + dz;
						if (!this.writable(fx, fz)) {
							continue;
						}
						BlockPos ground = new BlockPos(fx, this.floor(fx, fz), fz);
						int roll = this.random.nextInt(10);
						this.set(ground, (roll < 5 ? Blocks.BLACKSTONE : roll < 8 ? Blocks.BASALT : roll < 9 ? Blocks.MAGMA_BLOCK : Blocks.SOUL_SAND).getDefaultState());
						if (roll == 9) {
							// Soul sand: a column of bubbles up to the surface.
							for (int y = ground.getY() + 1; y < this.sea - 1 && this.water(new BlockPos(fx, y, fz)); y++) {
								this.set(new BlockPos(fx, y, fz), Blocks.BUBBLE_COLUMN.getDefaultState().with(net.minecraft.block.BubbleColumnBlock.DRAG, false));
							}
						}
					}
				}
				// The chimney: basalt narrowing upward, a magma mouth on top.
				int height = 3 + this.random.nextInt(8);
				for (int y = 1; y <= height; y++) {
					double radius = y < height / 2 ? 1.5 : 0.6;
					for (int dx = -2; dx <= 2; dx++) {
						for (int dz = -2; dz <= 2; dz++) {
							if (dx * dx + dz * dz <= radius * radius) {
								BlockPos at = new BlockPos(x + dx, floor + y, z + dz);
								if (this.writable(at) && this.water(at)) {
									this.set(at, (this.random.nextInt(4) == 0 ? Blocks.BLACKSTONE : Blocks.BASALT).getDefaultState());
								}
							}
						}
					}
				}
				BlockPos mouth = new BlockPos(x, floor + height + 1, z);
				if (this.water(mouth)) {
					this.set(mouth, Blocks.MAGMA_BLOCK.getDefaultState());
				}
				any = true;
			}
			return any;
		}

		/** A whale fall field's skeleton, with prismarine strewn round it. */
		boolean whaleField() {
			BlockPos centre = this.flatFloor(this.world.getBottomY(), SHELF - 1, 2, 3);
			if (centre == null || !this.whaleFall()) {
				return false;
			}
			for (int n = 4 + this.random.nextInt(6); n > 0; n--) {
				int x = centre.getX() + this.random.nextInt(17) - 8;
				int z = centre.getZ() + this.random.nextInt(17) - 8;
				if (!this.writable(x, z) || !this.sea(x, z)) {
					continue;
				}
				BlockPos at = new BlockPos(x, this.floor(x, z) + 1, z);
				if (this.water(at)) {
					int roll = this.random.nextInt(3);
					this.set(at, (roll == 0 ? Blocks.PRISMARINE : roll == 1 ? Blocks.DARK_PRISMARINE : Blocks.BONE_BLOCK).getDefaultState());
				}
			}
			return true;
		}

		/** The terrain plan of this world's biome provider (Moderner Beta's), or null. */
		@Nullable
		TerrainShape.State shape() {
			try {
				Object source = this.generator.getBiomeSource();
				Object provider = source.getClass().getMethod("getBiomeProvider").invoke(source);
				return provider instanceof com.eternalreturn.worldgen.terrain.TerrainShapeHolder holder ? holder.eternalreturn$shape() : null;
			} catch (ReflectiveOperationException | RuntimeException e) {
				return null;
			}
		}

		/** The blue hole's rim: coral blocks, coral and sea pickles on the floor just outside each sinkhole's edge. */
		boolean blueHoleRim() {
			boolean any = false;
			BlockPos.Mutable pos = new BlockPos.Mutable();
			Block[] blocks = {Blocks.TUBE_CORAL_BLOCK, Blocks.BRAIN_CORAL_BLOCK, Blocks.BUBBLE_CORAL_BLOCK, Blocks.FIRE_CORAL_BLOCK, Blocks.HORN_CORAL_BLOCK};
			Block[] plants = {Blocks.TUBE_CORAL, Blocks.BRAIN_CORAL, Blocks.BUBBLE_CORAL, Blocks.FIRE_CORAL, Blocks.HORN_CORAL,
					Blocks.TUBE_CORAL_FAN, Blocks.BRAIN_CORAL_FAN, Blocks.BUBBLE_CORAL_FAN, Blocks.FIRE_CORAL_FAN, Blocks.HORN_CORAL_FAN};
			TerrainShape.State state = this.shape();
			if (state == null) {
				return false;
			}
			for (int x = this.chunk.getStartX(); x <= this.chunk.getEndX(); x++) {
				for (int z = this.chunk.getStartZ(); z <= this.chunk.getEndZ(); z++) {
					double offset = TerrainShape.sinkholeEdgeOffset(state, x, z);
					if (Double.isNaN(offset) || offset < 1 || offset > TerrainShape.BLUE_HOLE_RIM - 2 || this.random.nextInt(3) == 0) {
						continue;
					}
					int floor = this.floor(x, z);
					if (floor >= this.sea - 2 || !this.water(pos.set(x, floor + 1, z))) {
						continue;
					}
					this.world.setBlockState(pos.set(x, floor, z), blocks[this.random.nextInt(blocks.length)].getDefaultState(), Block.NOTIFY_LISTENERS);
					BlockPos up = new BlockPos(x, floor + 1, z);
					int roll = this.random.nextInt(4);
					if (roll < 2) {
						this.world.setBlockState(up, plants[this.random.nextInt(plants.length)].getDefaultState(), Block.NOTIFY_LISTENERS);
					} else if (roll == 2) {
						this.world.setBlockState(up, Blocks.SEA_PICKLE.getDefaultState().with(SeaPickleBlock.PICKLES, 1 + this.random.nextInt(4)), Block.NOTIFY_LISTENERS);
					}
					any = true;
				}
			}
			return any;
		}

		/** The mangrove lagoon's shallows: mud for a floor where the water is 6 deep or less, so mangroves can root. */
		boolean lagoonMud() {
			boolean any = false;
			BlockPos.Mutable pos = new BlockPos.Mutable();
			for (int x = this.chunk.getStartX(); x <= this.chunk.getEndX(); x++) {
				for (int z = this.chunk.getStartZ(); z <= this.chunk.getEndZ(); z++) {
					int floor = this.floor(x, z);
					if (floor < this.sea - 7 || floor >= this.sea - 1 || !this.seaOf(x, z, MANGROVE_LAGOONS)) {
						continue;
					}
					for (int y = floor; y > floor - 3; y--) {
						BlockState state = this.world.getBlockState(pos.set(x, y, z));
						if (!loose(state)) {
							break;
						}
						this.world.setBlockState(pos, y == floor && this.random.nextInt(8) == 0 ? Blocks.MOSS_BLOCK.getDefaultState() : Blocks.MUD.getDefaultState(),
								Block.NOTIFY_LISTENERS);
						any = true;
					}
				}
			}
			return any;
		}

		/** Kelp forest: dense stands of kelp from the floor up to the surface, thinning in patches. */
		boolean kelpForest() {
			SimplexNoiseSampler stands = noise(this.world.getSeed(), 3);
			boolean any = false;
			BlockPos.Mutable pos = new BlockPos.Mutable();
			BlockState plant = Blocks.KELP_PLANT.getDefaultState();
			BlockState tip = Blocks.KELP.getDefaultState().with(net.minecraft.block.KelpBlock.AGE, 25);
			for (int x = this.chunk.getStartX(); x <= this.chunk.getEndX(); x++) {
				for (int z = this.chunk.getStartZ(); z <= this.chunk.getEndZ(); z++) {
					if (!this.seaOf(x, z, KELP_FORESTS)) {
						continue;
					}
					double density = 0.3 + 0.25 * stands.sample(x / 24.0, z / 24.0);
					if (this.random.nextDouble() >= density) {
						continue;
					}
					int floor = this.floor(x, z);
					if (!this.world.getBlockState(pos.set(x, floor, z)).isSideSolidFullSquare(this.world, pos, Direction.UP)) {
						continue;
					}
					int y = floor + 1;
					while (y < this.sea - 1 && this.water(pos.set(x, y + 1, z))) {
						this.world.setBlockState(pos.set(x, y, z), plant, Block.NOTIFY_LISTENERS);
						y++;
					}
					if (y > floor + 1 && this.water(pos.set(x, y, z))) {
						this.world.setBlockState(pos, tip, Block.NOTIFY_LISTENERS);
						any = true;
					}
				}
			}
			return any;
		}

		/** A whale's skeleton of bone blocks on the deep floor: a spine, arched ribs, a skull and a tail. */
		boolean whaleFall() {
			BlockPos centre = this.flatFloor(this.world.getBottomY(), SHELF - 1, 2, 2);
			if (centre == null) {
				return false;
			}
			Direction along = SIDES[this.random.nextInt(4)];
			Direction side = along.rotateYClockwise();
			int length = 12 + this.random.nextInt(7);
			int base = centre.getY() + 1;
			BlockPos head = centre.offset(along, length / 2);
			BlockState spine = Blocks.BONE_BLOCK.getDefaultState().with(PillarBlock.AXIS, along.getAxis());
			BlockState rib = Blocks.BONE_BLOCK.getDefaultState().with(PillarBlock.AXIS, side.getAxis());
			BlockState upright = Blocks.BONE_BLOCK.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Y);
			Map<BlockPos, BlockState> bones = new java.util.LinkedHashMap<>();
			for (int i = 0; i < length; i++) {
				BlockPos at = level(head.offset(along, -i), base + (i > length - 4 ? -1 : 0));
				bones.put(at, spine);
				// Ribs arching up and over from the front half of the spine, every other vertebra.
				if (i >= 3 && i <= length * 0.6 && i % 2 == 1) {
					for (int s = -1; s <= 1; s += 2) {
						Direction out = s > 0 ? side : side.getOpposite();
						bones.put(at.offset(out, 1), rib);
						bones.put(at.offset(out, 2), rib);
						bones.put(at.offset(out, 2).up(), upright);
						bones.put(at.offset(out, 2).up(2), upright);
						if (this.random.nextInt(3) != 0) {
							bones.put(at.offset(out, 1).up(3), rib);
						}
					}
				}
			}
			// The skull: a block of bone in front of the spine.
			for (int f = 1; f <= 3; f++) {
				for (int s = -1; s <= 1; s++) {
					for (int up = 0; up <= 1; up++) {
						if (f == 3 && s != 0 && up == 1) {
							continue;
						}
						bones.put(level(head.offset(along, f).offset(side, s), base + up), spine);
					}
				}
			}
			// The tail's flukes.
			BlockPos tail = level(head.offset(along, -length), base - 1);
			bones.put(tail, spine);
			for (int s = 1; s <= 2; s++) {
				bones.put(tail.offset(side, s), rib);
				bones.put(tail.offset(side.getOpposite(), s), rib);
			}
			for (Map.Entry<BlockPos, BlockState> bone : bones.entrySet()) {
				BlockPos at = bone.getKey();
				BlockState there = this.world.getBlockState(at);
				if (this.writable(at) && (there.isOf(Blocks.WATER) || loose(there) || there.isOf(Blocks.SEAGRASS) || there.isOf(Blocks.TALL_SEAGRASS))) {
					this.set(at, bone.getValue());
				}
			}
			return true;
		}
	}
}
