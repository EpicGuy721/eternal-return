package com.eternalreturn.mixin.mobs;

import com.eternalreturn.mobs.ai.Lounging;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A lounging monster gets up when it's hurt (Lounging). */
@Mixin(LivingEntity.class)
public abstract class LoungeWakeMixin {
	@Inject(method = "damage", at = @At("HEAD"))
	private void eternalreturn$wakeOnHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof MobEntity mob && !mob.getWorld().isClient && Lounging.isLounging(mob)) {
			Lounging.stand(mob);
		}
	}
}
