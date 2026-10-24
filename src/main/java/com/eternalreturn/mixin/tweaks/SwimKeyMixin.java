package com.eternalreturn.mixin.tweaks;

import com.eternalreturn.logic.LavaSwimming;
import com.eternalreturn.logic.SwimKey;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.FluidTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player swims (or crawls) by the swim key (SwimKey). With the key off in the config, swimming is vanilla's, and
 * a lava swimmer (LavaSwimming) sprint-swims in lava as in water.
 */
@Mixin(Entity.class)
public abstract class SwimKeyMixin {
	@Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$swimKey(CallbackInfo ci) {
		if (!((Object) this instanceof PlayerEntity player)) {
			return;
		}
		if (SwimKey.enabled()) {
			player.setSwimming(SwimKey.swimming(player));
			ci.cancel();
		} else if (player.isInLava() && LavaSwimming.swims(player)) {
			player.setSwimming(player.isSprinting() && !player.hasVehicle() && (player.isSwimming() || player.isSubmergedIn(FluidTags.LAVA)));
			ci.cancel();
		}
	}
}
