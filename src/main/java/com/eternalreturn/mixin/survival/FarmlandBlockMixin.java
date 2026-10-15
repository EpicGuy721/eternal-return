package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.Growing;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Feather Falling boots don't trample farmland (Growing). */
@Mixin(FarmlandBlock.class)
public abstract class FarmlandBlockMixin {
	@WrapOperation(method = "onLandedUpon", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/block/FarmlandBlock;setToDirt(Lnet/minecraft/entity/Entity;Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V"))
	private void eternalreturn$softBoots(Entity entity, BlockState state, World world, BlockPos pos, Operation<Void> original) {
		if (!Growing.keepsFarmland(entity)) {
			original.call(entity, state, world, pos);
		}
	}
}
