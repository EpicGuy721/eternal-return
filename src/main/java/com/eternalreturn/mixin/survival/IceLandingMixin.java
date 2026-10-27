package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.IceCracking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Landing on ice cracks it (IceCracking). */
@Mixin(Block.class)
public abstract class IceLandingMixin {
	@Inject(method = "onLandedUpon", at = @At("HEAD"))
	private void eternalreturn$crackIce(World world, BlockState state, BlockPos pos, Entity entity, float fallDistance, CallbackInfo ci) {
		IceCracking.land(world, pos, entity, fallDistance);
	}
}
