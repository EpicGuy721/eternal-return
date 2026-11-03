package com.eternalreturn.mixin.combat;

import com.eternalreturn.logic.SwordBlocking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Defense on a sword a player blocks with takes 5% more off the damage per level (SwordBlocking), at the same point as
 * Nostalgic Tweaks' own blocking reduction (the head of LivingEntity.damage, whose only float is the amount).
 */
@Mixin(LivingEntity.class)
public abstract class DefenseMixin {
	@ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
	private float eternalreturn$defense(float amount, DamageSource source) {
		if ((Object) this instanceof PlayerEntity player && !player.getWorld().isClient) {
			return SwordBlocking.defend(player, amount);
		}
		return amount;
	}
}
