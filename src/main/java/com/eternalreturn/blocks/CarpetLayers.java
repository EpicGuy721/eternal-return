package com.eternalreturn.blocks;

import net.minecraft.block.Block;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.shape.VoxelShape;

/**
 * Carpets stack like snow: placing a carpet on the same carpet adds a layer, up to 16 (a full block).
 * Every carpet block (each color of wool carpet, and moss carpet) gets a "layers" property, 1 by default,
 * so a single carpet is exactly vanilla's. Its shape is 1/16 of a block per layer, and it drops one
 * carpet per layer (CarpetBlockMixin; models, blockstates and loot tables from tools/blocks/build_blocks.py).
 */
public final class CarpetLayers {
	public static final int MAX_LAYERS = 16;
	public static final IntProperty LAYERS = IntProperty.of("layers", 1, MAX_LAYERS);
	private static final VoxelShape[] SHAPES = new VoxelShape[MAX_LAYERS + 1];

	static {
		for (int layers = 1; layers <= MAX_LAYERS; layers++) {
			SHAPES[layers] = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, layers, 16.0);
		}
	}

	private CarpetLayers() {
	}

	public static VoxelShape shape(int layers) {
		return SHAPES[Math.max(1, Math.min(MAX_LAYERS, layers))];
	}
}
