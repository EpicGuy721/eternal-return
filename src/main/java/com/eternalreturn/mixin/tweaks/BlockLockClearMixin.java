package com.eternalreturn.mixin.tweaks;

import com.eternalreturn.logic.BlockLocks;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A locked block (BlockLocks) broken or replaced by another block loses its lock. */
@Mixin(WorldChunk.class)
public abstract class BlockLockClearMixin {
	@Inject(method = "setBlockState", at = @At("RETURN"))
	private void eternalreturn$unlock(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
		BlockState old = cir.getReturnValue();
		if (old != null && !old.isOf(state.getBlock())) {
			BlockLocks.unlock((WorldChunk) (Object) this, pos);
		}
	}
}
