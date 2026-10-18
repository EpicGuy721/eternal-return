package com.eternalreturn.worldgen;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Dungeons and nether warts, in Eternal Return worlds only (other worlds never change):
 * <ul>
 * <li>Overworld dungeons are twice as common: every dungeon try (vanilla's monster room feature) is followed by
 * a second one elsewhere in the same chunk (worldgen.moreDungeons). A third are built of cobblestone as in
 * vanilla, a third of stone bricks, a third of bricks.</li>
 * <li>The Nether has dungeons too, worldgen.netherDungeonAttempts tries per chunk, built of nether bricks or
 * red nether bricks, their spawners raising wither skeletons, blazes or witches. Their mobs drop nothing, like
 * every spawner's (SpawnerMobs).</li>
 * <li>Nether wart grows wild on soul sand in the Nether: a patch in worldgen.netherWartPatchChance of chunks
 * with soul sand open to the air.</li>
 * </ul>
 * The extra Nether decoration runs after a chunk's own features (ChunkGeneratorMixin), with its own random seeded
 * from the world seed and the chunk, so it is the same in every run.
 */
public final class Dungeons {
	/** A dungeon's floor (mossy in vanilla) and walls. */
	public enum Style {
		COBBLESTONE(Blocks.MOSSY_COBBLESTONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState(), false),
		STONE_BRICKS(Blocks.MOSSY_STONE_BRICKS.getDefaultState(), Blocks.STONE_BRICKS.getDefaultState(), false),
		BRICKS(Blocks.BRICKS.getDefaultState(), Blocks.BRICKS.getDefaultState(), false),
		NETHER_BRICKS(Blocks.CRACKED_NETHER_BRICKS.getDefaultState(), Blocks.NETHER_BRICKS.getDefaultState(), true),
		RED_NETHER_BRICKS(Blocks.RED_NETHER_BRICKS.getDefaultState(), Blocks.RED_NETHER_BRICKS.getDefaultState(), true);

		public final BlockState floor;
		public final BlockState wall;
		public final boolean nether;

		Style(BlockState floor, BlockState wall, boolean nether) {
			this.floor = floor;
			this.wall = wall;
			this.nether = nether;
		}
	}

	private static final List<EntityType<?>> NETHER_SPAWNERS = List.of(EntityType.WITHER_SKELETON, EntityType.BLAZE, EntityType.WITCH);
	/** The style of the dungeon being built on this thread, or null for vanilla's. */
	private static final ThreadLocal<Style> STYLE = new ThreadLocal<>();
	/** Set while building the second, extra overworld dungeon, so it doesn't get one of its own. */
	private static final ThreadLocal<Boolean> EXTRA = ThreadLocal.withInitial(() -> false);

	private Dungeons() {
	}

	@Nullable
	public static Style style() {
		return STYLE.get();
	}

	/** The style set by the caller (Nether dungeons), or a pick for an overworld dungeon in an Eternal Return world. */
	@Nullable
	public static Style begin(FeatureContext<DefaultFeatureConfig> context) {
		Style set = STYLE.get();
		if (set != null || !EternalReturnServer.active() || context.getWorld().toServerWorld().getRegistryKey() != World.OVERWORLD) {
			return set;
		}
		// From the spot, not the feature's random, so the dungeon itself comes out as before.
		Random pick = Random.create(context.getOrigin().asLong() ^ 0x5DEECE66DL);
		Style style = List.of(Style.COBBLESTONE, Style.STONE_BRICKS, Style.BRICKS).get(pick.nextInt(3));
		STYLE.set(style);
		return null;
	}

	public static void end(@Nullable Style previous) {
		if (previous == null) {
			STYLE.remove();
		} else {
			STYLE.set(previous);
		}
	}

	public static BlockState floor(BlockState vanilla) {
		Style style = STYLE.get();
		return style == null ? vanilla : style.floor;
	}

	public static BlockState wall(BlockState vanilla) {
		Style style = STYLE.get();
		return style == null ? vanilla : style.wall;
	}

	@Nullable
	public static final net.minecraft.registry.RegistryKey<net.minecraft.loot.LootTable> NETHER_LOOT = net.minecraft.registry.RegistryKey.of(
			net.minecraft.registry.RegistryKeys.LOOT_TABLE, com.eternalreturn.EternalReturn.id("chests/nether_dungeon"));

	/** The loot table of the dungeon being built: a Nether dungeon's own, else vanilla's. */
	public static net.minecraft.registry.RegistryKey<net.minecraft.loot.LootTable> chestLoot(net.minecraft.registry.RegistryKey<net.minecraft.loot.LootTable> vanilla) {
		Style style = STYLE.get();
		return style != null && style.nether ? NETHER_LOOT : vanilla;
	}

	public static EntityType<?> spawnerMob(Random random) {
		Style style = STYLE.get();
		return style != null && style.nether ? NETHER_SPAWNERS.get(random.nextInt(NETHER_SPAWNERS.size())) : null;
	}

	/** After an overworld dungeon try in an Eternal Return world: one more try elsewhere in the chunk. */
	public static void tryAgain(FeatureContext<DefaultFeatureConfig> context) {
		if (EXTRA.get() || !EternalReturnServer.active() || !EternalReturnConfig.get().worldgen.moreDungeons || STYLE.get() == null || STYLE.get().nether) {
			return;
		}
		Random random = context.getRandom();
		BlockPos origin = context.getOrigin();
		StructureWorldAccess world = context.getWorld();
		int x = (origin.getX() & ~15) + random.nextInt(16);
		int z = (origin.getZ() & ~15) + random.nextInt(16);
		// The same heights as the try it follows: vanilla's monster rooms go from y 0 up, its deep ones below 0.
		int y = origin.getY() >= 0 ? random.nextInt(Math.max(1, world.getTopY())) : world.getBottomY() + 6 + random.nextInt(-(world.getBottomY() + 6));
		EXTRA.set(true);
		Style keep = STYLE.get();
		try {
			STYLE.remove();
			Feature.MONSTER_ROOM.generate(new FeatureContext<>(Optional.empty(), world, context.getGenerator(), random, new BlockPos(x, y, z), DefaultFeatureConfig.INSTANCE));
		} finally {
			EXTRA.set(false);
			STYLE.set(keep);
		}
	}

	/** One dungeon try at a spot, in a given style (tests). */
	public static boolean build(StructureWorldAccess world, ChunkGenerator generator, Random random, BlockPos at, Style style) {
		STYLE.set(style);
		try {
			return Feature.MONSTER_ROOM.generate(new FeatureContext<>(Optional.empty(), world, generator, random, at, DefaultFeatureConfig.INSTANCE));
		} finally {
			STYLE.remove();
		}
	}

	public static List<EntityType<?>> netherSpawnerMobs() {
		return NETHER_SPAWNERS;
	}

	/** After a Nether chunk's own features, in an Eternal Return world: its dungeons and wild nether wart. */
	public static void netherChunk(ChunkGenerator generator, StructureWorldAccess world, Chunk chunk) {
		if (!EternalReturnServer.active() || world.toServerWorld().getRegistryKey() != World.NETHER) {
			return;
		}
		EternalReturnConfig.WorldgenTweaks cfg = EternalReturnConfig.get().worldgen;
		ChunkPos pos = chunk.getPos();
		ChunkRandom random = new ChunkRandom(new CheckedRandom(0L));
		long seed = random.setPopulationSeed(world.getSeed(), pos.getStartX(), pos.getStartZ());
		random.setDecoratorSeed(seed, 7231, 9);
		for (int i = 0; i < cfg.netherDungeonAttempts; i++) {
			BlockPos at = new BlockPos(pos.getStartX() + random.nextInt(16), 8 + random.nextInt(112), pos.getStartZ() + random.nextInt(16));
			STYLE.set(random.nextBoolean() ? Style.NETHER_BRICKS : Style.RED_NETHER_BRICKS);
			try {
				Feature.MONSTER_ROOM.generate(new FeatureContext<>(Optional.empty(), world, generator, random, at, DefaultFeatureConfig.INSTANCE));
			} finally {
				STYLE.remove();
			}
		}
		if (random.nextDouble() < cfg.netherWartPatchChance) {
			wartPatch(world, random, pos);
		}
	}

	/** Up to a dozen nether warts, any age, on the soul sand around one open soul sand block in the chunk. */
	private static void wartPatch(StructureWorldAccess world, Random random, ChunkPos pos) {
		for (int attempt = 0; attempt < 8; attempt++) {
			int x = pos.getStartX() + random.nextInt(16);
			int z = pos.getStartZ() + random.nextInt(16);
			for (int y = 120; y > 8; y--) {
				BlockPos ground = new BlockPos(x, y, z);
				if (world.getBlockState(ground).isOf(Blocks.SOUL_SAND) && world.isAir(ground.up())) {
					int placed = 0;
					for (int i = 0; i < 24 && placed < 12; i++) {
						BlockPos spot = ground.add(random.nextInt(7) - 3, random.nextInt(3) - 1, random.nextInt(7) - 3);
						if (world.getBlockState(spot).isOf(Blocks.SOUL_SAND) && world.isAir(spot.up())) {
							world.setBlockState(spot.up(), Blocks.NETHER_WART.getDefaultState()
									.with(net.minecraft.block.NetherWartBlock.AGE, random.nextInt(4)), 2);
							placed++;
						}
					}
					return;
				}
				if (world.getBlockState(ground).isIn(BlockTags.SOUL_FIRE_BASE_BLOCKS) && !world.getBlockState(ground).isOf(Blocks.SOUL_SAND)) {
					break;
				}
			}
		}
	}
}
