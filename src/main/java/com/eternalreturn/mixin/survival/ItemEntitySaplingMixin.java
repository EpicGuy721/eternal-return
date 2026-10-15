package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.Growing;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A sapling on the ground plants itself (Growing). */
@Mixin(ItemEntity.class)
public abstract class ItemEntitySaplingMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void eternalreturn$plant(CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;
		if (!self.isRemoved() && self.age % 20 == 0) {
			Growing.saplingTick(self);
		}
	}
}
