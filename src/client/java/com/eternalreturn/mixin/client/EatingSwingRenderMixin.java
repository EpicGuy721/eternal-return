package com.eternalreturn.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The eating swing (SwordBlockingClientMixin) shown in first person: vanilla draws a hand that's eating or drinking
 * held up to the mouth, ignoring any swing. While it swings (holding attack on a block keeps it swinging), the hand is
 * drawn exactly as in an ordinary block-breaking swing, from where the hand is normally held, with only the eating
 * jiggle kept on top; when the swinging stops it goes back to the mouth. (Swinging from the mouth instead, the swing's
 * moves land on the eating pose's turned axes, or its sideways shift carries the food off the screen.)
 * renderFirstPersonItem's floats are tickDelta, pitch, swingProgress, equipProgress: the swing is the third (ordinal 2).
 */
@Mixin(HeldItemRenderer.class)
public abstract class EatingSwingRenderMixin {
	@Unique
	private boolean eternalreturn$swingingWhileEating;

	@Shadow
	protected abstract void applySwingOffset(MatrixStack matrices, Arm arm, float swingProgress);

	@WrapOperation(method = "renderFirstPersonItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEatOrDrinkTransformation(Lnet/minecraft/client/util/math/MatrixStack;FLnet/minecraft/util/Arm;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/PlayerEntity;)V"))
	private void eternalreturn$swingInsteadOfEatPose(HeldItemRenderer self, MatrixStack matrices, float tickDelta, Arm arm, ItemStack stack, PlayerEntity player,
			Operation<Void> original, @Local(argsOnly = true, ordinal = 2) float swingProgress) {
		this.eternalreturn$swingingWhileEating = swingProgress > 0.0F;
		if (!this.eternalreturn$swingingWhileEating) {
			original.call(self, matrices, tickDelta, arm, stack, player);
			return;
		}
		// The eating jiggle, as vanilla's eating pose has it.
		float left = player.getItemUseTimeLeft() - tickDelta + 1.0F;
		if (left / stack.getMaxUseTime(player) < 0.8F) {
			matrices.translate(0.0F, MathHelper.abs(MathHelper.cos(left / 4.0F * (float) Math.PI) * 0.1F), 0.0F);
		}
		// Vanilla's swing shift, before the equip offset.
		int side = arm == Arm.RIGHT ? 1 : -1;
		float root = MathHelper.sqrt(swingProgress);
		matrices.translate(side * -0.4F * MathHelper.sin(root * (float) Math.PI), 0.2F * MathHelper.sin(root * (float) (Math.PI * 2)),
				-0.2F * MathHelper.sin(swingProgress * (float) Math.PI));
	}

	@WrapOperation(method = "renderFirstPersonItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V"))
	private void eternalreturn$swingWhileEating(HeldItemRenderer self, MatrixStack matrices, Arm arm, float equipProgress, Operation<Void> original,
			@Local(argsOnly = true, ordinal = 2) float swingProgress) {
		boolean swinging = this.eternalreturn$swingingWhileEating;
		this.eternalreturn$swingingWhileEating = false;
		original.call(self, matrices, arm, equipProgress);
		if (swinging) {
			this.applySwingOffset(matrices, arm, swingProgress);
		}
	}
}
