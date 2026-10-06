package com.eternalreturn.worldgen.carver;

import com.mojang.serialization.Codec;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.carver.Carver;
import net.minecraft.world.gen.carver.CarverConfig;
import net.minecraft.world.gen.carver.CarverContext;
import net.minecraft.world.gen.carver.CarvingMask;
import net.minecraft.world.gen.chunk.AquiferSampler;

import java.util.function.Function;

/**
 * A deliberately trivial carver: one straight 3x3 tunnel along the x axis, centred on z = TUNNEL_Z
 * at the config's y, cut through every chunk that row passes through. It exists to check that
 * carvers attached through Fabric's biome modifications run under a given world generator.
 * It carves straight to cave air (no aquifer water or barriers) so the result is exact.
 */
public class DebugTunnelCarver extends Carver<CarverConfig> {
	public static final int TUNNEL_Z = 8;

	public DebugTunnelCarver(Codec<CarverConfig> configCodec) {
		super(configCodec);
	}

	@Override
	public boolean shouldCarve(CarverConfig config, Random random) {
		return true;
	}

	@Override
	public boolean carve(CarverContext context, CarverConfig config, Chunk chunk, Function<BlockPos, RegistryEntry<Biome>> posToBiome,
			Random random, AquiferSampler aquiferSampler, ChunkPos startPos, CarvingMask mask) {
		ChunkPos here = chunk.getPos();
		// Generators offer each chunk every nearby carver start; carve only from this chunk's own start, on the tunnel row.
		if (!startPos.equals(here) || TUNNEL_Z < here.getStartZ() || TUNNEL_Z > here.getEndZ()) {
			return false;
		}
		int centerY = config.y.get(random, context);
		BlockPos.Mutable pos = new BlockPos.Mutable();
		boolean carved = false;
		for (int x = here.getStartX(); x <= here.getEndX(); x++) {
			for (int y = centerY - 1; y <= centerY + 1; y++) {
				for (int z = TUNNEL_Z - 1; z <= TUNNEL_Z + 1; z++) {
					pos.set(x, y, z);
					BlockState state = chunk.getBlockState(pos);
					if (state.isIn(config.replaceable)) {
						chunk.setBlockState(pos, Blocks.CAVE_AIR.getDefaultState(), false);
						mask.set(x & 15, y, z & 15);
						carved = true;
					}
				}
			}
		}
		return carved;
	}
}
