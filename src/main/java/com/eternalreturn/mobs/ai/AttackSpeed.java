package com.eternalreturn.mobs.ai;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Haste and Mining Fatigue change how fast mobs attack in melee, as they change a player's attack speed: 10 percent
 * faster per level of Haste, 10 percent slower per level of Mining Fatigue (MeleeAttackGoalMixin for most mobs,
 * MobAttackMixin for piglins, brutes, hoglins and zoglins).
 */
public final class AttackSpeed {
	private AttackSpeed() {
	}

	/** How fast it attacks: 1 normally. */
	public static double factor(LivingEntity mob) {
		double factor = 1.0;
		StatusEffectInstance haste = mob.getStatusEffect(StatusEffects.HASTE);
		if (haste != null) {
			factor += 0.1 * (haste.getAmplifier() + 1);
		}
		StatusEffectInstance fatigue = mob.getStatusEffect(StatusEffects.MINING_FATIGUE);
		if (fatigue != null) {
			factor -= 0.1 * (fatigue.getAmplifier() + 1);
		}
		return Math.max(0.1, factor);
	}

	/** A cooldown between attacks (ticks), scaled to its attack speed. */
	public static long scale(LivingEntity mob, long ticks) {
		double factor = factor(mob);
		return factor == 1.0 ? ticks : Math.max(1L, Math.round(ticks / factor));
	}
}
