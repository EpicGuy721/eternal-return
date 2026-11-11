package com.eternalreturn.logic;

import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

/**
 * Which held block items light as the block they place (the client's ColorLight held level, ColorLightHeldLevelMixin):
 * those named as their block. An item named apart from it (glow berries, which place a cave vine without berries, dark)
 * keeps ColorLight's own level for that block, as LambDynamicLights lights it (glow berries 14).
 */
public final class HeldLight {
	private HeldLight() {
	}

	public static boolean litAsPlaced(Item item) {
		return item instanceof BlockItem block && Registries.ITEM.getId(item).equals(Registries.BLOCK.getId(block.getBlock()));
	}
}
