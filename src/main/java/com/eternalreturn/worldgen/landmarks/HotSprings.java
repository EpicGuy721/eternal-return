package com.eternalreturn.worldgen.landmarks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;

import java.util.ArrayList;
import java.util.List;

/**
 * Hot springs (worldgen expansion 2, phase 3; landmark kind hot_spring): two to six pools stepping down from the first
 * like terraces, calcite rims, clear water one to three deep, in cold land (and, rarer, the cherry grove and the volcanic
 * jungle). The first pool is the deepest, with a vent of magma at its bottom. Nothing about a spring runs on its own (the
 * owner's call after the first look, 2026-11-09): its magma does what magma does anywhere (warms the cold close by,
 * hurts to stand on, pulls swimmers down), and about one spring in 3 has lava under the vent, which makes it a geyser by
 * the block rule (Geysers). Everything comes from the spring's centre, size and seed, so each chunk builds its own part.
 */
public final class HotSprings {
	/** One pool: its centre, water level (the water's top block), radius and depth (water blocks over the floor). */
	public record Pool(int x, int level, int z, int radius, int depth) {
		boolean water(int px, int pz) {
			int dx = px - this.x;
			int dz = pz - this.z;
			return dx * dx + dz * dz <= (this.radius + 0.5) * (this.radius + 0.5);
		}

		boolean rim(int px, int pz) {
			int dx = px - this.x;
			int dz = pz - this.z;
			return dx * dx + dz * dz <= (this.radius + 1.6) * (this.radius + 1.6);
		}

		double distance(double px, double pz) {
			return Math.sqrt((px - this.x) * (px - this.x) + (pz - this.z) * (pz - this.z));
		}
	}

	/** A spring: its pools (the first the deepest, with the vent), and whether its vent sits on lava (a geyser: Geysers). */
	public record Spring(BlockPos centre, int size, long seed, List<Pool> pools, boolean geyser) {
		public Pool vent() {
			return this.pools.get(0);
		}

	}

	private HotSprings() {
	}

	/** The plan of a spring at "centre" (the air over its ground), from its size (pools) and seed. */
	public static Spring plan(BlockPos centre, int size, long seed) {
		Random random = Random.create(seed);
		List<Pool> pools = new ArrayList<>();
		double heading = random.nextDouble() * Math.PI * 2;
		int radius = 3 + random.nextInt(2);
		Pool first = new Pool(centre.getX(), centre.getY() - 1, centre.getZ(), radius, 3);
		pools.add(first);
		double x = first.x();
		double z = first.z();
		Pool last = first;
		for (int i = 1; i < size; i++) {
			heading += (random.nextDouble() - 0.5) * 1.2;
			int r = 2 + random.nextInt(2);
			// Far enough apart for a rim between them, whatever the rounding.
			double step = last.radius() + r + 2.5;
			x += Math.cos(heading) * step;
			z += Math.sin(heading) * step;
			Pool pool = new Pool((int) Math.round(x), last.level() - 1 - random.nextInt(2), (int) Math.round(z), r, 1 + random.nextInt(2));
			pools.add(pool);
			last = pool;
		}
		return new Spring(centre, size, seed, List.copyOf(pools), random.nextInt(3) == 0);
	}

	/** How far a spring of this size can reach round its centre (generous: it decides which chunks build a part). */
	static int reach(int size) {
		return size * 7 + 8;
	}

	// ------------------------------------------------------------------ building

	/** Builds this chunk's part of the spring (box: the chunk's part of the landmark's box). */
	public static void build(LandmarkPiece piece, StructureWorldAccess world, BlockBox box) {
		Spring spring = plan(piece.centre, piece.size, piece.seed);
		Random random = Random.create(piece.seed ^ BlockPos.asLong(box.getMinX(), 0, box.getMinZ()));
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int x = box.getMinX(); x <= box.getMaxX(); x++) {
			for (int z = box.getMinZ(); z <= box.getMaxZ(); z++) {
				Pool water = null;
				Pool rim = null;
				for (Pool pool : spring.pools()) {
					if (pool.water(x, z) && (water == null || pool.level() > water.level())) {
						water = pool;
					}
					if (pool.rim(x, z) && (rim == null || pool.level() > rim.level())) {
						rim = pool;
					}
				}
				if (water != null && (rim == null || rim.level() <= water.level())) {
					int floor = water.level() - water.depth();
					clearAbove(world, pos, x, water.level(), z);
					for (int y = floor + 1; y <= water.level(); y++) {
						world.setBlockState(pos.set(x, y, z), Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
					boolean centre = x == water.x() && z == water.z();
					boolean vent = water == spring.vent() && centre;
					world.setBlockState(pos.set(x, floor, z), vent ? Blocks.MAGMA_BLOCK.getDefaultState() : rimBlock(random), Block.NOTIFY_LISTENERS);
					if (vent && spring.geyser()) {
						// Lava under the vent: the magma erupts now and then (Geysers).
						world.setBlockState(pos.set(x, floor - 1, z), Blocks.LAVA.getDefaultState(), Block.NOTIFY_LISTENERS);
						fillBelow(world, pos, x, floor - 2, z);
					} else {
						fillBelow(world, pos, x, floor - 1, z);
					}
				} else if (rim != null) {
					clearAbove(world, pos, x, rim.level(), z);
					world.setBlockState(pos.set(x, rim.level(), z), rimBlock(random), Block.NOTIFY_LISTENERS);
					fillBelow(world, pos, x, rim.level() - 1, z);
				}
			}
		}
	}

	private static BlockState rimBlock(Random random) {
		return Blocks.CALCITE.getDefaultState();
	}

	/** Air over a pool or rim (a hillside it's cut into), up to 12 blocks. */
	private static void clearAbove(StructureWorldAccess world, BlockPos.Mutable pos, int x, int level, int z) {
		for (int y = level + 1; y <= level + 12; y++) {
			if (!world.getBlockState(pos.set(x, y, z)).isAir()) {
				world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
		}
	}

	/** Calcite under a pool or rim down to the ground (a terrace wall where the land falls away), up to 14 blocks. */
	private static void fillBelow(StructureWorldAccess world, BlockPos.Mutable pos, int x, int top, int z) {
		for (int y = top; y > top - 14 && y > world.getBottomY(); y--) {
			BlockState state = world.getBlockState(pos.set(x, y, z));
			if (state.isOpaqueFullCube(world, pos) && !state.isIn(BlockTags.LEAVES)) {
				return;
			}
			world.setBlockState(pos, Blocks.CALCITE.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
	}
}
