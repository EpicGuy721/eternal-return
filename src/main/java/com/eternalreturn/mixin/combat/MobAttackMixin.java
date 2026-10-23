package com.eternalreturn.mixin.combat;

import com.eternalreturn.logic.NewEnchantments;
import net.minecraft.entity.Entity;
import com.eternalreturn.mixin.mobs.BrainAccessor;
import com.eternalreturn.mobs.ai.AttackSpeed;
import net.minecraft.entity.ai.brain.Memory;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Launch (NewEnchantments) on a mob's hit. And for mobs run by a brain (piglins, brutes, hoglins, zoglins), Haste
 * and Mining Fatigue: the attack cooldown its brain set on swinging is rescaled at the end of that tick (AttackSpeed;
 * the other mobs' is in MeleeAttackGoalMixin).
 */
@Mixin(MobEntity.class)
public abstract class MobAttackMixin {
	@Unique
	private int eternalreturn$attackedAt = -1;

	@Inject(method = "tryAttack", at = @At("HEAD"))
	private void eternalreturn$swung(Entity target, CallbackInfoReturnable<Boolean> cir) {
		this.eternalreturn$attackedAt = ((MobEntity) (Object) this).age;
	}

	@Inject(method = "tryAttack", at = @At("RETURN"))
	private void eternalreturn$launch(Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			NewEnchantments.launch((MobEntity) (Object) this, target);
		}
	}

	@Inject(method = "tickNewAi", at = @At("TAIL"))
	private void eternalreturn$brainAttackSpeed(CallbackInfo ci) {
		MobEntity mob = (MobEntity) (Object) this;
		if (this.eternalreturn$attackedAt != mob.age || AttackSpeed.factor(mob) == 1.0) {
			return;
		}
		Optional<? extends Memory<?>> cooling = ((BrainAccessor) mob.getBrain()).eternalreturn$memories().get(MemoryModuleType.ATTACK_COOLING_DOWN);
		if (cooling != null && cooling.isPresent() && cooling.get().isTimed()) {
			mob.getBrain().remember(MemoryModuleType.ATTACK_COOLING_DOWN, true, AttackSpeed.scale(mob, cooling.get().getExpiry()));
		}
	}
}
