package com.eternalreturn.logic;

import com.eternalreturn.EternalReturn;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

/**
 * Attraction (armor, I to V): the player picks up items a block further away (sideways) per level, the levels of all
 * the pieces they wear added up (AttractionMixin). Only items: not arrows or experience.
 */
public final class Attraction {
	public static final Identifier ID = EternalReturn.id("attraction");

	private Attraction() {
	}

	/** The extra pickup reach of the armor this player wears, in blocks. */
	public static int reach(PlayerEntity player) {
		int reach = 0;
		for (ItemStack stack : player.getArmorItems()) {
			reach += NewEnchantments.level(stack, ID);
		}
		return reach;
	}

	/** Each tick, after the player's own pickups: the items in reach beyond vanilla's. */
	public static void pickUp(PlayerEntity player) {
		int reach = reach(player);
		if (reach <= 0 || player.getWorld().isClient || player.getHealth() <= 0.0F || player.isSpectator()) {
			return;
		}
		// Vanilla's pickup box, a block out sideways and half a block up and down, widened.
		Box box = player.getBoundingBox().expand(1.0 + reach, 0.5, 1.0 + reach);
		for (ItemEntity item : player.getWorld().getEntitiesByClass(ItemEntity.class, box, item -> !item.isRemoved())) {
			item.onPlayerCollision(player);
		}
	}
}
