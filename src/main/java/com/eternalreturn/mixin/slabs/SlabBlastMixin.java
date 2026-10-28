package com.eternalreturn.mixin.slabs;

import com.eternalreturn.blocks.MixedSlabs;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.explosion.ExplosionBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A mixed slab (MixedSlabs) withstands a blast as its stronger half. */
@Mixin(ExplosionBehavior.class)
public abstract class SlabBlastMixin {
	@WrapOperation(method = "getBlastResistance", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getBlastResistance()F"))
	private float eternalreturn$strongerHalf(Block block, Operation<Float> original, @Local(argsOnly = true) BlockView world,
			@Local(argsOnly = true) BlockPos pos, @Local(argsOnly = true) BlockState state) {
		if (state.isOf(MixedSlabs.MIXED_SLAB)) {
			MixedSlabs.Halves halves = MixedSlabs.halves(world, pos);
			if (halves != null) {
				return Math.max(halves.first().getBlock().getBlastResistance(), halves.second().getBlock().getBlastResistance());
			}
		}
		return original.call(block);
	}
}
