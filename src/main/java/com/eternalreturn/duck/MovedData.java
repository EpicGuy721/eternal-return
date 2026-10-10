package com.eternalreturn.duck;

import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

/** A moving block (piston) carrying the data of the block it moves (Redstone). */
public interface MovedData {
	@Nullable
	NbtCompound eternalreturn$movedData();

	void eternalreturn$setMovedData(@Nullable NbtCompound data);
}
