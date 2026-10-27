package com.eternalreturn.mixin.client;

import com.eternalreturn.blocks.Frames;
import com.eternalreturn.client.FrameModels;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.entity.ItemFrameEntityRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ItemFrameEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Glass and dyed item frames (Frames): a glass frame draws its own model (with see-through parts), a dyed frame
 * its dyed model (FrameModels), a glowing glass frame lights up like a glow item frame, and the dye colours the
 * faces marked for it (the back).
 */
@Mixin(ItemFrameEntityRenderer.class)
public abstract class ItemFrameEntityRendererMixin {
	@WrapOperation(method = "render(Lnet/minecraft/entity/decoration/ItemFrameEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/BakedModelManager;getModel(Lnet/minecraft/client/util/ModelIdentifier;)Lnet/minecraft/client/render/model/BakedModel;"))
	private BakedModel eternalreturn$frameModel(BakedModelManager models, ModelIdentifier id, Operation<BakedModel> original, @Local(argsOnly = true) ItemFrameEntity frame) {
		boolean dyed = ((Frames.Dyed) frame).eternalreturn$color() >= 0;
		if (frame instanceof Frames.GlassItemFrameEntity glass) {
			return FrameModels.glassFrame(models, glass.isGlowingFrame(), dyed);
		}
		return dyed ? FrameModels.dyedFrame(models, frame.getType() == EntityType.GLOW_ITEM_FRAME, id.variant().contains("map=true")) : original.call(models, id);
	}

	@WrapOperation(method = "render(Lnet/minecraft/entity/decoration/ItemFrameEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TexturedRenderLayers;getEntitySolid()Lnet/minecraft/client/render/RenderLayer;"))
	private RenderLayer eternalreturn$glassLayer(Operation<RenderLayer> original, @Local(argsOnly = true) ItemFrameEntity frame) {
		return frame instanceof Frames.GlassItemFrameEntity ? TexturedRenderLayers.getEntityCutout() : original.call();
	}

	@WrapOperation(method = "render(Lnet/minecraft/entity/decoration/ItemFrameEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/BlockModelRenderer;render(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/block/BlockState;Lnet/minecraft/client/render/model/BakedModel;FFFII)V"))
	private void eternalreturn$tint(BlockModelRenderer renderer, MatrixStack.Entry entry, VertexConsumer consumer, BlockState state, BakedModel model,
			float red, float green, float blue, int light, int overlay, Operation<Void> original, @Local(argsOnly = true) ItemFrameEntity frame) {
		int color = ((Frames.Dyed) frame).eternalreturn$color();
		if (color >= 0) {
			red = (color >> 16 & 255) / 255.0F;
			green = (color >> 8 & 255) / 255.0F;
			blue = (color & 255) / 255.0F;
		}
		original.call(renderer, entry, consumer, state, model, red, green, blue, light, overlay);
	}

	/** A glowing glass frame is lit like a glow item frame, wherever the renderer asks. */
	@WrapOperation(method = {"render(Lnet/minecraft/entity/decoration/ItemFrameEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			"getBlockLight(Lnet/minecraft/entity/decoration/ItemFrameEntity;Lnet/minecraft/util/math/BlockPos;)I", "getLight"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/decoration/ItemFrameEntity;getType()Lnet/minecraft/entity/EntityType;"))
	private EntityType<?> eternalreturn$glowingGlass(ItemFrameEntity frame, Operation<EntityType<?>> original) {
		return frame instanceof Frames.GlassItemFrameEntity glass && glass.isGlowingFrame() ? EntityType.GLOW_ITEM_FRAME : original.call(frame);
	}
}
