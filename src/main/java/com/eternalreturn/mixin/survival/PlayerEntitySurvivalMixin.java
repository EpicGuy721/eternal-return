package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.ArmorWeight;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Armor weight (ArmorWeight): the speed modifier follows the armor worn, checked every tick on the server. */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntitySurvivalMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void eternalreturn$armorWeight(CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (!self.getWorld().isClient) {
			ArmorWeight.update(self);
		}
	}
}
