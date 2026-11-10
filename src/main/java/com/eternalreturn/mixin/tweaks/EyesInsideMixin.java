package com.eternalreturn.mixin.tweaks;

import com.eternalreturn.logic.EyesInside;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A crouching player's hitbox always holds their eyes (EyesInside), whatever another mod did to either. */
@Mixin(LivingEntity.class)
public abstract class EyesInsideMixin {
	@ModifyReturnValue(method = "getDimensions", at = @At("RETURN"))
	private EntityDimensions eternalreturn$eyesInside(EntityDimensions dimensions, EntityPose pose) {
		return (Object) this instanceof PlayerEntity && pose == EntityPose.CROUCHING ? EyesInside.fit(dimensions) : dimensions;
	}
}
