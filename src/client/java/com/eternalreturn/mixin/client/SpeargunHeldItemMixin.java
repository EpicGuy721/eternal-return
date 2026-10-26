package com.eternalreturn.mixin.client;

import com.eternalreturn.items.Speargun;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** In first person the speargun (Speargun) is a crossbow: held in both hands when loaded, drawn back as one is. */
@Mixin(HeldItemRenderer.class)
public abstract class SpeargunHeldItemMixin {
	@WrapOperation(method = {"getHandRenderType", "getUsingItemHandRenderType", "isChargedCrossbow"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z"))
	private static boolean eternalreturn$speargunIsACrossbow(ItemStack stack, Item item, Operation<Boolean> original) {
		return original.call(stack, item) || item == Items.CROSSBOW && stack.isOf(Speargun.SPEARGUN);
	}

	@WrapOperation(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z"))
	private boolean eternalreturn$drawnAsACrossbow(ItemStack stack, Item item, Operation<Boolean> original) {
		return original.call(stack, item) || item == Items.CROSSBOW && stack.isOf(Speargun.SPEARGUN);
	}
}
