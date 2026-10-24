package com.eternalreturn.mixin.tweaks;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.projectile.TridentEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A drowned's returning trident (DrownedLoyalTridentMixin), once back at its owner, goes into its empty hand. */
@Mixin(TridentEntity.class)
public abstract class TridentReturnMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void eternalreturn$backInHand(CallbackInfo ci) {
		TridentEntity trident = (TridentEntity) (Object) this;
		if (trident.getWorld().isClient || trident.isRemoved() || !(trident.getOwner() instanceof DrownedEntity drowned) || !drowned.isAlive()
				|| trident.getDataTracker().get(TridentEntityAccessor.eternalreturn$loyalty()) <= 0) {
			return;
		}
		if (trident.squaredDistanceTo(drowned.getEyePos()) < 2.25 && drowned.getMainHandStack().isEmpty()) {
			drowned.equipStack(EquipmentSlot.MAINHAND, trident.getItemStack().copy());
			trident.discard();
		}
	}
}
