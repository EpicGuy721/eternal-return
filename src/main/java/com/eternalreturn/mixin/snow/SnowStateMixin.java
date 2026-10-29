package com.eternalreturn.mixin.snow;

import com.eternalreturn.blocks.SnowCover;
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
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * A block with snow on it (SnowCover): ticks now and then (to melt it: SnowMeltMixin), shows white on maps, takes a snow
 * layer put on its top by hand, keeps its snow as the space above says, drops its snow with itself, and is dug as snow by
 * a tool that digs snow.
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class SnowStateMixin {
	@Shadow
	protected abstract BlockState asBlockState();

	@WrapOperation(method = "initShapeCache", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;hasRandomTicks(Lnet/minecraft/block/BlockState;)Z"))
	private boolean eternalreturn$snowMelts(Block block, BlockState state, Operation<Boolean> original) {
		return original.call(block, state) || SnowCover.melts(state);
	}

	@Inject(method = "getMapColor", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$white(BlockView world, BlockPos pos, CallbackInfoReturnable<MapColor> cir) {
		if (SnowCover.held(this.asBlockState()) > 0) {
			cir.setReturnValue(MapColor.WHITE);
		}
	}

	@Inject(method = "onUseWithItem", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$putSnowOn(ItemStack stack, World world, PlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ItemActionResult> cir) {
		if (SnowCover.property(this.asBlockState()) != null
				&& SnowCover.placeByHand(stack, world, hit.getBlockPos(), this.asBlockState(), player, hit.getSide())) {
			cir.setReturnValue(ItemActionResult.success(world.isClient));
		}
	}

	@ModifyReturnValue(method = "getStateForNeighborUpdate", at = @At("RETURN"))
	private BlockState eternalreturn$snowAsTheSpaceAboveSays(BlockState state, @Local(argsOnly = true) Direction direction,
			@Local(argsOnly = true) BlockState neighborState) {
		return SnowCover.neighborChanged(state, direction, neighborState);
	}

	@ModifyReturnValue(method = "getDroppedStacks", at = @At("RETURN"))
	private List<ItemStack> eternalreturn$snowDrops(List<ItemStack> drops, @Local(argsOnly = true) LootContextParameterSet.Builder builder) {
		return SnowCover.withSnowDrops(this.asBlockState(), drops, builder);
	}

	@Inject(method = "calcBlockBreakingDelta", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$digSnow(PlayerEntity player, BlockView world, BlockPos pos, CallbackInfoReturnable<Float> cir) {
		BlockState state = this.asBlockState();
		if (SnowCover.digsSnow(player, state)) {
			cir.setReturnValue(SnowCover.asSnowLayer(state).calcBlockBreakingDelta(player, world, pos));
		}
	}
}
