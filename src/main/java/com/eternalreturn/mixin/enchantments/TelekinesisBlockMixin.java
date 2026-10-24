package com.eternalreturn.mixin.enchantments;

import com.eternalreturn.logic.Telekinesis;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/** A block broken with a Telekinesis tool: its drops go to the player, only what doesn't fit drops (Telekinesis). */
@Mixin(Block.class)
public abstract class TelekinesisBlockMixin {
	@WrapOperation(method = "dropStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getDroppedStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)Ljava/util/List;"))
	private static List<ItemStack> eternalreturn$telekinesis(BlockState state, ServerWorld world, BlockPos pos, BlockEntity blockEntity, Entity entity,
			ItemStack tool, Operation<List<ItemStack>> original) {
		List<ItemStack> drops = original.call(state, world, pos, blockEntity, entity, tool);
		PlayerEntity player = Telekinesis.collector(entity, tool);
		if (player == null) {
			return drops;
		}
		List<ItemStack> left = new ArrayList<>();
		for (ItemStack stack : drops) {
			if (!Telekinesis.give(player, stack).isEmpty()) {
				left.add(stack);
			}
		}
		return left;
	}
}
