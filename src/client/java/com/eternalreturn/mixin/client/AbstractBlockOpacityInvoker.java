package com.eternalreturn.mixin.client;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A block's own light blocking for a state (OldLightingOpacityMixin). */
@Mixin(AbstractBlock.class)
public interface AbstractBlockOpacityInvoker {
	@Invoker("getOpacity")
	int eternalreturn$opacity(BlockState state, BlockView world, BlockPos pos);
}
