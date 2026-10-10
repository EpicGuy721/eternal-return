package com.eternalreturn.mixin.frames;

import com.eternalreturn.blocks.Frames;
import net.minecraft.entity.decoration.GlowItemFrameEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A dyed glow item frame drops a dyed glow item frame (Frames). */
@Mixin(GlowItemFrameEntity.class)
public abstract class GlowItemFrameEntityMixin {
	@Inject(method = "getAsItemStack", at = @At("RETURN"), cancellable = true)
	private void eternalreturn$coloredItem(CallbackInfoReturnable<ItemStack> cir) {
		cir.setReturnValue(Frames.colored((ItemFrameEntity) (Object) this, cir.getReturnValue()));
	}
}
