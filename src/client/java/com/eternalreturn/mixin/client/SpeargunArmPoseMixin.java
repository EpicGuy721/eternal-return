package com.eternalreturn.mixin.client;

import com.eternalreturn.items.Speargun;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A player holding a loaded speargun (Speargun) aims it as a loaded crossbow. */
@Mixin(PlayerEntityRenderer.class)
public abstract class SpeargunArmPoseMixin {
	@WrapOperation(method = "getArmPose", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z"))
	private static boolean eternalreturn$aimedAsACrossbow(ItemStack stack, Item item, Operation<Boolean> original) {
		return original.call(stack, item) || item == Items.CROSSBOW && stack.isOf(Speargun.SPEARGUN);
	}
}
