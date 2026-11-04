package com.eternalreturn.blocks;

import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * The ice crystal (backported from Java 26.4's, as minecraft.wiki describes it): a cross of ice crystals on any full face
 * of a solid block, in any of the six directions (as an amethyst cluster), half a block high, waterloggable, giving light
 * level 4, mined with a pickaxe. Now and then it chimes when packed ice lies under it (the new sound isn't in 1.21.1:
 * the amethyst chime, pitched up).
 */
public class IceCrystalBlock extends AmethystClusterBlock {
	public IceCrystalBlock(Settings settings) {
		super(8.0F, 3.0F, settings);
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (random.nextInt(40) == 0 && world.getBlockState(pos.down()).isOf(Blocks.PACKED_ICE)) {
			world.playSoundAtBlockCenter(pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.6F, 1.2F + random.nextFloat() * 0.6F, false);
		}
	}
}
