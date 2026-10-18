package com.eternalreturn.mixin.survival;

import com.eternalreturn.config.EternalReturnConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** What a player drops on death lies there until picked up: it never despawns (survival.deathDropsStay). */
@Mixin(PlayerInventory.class)
public abstract class DeathDropsMixin {
	@WrapOperation(method = "dropAll", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/player/PlayerEntity;dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;"))
	private ItemEntity eternalreturn$stays(PlayerEntity player, ItemStack stack, boolean throwRandomly, boolean retainOwnership, Operation<ItemEntity> original) {
		ItemEntity item = original.call(player, stack, throwRandomly, retainOwnership);
		if (item != null && EternalReturnConfig.get().survival.deathDropsStay) {
			item.setNeverDespawn();
		}
		return item;
	}
}
