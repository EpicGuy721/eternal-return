package com.eternalreturn.mixin.tweaks;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.ai.goal.ProjectileAttackGoal;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A drowned's trident attack ends once its trident is thrown (DrownedLoyalTridentMixin): vanilla kept it going while the
 * drowned walked, arm raised with nothing in it and "throwing" nothing, until its trident came back.
 */
@Mixin(ProjectileAttackGoal.class)
public abstract class DrownedTridentGoalMixin {
	@Shadow
	@Final
	private MobEntity mob;

	@ModifyReturnValue(method = "shouldContinue", at = @At("RETURN"))
	private boolean eternalreturn$onlyWithATrident(boolean going) {
		return going && !(this.mob instanceof DrownedEntity drowned && !drowned.getMainHandStack().isOf(Items.TRIDENT));
	}
}
