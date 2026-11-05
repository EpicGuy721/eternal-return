package com.eternalreturn.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The eating swing (SwordBlockingClientMixin) shown in first person as 1.7.10 drew it (and the Old Animations mod brings
 * back): vanilla draws a hand that's eating or drinking with the eating pose and bob only, ignoring any swing; here the
 * food stays in the eating pose, bobbing, and the hand swings with it: the swing's turn is added after the equip offset,
 * as in an ordinary swing, but not the swing's shift (1.7.10 left it out while an item was in use; it would carry the
 * food off to the side). renderFirstPersonItem's floats are tickDelta, pitch, swingProgress, equipProgress: the swing is
 * the third (ordinal 2).
 */
@Mixin(HeldItemRenderer.class)
public abstract class EatingSwingRenderMixin {
	@Unique
	private boolean eternalreturn$eating;

	@Shadow
	protected abstract void applySwingOffset(MatrixStack matrices, Arm arm, float swingProgress);

	@WrapOperation(method = "renderFirstPersonItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEatOrDrinkTransformation(Lnet/minecraft/client/util/math/MatrixStack;FLnet/minecraft/util/Arm;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/PlayerEntity;)V"))
	private void eternalreturn$markEating(HeldItemRenderer self, MatrixStack matrices, float tickDelta, Arm arm, ItemStack stack, PlayerEntity player,
			Operation<Void> original) {
		original.call(self, matrices, tickDelta, arm, stack, player);
		this.eternalreturn$eating = true;
	}

	@WrapOperation(method = "renderFirstPersonItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V"))
	private void eternalreturn$swingWhileEating(HeldItemRenderer self, MatrixStack matrices, Arm arm, float equipProgress, Operation<Void> original,
			@Local(argsOnly = true, ordinal = 2) float swingProgress) {
		boolean eating = this.eternalreturn$eating;
		this.eternalreturn$eating = false;
		original.call(self, matrices, arm, equipProgress);
		if (eating && swingProgress > 0.0F) {
			this.applySwingOffset(matrices, arm, swingProgress);
		}
	}
}
