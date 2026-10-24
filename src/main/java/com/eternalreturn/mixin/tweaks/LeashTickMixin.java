package com.eternalreturn.mixin.tweaks;

import com.eternalreturn.logic.Leads;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Leashable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A lead (Leads) stretches to 12 blocks before it snaps, with a sound: between vanilla's 10 and 12 it pulls as
 * vanilla's does from 6 to 10.
 */
@Mixin(Entity.class)
public abstract class LeashTickMixin {
	@WrapOperation(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Leashable;tickLeash(Lnet/minecraft/entity/Entity;)V"))
	private void eternalreturn$longerLead(Entity entity, Operation<Void> original) {
		if (entity instanceof Leashable leashable && !entity.getWorld().isClient && entity.isAlive()) {
			Entity holder = leashable.getLeashHolder();
			if (holder != null && holder.isAlive() && holder.getWorld() == entity.getWorld()) {
				float distance = entity.distanceTo(holder);
				if (distance > Leads.SNAP) {
					Leads.snap(entity, leashable);
					return;
				}
				if (distance > Leads.VANILLA_SNAP) {
					if (leashable.beforeLeashTick(holder, distance)) {
						leashable.applyLeashElasticity(holder, distance);
						entity.limitFallDistance();
					}
					return;
				}
			}
		}
		original.call(entity);
	}
}
