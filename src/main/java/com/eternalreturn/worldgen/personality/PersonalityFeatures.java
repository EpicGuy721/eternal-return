package com.eternalreturn.worldgen.personality;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.worldgen.caves.CaveWorlds;
import com.eternalreturn.worldgen.clutter.Clutter;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.feature.SimpleBlockFeatureConfig;
import net.minecraft.world.gen.feature.TreeFeature;
import net.minecraft.world.gen.placementmodifier.PlacementModifier;
import net.minecraft.world.gen.feature.FeaturePlacementContext;
import net.minecraft.world.gen.stateprovider.BlockStateProvider;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The personality rolls' part in the world being made (Personality): density, by placing a biome's trees, grass patches
 * and boulders a little more or less often (PlacedFeatureMixin), and the flower palette, by planting the patch's own
 * flowers in place of the biome's others (SimpleBlockFlowerMixin), so the amount of flowers stays the same.
 */
public final class PersonalityFeatures {
	/** What a placed feature is to the density roll. */
	enum Kind { NONE, TREES, GRASS, BOULDERS }

	private static final Map<PlacedFeature, Kind> KINDS = Collections.synchronizedMap(new java.util.WeakHashMap<>());
	private static final Map<Biome, Flowers> FLOWERS = Collections.synchronizedMap(new java.util.WeakHashMap<>());
	private static final Set<Block> GRASSES = Set.of(Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN, Blocks.DEAD_BUSH);

	/** A biome's own flowers (block ids, sorted): the small ones and the tall ones. */
	record Flowers(List<String> small, List<String> tall) {
	}

	private PersonalityFeatures() {
	}

	/** Whether the rolls act in this world being made: an Eternal Return overworld, the setting on. */
	public static boolean active(StructureWorldAccess world, ChunkGenerator generator) {
		return EternalReturnConfig.get().worldgen.personalityRolls && world instanceof ChunkRegion && generator instanceof NoiseChunkGenerator noise
				&& CaveWorlds.usesEternalReturnSettings(noise, world.getRegistryManager());
	}

	@Nullable
	private static Personality.Roll rollAt(StructureWorldAccess world, BlockPos pos) {
		try {
			RegistryEntry<Biome> biome = world.getBiome(pos);
			Optional<Identifier> id = biome.getKey().map(key -> key.getValue());
			return id.map(value -> Personality.at(Personality.salt(world.getSeed()), value, pos.getX(), pos.getZ())).orElse(null);
		} catch (RuntimeException outside) {
			// Outside what this region may read.
			return null;
		}
	}

