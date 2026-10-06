package com.eternalreturn.worldgen.caves;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.carver.CarvingMask;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Writes cave shapes into the one chunk being carved. Every cave type ends up here, so the rules for
 * what may be carved live in one place:
 * <ul>
 * <li>only blocks the carvable predicate accepts (the eternalreturn:carvable tag) are replaced, so
 * bedrock, water and anything outside the tag stay;</li>
 * <li>nothing below the world's floor + 1 or within 8 blocks of its top is touched (vanilla's limits);</li>
 * <li>at or below the lava level (vanilla's 8 blocks above the floor) carved blocks become lava, above
 * it cave air;</li>
 * <li>a shape is skipped in this chunk when water, or lava above the lava level, lies inside or next to
 * its box (Release 1.6.4's rule, which keeps caves from breaching oceans and rivers). Water just across
 * the chunk's edge is found from the generator's height map, since the neighbouring chunk may not exist yet.</li>
 * </ul>
 * Decisions depend only on this chunk's blocks before carving and on the generator's noise, never on
 * neighbouring chunks' blocks, so the result is the same whichever order chunks generate in.
 */
public final class ChunkCarver {
	/** Water height in the columns just outside this chunk: the y of the first block above the ocean floor. */
	public interface NeighbourWater {
		int oceanFloorTop(int x, int z);
	}

	/** Which blocks of a shape's box are inside it. Arguments are offsets scaled by the shape's radii (-1 to 1 at the edge). */
	public interface Shape {
		boolean contains(double dx, double dy, double dz, int y);
	}

	private static final BlockState CAVE_AIR = Blocks.CAVE_AIR.getDefaultState();
	private static final BlockState LAVA = Blocks.LAVA.getDefaultState();

	private final Chunk chunk;
	private final CarvingMask mask;
	private final Predicate<BlockState> carvable;
	private final int startX;
	private final int startZ;
	private final int minY;
	private final int maxY;
	private final int lavaY;
	private final int seaLevel;
	@Nullable
	private final NeighbourWater neighbourWater;
	/** Cached ocean-floor tops of the 4 x 16 columns just outside the chunk; MIN_VALUE until sampled. */
	private final int[] edgeFloor = new int[64];
	private final BlockPos.Mutable pos = new BlockPos.Mutable();

	private long carvedBlocks;
	private long skippedForWater;

	public ChunkCarver(Chunk chunk, CarvingMask mask, Predicate<BlockState> carvable, int minY, int maxY, int lavaY, int seaLevel,
			@Nullable NeighbourWater neighbourWater) {
		this.chunk = chunk;
		this.mask = mask;
		this.carvable = carvable;
		ChunkPos chunkPos = chunk.getPos();
		this.startX = chunkPos.getStartX();
		this.startZ = chunkPos.getStartZ();
		this.minY = minY;
		this.maxY = maxY;
		this.lavaY = lavaY;
		this.seaLevel = seaLevel;
		this.neighbourWater = neighbourWater;
		java.util.Arrays.fill(this.edgeFloor, Integer.MIN_VALUE);
	}

	public Chunk chunk() {
		return this.chunk;
	}

	public int startX() {
		return this.startX;
	}

	public int startZ() {
		return this.startZ;
	}

	public int minY() {
		return this.minY;
	}

	public int maxY() {
		return this.maxY;
	}

	public long carvedBlocks() {
		return this.carvedBlocks;
	}

	public long skippedForWater() {
		return this.skippedForWater;
	}

	/** True when a box with these horizontal bounds overlaps this chunk at all. */
	public boolean touches(double minX, double maxX, double minZ, double maxZ) {
		return maxX >= this.startX && minX < this.startX + 16 && maxZ >= this.startZ && minZ < this.startZ + 16;
	}

	/** An ellipsoid with these radii, cut off below floor (a scaled y offset; -1 or less for none). */
	public boolean ellipsoid(double x, double y, double z, double rx, double ry, double rz, double floor) {
		return this.carve(x, y, z, rx, ry, rz, true, (dx, dy, dz, blockY) -> dy > floor && dx * dx + dy * dy + dz * dz < 1.0);
	}

	/**
	 * Carves the blocks of a shape centred at x, y, z whose box is radius rx, ry, rz. withinCircle says
	 * the shape never reaches past the unit circle horizontally, so columns outside it are skipped.
	 * Returns whether anything was carved.
	 */
	public boolean carve(double x, double y, double z, double rx, double ry, double rz, boolean withinCircle, Shape shape) {
		int boxMinX = MathHelper.floor(x - rx) - 1;
		int boxMaxX = MathHelper.floor(x + rx) + 1;
		int boxMinZ = MathHelper.floor(z - rz) - 1;
		int boxMaxZ = MathHelper.floor(z + rz) + 1;
		int boxMinY = MathHelper.floor(y - ry) - 1;
		int boxMaxY = MathHelper.floor(y + ry) + 1;
		int x0 = Math.max(boxMinX, this.startX);
		int x1 = Math.min(boxMaxX, this.startX + 15);
		int z0 = Math.max(boxMinZ, this.startZ);
		int z1 = Math.min(boxMaxZ, this.startZ + 15);
		int y0 = Math.max(boxMinY, this.minY);
		int y1 = Math.min(boxMaxY, this.maxY);
		if (x0 > x1 || z0 > z1 || y0 > y1) {
			return false;
		}
		if (this.hasFluidNear(x0, x1, y0, y1, z0, z1, boxMinX, boxMaxX, boxMinZ, boxMaxZ)) {
			this.skippedForWater++;
			return false;
		}

		boolean carved = false;
		for (int bx = x0; bx <= x1; bx++) {
			double dx = (bx + 0.5 - x) / rx;
			for (int bz = z0; bz <= z1; bz++) {
				double dz = (bz + 0.5 - z) / rz;
				if (withinCircle && dx * dx + dz * dz >= 1.0) {
					continue;
				}
				for (int by = y1; by >= y0; by--) {
					double dy = (by + 0.5 - y) / ry;
					if (shape.contains(dx, dy, dz, by)) {
						carved |= this.carveBlock(bx, by, bz);
					}
				}
			}
		}
		return carved;
	}

	/**
	 * Release 1.6.4's check, widened by one block: water, or lava above the lava level, anywhere in the
	 * box (in this chunk), or open water just across the chunk's edge beside the box.
	 */
	private boolean hasFluidNear(int x0, int x1, int y0, int y1, int z0, int z1, int boxMinX, int boxMaxX, int boxMinZ, int boxMaxZ) {
		int fx0 = Math.max(x0 - 1, this.startX);
		int fx1 = Math.min(x1 + 1, this.startX + 15);
		int fz0 = Math.max(z0 - 1, this.startZ);
		int fz1 = Math.min(z1 + 1, this.startZ + 15);
		int fy0 = Math.max(y0 - 1, this.chunk.getBottomY());
		int fy1 = Math.min(y1 + 1, this.chunk.getTopY() - 1);
		for (int bx = fx0; bx <= fx1; bx++) {
			for (int bz = fz0; bz <= fz1; bz++) {
				for (int by = fy1; by >= fy0; by--) {
					FluidState fluid = this.chunk.getFluidState(this.pos.set(bx, by, bz));
					if (!fluid.isEmpty() && (fluid.isIn(FluidTags.WATER) || by > this.lavaY)) {
						return true;
					}
				}
			}
		}
		if (this.neighbourWater == null || y0 - 1 >= this.seaLevel) {
			return false;
		}
		// Columns just outside the chunk, next to carved blocks on the chunk's edge.
		if (boxMinX < this.startX && x0 == this.startX && this.edgeHasWater(0, z0, z1, y0 - 1, y1 + 1)) {
			return true;
		}
		if (boxMaxX > this.startX + 15 && x1 == this.startX + 15 && this.edgeHasWater(1, z0, z1, y0 - 1, y1 + 1)) {
			return true;
		}
		if (boxMinZ < this.startZ && z0 == this.startZ && this.edgeHasWater(2, x0, x1, y0 - 1, y1 + 1)) {
			return true;
		}
		return boxMaxZ > this.startZ + 15 && z1 == this.startZ + 15 && this.edgeHasWater(3, x0, x1, y0 - 1, y1 + 1);
	}

	/** side: 0 west, 1 east, 2 north, 3 south; a0..a1 the block coordinates along that edge. */
	private boolean edgeHasWater(int side, int a0, int a1, int yLow, int yHigh) {
		for (int a = a0; a <= a1; a++) {
			int index = side * 16 + (a - (side < 2 ? this.startZ : this.startX));
			int top = this.edgeFloor[index];
			if (top == Integer.MIN_VALUE) {
				int columnX = side == 0 ? this.startX - 1 : side == 1 ? this.startX + 16 : a;
				int columnZ = side == 2 ? this.startZ - 1 : side == 3 ? this.startZ + 16 : a;
				top = this.neighbourWater.oceanFloorTop(columnX, columnZ);
				this.edgeFloor[index] = top;
			}
			// Water fills that column from its floor top up to sea level.
			if (top < this.seaLevel && yHigh >= top && yLow < this.seaLevel) {
				return true;
			}
		}
		return false;
	}

	private boolean carveBlock(int x, int y, int z) {
		this.pos.set(x, y, z);
		BlockState state = this.chunk.getBlockState(this.pos);
		if (!this.carvable.test(state)) {
			return false;
		}
		boolean grassy = state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.MYCELIUM);
		this.chunk.setBlockState(this.pos, y <= this.lavaY ? LAVA : CAVE_AIR, false);
		this.mask.set(x & 15, y, z & 15);
		this.carvedBlocks++;
		// As vanilla: when the top block is carved away, the dirt under it takes its place.
		if (grassy && y - 1 >= this.minY) {
			this.pos.set(x, y - 1, z);
			if (this.chunk.getBlockState(this.pos).isOf(Blocks.DIRT)) {
				this.chunk.setBlockState(this.pos, state, false);
			}
		}
		return true;
	}
}
