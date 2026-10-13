package com.eternalreturn.mixin.mobs;

import com.eternalreturn.mobs.PillagerBoats;
import net.minecraft.entity.vehicle.BoatEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pillager boats (PillagerBoats): steered by their pillagers, and gone once they have been empty a while. */
@Mixin(BoatEntity.class)
public abstract class BoatEntityPillagerMixin {
	@Unique
	private int eternalreturn$emptyTicks;

	@Shadow
	protected abstract void updatePaddles();

	@Inject(method = "tick", at = @At("HEAD"))
	private void eternalreturn$pillagersSteer(CallbackInfo ci) {
		BoatEntity self = (BoatEntity) (Object) this;
		if (self.getWorld().isClient || !PillagerBoats.isPillagerBoat(self)) {
			return;
		}
		if (self.hasPassengers()) {
			this.eternalreturn$emptyTicks = 0;
			PillagerBoats.steer(self);
		} else if (++this.eternalreturn$emptyTicks > 100) {
			self.discard();
		}
	}

	/** Vanilla only works the paddles on the client of the player steering; a pillager steers on the server. */
	@Inject(method = "tick", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/entity/vehicle/BoatEntity;updateVelocity()V"))
	private void eternalreturn$pillagersPaddle(CallbackInfo ci) {
		BoatEntity self = (BoatEntity) (Object) this;
		if (!self.getWorld().isClient && PillagerBoats.isPillagerBoat(self) && self.hasPassengers()) {
			this.updatePaddles();
		}
	}
}
