package com.eternalreturn.mixin.snow;

import com.eternalreturn.blocks.SnowCover;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Every block of a kind that holds snow (SnowCover) gets its snow property as it's made, whatever its own properties. */
@Mixin(Block.class)
public abstract class SnowHostMixin {
	@WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;appendProperties(Lnet/minecraft/state/StateManager$Builder;)V"))
	private void eternalreturn$snowProperty(Block block, StateManager.Builder<Block, BlockState> builder, Operation<Void> original) {
		original.call(block, builder);
		SnowCover.addProperty(block, builder);
	}
}
