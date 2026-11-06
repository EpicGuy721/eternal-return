package com.eternalreturn.mixin;

import net.minecraft.block.AbstractCauldronBlock;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluid;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A cauldron's own dripstone checks, for stone dripstone (StoneDripstoneBlock). */
@Mixin(AbstractCauldronBlock.class)
public interface CauldronDripInvoker {
	@Invoker("canBeFilledByDripstone")
	boolean eternalreturn$canFill(Fluid fluid);

	@Invoker("fillFromDripstone")
	void eternalreturn$fill(BlockState state, World world, BlockPos pos, Fluid fluid);
}
