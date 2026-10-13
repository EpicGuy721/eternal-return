package com.eternalreturn.mixin.villager;

import com.eternalreturn.villager.WanderingTraders;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradeOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** What a wandering trader sells (WanderingTraders). */
@Mixin(WanderingTraderEntity.class)
public abstract class WanderingTraderEntityMixin {
	@WrapOperation(method = {"fillRecipes", "fillRebalancedRecipes"}, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/passive/WanderingTraderEntity;fillRecipesFromPool(Lnet/minecraft/village/TradeOfferList;[Lnet/minecraft/village/TradeOffers$Factory;I)V"))
	private void eternalreturn$onlyWhatTheWorldHas(WanderingTraderEntity trader, TradeOfferList offers, TradeOffers.Factory[] pool, int count,
			Operation<Void> original) {
		original.call(trader, offers, WanderingTraders.filter(trader, pool, trader.getRandom()), count);
	}

	/** At every way out of fillRecipes: the rebalanced trade table (an experiment) leaves by its own return. */
	@Inject(method = "fillRecipes", at = @At("RETURN"))
	private void eternalreturn$asylumPapers(CallbackInfo ci) {
		WanderingTraders.addAsylumPapers((WanderingTraderEntity) (Object) this);
	}
}
