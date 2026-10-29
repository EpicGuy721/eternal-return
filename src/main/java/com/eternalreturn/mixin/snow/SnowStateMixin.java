package com.eternalreturn.mixin.snow;

import com.eternalreturn.blocks.SnowCover;
import com.eternalreturn.blocks.Snowlogging;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A block with snow logged in it (SnowCover): its snow can be aimed at and stood on (outline and collision, where the
 * world says how much there is), it's mined as snow when aimed at, shows white on maps, ticks now and then (to melt
 * it: SnowfallMixin), takes snow put on it by hand (before the block is used: a gate doesn't open), and stays marked
 * snowy as the snow on it says.
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class SnowStateMixin {
	@Shadow
	protected abstract BlockState asBlockState();

	@WrapOperation(method = "initShapeCache", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;hasRandomTicks(Lnet/minecraft/block/BlockState;)Z"))
	private boolean eternalreturn$snowMelts(Block block, BlockState state, Operation<Boolean> original) {
		return original.call(block, state) || SnowCover.marked(state);
	}

	@ModifyReturnValue(method = "getOutlineShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",
			at = @At("RETURN"))
	private VoxelShape eternalreturn$withSnow(VoxelShape shape, @Local(argsOnly = true) BlockView world, @Local(argsOnly = true) BlockPos pos) {
		int layers = SnowCover.layers(world, pos, this.asBlockState());
		return layers == 0 ? shape : VoxelShapes.union(shape, SnowCover.snowOutline(this.asBlockState(), layers));
	}

	@ModifyReturnValue(method = "getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",
			at = @At("RETURN"))
	private VoxelShape eternalreturn$standOnSnow(VoxelShape shape, @Local(argsOnly = true) BlockView world, @Local(argsOnly = true) BlockPos pos) {
		int layers = SnowCover.layers(world, pos, this.asBlockState());
		return layers <= 1 ? shape : VoxelShapes.union(shape, SnowCover.snowCollision(this.asBlockState(), layers));
	}

	@Inject(method = "getMapColor", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$white(BlockView world, BlockPos pos, CallbackInfoReturnable<MapColor> cir) {
		if (SnowCover.layers(world, pos, this.asBlockState()) > 0) {
			cir.setReturnValue(MapColor.WHITE);
		}
	}

	@Inject(method = "onUseWithItem", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$putSnowOn(ItemStack stack, World world, PlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ItemActionResult> cir) {
		if (SnowCover.isHost(this.asBlockState()) && Snowlogging.useSnow(new ItemUsageContext(player, hand, hit))) {
			cir.setReturnValue(ItemActionResult.success(world.isClient));
		}
	}

	@ModifyReturnValue(method = "getStateForNeighborUpdate", at = @At("RETURN"))
	private BlockState eternalreturn$snowyAsItsSnowSays(BlockState state, @Local(argsOnly = true) Direction direction,
			@Local(argsOnly = true) BlockState neighborState, @Local(argsOnly = true) WorldAccess world, @Local(ordinal = 0, argsOnly = true) BlockPos pos) {
		if (direction != Direction.UP || !SnowCover.isHost(state)) {
			return state;
		}
		return SnowCover.markedFor(state, Math.min(Snowlogging.layers(world, pos), SnowCover.capacity(state)), neighborState);
	}

	@Inject(method = "calcBlockBreakingDelta", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$digSnow(PlayerEntity player, BlockView world, BlockPos pos, CallbackInfoReturnable<Float> cir) {
		BlockState state = this.asBlockState();
		if (SnowCover.aimsSnow(player, world, pos, state)) {
			cir.setReturnValue(SnowCover.asSnowLayer(SnowCover.layers(world, pos, state)).calcBlockBreakingDelta(player, world, pos));
		}
	}
}
