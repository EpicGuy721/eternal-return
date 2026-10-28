package com.eternalreturn.mixin.slabs;

import com.eternalreturn.blocks.MixedSlabs;
import com.eternalreturn.blocks.Slabs;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A mixed slab (MixedSlabs) taken whole is as hard as its harder half (players mine one half at a time), and shows on
 * maps as its top half (on edge, its north or west half).
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class MixedSlabStateMixin {
	@Shadow
	protected abstract BlockState asBlockState();

	@Inject(method = "getHardness", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$harderHalf(BlockView world, BlockPos pos, CallbackInfoReturnable<Float> cir) {
		BlockState state = this.asBlockState();
		if (state.isOf(MixedSlabs.MIXED_SLAB)) {
			BlockState negative = MixedSlabs.half(state, MixedSlabs.halves(world, pos), false);
			BlockState positive = MixedSlabs.half(state, MixedSlabs.halves(world, pos), true);
			if (negative != null) {
				cir.setReturnValue(Math.max(negative.getHardness(world, pos), positive.getHardness(world, pos)));
			}
		}
	}

	@Inject(method = "getMapColor", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$topHalfColor(BlockView world, BlockPos pos, CallbackInfoReturnable<MapColor> cir) {
		BlockState state = this.asBlockState();
		if (state.isOf(MixedSlabs.MIXED_SLAB)) {
			BlockState seen = MixedSlabs.half(state, MixedSlabs.halves(world, pos), state.get(Slabs.AXIS) == net.minecraft.util.math.Direction.Axis.Y);
			if (seen != null) {
				cir.setReturnValue(seen.getMapColor(world, pos));
			}
		}
	}
}
