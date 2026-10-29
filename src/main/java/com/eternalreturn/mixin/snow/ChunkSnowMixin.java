package com.eternalreturn.mixin.snow;

import com.eternalreturn.blocks.Snowlogging;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A block changing in a loaded chunk keeps the snow logged in it as far as the new block holds it (Snowlogging). */
@Mixin(WorldChunk.class)
public abstract class ChunkSnowMixin {
	@Inject(method = "setBlockState", at = @At("RETURN"))
	private void eternalreturn$snowStays(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
		BlockState old = cir.getReturnValue();
		if (old != null && old != state) {
			Snowlogging.blockChanged((WorldChunk) (Object) this, pos, old, state);
		}
	}
}
