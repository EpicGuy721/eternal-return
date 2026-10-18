package com.eternalreturn.villager;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Masons: one who reaches a new level in the Nether sells Nether blocks instead of vanilla's (nether bricks,
 * blackstone, basalt and the rest, two trades a level); in the Overworld, mud takes the place of dripstone blocks.
 */
public final class MasonTrades {
	private record Sale(Item item, int count, int emeralds) {
	}

	private static final List<List<Sale>> NETHER = List.of(
			List.of(new Sale(Items.NETHER_BRICKS, 10, 1), new Sale(Items.BLACKSTONE, 8, 1), new Sale(Items.NETHERRACK, 16, 1)),
			List.of(new Sale(Items.BASALT, 8, 1), new Sale(Items.POLISHED_BLACKSTONE_BRICKS, 4, 1), new Sale(Items.CRACKED_NETHER_BRICKS, 4, 1)),
			List.of(new Sale(Items.RED_NETHER_BRICKS, 4, 1), new Sale(Items.POLISHED_BASALT, 4, 1), new Sale(Items.SMOOTH_BASALT, 4, 1),
					new Sale(Items.CHISELED_NETHER_BRICKS, 2, 1)),
			List.of(new Sale(Items.GILDED_BLACKSTONE, 1, 2), new Sale(Items.CRYING_OBSIDIAN, 1, 2), new Sale(Items.CHISELED_POLISHED_BLACKSTONE, 2, 1)),
			List.of(new Sale(Items.QUARTZ_BLOCK, 1, 1), new Sale(Items.QUARTZ_PILLAR, 1, 1), new Sale(Items.MAGMA_BLOCK, 2, 1)));
	private static final int[] EXPERIENCE = {2, 5, 10, 15, 30};

	private MasonTrades() {
	}

	public static boolean netherMason(VillagerEntity villager) {
		return villager.getVillagerData().getProfession() == VillagerProfession.MASON && villager.getWorld().getRegistryKey() == World.NETHER;
	}

	/** A Nether mason's trades for its new level: two of that level's Nether block sales it hasn't got. */
	public static void fillNether(VillagerEntity villager, TradeOfferList offers) {
		int level = Math.max(1, Math.min(5, villager.getVillagerData().getLevel()));
		List<Sale> left = new ArrayList<>(NETHER.get(level - 1));
		left.removeIf(sale -> offers.stream().anyMatch(offer -> offer.getSellItem().isOf(sale.item())));
		for (int i = 0; i < 2 && !left.isEmpty(); i++) {
			Sale sale = left.remove(villager.getRandom().nextInt(left.size()));
			offers.add(new TradeOffer(new TradedItem(Items.EMERALD, sale.emeralds()), new ItemStack(sale.item(), sale.count()), 16,
					EXPERIENCE[level - 1], 0.05F));
		}
	}

	/** Mud, not dripstone: any dripstone block sale becomes the same deal for mud. */
	public static void mudForDripstone(TradeOfferList offers) {
		for (int i = 0; i < offers.size(); i++) {
			TradeOffer offer = offers.get(i);
			if (offer.getSellItem().isOf(Items.DRIPSTONE_BLOCK)) {
				offers.set(i, new TradeOffer(offer.getFirstBuyItem(), offer.getSecondBuyItem(), new ItemStack(Items.MUD, offer.getSellItem().getCount()),
						offer.getMaxUses(), offer.getMerchantExperience(), offer.getPriceMultiplier()));
			}
		}
	}
}
