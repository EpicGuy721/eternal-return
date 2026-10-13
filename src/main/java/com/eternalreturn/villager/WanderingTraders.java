package com.eternalreturn.villager;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.items.AsylumPapers;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Wandering traders:
 * <ul>
 * <li>only come to a player near a village (villagers.traderVillageVillagers villagers within
 * villagers.traderVillageRadius blocks; in Eternal Return worlds that means villagers the player has cured or
 * brought), never out in the wild;</li>
 * <li>never sell what the world can't give (item tag eternalreturn:not_from_wandering_traders: allium, moss,
 * small dripleaf, pointed dripstone, rooted dirt, mangrove propagules), picking from the rest instead;</li>
 * <li>always sell asylum papers (AsylumPapers), one for villagers.asylumPapersPrice emeralds.</li>
 * </ul>
 */
public final class WanderingTraders {
	public static final TagKey<Item> NOT_SOLD = TagKey.of(RegistryKeys.ITEM, EternalReturn.id("not_from_wandering_traders"));

	private WanderingTraders() {
	}

	public static boolean nearVillage(PlayerEntity player) {
		EternalReturnConfig.VillagerTweaks cfg = EternalReturnConfig.get().villagers;
		Box around = player.getBoundingBox().expand(cfg.traderVillageRadius);
		return player.getWorld().getEntitiesByClass(VillagerEntity.class, around, villager -> villager.isAlive()).size() >= cfg.traderVillageVillagers;
	}

	/** A random living player near a village, or null. */
	@Nullable
	public static ServerPlayerEntity playerNearVillage(ServerWorld world) {
		List<ServerPlayerEntity> players = world.getPlayers(player -> player.isAlive() && !player.isSpectator() && nearVillage(player));
		return players.isEmpty() ? null : players.get(world.random.nextInt(players.size()));
	}

	/** The trade pool without the trades for items the world can't give. */
	public static TradeOffers.Factory[] filter(WanderingTraderEntity trader, TradeOffers.Factory[] pool, Random random) {
		return Arrays.stream(pool).filter(factory -> {
			TradeOffer offer = factory.create(trader, random);
			return offer == null || !offer.getSellItem().isIn(NOT_SOLD);
		}).toArray(TradeOffers.Factory[]::new);
	}

	public static void addAsylumPapers(WanderingTraderEntity trader) {
		int price = EternalReturnConfig.get().villagers.asylumPapersPrice;
		if (price > 0 && trader.getOffers().stream().noneMatch(offer -> offer.getSellItem().isOf(AsylumPapers.ITEM))) {
			trader.getOffers().add(new TradeOffer(new TradedItem(Items.EMERALD, price), new ItemStack(AsylumPapers.ITEM), 1, 1, 0.05F));
		}
	}
}
