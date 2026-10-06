package com.eternalreturn.mixin;

import com.eternalreturn.logic.EnchantLevels;
import com.eternalreturn.logic.InherentEnchantments;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	/** Catch-all: anything in a player's inventory gets its material's inherent enchantments. */
	@Inject(method = "inventoryTick", at = @At("HEAD"))
	private void eternalreturn$applyInherent(World world, Entity entity, int slot, boolean selected, CallbackInfo ci) {
		if (!world.isClient) {
			InherentEnchantments.apply((ItemStack) (Object) this, world.getRegistryManager());
		}
	}

	/** Inherent enchantments and curses do not stop an item from going into an enchanting table. */
	@ModifyReturnValue(method = "isEnchantable", at = @At("RETURN"))
	private boolean eternalreturn$inherentStillEnchantable(boolean original) {
		if (original) {
			return true;
		}
		ItemStack self = (ItemStack) (Object) this;
		return self.getItem().isEnchantable(self) && InherentEnchantments.hasNoPurchasedEnchantments(self);
	}

	/** Curse of Invisibility: hide the item's enchantment list entirely. */
	@Inject(method = "appendTooltip", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$concealEnchantments(ComponentType<?> componentType, Item.TooltipContext context,
			Consumer<Text> textConsumer, TooltipType type, CallbackInfo ci) {
		if (componentType == DataComponentTypes.ENCHANTMENTS && EnchantLevels.isConcealed((ItemStack) (Object) this)) {
			ci.cancel();
		}
	}
}
