package com.eternalreturn.survival;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.logic.NewEnchantments;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CocoaBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.Biome;

/**
 * Farming:
 * <ul>
 * <li>rain makes crops, sugar cane, bamboo, cocoa beans and sweet berry bushes grow faster: out in the rain they get
 * survival.rainGrowthMultiplier (2) times the growth (cocoa, under its tree's leaves, only needs it to be raining in
 * its biome) (RainGrowthMixin);</li>
 * <li>bone meal works on sugar cane and cactus (a block taller, up to 3) and on nether wart (a stage older)
 * (BoneMealMixins);</li>
 * <li>Feather Falling boots keep their wearer from trampling farmland (FarmlandBlockMixin);</li>
 * <li>a sapling (or propagule) lying on the ground plants itself where it lies, if it can grow there, some time
 * after its first minute and before it would despawn (ItemEntitySaplingMixin).</li>
 * </ul>
 */
public final class Growing {
	private static final ThreadLocal<Boolean> EXTRA = ThreadLocal.withInitial(() -> false);
	private static final Identifier FEATHER_FALLING = Identifier.ofVanilla("feather_falling");

	private Growing() {
	}

	/** After a growing block's random tick: in the rain, maybe another. */
	public static void rainTick(BlockState before, ServerWorld world, BlockPos pos, Random random) {
		if (EXTRA.get() || !world.isRaining()) {
			return;
		}
		boolean wet = before.getBlock() instanceof CocoaBlock
				? world.getBiome(pos).value().getPrecipitation(pos) == Biome.Precipitation.RAIN
				: world.hasRain(pos) || world.hasRain(pos.up());
		double extra = EternalReturnConfig.get().survival.rainGrowthMultiplier - 1.0;
		if (!wet || extra <= 0.0) {
			return;
		}
		int ticks = (int) extra + (random.nextDouble() < extra - (int) extra ? 1 : 0);
		EXTRA.set(true);
		try {
			for (int i = 0; i < ticks; i++) {
				BlockState now = world.getBlockState(pos);
				if (now.hasRandomTicks()) {
					now.randomTick(world, pos, random);
				}
			}
		} finally {
			EXTRA.set(false);
		}
	}

	public static boolean keepsFarmland(Entity entity) {
		return entity instanceof LivingEntity living && NewEnchantments.level(living.getEquippedStack(EquipmentSlot.FEET), FEATHER_FALLING) > 0;
	}

	/** Each second for a sapling lying on the ground: maybe it plants itself. */
	public static void saplingTick(ItemEntity item) {
		ItemStack stack = item.getStack();
		if (!stack.isIn(ItemTags.SAPLINGS) || !(stack.getItem() instanceof BlockItem blockItem) || !item.isOnGround()
				|| !EternalReturnConfig.get().survival.saplingsPlantThemselves || !(item.getWorld() instanceof ServerWorld world)) {
			return;
		}
		int age = item.getItemAge();
		if (age < 1200 || (age < 5600 && world.random.nextInt(30) != 0)) {
			return;
		}
		BlockPos pos = item.getBlockPos();
		BlockState sapling = blockItem.getBlock().getDefaultState();
		BlockState here = world.getBlockState(pos);
		if ((here.isAir() || here.isOf(Blocks.SHORT_GRASS) || here.isReplaceable() && here.getFluidState().isEmpty()) && sapling.canPlaceAt(world, pos)) {
			world.setBlockState(pos, sapling);
			stack.decrement(1);
			if (stack.isEmpty()) {
				item.discard();
			} else {
				item.setStack(stack);
			}
		}
	}
}
