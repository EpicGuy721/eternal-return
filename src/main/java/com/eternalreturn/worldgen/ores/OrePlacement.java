package com.eternalreturn.worldgen.ores;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.worldgen.caves.CaveWorlds;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Ore features in Eternal Return worlds with biome stone on (OreFeatureMixin, ScatteredOreFeatureMixin):
 * every ore feature, vanilla's, Moderner Beta's or another mod's, places its ore into a host stone as that
 * host's variant (iron ore into granite becomes granite iron ore), from the table in OreVariants. So that
 * ores reach the host stones at all, an ore feature's target test sees a host stone as plain stone; the
 * vanilla ore tags stay as they are, so other worlds are untouched. Granite, diorite and andesite blobs in
 * any biome are host stones too. Lava lakes keep host stone where they would line themselves with plain
 * stone. With biome stone off, Eternal Return generates its ores and lakes as before.
 */
public final class OrePlacement {
	private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

	private OrePlacement() {
	}

	/** An ore feature (or a lake) starts: is this an Eternal Return world with biome stone on? */
	public static void enter(FeatureContext<?> context) {
		ACTIVE.set(EternalReturnConfig.get().worldgen.biomeStone && context.getGenerator() instanceof NoiseChunkGenerator generator
				&& CaveWorlds.usesEternalReturnSettings(generator, context.getWorld().getRegistryManager()));
	}

	public static void exit() {
		ACTIVE.set(false);
	}

	/** The block an ore feature's target test should see: stone in place of a host stone. */
	public static BlockState asTarget(BlockState state) {
		return ACTIVE.get() && OreVariants.isHost(state) ? Blocks.STONE.getDefaultState() : state;
	}

	/**
	 * Lakes: vanilla's lake feature lines a lava lake with a shell of stone where it touches solid ground.
	 * In host stone, the host stays instead (it is just as solid), so lakes leave no plain stone patches
	 * there and the ores placed after them find host stone (LakeFeatureMixin).
	 */
	public static boolean keepsHost(BlockState placed, BlockState replaced) {
		return ACTIVE.get() && placed.isOf(Blocks.STONE) && OreVariants.isHost(replaced);
	}

	/** The block an ore feature should place where it found the replaced block. */
	public static BlockState place(BlockState ore, BlockState replaced) {
		return ACTIVE.get() ? OreVariants.variantFor(ore, replaced) : ore;
	}
}
