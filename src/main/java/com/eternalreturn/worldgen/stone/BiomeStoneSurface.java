package com.eternalreturn.worldgen.stone;

import com.eternalreturn.EternalReturn;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps Moderner Beta's own surface pass in step with biome stone. After the surface rules, Moderner
 * Beta's Release-style generators run a pass of their own (sand and gravel beaches, and bare patches
 * where the topsoil is stripped down to stone) that takes any opaque block other than stone for topsoil.
 * In a chunk where biome stone ran, the host stones (block tag eternalreturn:biome_host_stones, written
 * by build_preset.py from the biome stone table) count as stone for that pass (ModernerBetaSurfaceMixin),
 * and where the pass strips the topsoil down to bare stone, that stone becomes the host stone underneath.
 * The chunk is marked by BiomeStoneCondition, which only Eternal Return noise settings use.
 */
public final class BiomeStoneSurface {
	public static final TagKey<Block> HOST_STONES = TagKey.of(RegistryKeys.BLOCK, EternalReturn.id("biome_host_stones"));
	private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);
	private static final ThreadLocal<List<int[]>> CANDIDATES = new ThreadLocal<>();

	private BiomeStoneSurface() {
	}

	/** A chunk's surface is starting on this thread (SurfaceBuilderMixin). */
	public static void unmarkChunk() {
		ACTIVE.set(false);
	}

	/** Biome stone's rules are about to run on this thread's current chunk. */
	static void markChunk() {
		ACTIVE.set(true);
	}

	public static boolean isHostStoneInActiveChunk(BlockState state) {
		return ACTIVE.get() && state.isIn(HOST_STONES);
	}

	/**
	 * Before Moderner Beta's surface pass: remember, per column, the blocks it may turn into stone (the
	 * opaque blocks other than stone and host stones above the column's first stone or host stone).
	 */
	public static void beforeSurfacePass(Chunk chunk) {
		CANDIDATES.remove();
		if (!ACTIVE.get()) {
			return;
		}
		List<int[]> candidates = new ArrayList<>();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int startX = chunk.getPos().getStartX();
		int startZ = chunk.getPos().getStartZ();
		for (int x = startX; x < startX + 16; x++) {
			for (int z = startZ; z < startZ + 16; z++) {
				for (int y = chunk.getTopY() - 1; y >= chunk.getBottomY(); y--) {
					BlockState state = chunk.getBlockState(pos.set(x, y, z));
					if (state.isOf(Blocks.STONE) || state.isIn(HOST_STONES)) {
						break;
					}
					if (state.isOpaque()) {
						candidates.add(new int[]{x, y, z});
					}
				}
			}
		}
		CANDIDATES.set(candidates);
	}

	/**
	 * After Moderner Beta's surface pass: where it stripped topsoil down to bare stone, the stone becomes
	 * the host stone it sits on (if it sits on one; stripped stone on plain stone stays stone).
	 */
	public static void afterSurfacePass(Chunk chunk) {
		List<int[]> candidates = CANDIDATES.get();
		CANDIDATES.remove();
		ACTIVE.set(false);
		if (candidates == null) {
			return;
		}
		BlockPos.Mutable pos = new BlockPos.Mutable();
		Set<Long> stripped = new HashSet<>();
		for (int[] candidate : candidates) {
			if (chunk.getBlockState(pos.set(candidate[0], candidate[1], candidate[2])).isOf(Blocks.STONE)) {
				stripped.add(pos.asLong());
			}
		}
		// Candidates run top-down in each column; walk down through stripped stone only, to the host stone under it.
		for (int[] candidate : candidates) {
			if (!stripped.contains(pos.set(candidate[0], candidate[1], candidate[2]).asLong())) {
				continue;
			}
			for (int y = candidate[1] - 1; y >= chunk.getBottomY(); y--) {
				BlockState below = chunk.getBlockState(pos.set(candidate[0], y, candidate[2]));
				if (below.isIn(HOST_STONES)) {
					chunk.setBlockState(pos.set(candidate[0], candidate[1], candidate[2]), below, false);
					break;
				}
				if (!stripped.contains(pos.asLong())) {
					break;
				}
			}
		}
	}
}
