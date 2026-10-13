package com.eternalreturn.villager;

import com.eternalreturn.fishing.FishTable;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;

import java.util.ArrayList;
import java.util.List;

/**
 * The fisherman's fixed trades, on top of the ones he picks from vanilla's pools:
 * <ul>
 * <li>from novice, every bait for a few emeralds (fish.json "baits": chum, glistering melon, magma cream);</li>
 * <li>every fish he buys, from the level of its rarity (common at novice ... legendary at master), all at once
 * rather than a random two. He buys the freshwater fish of his own region only (his villager type: plains,
 * desert, savanna, snow, taiga, jungle or swamp), and every fish from the sea, cherry groves, underground
 * water and the Nether.</li>
 * </ul>
 * Added when he levels up, and topped up whenever a player opens his trades, so fishermen from before this
 * get them too. Trades for items the game no longer has (the old fish and baits) are dropped then.
 */
public final class FishermanTrades {
	private static final float PRICE_MULTIPLIER = 0.05F;

	private FishermanTrades() {
	}

	/** The fixed trades a fisherman of this region and level has. */
	public static List<TradeOffer> offers(String region, int level) {
		List<TradeOffer> offers = new ArrayList<>();
		for (FishTable.BaitOffer bait : FishTable.baits()) {
			Item item = Registries.ITEM.get(bait.item());
			if (item != Items.AIR) {
				offers.add(new TradeOffer(new TradedItem(Items.EMERALD, bait.emeralds()), new ItemStack(item, bait.count()), 16, 1, PRICE_MULTIPLIER));
			}
		}
		for (FishTable.Fish fish : FishTable.fish()) {
			if (fish.rarity().level() <= level && buys(region, fish)) {
				offers.add(new TradeOffer(new TradedItem(FishTable.item(fish), fish.sellCount()), new ItemStack(Items.EMERALD, fish.sellEmeralds()),
						fish.rarity().maxUses(), fish.rarity().experience(), PRICE_MULTIPLIER));
			}
		}
		return offers;
	}

	public static boolean buys(String region, FishTable.Fish fish) {
		return fish.regions() == null || fish.regions().contains(region);
	}

	public static String region(VillagerEntity villager) {
		return Registries.VILLAGER_TYPE.getId(villager.getVillagerData().getType()).getPath();
	}

	/** Gives a fisherman any fixed trade he is missing for his level, and drops trades for items that are gone. */
	public static void topUp(VillagerEntity villager) {
		if (villager.getVillagerData().getProfession() != VillagerProfession.FISHERMAN || villager.getWorld().isClient) {
			return;
		}
		TradeOfferList offers = villager.getOffers();
		offers.removeIf(offer -> offer.getOriginalFirstBuyItem().isEmpty() || offer.getSellItem().isEmpty());
		for (TradeOffer wanted : offers(region(villager), villager.getVillagerData().getLevel())) {
			boolean present = offers.stream().anyMatch(offer -> offer.getOriginalFirstBuyItem().isOf(wanted.getOriginalFirstBuyItem().getItem())
					&& offer.getSellItem().isOf(wanted.getSellItem().getItem()) && offer.getSecondBuyItem().isEmpty());
			if (!present) {
				offers.add(wanted);
			}
		}
	}
}
