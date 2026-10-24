package com.eternalreturn.logic;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Fire Protection at its highest level, on any one piece of armor: the player swims in lava as in water (moving,
 * swimming and seeing as well as there: LavaTravelMixin, SwimKeyMixin, client LavaFogMixin), and lava neither hurts
 * nor sets them alight (LavaSwimmingMixin).
 */
public final class LavaSwimming {
	private LavaSwimming() {
	}

	public static boolean swims(Entity entity) {
		if (!(entity instanceof PlayerEntity player)) {
			return false;
		}
		for (ItemStack stack : player.getArmorItems()) {
			for (var entry : stack.getEnchantments().getEnchantmentEntries()) {
				if (entry.getKey().matchesKey(Enchantments.FIRE_PROTECTION) && entry.getIntValue() >= entry.getKey().value().getMaxLevel()) {
					return true;
				}
			}
		}
		return false;
	}
}
