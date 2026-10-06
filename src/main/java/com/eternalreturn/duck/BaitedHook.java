package com.eternalreturn.duck;

import net.minecraft.item.ItemStack;

/** Fishing hooks remember which bait the catch being reeled in was made with, for the bait loot condition. */
public interface BaitedHook {
	ItemStack eternalreturn$getBait();
}
