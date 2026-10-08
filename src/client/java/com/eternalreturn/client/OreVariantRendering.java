package com.eternalreturn.client;

import com.eternalreturn.worldgen.ores.OreVariants;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;

/**
 * Ore variants draw the host stone with the ore overlay on top (models from tools/ores/build_ores.py); the
 * overlay has transparent pixels, so the blocks render in the cutout layer, like grass blocks.
 */
public final class OreVariantRendering {
	private OreVariantRendering() {
	}

	public static void register() {
		for (OreVariants.Variant variant : OreVariants.variants()) {
			BlockRenderLayerMap.INSTANCE.putBlock(variant.block(), RenderLayer.getCutoutMipped());
		}
	}
}
