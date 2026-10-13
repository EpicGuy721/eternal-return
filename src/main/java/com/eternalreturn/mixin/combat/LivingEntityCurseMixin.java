package com.eternalreturn.mixin.combat;

import com.eternalreturn.logic.Curses;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Curses of Visibility and Blindness on worn armor (Curses). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityCurseMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void eternalreturn$wornCurses(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.getWorld().isClient && self.age % 20 == 0 && self.isAlive()) {
			Curses.tickWorn(self);
		}
	}
}
