package com.eternalreturn.mixin;

import com.eternalreturn.logic.FortuneHelper;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(Block.class)
public abstract class BlockMixin {
	/** Fortune on a hoe multiplies fully grown crop drops using the ore formula. */
	@ModifyReturnValue(
			method = "getDroppedStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)Ljava/util/List;",
			at = @At("RETURN"))
	private static List<ItemStack> eternalreturn$cropFortune(List<ItemStack> drops,
			@Local(argsOnly = true) BlockState state,
			@Local(argsOnly = true) ServerWorld world,
			@Local(argsOnly = true) ItemStack tool) {
		return FortuneHelper.applyCropFortune(drops, state, world.getRandom(), tool);
	}
}
