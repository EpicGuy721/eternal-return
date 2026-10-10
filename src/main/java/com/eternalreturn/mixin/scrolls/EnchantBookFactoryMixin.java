package com.eternalreturn.mixin.scrolls;

import com.eternalreturn.scrolls.Scrolls;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Librarians (both trade tables) sell a scroll of the enchantment instead of an enchanted book (Scrolls),
 * for emeralds and a piece of paper. The price is what a level I book of it would cost (double for
 * treasure enchantments), since a scroll teaches the level I book.
 */
@Mixin(TradeOffers.EnchantBookFactory.class)
public abstract class EnchantBookFactoryMixin {
	@Inject(method = "create", at = @At("RETURN"), cancellable = true)
	private void eternalreturn$sellScroll(Entity entity, Random random, CallbackInfoReturnable<TradeOffer> cir) {
		TradeOffer book = cir.getReturnValue();
		ItemEnchantmentsComponent stored = book == null ? null : book.getSellItem().get(DataComponentTypes.STORED_ENCHANTMENTS);
		if (stored == null || stored.isEmpty()) {
			return;
		}
		RegistryEntry<Enchantment> enchantment = stored.getEnchantments().iterator().next();
		int price = 2 + random.nextInt(15) + 3;
		if (enchantment.isIn(EnchantmentTags.DOUBLE_TRADE_PRICE)) {
			price *= 2;
		}
		cir.setReturnValue(new TradeOffer(new TradedItem(Items.EMERALD, Math.min(price, 64)), Optional.of(new TradedItem(Items.PAPER)),
				Scrolls.of(enchantment), book.getMaxUses(), book.getMerchantExperience(), book.getPriceMultiplier()));
	}
}
