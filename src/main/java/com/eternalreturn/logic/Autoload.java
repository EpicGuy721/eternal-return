package com.eternalreturn.logic;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.mixin.combat.CrossbowItemInvoker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

/**
 * Autoload (crossbows, one level): a crossbow with it that you aren't holding loads itself from your arrows (or
 * rockets), about as fast as you could load it by hand.
 */
public final class Autoload {
	public static final Identifier ID = EternalReturn.id("autoload");

	private Autoload() {
	}

	/** Every 25 ticks (a crossbow's loading time), server side. */
	public static void tick(PlayerEntity player) {
		if (player.age % 25 != 0 || player.getWorld().isClient) {
			return;
		}
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.main.size(); slot++) {
			ItemStack stack = inventory.main.get(slot);
			if (slot == inventory.selectedSlot || !(stack.getItem() instanceof CrossbowItem) || CrossbowItem.isCharged(stack)
					|| NewEnchantments.level(stack, ID) <= 0) {
				continue;
			}
			if (CrossbowItemInvoker.eternalreturn$loadProjectiles(player, stack)) {
				player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_CROSSBOW_LOADING_END.value(),
						SoundCategory.PLAYERS, 0.4F, 1.2F);
				return;
			}
		}
	}
}
