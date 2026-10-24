package com.eternalreturn.mixin.tweaks;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A scared (hurt) squid's ink cloud blinds everything around it but squids for INK_TICKS, and its attacker if close. */
@Mixin(SquidEntity.class)
public abstract class SquidInkMixin {
	@Unique
	private static final int INK_TICKS = 60;
	@Unique
	private static final double CLOUD = 3.0;
	@Unique
	private static final double ATTACKER_REACH = 6.0;

	@Inject(method = "damage", at = @At("RETURN"))
	private void eternalreturn$inkCloud(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		SquidEntity squid = (SquidEntity) (Object) this;
		if (!cir.getReturnValueZ() || squid.getWorld().isClient || !squid.isAlive()) {
			return;
		}
		for (LivingEntity near : squid.getWorld().getEntitiesByClass(LivingEntity.class, new Box(squid.getBlockPos()).expand(CLOUD),
				entity -> !(entity instanceof SquidEntity) && entity.isAlive())) {
			near.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, INK_TICKS), squid);
		}
		if (source.getAttacker() instanceof LivingEntity attacker && attacker.squaredDistanceTo(squid) <= ATTACKER_REACH * ATTACKER_REACH) {
			attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, INK_TICKS), squid);
		}
	}
}
