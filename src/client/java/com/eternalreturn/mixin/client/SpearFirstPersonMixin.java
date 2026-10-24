package com.eternalreturn.mixin.client;

import com.eternalreturn.items.Spears;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Charging with a spear (Spears) in first person: the spear stays up (no dip as the charge starts) and, as it is
 * levelled, eases forward and down a little and points further ahead, as the later versions' does.
 */
@Mixin(HeldItemRenderer.class)
public abstract class SpearFirstPersonMixin {
	@Unique
	private static boolean eternalreturn$charging(AbstractClientPlayerEntity player, Hand hand, ItemStack item) {
		return item.getItem() instanceof Spears.Spear && player.isUsingItem() && player.getActiveHand() == hand && player.getItemUseTimeLeft() > 0;
	}

	@ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true, ordinal = 3)
	private float eternalreturn$noDip(float equipProgress, @Local(argsOnly = true) AbstractClientPlayerEntity player, @Local(argsOnly = true) Hand hand,
			@Local(argsOnly = true) ItemStack item) {
		return eternalreturn$charging(player, hand, item) ? 0.0F : equipProgress;
	}

	@Inject(method = "renderFirstPersonItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
	private void eternalreturn$levelled(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item,
			float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (!eternalreturn$charging(player, hand, item)) {
			return;
		}
		Spears.Spear spear = (Spears.Spear) item.getItem();
		float held = item.getMaxUseTime(player) - player.getItemUseTimeLeft() + tickDelta;
		float level = MathHelper.clamp(held / spear.tier().delay(), 0.0F, 1.0F);
		level = level * level * (3.0F - 2.0F * level);
		matrices.translate(0.0F, -0.1F * level, -0.38F * level);
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-12.0F * level));
	}
}
