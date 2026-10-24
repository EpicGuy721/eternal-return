package com.eternalreturn.mixin.enchantments;

import com.eternalreturn.logic.Telekinesis;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** What a mob drops while its drops go to its killer (TelekinesisMobMixin) goes to the killer; only what doesn't fit drops. */
@Mixin(Entity.class)
public abstract class TelekinesisDropMixin {
	@Inject(method = "dropStack(Lnet/minecraft/item/ItemStack;F)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$toCollector(ItemStack stack, float yOffset, CallbackInfoReturnable<ItemEntity> cir) {
		if (!stack.isEmpty() && (Object) this instanceof Telekinesis.Collected collected) {
			PlayerEntity player = collected.eternalreturn$collector();
			if (player != null && Telekinesis.give(player, stack).isEmpty()) {
				cir.setReturnValue(null);
			}
		}
	}
}
