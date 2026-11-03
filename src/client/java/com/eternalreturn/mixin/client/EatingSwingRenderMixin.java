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
 * with the eating bob only, ignoring any swing, so the arm swings here on top of the bob, as 1.7.10 drew it. The eat
 * transformation marks the hand; the equip offset that follows it adds the swing (the same moves as an ordinary
 * swing: a shift before the equip offset, the swing's turn after it). renderFirstPersonItem's floats are tickDelta,
 * pitch, swingProgress, equipProgress: the swing is the third (ordinal 2).
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
		if (!eating || swingProgress <= 0.0F) {
			original.call(self, matrices, arm, equipProgress);
			return;
		}
		int side = arm == Arm.RIGHT ? 1 : -1;
		float root = MathHelper.sqrt(swingProgress);
		matrices.translate(side * -0.4F * MathHelper.sin(root * (float) Math.PI), 0.2F * MathHelper.sin(root * (float) (Math.PI * 2)),
				-0.2F * MathHelper.sin(swingProgress * (float) Math.PI));
		original.call(self, matrices, arm, equipProgress);
		this.applySwingOffset(matrices, arm, swingProgress);
	}
}
