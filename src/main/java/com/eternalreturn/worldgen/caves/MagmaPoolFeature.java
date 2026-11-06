package com.eternalreturn.worldgen.caves;

import com.eternalreturn.EternalReturn;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * A lava pool sunk into a cave floor (the magma caves): a round pool of radius 2 to 4, one block deep (two in the middle
 * of the bigger ones), rimmed with magma blocks. It is only made where the whole of it sits in solid floor, so the lava
 * never runs out of it.
 */
public class MagmaPoolFeature extends Feature<DefaultFeatureConfig> {
	public static final Feature<DefaultFeatureConfig> FEATURE = Registry.register(Registries.FEATURE, EternalReturn.id("magma_pool"), new MagmaPoolFeature());

	public MagmaPoolFeature() {
		super(DefaultFeatureConfig.CODEC);
	}

	public static void register() {
		// Loads the feature.
	}

	private static boolean solid(StructureWorldAccess world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return state.isSolidBlock(world, pos) && state.getFluidState().isEmpty();
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		BlockPos origin = context.getOrigin();
		Random random = context.getRandom();
		int r = 2 + random.nextInt(3);
		if (!world.getBlockState(origin).isAir()) {
			return false;
		}
		// Everything the pool and its rim take, and the block under each, must be solid floor.
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r * r) {
					continue;
				}
				BlockPos floor = origin.add(dx, -1, dz);
				if (!solid(world, floor) || !solid(world, floor.down()) || !solid(world, floor.down(2))) {
					return false;
				}
			}
		}
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int d = dx * dx + dz * dz;
				if (d > r * r) {
					continue;
				}
				BlockPos floor = origin.add(dx, -1, dz);
				if (d > (r - 1) * (r - 1)) {
					this.setBlockState(world, floor, Blocks.MAGMA_BLOCK.getDefaultState());
					continue;
				}
				this.setBlockState(world, floor, Blocks.LAVA.getDefaultState());
				if (r >= 3 && d <= (r - 2) * (r - 2)) {
					this.setBlockState(world, floor.down(), Blocks.LAVA.getDefaultState());
				}
				// Lava under a wall would leak sideways along the cave: wall it in with magma.
				for (Direction side : Direction.Type.HORIZONTAL) {
					BlockPos next = floor.offset(side);
					if (!world.getBlockState(next).isOf(Blocks.LAVA) && !solid(world, next)) {
						this.setBlockState(world, next, Blocks.MAGMA_BLOCK.getDefaultState());
					}
				}
			}
		}
		return true;
	}
}
