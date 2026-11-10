package com.eternalreturn.mixin.snow;

import com.eternalreturn.blocks.Snowlogging;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.block.SnowyBlock;
import net.minecraft.block.SpreadableBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Grass and mycelium spreading onto dirt under snow logged in a plant, fence, wall or gate come up snowy, as under a snow layer (SnowyGroundMixin). */
@Mixin(SpreadableBlock.class)
public abstract class SpreadSnowyMixin {
	@WrapOperation(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)Z"))
	private boolean eternalreturn$spreadUnderSnow(ServerWorld world, BlockPos pos, BlockState state, Operation<Boolean> original) {
		if (state.contains(SnowyBlock.SNOWY) && !state.get(SnowyBlock.SNOWY) && Snowlogging.snowOnGround(world, pos)) {
			state = state.with(SnowyBlock.SNOWY, true);
		}
		return original.call(world, pos, state);
	}
}
