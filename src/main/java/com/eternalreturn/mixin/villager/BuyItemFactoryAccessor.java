package com.eternalreturn.mixin.villager;

import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TradeOffers.BuyItemFactory.class)
public interface BuyItemFactoryAccessor {
	/** What the villager takes in exchange for emeralds. */
	@Accessor("stack")
	TradedItem eternalreturn$getStack();

	@Accessor("maxUses")
	int eternalreturn$getMaxUses();

	@Accessor("experience")
	int eternalreturn$getExperience();

	/** Emeralds paid. */
	@Accessor("price")
	int eternalreturn$getPrice();
}
