package com.eternalreturn.mixin.client;

import com.eternalreturn.blocks.MixedSlabs;
import com.eternalreturn.blocks.Slabs;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mining a mixed slab (MixedSlabs) in the player's own game: the hits sound of the half aimed at, turning to the other
 * half starts the mining over, and breaking a half leaves the other at once (rather than the whole block blinking out
 * until the server says).
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class MixedSlabMiningMixin {
	@Shadow
	@Final
	private MinecraftClient client;
	/** Whether the mining under way is of a mixed slab's positive half. */
	@Unique
	private boolean eternalreturn$minedPositive;

	@Unique
	private boolean eternalreturn$aimsPositive(BlockPos pos) {
		BlockState state = this.client.world.getBlockState(pos);
		return state.isOf(MixedSlabs.MIXED_SLAB) && Slabs.aimsPositive(this.client.player, pos, state.get(Slabs.AXIS));
	}

	@Inject(method = "attackBlock", at = @At("RETURN"))
	private void eternalreturn$noteHalf(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		this.eternalreturn$minedPositive = this.eternalreturn$aimsPositive(pos);
	}

	@ModifyReturnValue(method = "isCurrentlyBreaking", at = @At("RETURN"))
	private boolean eternalreturn$sameHalf(boolean breaking, @Local(argsOnly = true) BlockPos pos) {
		return breaking && (!this.client.world.getBlockState(pos).isOf(MixedSlabs.MIXED_SLAB) || this.eternalreturn$aimsPositive(pos) == this.eternalreturn$minedPositive);
	}

	@WrapOperation(method = "updateBlockBreakingProgress", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;getSoundGroup()Lnet/minecraft/sound/BlockSoundGroup;"))
	private BlockSoundGroup eternalreturn$halfHitSound(BlockState state, Operation<BlockSoundGroup> original, @Local(argsOnly = true) BlockPos pos) {
		return original.call(state.isOf(MixedSlabs.MIXED_SLAB) ? MixedSlabs.aimedHalf(this.client.player, this.client.world, pos, state) : state);
	}

	@WrapOperation(method = "breakBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
	private boolean eternalreturn$leaveTheOtherHalf(World world, BlockPos pos, BlockState emptied, int flags, Operation<Boolean> original) {
		BlockState state = world.getBlockState(pos);
		if (state.isOf(MixedSlabs.MIXED_SLAB)) {
			BlockState left = MixedSlabs.otherHalf(world, pos, state, MixedSlabs.aimedHalf(this.client.player, world, pos, state));
			if (left != null) {
				return original.call(world, pos, left, flags);
			}
		}
		return original.call(world, pos, emptied, flags);
	}
}
