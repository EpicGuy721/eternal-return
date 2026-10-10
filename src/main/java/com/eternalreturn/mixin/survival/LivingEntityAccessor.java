package com.eternalreturn.mixin.survival;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Animals panic from recent damage (EscapeDangerGoal), so herd panic hands the same damage to the herd (HerdPanic). */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("lastDamageSource")
	void eternalreturn$setLastDamageSource(DamageSource source);

	@Accessor("lastDamageTime")
	void eternalreturn$setLastDamageTime(long time);
}
