package com.eternalreturn.mixin.redstone;

import com.eternalreturn.blocks.Redstone;
import com.eternalreturn.duck.MovedData;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.PistonExtensionBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pistons move blocks with data (Redstone): such blocks count as movable unless tagged immovable; as a
 * move starts, each moved block's data is taken (contents out, so nothing spills when it is lifted) and
 * handed to the moving block at its new place, which puts it back when it lands.
 */
@Mixin(PistonBlock.class)
public abstract class PistonBlockMixin {
	@Unique
	private static final ThreadLocal<Map<BlockPos, NbtCompound>> ETERNALRETURN_MOVING = ThreadLocal.withInitial(HashMap::new);

	@ModifyExpressionValue(method = "isMovable", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;hasBlockEntity()Z"))
	private static boolean eternalreturn$movableData(boolean hasBlockEntity, BlockState state) {
		return hasBlockEntity && Redstone.blocksPiston(state);
	}

	@WrapOperation(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/piston/PistonHandler;getMovedBlocks()Ljava/util/List;"))
	private List<BlockPos> eternalreturn$takeData(PistonHandler handler, Operation<List<BlockPos>> original, World world) {
		List<BlockPos> moved = original.call(handler);
		Map<BlockPos, NbtCompound> data = ETERNALRETURN_MOVING.get();
		data.clear();
		for (BlockPos pos : moved) {
			if (Redstone.carriesData(world.getBlockState(pos))) {
				NbtCompound nbt = Redstone.takeData(world, pos);
				if (nbt != null) {
					data.put(pos.toImmutable(), nbt);
				}
			}
		}
		return moved;
	}

	@WrapOperation(method = "move", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/block/PistonExtensionBlock;createBlockEntityPiston(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/Direction;ZZ)Lnet/minecraft/block/entity/BlockEntity;"))
	private BlockEntity eternalreturn$carryData(BlockPos pos, BlockState state, BlockState pushed, Direction facing, boolean extending, boolean source,
			Operation<BlockEntity> original) {
		BlockEntity piston = original.call(pos, state, pushed, facing, extending, source);
		if (!source) {
			Direction motion = extending ? facing : facing.getOpposite();
			NbtCompound nbt = ETERNALRETURN_MOVING.get().remove(pos.offset(motion.getOpposite()));
			if (nbt != null && piston instanceof MovedData carrier) {
				carrier.eternalreturn$setMovedData(nbt);
			}
		}
		return piston;
	}

	@Inject(method = "move", at = @At("RETURN"))
	private void eternalreturn$forget(World world, BlockPos pos, Direction dir, boolean retract, CallbackInfoReturnable<Boolean> cir) {
		ETERNALRETURN_MOVING.get().clear();
	}
}
