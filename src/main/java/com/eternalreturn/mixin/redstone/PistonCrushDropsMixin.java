package com.eternalreturn.mixin.redstone;

import com.eternalreturn.blocks.Crushing;
import com.eternalreturn.duck.CrushingPush;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A block the push crushes (Crushing) drops its crusher-chain step instead of what it drops broken by hand. */
@Mixin(PistonBlock.class)
public abstract class PistonCrushDropsMixin {
	@WrapOperation(method = "move", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/block/PistonBlock;dropStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/WorldAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;)V"))
	private void eternalreturn$crushDrops(BlockState state, WorldAccess world, BlockPos pos, BlockEntity blockEntity, Operation<Void> original,
			@Local(ordinal = 0) PistonHandler handler) {
		if (world instanceof ServerWorld server && ((CrushingPush) handler).eternalreturn$crushed().contains(pos)) {
			Crushing.dropCrushed(server, pos, state, blockEntity);
		} else {
			original.call(state, world, pos, blockEntity);
		}
	}
}
