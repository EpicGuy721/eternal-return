package com.eternalreturn.client;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.blocks.Frames;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.ItemFrameEntityRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

/**
 * Client side of glass and dyed item frames (Frames): the glass frame model, renderers, and dyed item icons.
 * Dye shows only where a model face is marked tintable: the frame models (assets/minecraft/models/block/
 * template_item_frame*.json, vanilla's with the back panel marked) colour the inside only; glass frames
 * colour their glass. Item icons have the border and inside as two layers.
 */
public final class FrameModels {
	public static final Identifier GLASS_FRAME = EternalReturn.id("block/glass_item_frame");

	private FrameModels() {
	}

	public static void register() {
		ModelLoadingPlugin.register(context -> context.addModels(GLASS_FRAME));
		EntityRendererRegistry.register(Frames.GLASS_ITEM_FRAME, ItemFrameEntityRenderer::new);
		EntityRendererRegistry.register(Frames.GLOWING_GLASS_ITEM_FRAME, ItemFrameEntityRenderer::new);
		// Vanilla frames: layer 1 is the inside (the border is layer 0 and keeps its colour). Glass frames: the glass itself.
		ColorProviderRegistry.ITEM.register((stack, tintIndex) -> tintIndex == 1 ? DyedColorComponent.getColor(stack, -1) : -1, Items.ITEM_FRAME, Items.GLOW_ITEM_FRAME);
		ColorProviderRegistry.ITEM.register((stack, tintIndex) -> tintIndex == 0 ? DyedColorComponent.getColor(stack, -1) : -1,
				Frames.GLASS_ITEM_FRAME_ITEM, Frames.GLOWING_GLASS_ITEM_FRAME_ITEM);
	}

	public static BakedModel glassFrame(BakedModelManager models) {
		return models.getModel(GLASS_FRAME);
	}
}
