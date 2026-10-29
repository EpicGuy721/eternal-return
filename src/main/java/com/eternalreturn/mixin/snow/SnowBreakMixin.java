package com.eternalreturn.mixin.snow;

import com.eternalreturn.blocks.SnowCover;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A player digging a block with snow on it (SnowCover) with a tool that digs snow digs only the snow: it breaks as snow
 * (its particles and sound), the block stays, and the tool wear, the drops and the stat are the snow's.
 */
@Mixin(ServerPlayerInteractionManager.class)
public abstract class SnowBreakMixin {
	@Shadow
	@Final
	protected ServerPlayerEntity player;

	@WrapOperation(method = "tryBreakBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;onBreak(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/block/BlockState;"))
	private BlockState eternalreturn$breakTheSnow(Block block, World world, BlockPos pos, BlockState state, PlayerEntity player, Operation<BlockState> original) {
		return original.call(block, world, pos, SnowCover.digsSnow(player, state) ? SnowCover.asSnowLayer(state) : state, player);
	}

	@WrapOperation(method = "tryBreakBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;removeBlock(Lnet/minecraft/util/math/BlockPos;Z)Z"))
	private boolean eternalreturn$leaveTheBlock(ServerWorld world, BlockPos pos, boolean move, Operation<Boolean> original) {
		BlockState state = world.getBlockState(pos);
		if (SnowCover.digsSnow(this.player, state)) {
			return world.setBlockState(pos, SnowCover.bare(state), Block.NOTIFY_ALL);
		}
		return original.call(world, pos, move);
	}

	@WrapOperation(method = "tryBreakBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;afterBreak(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/item/ItemStack;)V"))
	private void eternalreturn$snowsOwnDrops(Block block, World world, PlayerEntity player, BlockPos pos, BlockState broken, BlockEntity entity, ItemStack tool,
			Operation<Void> original) {
		original.call(broken.isOf(Blocks.SNOW) ? Blocks.SNOW : block, world, player, pos, broken, entity, tool);
	}
}
