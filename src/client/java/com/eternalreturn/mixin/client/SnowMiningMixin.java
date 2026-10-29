package com.eternalreturn.mixin.client;

import com.eternalreturn.blocks.SnowCover;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Digging the snow off a block (SnowCover) in the player's own game: the hits sound of snow, and the snow goes with
 * its particles leaving the block (rather than the whole block blinking out until the server says).
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class SnowMiningMixin {
	@Shadow
	@Final
	private MinecraftClient client;

	@WrapOperation(method = "updateBlockBreakingProgress", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;getSoundGroup()Lnet/minecraft/sound/BlockSoundGroup;"))
	private BlockSoundGroup eternalreturn$snowHitSound(BlockState state, Operation<BlockSoundGroup> original) {
		return original.call(SnowCover.digsSnow(this.client.player, state) ? SnowCover.asSnowLayer(state) : state);
	}

	@WrapOperation(method = "breakBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;onBreak(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/block/BlockState;"))
	private BlockState eternalreturn$breakTheSnow(Block block, World world, BlockPos pos, BlockState state, PlayerEntity player, Operation<BlockState> original) {
		return original.call(block, world, pos, SnowCover.digsSnow(player, state) ? SnowCover.asSnowLayer(state) : state, player);
	}

	@WrapOperation(method = "breakBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
	private boolean eternalreturn$leaveTheBlock(World world, BlockPos pos, BlockState emptied, int flags, Operation<Boolean> original) {
		BlockState state = world.getBlockState(pos);
		return original.call(world, pos, SnowCover.digsSnow(this.client.player, state) ? SnowCover.bare(state) : emptied, flags);
	}
}
