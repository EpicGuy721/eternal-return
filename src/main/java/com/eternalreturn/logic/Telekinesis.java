package com.eternalreturn.logic;

import com.eternalreturn.EternalReturn;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Telekinesis (tools and weapons, one level): what a block broken with it drops, and what a mob killed with it drops
 * (struck, or shot with an arrow or trident from it), goes straight into the player's inventory; what doesn't fit drops
 * as usual (TelekinesisBlockMixin, TelekinesisMobMixin, TelekinesisDropMixin). Experience still drops as orbs.
 */
public final class Telekinesis {
	public static final Identifier ID = EternalReturn.id("telekinesis");

	private Telekinesis() {
	}

	/** The player the drops go to: the one breaking or killing, if the tool has Telekinesis. */
	@Nullable
	public static PlayerEntity collector(@Nullable Entity entity, @Nullable ItemStack tool) {
		return entity instanceof PlayerEntity player && !player.isSpectator() && NewEnchantments.level(tool, ID) > 0 ? player : null;
	}

	/** Puts the stack in the player's inventory, with the pickup sound; returns what didn't fit (empty if it all did). */
	public static ItemStack give(PlayerEntity player, ItemStack stack) {
		int count = stack.getCount();
		player.getInventory().insertStack(stack);
		if (stack.getCount() < count) {
			player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.2F,
					((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);
		}
		return stack;
	}

	/** A dying mob, while its drops go to the player who killed it. */
	public interface Collected {
		@Nullable
		PlayerEntity eternalreturn$collector();
	}
}
