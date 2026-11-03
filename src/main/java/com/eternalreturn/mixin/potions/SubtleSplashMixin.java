package com.eternalreturn.mixin.potions;

import com.eternalreturn.items.SubtlePotions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A subtle splash or lingering potion breaks without the coloured burst (SubtlePotions). */
@Mixin(PotionEntity.class)
public abstract class SubtleSplashMixin {
	@WrapOperation(method = "onCollision", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;syncWorldEvent(ILnet/minecraft/util/math/BlockPos;I)V"))
	private void eternalreturn$noBurst(World world, int event, BlockPos pos, int data, Operation<Void> original) {
		if (!SubtlePotions.isSubtle(((PotionEntity) (Object) this).getStack())) {
			original.call(world, event, pos, data);
		}
	}
}
