package com.eternalreturn.mixin.colorlight;

import com.eternalreturn.client.ColorLightBrighter;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ColorLight puts a changed block's light in at the block's own level; a soul light or candle placed or lit on the client
 * is raised to its brighter level just after (ColorLightBrighter). Applied only when ColorLight is installed
 * (ColorLightMixinPlugin).
 */
@Mixin(WorldChunk.class)
public abstract class ColorLightBlockChangeMixin {
	@Shadow
	@Final
	World world;

	@Inject(method = "setBlockState", at = @At("RETURN"))
	private void eternalreturn$brighter(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
		if (this.world.isClient && cir.getReturnValue() != null) {
			ColorLightBrighter.changed(pos, state);
		}
	}
}
