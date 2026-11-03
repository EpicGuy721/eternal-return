package com.eternalreturn.mixin.potions;

import com.eternalreturn.items.SubtlePotions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.AreaEffectCloudEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A subtle lingering cloud is invisible (SubtlePotions): its particles are given no opacity (the cloud's particle
 * colour is its potion's, made opaque here; a subtle one stays at alpha 0, which the effect particles draw as nothing).
 */
@Mixin(AreaEffectCloudEntity.class)
public abstract class SubtleCloudMixin {
	@Shadow
	private PotionContentsComponent potionContentsComponent;

	@WrapOperation(method = "updateColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/ColorHelper$Argb;fullAlpha(I)I"))
	private int eternalreturn$invisible(int color, Operation<Integer> original) {
		return SubtlePotions.isSubtle(this.potionContentsComponent) ? 0 : original.call(color);
	}
}
