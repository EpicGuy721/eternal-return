package com.eternalreturn.worldgen.climate;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.worldgen.EternalReturnServer;
import com.eternalreturn.worldgen.WorldgenFeatures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Climate rules (phase 5 of the worldgen expansion, docs/worldgen.md), Eternal Return worlds only. Moderner Beta's
 * snow-and-freeze step (it takes over vanilla's freeze_top_layer in these worlds) lays one layer of snow on cold ground
 * and freezes the top of cold water; the feature eternalreturn:climate runs right after it, last of every overworld
 * biome's decoration, and:
 * <ul>
 * <li>deepens the snow by temperature: colder spots get more layers, up to a full 8, with soft drifts;</li>
 * <li>lets snow fall through tree leaves: a layer now and then on leaves inside the crown, and thinner snow on the ground
 * under it;</li>
 * <li>thickens the ice on cold water by temperature, always leaving the bottom of the water open.</li>
 * </ul>
 * Lava lakes in cold spots get an obsidian crust over their lava as they are made (LakeFeatureMixin, {@link #crustLava}),
 * and storms pile snow up to the same depth and sift it through leaves (ClimateStormMixin). All the numbers are in the
 * feature's JSON, data/eternalreturn/worldgen/configured_feature/climate.json.
 */
public final class Climate extends Feature<Climate.Config> {
	public static final Climate FEATURE = Registry.register(Registries.FEATURE, EternalReturn.id("climate"), new Climate());
	public static final RegistryKey<ConfiguredFeature<?, ?>> CONFIGURED = RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, EternalReturn.id("climate"));

	/** One point of a curve: at this temperature, this value; between points the value runs straight from one to the next. */
	public record Point(float temperature, float value) {
		static final Codec<Point> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.FLOAT.fieldOf("temperature").forGetter(Point::temperature),
				Codec.FLOAT.fieldOf("value").forGetter(Point::value)).apply(instance, Point::new));
	}

	/**
	 * The rules' numbers. Temperatures are the biome's (as F3's biome JSON gives them), cooled by height_cooling for every
	 * block above y 80, and averaged over the biomes around (blend_radius) so the snow doesn't step at a border.
	 */
	public record Config(float snowLine, float heightCooling, int blendRadius, List<Point> snowLayers, float driftLayers, float driftSize,
			float canopyShare, float canopyGapChance, List<Point> iceLayers, int waterKept, List<Point> lavaCrust, int lavaKept) implements FeatureConfig {
		public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.FLOAT.fieldOf("snow_line").forGetter(Config::snowLine),
				Codec.FLOAT.fieldOf("height_cooling").forGetter(Config::heightCooling),
				Codec.intRange(0, 12).fieldOf("blend_radius").forGetter(Config::blendRadius),
				Point.CODEC.listOf().fieldOf("snow_layers").forGetter(Config::snowLayers),
				Codec.FLOAT.fieldOf("drift_layers").forGetter(Config::driftLayers),
				Codec.floatRange(1, 1024).fieldOf("drift_size").forGetter(Config::driftSize),
				Codec.floatRange(0, 1).fieldOf("canopy_share").forGetter(Config::canopyShare),
				Codec.floatRange(0, 1).fieldOf("canopy_gap_chance").forGetter(Config::canopyGapChance),
				Point.CODEC.listOf().fieldOf("ice_layers").forGetter(Config::iceLayers),
				Codec.intRange(0, 64).fieldOf("water_kept").forGetter(Config::waterKept),
				Point.CODEC.listOf().fieldOf("lava_crust").forGetter(Config::lavaCrust),
				Codec.intRange(0, 64).fieldOf("lava_kept").forGetter(Config::lavaKept)).apply(instance, Config::new));
	}

	/** How far down the water and lava are counted. */
	private static final int MAX_DEPTH = 48;
	/** How much open air over a lava lake counts as open to the sky. */
	private static final int SKY_CHECK = 48;

	private static volatile long noiseSeed;
	@Nullable
	private static volatile SimplexNoiseSampler noise;

	private Climate() {
		super(Config.CODEC);
	}

	public static void register() {
		// Last of the last step, after the freezing, so no other feature's place in the decoration order moves. The
		// placed feature carries eternalreturn:eternal_return_only.
		BiomeModifications.addFeature(WorldgenFeatures.overworld(), GenerationStep.Feature.TOP_LAYER_MODIFICATION,
				RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalReturn.id("climate")));
		FogTints.register();
	}

	/** The rules of this world's climate feature (null if the data has none). */
	@Nullable
	public static Config config(DynamicRegistryManager registries) {
		return registries.getOptional(RegistryKeys.CONFIGURED_FEATURE).flatMap(registry -> registry.getOrEmpty(CONFIGURED))
				.map(feature -> feature.config() instanceof Config config ? config : null).orElse(null);
	}

	/** Whether the rules apply in this running world: the overworld of an Eternal Return world. */
	public static boolean applies(World world) {
		return world.getRegistryKey() == World.OVERWORLD && EternalReturnServer.active();
	}

	private static EternalReturnConfig.WorldgenTweaks settings() {
		return EternalReturnConfig.get().worldgen;
	}

	// ---- Temperature and the curves ----

	/** The temperature here: the biomes' around, averaged, cooled with height above y 80. */
	public static float temperature(WorldView world, Config config, BlockPos pos) {
		int r = config.blendRadius();
		float sum = 0;
		int count = 0;
		for (int[] offset : r > 0 ? new int[][]{{0, 0}, {r, 0}, {-r, 0}, {0, r}, {0, -r}} : new int[][]{{0, 0}}) {
			int x = pos.getX() + offset[0];
			int z = pos.getZ() + offset[1];
			// A world being made can only be read round the chunk it makes: samples past it are left out. A chunk region
			// holds more chunks than a feature may read (isChunkLoaded says yes to them, and reading one throws), so the
			// test is the distance from the chunk being decorated: one chunk at most. (The biome is read unblurred:
			// vanilla's blur reads round the spot, past what may be read.)
			if (world instanceof net.minecraft.world.ChunkRegion region && region.getCenterPos().getChebyshevDistance(x >> 4, z >> 4) > 1) {
				continue;
			}
			sum += world.getBiomeForNoiseGen(x >> 2, pos.getY() >> 2, z >> 2).value().getTemperature();
			count++;
		}
		return (count == 0 ? world.getBiome(pos).value().getTemperature() : sum / count) - Math.max(0, pos.getY() - 80) * config.heightCooling();
	}

	/** A curve's value at a temperature: straight between its points, flat past its ends. */
	public static float curve(List<Point> points, float temperature) {
		if (points.isEmpty()) {
			return 0;
		}
		Point first = points.getFirst();
		if (temperature >= first.temperature()) {
			return first.value();
		}
		for (int i = 1; i < points.size(); i++) {
			Point warm = points.get(i - 1);
			Point cold = points.get(i);
			if (temperature >= cold.temperature()) {
				float t = (warm.temperature() - temperature) / Math.max(1e-6F, warm.temperature() - cold.temperature());
				return MathHelper.lerp(t, warm.value(), cold.value());
			}
		}
		return points.getLast().value();
	}

	private static SimplexNoiseSampler noise(long seed) {
		SimplexNoiseSampler sampler = noise;
		if (sampler == null || noiseSeed != seed) {
			sampler = new SimplexNoiseSampler(Random.create(seed ^ 0x5E0A_C11FL));
			noiseSeed = seed;
			noise = sampler;
		}
		return sampler;
	}

	/** The snow layers that settle on open ground here (0: too warm for snow). */
	public static int snowDepth(WorldView world, long seed, Config config, BlockPos pos) {
		float temperature = temperature(world, config, pos);
		if (temperature >= config.snowLine()) {
			return 0;
		}
		double drift = noise(seed).sample(pos.getX() / config.driftSize(), pos.getZ() / config.driftSize()) * config.driftLayers();
		return MathHelper.clamp(Math.round(curve(config.snowLayers(), temperature) + (float) drift), 1, SnowBlock.MAX_LAYERS);
	}

	/** The snow layers that get through the leaves to the ground under a tree, for an open-ground depth. */
	public static int canopyDepth(Config config, int openDepth) {
		return openDepth <= 0 ? 0 : Math.max(1, Math.round(openDepth * config.canopyShare()));
	}

	// ---- The generation step ----

	@Override
	public boolean generate(FeatureContext<Config> context) {
		WorldAccess world = context.getWorld();
		Config config = context.getConfig();
		Random random = context.getRandom();
		long seed = context.getWorld().getSeed();
		EternalReturnConfig.WorldgenTweaks settings = settings();
		BlockPos origin = context.getOrigin();
		int startX = origin.getX() & ~15;
		int startZ = origin.getZ() & ~15;
		BlockPos.Mutable top = new BlockPos.Mutable();
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = startX + dx;
				int z = startZ + dz;
				top.set(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z), z);
				BlockState state = world.getBlockState(top);
				BlockState under = world.getBlockState(top.down());
				if (state.isOf(Blocks.SNOW)) {
					if (under.isIn(BlockTags.LEAVES)) {
						if (settings.snowThroughLeaves) {
							sift(world, seed, config, top.toImmutable(), random, false);
						}
					} else if (settings.deepSnow) {
						int depth = snowDepth(world, seed, config, top);
						if (depth > state.get(SnowBlock.LAYERS)) {
							world.setBlockState(top, state.with(SnowBlock.LAYERS, depth), 2);
						}
					}
				} else if (com.eternalreturn.blocks.SnowCover.isFlora(state) || state.isAir() && com.eternalreturn.blocks.SnowCover.isHost(under)) {
					// Moderner Beta's freezing step lays no snow in plants, slabs, fences and the like (Snowlogging): here they
					// get as many layers as the ground around them.
					int depth = snowDepth(world, seed, config, top);
					if (depth > 0) {
						int most = settings.deepSnow ? depth : 1;
						BlockPos at = top.toImmutable();
						for (int i = 0; i < most && com.eternalreturn.blocks.Snowlogging.snowfallHere(world, at, most, Block.NOTIFY_LISTENERS); i++) {
						}
					}
				} else if (under.isOf(Blocks.ICE) && settings.thickLakeIce) {
					thickenIce(world, config, top.down());
				}
			}
		}
		return true;
	}

	/**
	 * Snow falling through a tree crown from the snow on its top leaves (top): on leaves inside the crown, where there is
	 * room above them, a layer with chance canopy_gap_chance; on the ground under the crown, canopyDepth of the open
	 * ground's depth. In a storm (storm true) each settles one layer at a time, up to the same depth; at generation the
	 * whole depth settles at once. Stops at anything but air and leaves (a trunk keeps the ground under it bare).
	 */
	public static void sift(WorldAccess world, long seed, Config config, BlockPos top, Random random, boolean storm) {
		int flags = storm ? 3 : 2;
		BlockPos.Mutable at = top.mutableCopy().move(0, -1, 0);
		while (at.getY() > world.getBottomY()) {
			at.move(0, -1, 0);
			BlockState state = world.getBlockState(at);
			if (state.isIn(BlockTags.LEAVES)) {
				continue;
			}
			if (!state.isAir() && !state.isOf(Blocks.SNOW)) {
				return;
			}
			BlockState below = world.getBlockState(at.down());
			if (below.isAir() || below.isOf(Blocks.SNOW)) {
				// (Snow already lying lower down: the next layer goes on it.)
				continue;
			}
			if (below.isIn(BlockTags.LEAVES)) {
				// Room on leaves inside the crown.
				if (state.isAir() && random.nextFloat() < config.canopyGapChance() && Blocks.SNOW.getDefaultState().canPlaceAt(world, at)) {
					world.setBlockState(at, Blocks.SNOW.getDefaultState(), flags);
					if (storm) {
						return;
					}
				}
				continue;
			}
			// The ground under the crown.
			int depth = canopyDepth(config, snowDepth(world, seed, config, at));
			if (depth <= 0 || !Blocks.SNOW.getDefaultState().canPlaceAt(world, at)) {
				return;
			}
			int now = state.isOf(Blocks.SNOW) ? state.get(SnowBlock.LAYERS) : 0;
			int layers = storm ? Math.min(depth, now + 1) : Math.max(depth, now);
			if (layers > now) {
				world.setBlockState(at, Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, layers), flags);
				if (!storm && below.contains(Properties.SNOWY)) {
					world.setBlockState(at.down(), below.with(Properties.SNOWY, true), flags);
				}
			}
			return;
		}
	}

	/** Thicker ice on the cold water under this frozen top (ice): ice_layers in all, the bottom water_kept blocks left water. */
	static void thickenIce(WorldAccess world, Config config, BlockPos ice) {
		BlockPos.Mutable at = ice.mutableCopy();
		int water = 0;
		while (water < MAX_DEPTH && world.getFluidState(at.move(0, -1, 0)).isOf(Fluids.WATER) && world.getBlockState(at).isOf(Blocks.WATER)) {
			water++;
		}
		if (water == 0) {
			return;
		}
		int layers = Math.round(curve(config.iceLayers(), temperature(world, config, ice)));
		int extra = Math.min(layers - 1, water - config.waterKept());
		at.set(ice);
		for (int i = 0; i < extra; i++) {
			world.setBlockState(at.move(0, -1, 0), Blocks.ICE.getDefaultState(), 2);
		}
	}

	/** Whether a feature is being placed in the overworld of an Eternal Return world. */
	public static boolean inEternalReturn(FeatureContext<?> context) {
		return context.getWorld().toServerWorld().getRegistryKey() == World.OVERWORLD
				&& context.getGenerator() instanceof net.minecraft.world.gen.chunk.NoiseChunkGenerator generator
				&& com.eternalreturn.worldgen.caves.CaveWorlds.usesEternalReturnSettings(generator, context.getWorld().getRegistryManager());
	}

	/** Whether a world being made is the overworld of an Eternal Return world. */
	public static boolean inEternalReturn(net.minecraft.world.StructureWorldAccess world) {
		return world.toServerWorld().getRegistryKey() == World.OVERWORLD && EternalReturnServer.active();
	}

	/** A lake just made (LakeFeatureMixin): a lava one in an Eternal Return world may get an obsidian crust. */
	public static void afterLake(FeatureContext<net.minecraft.world.gen.feature.LakeFeature.Config> context) {
		if (!settings().coldLavaCrust || context.getOrigin().getY() <= context.getWorld().getBottomY() + 4 || !inEternalReturn(context)) {
			return;
		}
		Config config = config(context.getWorld().getRegistryManager());
		if (config != null) {
			crustLava(context.getWorld(), config, context.getOrigin().down(4));
		}
	}

	/**
	 * A lava lake just made (its box from corner, 16 x 8 x 16, lava in its lower four layers): where its top lies open to
	 * the sky in a cold spot, the lava's top turns to obsidian, lava_crust layers thick, keeping lava_kept layers of lava
	 * under it (where the lake is shallower, the crust takes what there is).
	 */
	public static void crustLava(WorldAccess world, Config config, BlockPos corner) {
		BlockPos.Mutable at = new BlockPos.Mutable();
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = corner.getX() + dx;
				int z = corner.getZ() + dz;
				int topY = -1;
				for (int dy = 3; dy >= 0; dy--) {
					if (world.getBlockState(at.set(x, corner.getY() + dy, z)).isOf(Blocks.LAVA)) {
						topY = corner.getY() + dy;
						break;
					}
				}
				if (topY < 0 || !openAbove(world, at.set(x, topY, z))) {
					continue;
				}
				at.set(x, topY, z);
				float temperature = temperature(world, config, at);
				if (temperature >= config.snowLine()) {
					continue;
				}
				int lava = 0;
				while (lava < MAX_DEPTH && world.getBlockState(at.set(x, topY - lava, z)).isOf(Blocks.LAVA)) {
					lava++;
				}
				int crust = Math.round(curve(config.lavaCrust(), temperature));
				crust = lava <= config.lavaKept() ? Math.min(crust, lava) : Math.min(crust, lava - config.lavaKept());
				for (int i = 0; i < crust; i++) {
					world.setBlockState(at.set(x, topY - i, z), Blocks.OBSIDIAN.getDefaultState(), 2);
				}
			}
		}
	}

	/** Whether nothing but air lies over this block for SKY_CHECK blocks (or to the top of the world): open to the sky. */
	private static boolean openAbove(WorldAccess world, BlockPos pos) {
		BlockPos.Mutable at = pos.mutableCopy();
		int top = Math.min(world.getTopY(), pos.getY() + SKY_CHECK);
		while (at.getY() < top - 1) {
			if (!world.getBlockState(at.move(0, 1, 0)).isAir()) {
				return false;
			}
		}
		return true;
	}

	// ---- Storms ----

	/** How many layers a storm may pile at this snowfall spot: the climate's depth on open ground, one layer on leaves. */
	public static int stormLimit(ServerWorld world, BlockPos top, int rule) {
		if (rule <= 0 || !applies(world) || !settings().deepSnow) {
			return rule;
		}
		Config config = config(world.getRegistryManager());
		if (config == null || world.getBlockState(top.down()).isIn(BlockTags.LEAVES)) {
			return rule;
		}
		return Math.max(rule, snowDepth(world, world.getSeed(), config, top));
	}

	/** A storm's snowfall at this spot (top, over leaves): some of it sifts through the crown. */
	public static void stormSift(ServerWorld world, BlockPos top) {
		if (!applies(world) || !settings().snowThroughLeaves || !world.getBlockState(top.down()).isIn(BlockTags.LEAVES)) {
			return;
		}
		Config config = config(world.getRegistryManager());
		if (config != null && world.getBiome(top).value().isCold(top)) {
			sift(world, world.getSeed(), config, top, world.random, true);
		}
	}
}
