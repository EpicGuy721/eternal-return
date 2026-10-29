package com.eternalreturn.mixin.snow;

import com.eternalreturn.blocks.SnowCover;
import com.eternalreturn.blocks.Snowlogging;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Snow put on blocks that hold it by hand while sneaking, when the block isn't used first (Snowlogging.useSnow); and a
 * slab, stairs, wall, fence or gate placed into a snow layer keeps the snow, logged in it (as much as lies above its
 * floor), instead of replacing it.
 */
@Mixin(BlockItem.class)
public abstract class SnowItemMixin {
	@Unique
	private static final ThreadLocal<Integer> eternalreturn$snowThere = ThreadLocal.withInitial(() -> 0);

	@Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$putSnowOn(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
		if (Snowlogging.useSnow(context)) {
			cir.setReturnValue(ActionResult.success(context.getWorld().isClient));
		}
	}

	@Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At("HEAD"))
	private void eternalreturn$noteSnow(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
		BlockState there = context.getWorld().getBlockState(context.getBlockPos());
		eternalreturn$snowThere.set(there.isOf(Blocks.SNOW) ? there.get(SnowBlock.LAYERS) : 0);
	}

	@Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At("RETURN"))
	private void eternalreturn$keepSnow(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
		int snow = eternalreturn$snowThere.get();
		eternalreturn$snowThere.set(0);
		BlockPos pos = context.getBlockPos();
		BlockState placed = context.getWorld().getBlockState(pos);
		if (snow > 0 && cir.getReturnValue().isAccepted() && SnowCover.isHost(placed)) {
			Snowlogging.snowlog(context.getWorld(), pos, Snowlogging.layersOver(placed, snow), Block.NOTIFY_ALL);
		}
	}
}
