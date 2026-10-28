package com.eternalreturn.mixin.slabs;

import com.eternalreturn.blocks.MixedSlabs;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Footsteps on a mixed slab (MixedSlabs) sound, and running kicks up dust, of the half underfoot. */
@Mixin(Entity.class)
public abstract class SlabStepMixin {
	@ModifyVariable(method = "playStepSounds", at = @At("HEAD"), argsOnly = true)
	private BlockState eternalreturn$halfUnderfoot(BlockState state, @Local(argsOnly = true) BlockPos pos) {
		return MixedSlabs.underfoot((Entity) (Object) this, pos, state);
	}

	@WrapOperation(method = "spawnSprintingParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
	private BlockState eternalreturn$dustOfTheHalf(World world, BlockPos pos, Operation<BlockState> original) {
		return MixedSlabs.underfoot((Entity) (Object) this, pos, original.call(world, pos));
	}
}
