package com.eternalreturn.client;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.worldgen.stone.HardenedBlocks;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;

/**
 * Hardened blocks look exactly like their base block: their block states and item models are the base
 * block's own model (HardenedBlocks.Definition.model), so no model files are needed per block.
 */
public final class HardenedBlockModels {
	private HardenedBlockModels() {
	}

	public static void register() {
		ModelLoadingPlugin.register(plugin -> {
			for (HardenedBlocks.Definition definition : HardenedBlocks.DEFINITIONS) {
				Block block = HardenedBlocks.get(definition.name());
				plugin.registerBlockStateResolver(block, context -> {
					for (BlockState state : block.getStateManager().getStates()) {
						context.setModel(state, context.getOrLoadModel(definition.model()));
					}
				});
			}
			plugin.resolveModel().register(context -> {
				Identifier id = context.id();
				if (!id.getNamespace().equals(EternalReturn.MOD_ID) || !id.getPath().startsWith("item/")) {
					return null;
				}
				for (HardenedBlocks.Definition definition : HardenedBlocks.DEFINITIONS) {
					if (id.getPath().equals("item/" + definition.name())) {
						return context.getOrLoadModel(definition.model());
					}
				}
				return null;
			});
		});
	}
}
