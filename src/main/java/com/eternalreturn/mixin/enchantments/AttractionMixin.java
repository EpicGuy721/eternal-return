package com.eternalreturn.mixin.enchantments;

import com.eternalreturn.logic.Attraction;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Attraction armor picks up items from further away (Attraction). */
@Mixin(PlayerEntity.class)
public abstract class AttractionMixin {
	@Inject(method = "tickMovement", at = @At("TAIL"))
	private void eternalreturn$attraction(CallbackInfo ci) {
		Attraction.pickUp((PlayerEntity) (Object) this);
	}
}
