package com.eternalreturn.villager;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.fishing.FishingItems;
import com.eternalreturn.mixin.villager.BuyItemFactoryAccessor;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Villagers no longer pay emeralds for goods. The one exception is the fisherman buying raw fish,
 * which makes fishing the way to earn emeralds: vanilla's own fish trades here (with the fish price
 * multiplier), and every fish of the mod's fish table in FishermanTrades.
 */
public final class TradeRules {
	private TradeRules() {
	}

	public static TradeOffers.Factory[] filter(VillagerProfession profession, TradeOffers.Factory[] pool) {
		List<TradeOffers.Factory> kept = new ArrayList<>(pool.length);
		boolean changed = false;
		for (TradeOffers.Factory factory : pool) {
			TradeOffers.Factory allowed = adjust(profession, factory);
			if (allowed != null) {
				kept.add(allowed);
			}
			changed |= allowed != factory;
		}
		return changed ? kept.toArray(TradeOffers.Factory[]::new) : pool;
	}

	/**
	 * The factory as this villager should offer it: forbidden buy trades become null, and the
	 * fisherman's fish trades get the fish price multiplier. The experimental trade rebalance wraps
	 * per-biome variants in TypedWrapperFactory, so those are handled one by one.
	 */
	@Nullable
	private static TradeOffers.Factory adjust(VillagerProfession profession, TradeOffers.Factory factory) {
		EternalReturnConfig.VillagerTweaks cfg = EternalReturnConfig.get().villagers;
		if (factory instanceof TradeOffers.TypedWrapperFactory wrapper) {
			Map<VillagerType, TradeOffers.Factory> variants = new HashMap<>();
			wrapper.typeToFactory().forEach((type, variant) -> {
				TradeOffers.Factory adjusted = adjust(profession, variant);
				if (adjusted != null) {
					variants.put(type, adjusted);
				}
			});
			if (variants.isEmpty()) {
				return null;
			}
			return variants.equals(wrapper.typeToFactory()) ? wrapper : new TradeOffers.TypedWrapperFactory(variants);
		}
		// Vanilla's emerald-paying trades: BuyItemFactory (the player sells N items) and
		// TypeAwareBuyForOneEmeraldFactory (the master fisherman buying boats).
		if (factory instanceof TradeOffers.TypeAwareBuyForOneEmeraldFactory) {
			return cfg.onlyFishermenBuy ? null : factory;
		}
		if (factory instanceof TradeOffers.BuyItemFactory buy) {
			BuyItemFactoryAccessor trade = (BuyItemFactoryAccessor) buy;
			if (profession == VillagerProfession.FISHERMAN && trade.eternalreturn$getStack().itemStack().isIn(FishingItems.RAW_FISH)) {
				return withFishPrice(buy, cfg.fishPriceMultiplier);
			}
			return cfg.onlyFishermenBuy ? null : factory;
		}
		return factory;
	}

	private static TradeOffers.Factory withFishPrice(TradeOffers.BuyItemFactory buy, double multiplier) {
		BuyItemFactoryAccessor trade = (BuyItemFactoryAccessor) buy;
		int emeralds = Math.max(1, (int) Math.round(trade.eternalreturn$getPrice() * multiplier));
		if (emeralds == trade.eternalreturn$getPrice()) {
			return buy;
		}
		return new TradeOffers.BuyItemFactory(trade.eternalreturn$getStack(), trade.eternalreturn$getMaxUses(), trade.eternalreturn$getExperience(), emeralds);
	}

}
