package com.eternalreturn.mixin;

import com.eternalreturn.blocks.StoneDripstoneBlock;
import net.minecraft.block.AbstractCauldronBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.PointedDripstoneBlock;
import net.minecraft.fluid.Fluid;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A cauldron fills from a drip a step after it falls (a scheduled tick), by looking up for the dripstone over it. Vanilla
 * only finds pointed dripstone; when there is none, a stone dripstone stalactite over it fills it the same way.
 */
@Mixin(AbstractCauldronBlock.class)
public abstract class CauldronStoneDripMixin {
	@Inject(method = "scheduledTick", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$stoneDrip(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
		if (PointedDripstoneBlock.getDripPos(world, pos) != null) {
			return;
		}
		Fluid fluid = StoneDripstoneBlock.dripInto(world, pos);
		CauldronDripInvoker self = (CauldronDripInvoker) this;
		if (fluid != null && self.eternalreturn$canFill(fluid)) {
			self.eternalreturn$fill(state, world, pos, fluid);
			ci.cancel();
		}
	}
}
