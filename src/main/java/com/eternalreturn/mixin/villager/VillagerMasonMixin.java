package com.eternalreturn.mixin.villager;

import com.eternalreturn.villager.MasonTrades;
import net.minecraft.entity.passive.VillagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Masons' trades (MasonTrades): Nether blocks for masons in the Nether, mud for dripstone elsewhere. */
@Mixin(VillagerEntity.class)
public abstract class VillagerMasonMixin {
	@Inject(method = "fillRecipes", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$netherMason(CallbackInfo ci) {
		VillagerEntity villager = (VillagerEntity) (Object) this;
		if (MasonTrades.netherMason(villager)) {
			MasonTrades.fillNether(villager, villager.getOffers());
			ci.cancel();
		}
	}

	@Inject(method = "fillRecipes", at = @At("TAIL"))
	private void eternalreturn$mud(CallbackInfo ci) {
		MasonTrades.mudForDripstone(((VillagerEntity) (Object) this).getOffers());
	}
}
