package com.eternalreturn.mixin.combat;

import com.eternalreturn.logic.NewEnchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Launch (NewEnchantments) on a player's hit, after vanilla's knockback, which it replaces. */
@Mixin(PlayerEntity.class)
public abstract class PlayerAttackMixin {
	@Inject(method = "attack", at = @At("TAIL"))
	private void eternalreturn$launch(Entity target, CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (!self.getWorld().isClient) {
			NewEnchantments.launch(self, target);
		}
	}
}
