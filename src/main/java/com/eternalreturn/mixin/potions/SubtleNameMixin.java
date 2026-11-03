package com.eternalreturn.mixin.potions;

import com.eternalreturn.items.SubtlePotions;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.item.TippedArrowItem;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A subtle potion or arrow is named after the potion it was made from: "Subtle Potion of Swiftness" (SubtlePotions). */
@Mixin(Item.class)
public abstract class SubtleNameMixin {
	@Inject(method = "getName(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/text/Text;", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$subtleName(ItemStack stack, CallbackInfoReturnable<Text> cir) {
		Item self = (Item) (Object) this;
		if (self instanceof PotionItem || self instanceof TippedArrowItem) {
			PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
			if (SubtlePotions.isSubtle(contents)) {
				cir.setReturnValue(SubtlePotions.name(self, contents));
			}
		}
	}
}
