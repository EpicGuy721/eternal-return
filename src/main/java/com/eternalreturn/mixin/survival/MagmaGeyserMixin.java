package com.eternalreturn.mixin.survival;

import com.eternalreturn.blocks.Geysers;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MagmaBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;

/** A magma block gets random ticks, in which, with lava under it and water over it, it may erupt as a geyser (Geysers). */
@Mixin(MagmaBlock.class)
public abstract class MagmaGeyserMixin extends Block {
	protected MagmaGeyserMixin(Settings settings) {
		super(settings);
	}

	@Override
	protected boolean hasRandomTicks(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		Geysers.randomTick(state, world, pos, random);
	}
}
