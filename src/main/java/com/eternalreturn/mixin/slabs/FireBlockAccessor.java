package com.eternalreturn.mixin.slabs;

import net.minecraft.block.BlockState;
import net.minecraft.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** How readily fire catches on a block (MixedSlabs.fireView: which half of a mixed slab fire takes). */
@Mixin(FireBlock.class)
public interface FireBlockAccessor {
	@Invoker("getBurnChance")
	int eternalreturn$burnChance(BlockState state);
}
