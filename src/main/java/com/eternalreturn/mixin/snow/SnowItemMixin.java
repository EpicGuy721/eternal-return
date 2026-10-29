package com.eternalreturn.mixin.snow;

import com.eternalreturn.blocks.SnowCover;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A snow layer put on the top of a block that holds snow (SnowCover) while sneaking, when the block isn't used first. */
@Mixin(BlockItem.class)
public abstract class SnowItemMixin {
	@Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$putSnowOn(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
		BlockState state = context.getWorld().getBlockState(context.getBlockPos());
		if (SnowCover.property(state) != null
				&& SnowCover.placeByHand(context.getStack(), context.getWorld(), context.getBlockPos(), state, context.getPlayer(), context.getSide())) {
			cir.setReturnValue(ActionResult.success(context.getWorld().isClient));
		}
	}
}
