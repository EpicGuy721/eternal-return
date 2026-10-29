package com.eternalreturn.mixin.client;

import com.eternalreturn.blocks.SnowCover;
import com.eternalreturn.blocks.Snowlogging;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
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
 * Mining a block with snow logged in it (SnowCover) in the player's own game: the snow or the block, whichever is aimed
 * at; turning from one to the other starts the mining over; the hits sound of what's mined; and the snow goes, leaving
 * the block, at once (rather than the whole block blinking out until the server says).
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class SnowMiningMixin {
	@Shadow
	@Final
	private MinecraftClient client;
	/** Whether the mining under way is of the snow logged in a block. */
	@Unique
	private boolean eternalreturn$minedSnow;

	@Unique
	private boolean eternalreturn$aimsSnow(BlockPos pos) {
		return SnowCover.aimsSnow(this.client.player, this.client.world, pos, this.client.world.getBlockState(pos));
	}

	@Inject(method = "attackBlock", at = @At("RETURN"))
	private void eternalreturn$noteSnow(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		this.eternalreturn$minedSnow = this.eternalreturn$aimsSnow(pos);
	}

	@ModifyReturnValue(method = "isCurrentlyBreaking", at = @At("RETURN"))
	private boolean eternalreturn$sameThing(boolean breaking, @Local(argsOnly = true) BlockPos pos) {
		return breaking && this.eternalreturn$aimsSnow(pos) == this.eternalreturn$minedSnow;
	}

	@WrapOperation(method = "updateBlockBreakingProgress", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;getSoundGroup()Lnet/minecraft/sound/BlockSoundGroup;"))
	private BlockSoundGroup eternalreturn$snowHitSound(BlockState state, Operation<BlockSoundGroup> original, @Local(argsOnly = true) BlockPos pos) {
		return original.call(this.eternalreturn$aimsSnow(pos) ? SnowCover.asSnowLayer(1) : state);
	}

	@WrapOperation(method = "breakBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;onBreak(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/block/BlockState;"))
	private BlockState eternalreturn$breakTheSnow(Block block, World world, BlockPos pos, BlockState state, PlayerEntity player, Operation<BlockState> original) {
		boolean snow = SnowCover.aimsSnow(player, world, pos, state);
		return original.call(block, world, pos, snow ? SnowCover.asSnowLayer(SnowCover.layers(world, pos, state)) : state, player);
	}

	@WrapOperation(method = "breakBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
	private boolean eternalreturn$leaveTheBlock(World world, BlockPos pos, BlockState emptied, int flags, Operation<Boolean> original) {
		BlockState state = world.getBlockState(pos);
		if (this.eternalreturn$aimsSnow(pos)) {
			Snowlogging.setLayers(world, pos, 0);
			return original.call(world, pos, SnowCover.markedFor(state, 0, world.getBlockState(pos.up())), flags);
		}
		return original.call(world, pos, emptied, flags);
	}
}
