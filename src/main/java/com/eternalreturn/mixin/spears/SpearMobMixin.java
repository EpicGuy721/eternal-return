package com.eternalreturn.mixin.spears;

import com.eternalreturn.items.Spears;
import com.eternalreturn.logic.SpearCombat;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mobs with spears (Spears): they jab from further off (half a player's reach), and the chargers among them
 * (SpearCombat.CHARGERS) now and then lower the spear and run at their target, from 4 to 16 blocks off, until the
 * charge tires out; then a rest of 5 to 8 seconds.
 */
@Mixin(MobEntity.class)
public abstract class SpearMobMixin extends LivingEntity {
	/** Ticks since the spear was lowered, or -1. */
	@Unique
	private int eternalreturn$charge = -1;
	@Unique
	private int eternalreturn$chargeRest = 60;

	protected SpearMobMixin(EntityType<? extends LivingEntity> type, World world) {
		super(type, world);
	}

	/** Vanilla's reach is about 0.83 blocks past the mob's side; a spear's about 2.25 from its middle. */
	@ModifyReturnValue(method = "getAttackBox", at = @At("RETURN"))
	private Box eternalreturn$spearReach(Box box) {
		return SpearCombat.armed(this) ? box.expand(1.1, 0.0, 1.1) : box;
	}

	@Inject(method = "tickNewAi", at = @At("TAIL"))
	private void eternalreturn$spearCharge(CallbackInfo ci) {
		MobEntity mob = (MobEntity) (Object) this;
		ItemStack stack = mob.getMainHandStack();
		if (this.eternalreturn$charge < 0 && !(stack.getItem() instanceof Spears.Spear)) {
			return;
		}
		// Goal-driven mobs keep their target themselves, brain-driven ones (piglins) in their brain.
		LivingEntity target = mob.getTarget();
		if (target == null && mob.getBrain().hasMemoryModule(MemoryModuleType.ATTACK_TARGET)) {
			target = mob.getBrain().getOptionalRegisteredMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
		}
		boolean able = stack.getItem() instanceof Spears.Spear && mob.getType().isIn(SpearCombat.CHARGERS) && target != null && target.isAlive();
		if (this.eternalreturn$charge >= 0) {
			Spears.Tier tier = able ? ((Spears.Spear) stack.getItem()).tier() : null;
			if (tier == null || this.eternalreturn$charge >= tier.delay() + tier.damageTicks()) {
				this.eternalreturn$charge = -1;
				this.eternalreturn$chargeRest = 100 + this.random.nextInt(60);
				SpearCombat.endMobCharge(mob);
				return;
			}
			SpearCombat.charge(mob, stack, this.eternalreturn$charge++);
		} else if (this.eternalreturn$chargeRest > 0) {
			this.eternalreturn$chargeRest--;
		} else if (able) {
			double distance = mob.squaredDistanceTo(target);
			if (distance > 16.0 && distance < 256.0 && mob.canSee(target)) {
				this.eternalreturn$charge = 0;
				SpearCombat.startMobCharge(mob);
			}
		}
	}
}
