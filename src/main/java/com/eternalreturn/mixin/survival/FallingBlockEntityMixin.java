package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.SnowFall;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Falling snow that lands on a snow layer adds to the pile instead of breaking (SnowFall.land); the players who see it
 * fall are shown the grown pile as it goes (SnowFall.showLanding), as vanilla shows a landed block.
 */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityMixin {
	@Shadow
	private BlockState block;

	@Inject(method = "tick", cancellable = true, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/block/BlockState;canReplace(Lnet/minecraft/item/ItemPlacementContext;)Z"))
	private void eternalreturn$pileSnow(CallbackInfo ci, @Local(ordinal = 0) BlockPos pos, @Local(ordinal = 0) BlockState landingOn) {
		FallingBlockEntity self = (FallingBlockEntity) (Object) this;
		if (SnowFall.land(self.getWorld(), pos, this.block, landingOn)) {
			SnowFall.showLanding(self, pos);
			self.discard();
			ci.cancel();
		}
	}
}
