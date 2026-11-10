package com.eternalreturn.mixin.snow;

import com.eternalreturn.blocks.SnowCover;
import com.eternalreturn.blocks.Snowlogging;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A block changing in a loaded chunk keeps the snow logged in it as far as the new block holds it (Snowlogging); a plant
 * growing (a crop's age, made from its plain state) stays snowy while it has snow logged in it; a gate swinging open
 * sweeps its snow away.
 */
@Mixin(WorldChunk.class)
public abstract class ChunkSnowMixin {
	@ModifyVariable(method = "setBlockState", at = @At("HEAD"), argsOnly = true)
	private BlockState eternalreturn$plantKeepsSnow(BlockState state, @Local(argsOnly = true) BlockPos pos) {
		if (SnowCover.marked(state) || !SnowCover.isFlora(state)) {
			return state;
		}
		WorldChunk chunk = (WorldChunk) (Object) this;
		BlockState old = chunk.getBlockState(pos);
		if (old.getBlock() == state.getBlock() && SnowCover.marked(old) && Snowlogging.layers(chunk.getWorld(), pos) > 0) {
			return SnowCover.mark(state, true);
		}
		return state;
	}

	@ModifyVariable(method = "setBlockState", at = @At("HEAD"), argsOnly = true)
	private BlockState eternalreturn$gateSweepsSnow(BlockState state, @Local(argsOnly = true) BlockPos pos) {
		WorldChunk chunk = (WorldChunk) (Object) this;
		return Snowlogging.gateSwept(chunk, pos, chunk.getBlockState(pos), state);
	}

	@Inject(method = "setBlockState", at = @At("RETURN"))
	private void eternalreturn$snowStays(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
		BlockState old = cir.getReturnValue();
		if (old != null && old != state) {
			Snowlogging.blockChanged((WorldChunk) (Object) this, pos, old, state);
		}
	}
}
