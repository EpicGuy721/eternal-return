package com.eternalreturn.mixin.tweaks;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A drowned throws the trident in its hand (not an endless supply of fresh ones), and it flies back to it as a Loyalty
 * trident does (TridentReturnMixin); till then it has none to throw. If the drowned dies meanwhile, the trident is lost.
 */
@Mixin(DrownedEntity.class)
public abstract class DrownedLoyalTridentMixin {
	/** How strongly a drowned's trident comes back (Loyalty III). */
	@Unique
	private static final byte LOYALTY = 3;

	@Inject(method = "shootAt", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$throwItsOwn(LivingEntity target, float pullProgress, CallbackInfo ci) {
		DrownedEntity drowned = (DrownedEntity) (Object) this;
		ItemStack held = drowned.getMainHandStack();
		if (!held.isOf(Items.TRIDENT)) {
			ci.cancel();
			return;
		}
		TridentEntity trident = new TridentEntity(drowned.getWorld(), drowned, held.copyWithCount(1));
		trident.getDataTracker().set(TridentEntityAccessor.eternalreturn$loyalty(), LOYALTY);
		double dx = target.getX() - drowned.getX();
		double dy = target.getBodyY(0.3333333333333333) - trident.getY();
		double dz = target.getZ() - drowned.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		trident.setVelocity(dx, dy + flat * 0.2F, dz, 1.6F, 14 - drowned.getWorld().getDifficulty().getId() * 4);
		drowned.playSound(SoundEvents.ENTITY_DROWNED_SHOOT, 1.0F, 1.0F / (drowned.getRandom().nextFloat() * 0.4F + 0.8F));
		drowned.getWorld().spawnEntity(trident);
		drowned.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		ci.cancel();
	}
}