	/**
	 * A biome's placed feature generating in a chunk (PlacedFeatureMixin): trees, grass patches and boulders placed with
	 * each spot's patch density: below normal each spot kept with that chance; above it, the placement run again and its
	 * spots kept with the excess. Null for the rest, which generate as they would.
	 */
	@Nullable
	public static Boolean generate(PlacedFeature placed, StructureWorldAccess world, ChunkGenerator generator, Random random, BlockPos origin) {
		Kind kind = KINDS.computeIfAbsent(placed, PersonalityFeatures::kindOf);
		if (kind == Kind.NONE || !active(world, generator)) {
			return null;
		}
		FeaturePlacementContext context = new FeaturePlacementContext(world, generator, Optional.of(placed));
		ConfiguredFeature<?, ?> feature = placed.feature().value();
		// Each spot is worked out only after the trees before it have grown, as vanilla does (the placement's own checks,
		// a sapling's ground among them, then see them): spots worked out ahead put trees on other trees' trunks, which
		// vanilla's cocoa decorator can't take. The extra pass runs where the patch in the chunk's middle is denser (a chunk
		// lies in one cell).
		int middleX = origin.getX() + 8;
		int middleZ = origin.getZ() + 8;
		Personality.Roll middle = rollAt(world, new BlockPos(middleX, world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE_WG, middleX, middleZ), middleZ));
		boolean[] any = {false};
		stream(placed, context, random, origin).forEach(pos -> {
			Personality.Roll roll = rollAt(world, pos);
			double density = roll == null ? 1 : roll.density();
			if (density >= 1 || random.nextDouble() < density) {
				any[0] |= feature.generate(world, generator, random, pos);
			}
		});
		if (middle != null && middle.density() > 1) {
			stream(placed, context, random, origin).forEach(pos -> {
				Personality.Roll roll = rollAt(world, pos);
				double density = roll == null ? 1 : roll.density();
				if (density > 1 && random.nextDouble() < density - 1 && (kind != Kind.TREES || !trunkAt(world, pos))) {
					any[0] |= feature.generate(world, generator, random, pos);
				}
			});
		}
		return any[0];
	}

	/**
	 * Whether a tree's trunk already stands at a spot: an extra tree there would put no logs of its own (its trunk's
	 * space taken) yet still its leaves, and vanilla's cocoa decorator fails on a tree without logs.
	 */
	private static boolean trunkAt(StructureWorldAccess world, BlockPos pos) {
		for (int up = 0; up <= 3; up++) {
			if (world.getBlockState(pos.up(up)).isIn(BlockTags.LOGS)) {
				return true;
			}
		}
		return false;
	}

	/** The placement's spots, worked out lazily as vanilla does. */
	private static Stream<BlockPos> stream(PlacedFeature placed, FeaturePlacementContext context, Random random, BlockPos origin) {
		Stream<BlockPos> stream = Stream.of(origin);
		for (PlacementModifier modifier : placed.placementModifiers()) {
			stream = stream.flatMap(pos -> modifier.getPositions(context, random, pos));
		}
		return stream;
	}

	/** What a placed feature is to the density roll (trees, grass, boulders or none), for the tests. */
	public static String kind(PlacedFeature placed) {
		return kindOf(placed).name().toLowerCase(java.util.Locale.ROOT);
	}

	/** A biome's own small flowers and tall flowers (block ids), for the tests. */
	public static List<List<String>> flowersOfBiome(Biome biome) {
		Flowers flowers = FLOWERS.computeIfAbsent(biome, PersonalityFeatures::flowersOf);
		return List.of(flowers.small(), flowers.tall());
	}

	private static Kind kindOf(PlacedFeature placed) {
		List<ConfiguredFeature<?, ?>> features = inside(placed);
		boolean grass = false;
		for (ConfiguredFeature<?, ?> feature : features) {
			if (feature.feature() instanceof TreeFeature) {
				return Kind.TREES;
			}
			if (feature.feature() == Clutter.BOULDER) {
				return Kind.BOULDERS;
			}
			if (feature.config() instanceof SimpleBlockFeatureConfig simple) {
				for (BlockState state : sample(simple.toPlace())) {
					if (!GRASSES.contains(state.getBlock())) {
						return Kind.NONE;
					}
					grass = true;
				}
			}
		}
		return grass ? Kind.GRASS : Kind.NONE;
	}

	/**
	 * The configured features a placed feature can generate, nested ones too: vanilla's own list (getDecoratedFeatures)
	 * leaves out what random patches and vegetation patches place.
	 */
	static List<ConfiguredFeature<?, ?>> inside(PlacedFeature placed) {
		List<ConfiguredFeature<?, ?>> out = new ArrayList<>();
		walk(placed.feature().value(), out, 0);
		return out;
	}

	private static void walk(ConfiguredFeature<?, ?> feature, List<ConfiguredFeature<?, ?>> out, int depth) {
		if (depth > 8 || out.contains(feature)) {
			return;
		}
		out.add(feature);
		feature.config().getDecoratedFeatures().forEach(nested -> walk(nested, out, depth + 1));
		if (feature.config() instanceof net.minecraft.world.gen.feature.RandomPatchFeatureConfig patch) {
			walk(patch.feature().value().feature().value(), out, depth + 1);
		} else if (feature.config() instanceof net.minecraft.world.gen.feature.VegetationPatchFeatureConfig patch) {
			walk(patch.vegetationFeature.value().feature().value(), out, depth + 1);
		}
	}

	/** What a block state provider places: sampled over 1,024 spots far apart (noise providers vary across the land, and the plains' tulips need its lowest). */
	private static Set<BlockState> sample(BlockStateProvider provider) {
		Set<BlockState> states = new java.util.HashSet<>();
		Random random = Random.create(0x5EEDL);
		for (int i = 0; i < 1024; i++) {
			try {
				states.add(provider.get(random, new BlockPos(i * 1709 % 40_000 - 20_000, 64, i * 2903 % 40_000 - 20_000)));
			} catch (RuntimeException e) {
				// A provider that needs more than a spot: left out.
			}
		}
		return states;
	}

	/**
	 * A simple block feature about to place a state (SimpleBlockFlowerMixin): a flower of the biome's that isn't in the
	 * patch's palette becomes one that is (small for small, tall for tall), picked by the spot.
	 */
	public static BlockState flower(BlockState state, StructureWorldAccess world, ChunkGenerator generator, BlockPos pos) {
		boolean small = state.isIn(BlockTags.SMALL_FLOWERS);
		if (!small && !state.isIn(BlockTags.TALL_FLOWERS) || !active(world, generator)) {
			return state;
		}
		RegistryEntry<Biome> biome;
		try {
			biome = world.getBiome(pos);
		} catch (RuntimeException outside) {
			return state;
		}
		Personality.Roll roll = rollAt(world, pos);
		if (roll == null) {
			return state;
		}
		Flowers flowers = FLOWERS.computeIfAbsent(biome.value(), PersonalityFeatures::flowersOf);
		List<String> own = small ? flowers.small() : flowers.tall();
		String id = Registries.BLOCK.getId(state.getBlock()).toString();
		if (!own.contains(id)) {
			return state;
		}
		List<String> palette = roll.palette(own);
		if (palette.contains(id)) {
			return state;
		}
		String pick = palette.get(Math.floorMod((int) Personality.mix(pos.asLong() ^ roll.bits()), palette.size()));
		return Registries.BLOCK.get(Identifier.of(pick)).getDefaultState();
	}

	/** The flowers a biome's own decoration plants (sampled from its features' simple block placements). */
	static Flowers flowersOf(Biome biome) {
		Set<String> small = new TreeSet<>();
		Set<String> tall = new TreeSet<>();
		for (RegistryEntryList<PlacedFeature> step : biome.getGenerationSettings().getFeatures()) {
			for (RegistryEntry<PlacedFeature> placed : step) {
				inside(placed.value()).forEach(feature -> {
					if (feature.config() instanceof SimpleBlockFeatureConfig simple) {
						for (BlockState state : sample(simple.toPlace())) {
							if (state.isIn(BlockTags.SMALL_FLOWERS)) {
								small.add(Registries.BLOCK.getId(state.getBlock()).toString());
							} else if (state.isIn(BlockTags.TALL_FLOWERS)) {
								tall.add(Registries.BLOCK.getId(state.getBlock()).toString());
							}
						}
					}
				});
			}
		}
		return new Flowers(List.copyOf(new ArrayList<>(small)), List.copyOf(new ArrayList<>(tall)));
	}

	/** A biome's own flowers and this patch's palette of them, for the patch command. */
	public static List<List<String>> describeFlowers(Biome biome, Personality.Roll roll) {
		Flowers flowers = FLOWERS.computeIfAbsent(biome, PersonalityFeatures::flowersOf);
		return List.of(flowers.small(), roll.palette(flowers.small()), flowers.tall(), roll.palette(flowers.tall()));
	}
}
