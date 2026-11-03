package com.eternalreturn.mixin.potions;

import com.eternalreturn.items.SubtlePotions;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.projectile.ArrowEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A subtle tipped arrow flies as a plain one, with no trail of effect particles (SubtlePotions). */
@Mixin(ArrowEntity.class)
public abstract class SubtleArrowMixin {
	@Shadow
	@Final
	private static TrackedData<Integer> COLOR;

	@Shadow
	private PotionContentsComponent getPotionContents() {
		throw new AssertionError();
	}

	@Inject(method = "initColor", at = @At("TAIL"))
	private void eternalreturn$noTrail(CallbackInfo ci) {
		if (SubtlePotions.isSubtle(this.getPotionContents())) {
			((ArrowEntity) (Object) this).getDataTracker().set(COLOR, -1);
		}
	}
}
