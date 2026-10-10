package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.AnimalHunger;
import net.minecraft.entity.passive.SheepEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sheep already graze on their own (vanilla's eat-grass goal); each time counts as a meal (AnimalHunger). */
@Mixin(SheepEntity.class)
public abstract class SheepGrazingMixin {
	@Inject(method = "onEatingGrass", at = @At("HEAD"))
	private void eternalreturn$grazed(CallbackInfo ci) {
		AnimalHunger.fed((SheepEntity) (Object) this);
	}
}
