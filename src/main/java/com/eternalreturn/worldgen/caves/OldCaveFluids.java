package com.eternalreturn.worldgen.caves;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.AquiferSampler;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import org.jetbrains.annotations.Nullable;

/**
 * What the old caves (worldgen.caves.oldCaves) fill with in Eternal Return worlds. The preset turns
 * aquifers off (they walled sinkholes and deep sea floors with stone ledges and air pockets), and without
 * them every carver would fill whatever it carves below sea level with water. Instead, as the cave
 * engine does: air, and water below sea level only in columns that have water over their ground (under
 * the sea, a lake or a river), so a ravine opening into the sea floor stays full. Lava below the carver's
 * lava level is the carver's own doing and unchanged.
 */
public final class OldCaveFluids {
	private OldCaveFluids() {
	}

	/** The fill for this chunk's old carvers, or null to leave the world's own aquifer (aquifers on, or not an Eternal Return world). */
	@Nullable
	public static AquiferSampler forChunk(@Nullable NoiseChunkGenerator generator, Chunk chunk) {
		if (generator == null || generator.getSettings().value().aquifers()) {
			return null;
		}
		int seaLevel = generator.getSeaLevel();
		BlockState water = Blocks.WATER.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();
		return new AquiferSampler() {
			@Nullable
			@Override
			public BlockState apply(DensityFunction.NoisePos pos, double density) {
				if (density > 0.0) {
					return null;
				}
				if (pos.blockY() < seaLevel) {
					int x = pos.blockX() & 15;
					int z = pos.blockZ() & 15;
					if (chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG, x, z) > chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR_WG, x, z)) {
						return water;
					}
				}
				return air;
			}

			@Override
			public boolean needsFluidTick() {
				return false;
			}
		};
	}
}
