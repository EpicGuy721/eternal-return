package com.eternalreturn.mixin.redstone;

import com.eternalreturn.blocks.Minecarts;
import net.minecraft.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Players are sent a minecart's position every tick, not every 3, so a fast one (Minecarts) moves smoothly. */
@Mixin(EntityType.class)
public abstract class MinecartTrackingMixin {
	@Inject(method = "getTrackTickInterval", at = @At("RETURN"), cancellable = true)
	private void eternalreturn$everyTick(CallbackInfoReturnable<Integer> cir) {
		if (Minecarts.isMinecart((EntityType<?>) (Object) this)) {
			cir.setReturnValue(1);
		}
	}
}
