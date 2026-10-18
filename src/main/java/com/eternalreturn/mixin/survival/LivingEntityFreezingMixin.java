package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.Freezing;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freezing (the effect): frozen, it slows to half speed (vanilla's powder snow takes 0.05 off), and its freezing
 * hurts half as much again (1.5; blazes, magma cubes and striders take five times that, as vanilla does).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFreezingMixin extends Entity {
	@Shadow
	@Final
	private static Identifier POWDER_SNOW_SPEED_MODIFIER_ID;

	protected LivingEntityFreezingMixin(EntityType<?> type, World world) {
		super(type, world);
	}

	@Inject(method = "addPowderSnowSlowIfNeeded", at = @At("TAIL"))
	private void eternalreturn$halfSpeed(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		EntityAttributeInstance speed = self.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed == null || !Freezing.freezing(self) || this.getFrozenTicks() <= 0) {
			return;
		}
		speed.removeModifier(POWDER_SNOW_SPEED_MODIFIER_ID);
		speed.addTemporaryModifier(new EntityAttributeModifier(POWDER_SNOW_SPEED_MODIFIER_ID, -0.5 * this.getFreezingScale(),
				EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	@WrapOperation(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"))
	private boolean eternalreturn$freezingHurts(LivingEntity self, DamageSource source, float amount, Operation<Boolean> original) {
		return original.call(self, source, source.isOf(DamageTypes.FREEZE) && Freezing.freezing(self) ? amount * 1.5F : amount);
	}
}
