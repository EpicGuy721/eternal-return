package com.eternalreturn.mixin.snow;

import com.eternalreturn.blocks.Snowlogging;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A gate changed: the grass under it follows its snow (a gate opening sweeps its snow away, ChunkSnowMixin, and gates
 * swing without telling their neighbours, so the ground below isn't told by vanilla).
 */
@Mixin(World.class)
public abstract class GateGroundMixin {
	@Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at = @At("RETURN"))
	private void eternalreturn$groundFollowsGate(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
		World world = (World) (Object) this;
		if (cir.getReturnValueZ() && !world.isClient && state.getBlock() instanceof FenceGateBlock) {
			Snowlogging.groundFollows(world, pos.down(), Block.NOTIFY_ALL);
		}
	}
}
