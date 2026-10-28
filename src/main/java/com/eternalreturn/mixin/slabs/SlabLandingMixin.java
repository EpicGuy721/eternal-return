package com.eternalreturn.mixin.slabs;

import com.eternalreturn.blocks.MixedSlabs;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Landing on a mixed slab (MixedSlabs) sounds, and raises dust, of the half landed on. */
@Mixin(LivingEntity.class)
public abstract class SlabLandingMixin {
	@ModifyVariable(method = "fall", at = @At("HEAD"), argsOnly = true)
	private BlockState eternalreturn$halfLandedOn(BlockState state, @Local(argsOnly = true) BlockPos landedPosition) {
		return MixedSlabs.underfoot((Entity) (Object) this, landedPosition, state);
	}

	@WrapOperation(method = "playBlockFallSound", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
	private BlockState eternalreturn$fallSoundOfTheHalf(World world, BlockPos pos, Operation<BlockState> original) {
		return MixedSlabs.underfoot((Entity) (Object) this, pos, original.call(world, pos));
	}
}
