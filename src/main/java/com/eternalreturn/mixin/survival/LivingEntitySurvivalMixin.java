package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.BlindMobs;
import com.eternalreturn.survival.Food;
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
			Food.interrupt((LivingEntity) (Object) this);
		}
	}

	/** The client hears of damage here: stop eating on its side too, so the two agree (Food). */
	@Inject(method = "onDamaged", at = @At("TAIL"))
	private void eternalreturn$interruptClient(DamageSource source, CallbackInfo ci) {
		Food.interrupt((LivingEntity) (Object) this);
	}

	@Inject(method = "eatFood", at = @At("HEAD"))
	private void eternalreturn$rawFood(net.minecraft.world.World world, net.minecraft.item.ItemStack stack, net.minecraft.component.type.FoodComponent food,
			CallbackInfoReturnable<net.minecraft.item.ItemStack> cir) {
		Food.ate((LivingEntity) (Object) this, stack);
		// No hunger: the food heals instead (Meals).
		if ((Object) this instanceof net.minecraft.entity.player.PlayerEntity player && com.eternalreturn.survival.Meals.enabled()) {
			com.eternalreturn.survival.Meals.ate(player, stack);
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

	/** A beehive or TNT on a zombie's or skeleton's head (HeadGear). */
	@Inject(method = "onDeath", at = @At("HEAD"))
	private void eternalreturn$headGear(DamageSource source, CallbackInfo ci) {
		com.eternalreturn.mobs.HeadGear.died((LivingEntity) (Object) this, source);
	}

	/** A glowing creature can be seen through blocks (survival.glowingSeenThroughWalls), within 128 blocks. */
	@Inject(method = "canSee", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$glowingSeen(net.minecraft.entity.Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (com.eternalreturn.config.EternalReturnConfig.get().survival.glowingSeenThroughWalls && entity instanceof LivingEntity living
				&& living.hasStatusEffect(StatusEffects.GLOWING) && living.getWorld() == ((LivingEntity) (Object) this).getWorld()
				&& living.squaredDistanceTo((LivingEntity) (Object) this) < 128.0 * 128.0) {
			cir.setReturnValue(true);
		}
	}

	/** Undead servants (Servants) leave no experience either. */
	@Inject(method = "dropXp", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$noServantXp(net.minecraft.entity.Entity attacker, CallbackInfo ci) {
		if (com.eternalreturn.mobs.Servants.isServant((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "dropLoot", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$noSpawnerLoot(DamageSource damageSource, boolean causedByPlayer, CallbackInfo ci) {
		if (SpawnerMobs.dropsNothing((LivingEntity) (Object) this) || com.eternalreturn.mobs.Servants.isServant((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
