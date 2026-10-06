package com.eternalreturn.mixin.villager;

import com.eternalreturn.villager.TradeRules;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradeOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin {
	/**
	 * Filters the trade pool right before the villager picks from it, so it covers both trade tables
	 * (normal and the experimental rebalance) and buy trades other mods register. The villager then
	 * picks its trades from what is left, so it still gets a full set where the pool allows.
	 */
	@WrapOperation(method = "fillRecipes", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/passive/VillagerEntity;fillRecipesFromPool(Lnet/minecraft/village/TradeOfferList;[Lnet/minecraft/village/TradeOffers$Factory;I)V"))
	private void eternalreturn$dropBuyTrades(VillagerEntity villager, TradeOfferList offers, TradeOffers.Factory[] pool, int count, Operation<Void> original) {
		original.call(villager, offers, TradeRules.filter(villager.getVillagerData().getProfession(), pool), count);
	}
}
