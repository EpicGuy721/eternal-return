package com.eternalreturn.survival;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.mixin.survival.LivingEntityAccessor;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

/**
 * Herd panic (survival.herdPanic): when an animal in the entity tag eternalreturn:herd_animals (farm
 * animals that flee when hurt) is hurt by something, every animal of the same kind within 16 blocks
 * flees too, as if hurt by the same attacker.
 */
public final class HerdPanic {
	public static final TagKey<EntityType<?>> HERD_ANIMALS = TagKey.of(RegistryKeys.ENTITY_TYPE, EternalReturn.id("herd_animals"));

	private HerdPanic() {
	}

	public static void attacked(LivingEntity victim, DamageSource source) {
		EternalReturnConfig.SurvivalTweaks cfg = EternalReturnConfig.get().survival;
		if (!cfg.herdPanic || victim.getWorld().isClient || !(victim instanceof AnimalEntity) || !victim.getType().isIn(HERD_ANIMALS)
				|| !(source.getAttacker() instanceof LivingEntity attacker)) {
			return;
		}
		for (AnimalEntity other : victim.getWorld().getEntitiesByClass(AnimalEntity.class, victim.getBoundingBox().expand(cfg.herdPanicRadius),
				animal -> animal != victim && animal.getType() == victim.getType() && animal.isAlive())) {
			// Animals flee for two seconds after damage that causes panic (EscapeDangerGoal): give each the same
			// damage, without the hurt, and the same attacker.
			((LivingEntityAccessor) other).eternalreturn$setLastDamageSource(source);
			((LivingEntityAccessor) other).eternalreturn$setLastDamageTime(other.getWorld().getTime());
			other.setAttacker(attacker);
		}
	}
}
