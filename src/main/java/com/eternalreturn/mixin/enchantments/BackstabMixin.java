package com.eternalreturn.mixin.enchantments;

import com.eternalreturn.logic.Backstab;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A weapon's damage, players' and mobs' alike, with Backstabber's bonus for a hit from behind (Backstab). */
@Mixin(EnchantmentHelper.class)
public abstract class BackstabMixin {
	@ModifyReturnValue(method = "getDamage", at = @At("RETURN"))
	private static float eternalreturn$backstab(float damage, ServerWorld world, ItemStack stack, Entity target, DamageSource source, float baseDamage) {
		return Backstab.damage(stack, target, source, damage);
	}
}
