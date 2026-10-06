package com.eternalreturn.fishing;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Bait is looked up in the angler's inventory: off hand, main hand, then hotbar and inventory in
 * slot order, the same order bows look for arrows. Putting a bait in the off hand picks it.
 */
public final class Bait {
	private Bait() {
	}

	@Nullable
	public static EternalReturnConfig.BaitStats stats(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		return EternalReturnConfig.get().fishing.baits.get(Registries.ITEM.getId(stack.getItem()).toString());
	}

	private static boolean usable(ItemStack stack, boolean lava) {
		EternalReturnConfig.BaitStats stats = stats(stack);
		return stats != null && stats.lava == lava;
	}

	/** The stack that would be used, or EMPTY. The returned stack is the live inventory stack. */
	public static ItemStack find(PlayerEntity player, boolean lava) {
		for (ItemStack stack : searchOrder(player)) {
			if (usable(stack, lava)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	public static int lureTicks(ItemStack bait, World world) {
		EternalReturnConfig.BaitStats stats = stats(bait);
		if (stats == null) {
			return 0;
		}
		double seconds = stats.nightLureSeconds > 0 && world.isNight() ? stats.nightLureSeconds : stats.lureSeconds;
		return (int) Math.round(seconds * 20.0);
	}

	/**
	 * After a catch: uses up one bait unless the rod's Thriftiness saves it. Returns whether bait was
	 * used.
	 */
	public static boolean consumeAfterCatch(PlayerEntity player, ItemStack bait, ItemStack rod, Random random) {
		if (bait.isEmpty() || random.nextFloat() < Thriftiness.baitSavingChance(rod, random)) {
			return false;
		}
		consumeOne(player, bait);
		return true;
	}

	/** Uses up one of the given bait. Creative players keep their bait. */
	public static void consumeOne(PlayerEntity player, ItemStack bait) {
		if (bait.isEmpty() || player.getAbilities().creativeMode) {
			return;
		}
		for (ItemStack stack : searchOrder(player)) {
			if (ItemStack.areItemsAndComponentsEqual(stack, bait)) {
				stack.decrement(1);
				return;
			}
		}
	}

	private static List<ItemStack> searchOrder(PlayerEntity player) {
		List<ItemStack> order = new ArrayList<>(2 + player.getInventory().main.size());
		order.add(player.getOffHandStack());
		order.add(player.getMainHandStack());
		order.addAll(player.getInventory().main);
		return order;
	}
}
