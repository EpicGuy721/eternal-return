package com.eternalreturn.mixin.tweaks;

import com.eternalreturn.blocks.Dyeing;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A dyed redstone lamp item places a dyed redstone lamp (Dyeing), which takes its colour from the item. */
@Mixin(BlockItem.class)
public abstract class RedstoneLampItemMixin {
	@Inject(method = "getPlacementState", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$dyedLamp(ItemPlacementContext context, CallbackInfoReturnable<BlockState> cir) {
		if (((BlockItem) (Object) this).getBlock() == Blocks.REDSTONE_LAMP && context.getStack().contains(DataComponentTypes.DYED_COLOR)) {
			BlockState state = Dyeing.DYED_REDSTONE_LAMP.getPlacementState(context);
			cir.setReturnValue(state != null && context.getWorld().canPlace(state, context.getBlockPos(), net.minecraft.block.ShapeContext.absent()) ? state : null);
		}
	}
}
