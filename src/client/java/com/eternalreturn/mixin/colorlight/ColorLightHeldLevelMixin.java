package com.eternalreturn.mixin.colorlight;

import com.eternalreturn.client.ColorLightBrighter;
import me.mrhikmen.colorlight.client.config.BlockSettings;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlockStateComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ColorLight (an optional client mod) lights a held, dropped or framed block item at one level per kind of block, the
 * brightest that block gets in any state: an unlit candle in the hand as four lit candles (12), an unpowered redstone
 * lamp or redstone ore as lit ones. Here the item lights as the block it places does, in the state it is held in (the
 * light LambDynamicLights gives it on its own, made brighter for soul lights as placed ones are, ColorLightBrighter): a
 * torch, soul torch, redstone torch or lantern as placed, and a candle, redstone lamp or redstone ore not at all.
 * Applied only when ColorLight is installed and still has this step (ColorLightMixinPlugin).
 */
@Mixin(targets = "me.mrhikmen.colorlight.client.compat.lambdynlights.ColorLightEntityLightTicker", remap = false)
public abstract class ColorLightHeldLevelMixin {
	@Inject(method = "effectiveStrength", at = @At("HEAD"), cancellable = true, remap = false)
	private static void eternalreturn$levelAsPlaced(BlockSettings settings, ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (!stack.isEmpty() && stack.getItem() instanceof BlockItem item) {
			BlockState state = stack.getOrDefault(DataComponentTypes.BLOCK_STATE, BlockStateComponent.DEFAULT)
					.applyToState(item.getBlock().getDefaultState());
			cir.setReturnValue(ColorLightBrighter.level(state));
		}
	}
}
