package com.eternalreturn.scrolls;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** A scroll of one enchantment (Scrolls), named after it: "Scroll of Sharpness". */
public class ScrollItem extends Item {
	public ScrollItem(Settings settings) {
		super(settings);
	}

	@Override
	public Text getName(ItemStack stack) {
		RegistryEntry<Enchantment> enchantment = Scrolls.enchantment(stack);
		return enchantment == null ? super.getName(stack) : Text.translatable("item.eternalreturn.scroll.of", enchantment.value().description());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.eternalreturn.scroll.hint").formatted(Formatting.GRAY));
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return Scrolls.enchantment(stack) != null;
	}
}
