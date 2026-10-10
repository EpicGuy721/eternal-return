package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.BlindMobs;
import com.eternalreturn.survival.HerdPanic;
import com.eternalreturn.survival.SpawnerMobs;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Herd panic, blind mobs' follow range, and spawner mobs' drops (com.eternalreturn.survival). */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySurvivalMixin {
	@Inject(method = "damage", at = @At("RETURN"))
	private void eternalreturn$herdPanic(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			HerdPanic.attacked((LivingEntity) (Object) this, source);
		}
	}

	/** A blinded mob notices and follows targets only within a couple of blocks (BlindMobs). */
	@ModifyReturnValue(method = "getAttributeValue", at = @At("RETURN"))
	private double eternalreturn$blindFollowRange(double value, RegistryEntry<EntityAttribute> attribute) {
		if (attribute == EntityAttributes.GENERIC_FOLLOW_RANGE && (Object) this instanceof MobEntity && ((LivingEntity) (Object) this).hasStatusEffect(StatusEffects.BLINDNESS)) {
			return Math.min(value, BlindMobs.followRange());
		}
		return value;
	}

	@Inject(method = "dropLoot", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$noSpawnerLoot(DamageSource damageSource, boolean causedByPlayer, CallbackInfo ci) {
		if (SpawnerMobs.dropsNothing((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
