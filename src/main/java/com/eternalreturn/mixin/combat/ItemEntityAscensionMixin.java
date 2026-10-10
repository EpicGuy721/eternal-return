package com.eternalreturn.mixin.combat;

import com.eternalreturn.logic.NewEnchantments;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Curse of Ascension (NewEnchantments): dropped, the item drifts up. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityAscensionMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void eternalreturn$ascend(CallbackInfo ci) {
		NewEnchantments.ascend((ItemEntity) (Object) this);
	}
}
