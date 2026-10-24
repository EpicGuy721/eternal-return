package com.eternalreturn.mixin.tweaks;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A drowned with a trident in hand leaves the fighting to its trident throws, as in vanilla (where the trident never
 * leaves its hand): once its thrown trident comes back (DrownedLoyalTridentMixin), the hand-to-hand attack it took up
 * meanwhile stops, so it throws again rather than only chasing.
 */
@Mixin(targets = "net.minecraft.entity.mob.DrownedEntity$DrownedAttackGoal")
public abstract class DrownedMeleeMixin {
	@Shadow
	@Final
	private DrownedEntity drowned;

	@ModifyReturnValue(method = {"canStart", "shouldContinue"}, at = @At("RETURN"))
	private boolean eternalreturn$notWithATrident(boolean going) {
		return going && !this.drowned.getMainHandStack().isOf(Items.TRIDENT);
	}
}
