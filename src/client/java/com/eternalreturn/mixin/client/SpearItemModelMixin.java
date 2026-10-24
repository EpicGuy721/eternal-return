package com.eternalreturn.mixin.client;

import com.eternalreturn.client.SpearClient;
import com.eternalreturn.items.Spears;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A held spear is drawn from its in-hand model; in inventories, on the ground and in frames from its icon (SpearClient). */
@Mixin(ItemRenderer.class)
public abstract class SpearItemModelMixin {
	@ModifyVariable(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
			at = @At("HEAD"), argsOnly = true)
	private BakedModel eternalreturn$spearInHand(BakedModel model, @Local(argsOnly = true) ItemStack stack,
			@Local(argsOnly = true) ModelTransformationMode mode) {
		if (stack.getItem() instanceof Spears.Spear spear && mode != ModelTransformationMode.GUI && mode != ModelTransformationMode.GROUND
				&& mode != ModelTransformationMode.FIXED) {
			return SpearClient.inHand(spear);
		}
		return model;
	}
}
