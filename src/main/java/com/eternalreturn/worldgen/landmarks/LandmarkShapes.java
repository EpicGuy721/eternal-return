package com.eternalreturn.worldgen.landmarks;

import com.eternalreturn.worldgen.ores.OreVariants;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.VineBlock;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/**
 * The landmarks' shapes, built column by column for one chunk (the part of the landmark inside it). Everything comes
 * from the landmark's seed (noise and per-block chances), so neighbouring chunks agree. "Ground" is the column's own
 * ground as the chunk stands when structures are built (before trees and plants).
 */
final class LandmarkShapes {
	/** This mod's hardened packed ice (registered by HardenedBlocks), the core of glaciers and ice walls. */
	private static final java.util.function.Supplier<Block> HARDENED_PACKED_ICE = () ->
			net.minecraft.registry.Registries.BLOCK.get(com.eternalreturn.EternalReturn.id("hardened_packed_ice"));
	private final LandmarkPiece piece;
	private final StructureWorldAccess world;
	private final BlockBox box;
	private final ChunkGenerator generator;
	private final BlockState stone;
	private final SimplexNoiseSampler noise;
	private final Random random;
	private final int cx;
	private final int cy;
	private final int cz;
	private final BlockPos.Mutable pos = new BlockPos.Mutable();

	LandmarkShapes(LandmarkPiece piece, StructureWorldAccess world, BlockBox chunkBox, ChunkGenerator generator) {
		this.piece = piece;
		this.world = world;
		BlockBox own = piece.getBoundingBox();
		this.box = new BlockBox(Math.max(own.getMinX(), chunkBox.getMinX()), Math.max(own.getMinY(), world.getBottomY() + 1),
				Math.max(own.getMinZ(), chunkBox.getMinZ()), Math.min(own.getMaxX(), chunkBox.getMaxX()), Math.min(own.getMaxY(), world.getTopY() - 1),
				Math.min(own.getMaxZ(), chunkBox.getMaxZ()));
		this.generator = generator;
		this.stone = piece.stone.getDefaultState();
		this.noise = new SimplexNoiseSampler(Random.create(piece.seed));
		this.random = Random.create(piece.seed ^ 0x5DEECE66DL);
		this.cx = piece.centre.getX();
		this.cy = piece.centre.getY();
		this.cz = piece.centre.getZ();
	}

	void build() {
		if (this.box.getMinX() > this.box.getMaxX() || this.box.getMinZ() > this.box.getMaxZ()) {
			return;
		}
		switch (this.piece.kind) {
			case ARCH -> this.arch();
			case SPIRE -> this.spire();
			case TROPICAL_PILLARS -> this.pillars();
			case CRATER -> this.crater();
			case LAVA_FLOW -> this.lavaFlow();
			case VOLCANO -> this.volcano();
			case GLACIER -> this.glacier();
			case ICE_WALL -> this.iceWall();
		}
	}

	// ------------------------------------------------------------------ helpers

	private void set(int x, int y, int z, BlockState state) {
		if (this.box.contains(x, y, z)) {
			this.world.setBlockState(this.pos.set(x, y, z), state, net.minecraft.block.Block.NOTIFY_LISTENERS);
		}
	}

	private BlockState get(int x, int y, int z) {
		return this.world.getBlockState(this.pos.set(x, y, z));
	}

	/** The first free block above the column's ground (the top solid block is one below). */
	private int ground(int x, int z) {
		return this.world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z);
	}

	private double n2(double x, double z) {
		return this.noise.sample(x, z);
	}

	private double n3(double x, double y, double z) {
		return this.noise.sample(x, y, z);
	}

	/** A fixed chance in [0, 1) for this block and this landmark. */
	private double chance(int x, int y, int z, int salt) {
		long h = this.piece.seed + salt * 0x9E3779B97F4A7C15L;
		h ^= x * 0x632BE59BD9B4E019L;
		h ^= y * 0x8CB92BA72F3D8DD7L;
		h ^= z * 0xB492B66FBE98F273L;
		h = (h ^ (h >>> 31)) * 0x7FB5D329728EA185L;
		h = (h ^ (h >>> 27)) * 0x81DADEF4BC2DD44DL;
		return ((h ^ (h >>> 33)) >>> 11) / (double) (1L << 53);
	}

	/** Stone from the column's ground up to (not including) toY, so a landmark stands on the land, not over a gap. */
	private void fillUp(int x, int z, int toY, BlockState state) {
		for (int y = this.ground(x, z) - 1; y < toY; y++) {
			this.set(x, y, z, state);
		}
	}

	private boolean nearWater(int x, int y, int z) {
		for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP}) {
			if (this.world.getFluidState(this.pos.set(x + d.getOffsetX(), y + d.getOffsetY(), z + d.getOffsetZ())).isIn(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	/** Lava, or obsidian where it would touch water. */
	private void lava(int x, int y, int z) {
		this.set(x, y, z, this.nearWater(x, y, z) ? Blocks.OBSIDIAN.getDefaultState() : Blocks.LAVA.getDefaultState());
	}

	/** An ore of the landmark's stone (the stone's own ore variant), weighted toward the common ores. */
	private BlockState ore(int x, int y, int z) {
		double c = this.chance(x, y, z, 7);
		BlockState vanilla = c < 0.34 ? Blocks.COAL_ORE.getDefaultState() : c < 0.62 ? Blocks.IRON_ORE.getDefaultState()
				: c < 0.82 ? Blocks.COPPER_ORE.getDefaultState() : c < 0.89 ? Blocks.GOLD_ORE.getDefaultState()
				: c < 0.95 ? Blocks.REDSTONE_ORE.getDefaultState() : c < 0.985 ? Blocks.LAPIS_ORE.getDefaultState() : Blocks.DIAMOND_ORE.getDefaultState();
		return OreVariants.variantFor(vanilla, this.stone);
	}

	// ------------------------------------------------------------------ rock arch

	/**
	 * A natural arch: a curved span of rock (size: the span, feet to feet), thicker at the feet, its feet standing on
	 * the ground, a ragged surface.
	 */
	private void arch() {
		double angle = this.random.nextDouble() * Math.PI * 2;
		double ux = Math.cos(angle);
		double uz = Math.sin(angle);
		double span = this.piece.size;
		double height = span * 0.55 + 3;
		double thick = 2.2 + span / 14.0;
		int samples = 64;
		double[][] curve = new double[samples + 1][4];
		for (int i = 0; i <= samples; i++) {
			double s = i / (double) samples;
			curve[i] = new double[]{this.cx + ux * (s - 0.5) * span, this.cy + height * Math.sin(Math.PI * s), this.cz + uz * (s - 0.5) * span,
					thick * (1 + 0.6 * (1 - Math.sin(Math.PI * s)))};
		}
		for (int x = this.box.getMinX(); x <= this.box.getMaxX(); x++) {
			for (int z = this.box.getMinZ(); z <= this.box.getMaxZ(); z++) {
				double rx = x + 0.5 - this.cx;
				double rz = z + 0.5 - this.cz;
				double along = rx * ux + rz * uz;
				double across = -rx * uz + rz * ux;
				if (Math.abs(along) > span / 2 + thick * 2 + 2 || Math.abs(across) > thick * 2.2 + 1) {
					continue;
				}
				for (int y = this.cy - 3; y <= this.cy + height + thick * 2; y++) {
					double best = Double.MAX_VALUE;
					for (double[] p : curve) {
						double dx = x + 0.5 - p[0];
						double dy = y + 0.5 - p[1];
						double dz = z + 0.5 - p[2];
						best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz) / p[3]);
					}
					if (best < 1 + 0.22 * this.n3(x * 0.25, y * 0.25, z * 0.25)) {
						this.set(x, y, z, this.stone);
					}
				}
				// The feet go down to the ground.
				for (double[] foot : new double[][]{curve[0], curve[samples]}) {
					double dx = x + 0.5 - foot[0];
					double dz = z + 0.5 - foot[2];
					if (dx * dx + dz * dz < foot[3] * foot[3]) {
						this.fillUp(x, z, this.cy - 2, this.stone);
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ rock spire

	/** A tall, tapering, slightly leaning needle of rock (size: its height). */
	private void spire() {
		double height = this.piece.size;
		double base = 2.3 + height / 10.0;
		double lean = this.random.nextDouble() * Math.PI * 2;
		double leanBy = height * 0.12;
		for (int x = this.box.getMinX(); x <= this.box.getMaxX(); x++) {
			for (int z = this.box.getMinZ(); z <= this.box.getMaxZ(); z++) {
				for (int y = this.cy - 3; y <= this.cy + height; y++) {
					double frac = Math.max(0, (y - this.cy) / height);
					double ox = this.cx + Math.cos(lean) * leanBy * frac * frac;
					double oz = this.cz + Math.sin(lean) * leanBy * frac * frac;
					double radius = (base * Math.pow(1 - frac, 0.85) + 0.7) * (1 + 0.25 * this.n3(x * 0.3, y * 0.15, z * 0.3));
					double dx = x + 0.5 - ox;
					double dz = z + 0.5 - oz;
					if (dx * dx + dz * dz < radius * radius) {
						this.set(x, y, z, this.stone);
					}
				}
				double dx = x + 0.5 - this.cx;
				double dz = z + 0.5 - this.cz;
				if (dx * dx + dz * dz < (base + 0.7) * (base + 0.7)) {
					this.fillUp(x, z, this.cy - 3, this.stone);
				}
			}
		}
	}

	// ------------------------------------------------------------------ tropical pillars

	/**
	 * A cluster of three to six tall stone columns (karst towers; size: the tallest one's height), bulging and leaning a
	 * little, flat-topped with dirt and grass (the jungle's trees and plants then grow up there), vines down their sides.
	 */
	private void pillars() {
		int count = 3 + this.random.nextInt(4);
		double[][] pillars = new double[count][];
		for (int i = 0; i < count; i++) {
			double angle = this.random.nextDouble() * Math.PI * 2;
			double dist = i == 0 ? 0 : 5 + this.random.nextDouble() * 11;
			pillars[i] = new double[]{this.cx + Math.cos(angle) * dist, this.cz + Math.sin(angle) * dist, 2.8 + this.random.nextDouble() * 2.7,
					this.piece.size * (0.55 + this.random.nextDouble() * 0.45), this.random.nextDouble() * Math.PI * 2};
		}
		for (int x = this.box.getMinX(); x <= this.box.getMaxX(); x++) {
			for (int z = this.box.getMinZ(); z <= this.box.getMaxZ(); z++) {
				int top = Integer.MIN_VALUE;
				for (double[] p : pillars) {
					double height = p[3];
					for (int y = this.cy - 4; y <= this.cy + height; y++) {
						double frac = Math.max(0, (y - this.cy) / height);
						double ox = p[0] + Math.cos(p[4]) * height * 0.08 * frac * frac;
						double oz = p[1] + Math.sin(p[4]) * height * 0.08 * frac * frac;
						double radius = p[2] * (1 + 0.22 * this.n3(x * 0.2, y * 0.12, z * 0.2));
						double dx = x + 0.5 - ox;
						double dz = z + 0.5 - oz;
						if (dx * dx + dz * dz < radius * radius) {
							this.set(x, y, z, this.stone);
							top = Math.max(top, y);
						}
					}
					double dx = x + 0.5 - p[0];
					double dz = z + 0.5 - p[1];
					if (dx * dx + dz * dz < p[2] * p[2]) {
						this.fillUp(x, z, this.cy - 4, this.stone);
					}
				}
				if (top > this.cy + 4) {
					this.set(x, top, z, Blocks.GRASS_BLOCK.getDefaultState());
					this.set(x, top - 1, z, Blocks.DIRT.getDefaultState());
				}
			}
		}
		// Vines on the sides, hanging from the rock into the air beside it.
		for (int x = this.box.getMinX(); x <= this.box.getMaxX(); x++) {
			for (int z = this.box.getMinZ(); z <= this.box.getMaxZ(); z++) {
				for (int y = this.cy + 2; y <= this.cy + this.piece.size; y++) {
					if (!this.get(x, y, z).isAir() || this.chance(x, y, z, 3) > 0.22) {
						continue;
					}
					for (Direction d : Direction.Type.HORIZONTAL) {
						if (this.box.contains(x + d.getOffsetX(), y, z + d.getOffsetZ()) && this.get(x + d.getOffsetX(), y, z + d.getOffsetZ()).equals(this.stone)) {
							this.set(x, y, z, Blocks.VINE.getDefaultState().with(VineBlock.getFacingProperty(d), true));
							break;
						}
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ crater

	/**
	 * A shallow bowl (size: its radius) with bare stone inside and a raised rim of rubble: stone, gravel, cobblestone and
	 * ores of the local stone, which also show in the bowl's upper walls.
	 */
	private void crater() {
		double radius = this.piece.size;
		double depth = Math.max(4, radius * 0.45);
		double rimWidth = radius * 0.45;
		double rimHeight = 2 + radius / 8.0;
		for (int x = this.box.getMinX(); x <= this.box.getMaxX(); x++) {
			for (int z = this.box.getMinZ(); z <= this.box.getMaxZ(); z++) {
				double dx = x + 0.5 - this.cx;
				double dz = z + 0.5 - this.cz;
				double d = Math.sqrt(dx * dx + dz * dz) / (1 + 0.08 * this.n2(x * 0.08, z * 0.08));
				if (d > radius + rimWidth) {
					continue;
				}
				int ground = this.ground(x, z);
				if (d < radius) {
					int floor = ground - 1 - (int) Math.round(depth * (1 - (d / radius) * (d / radius)));
					for (int y = floor + 1; y <= ground + 4; y++) {
						this.set(x, y, z, Blocks.AIR.getDefaultState());
					}
					for (int y = floor - 1; y <= floor; y++) {
						boolean wall = d > radius * 0.65;
						this.set(x, y, z, wall ? this.rubble(x, y, z) : this.chance(x, y, z, 1) < 0.25 ? Blocks.GRAVEL.getDefaultState() : this.stone);
					}
				} else {
					int top = ground - 1 + (int) Math.round(rimHeight * (1 - (d - radius) / rimWidth));
					for (int y = ground - 2; y <= top; y++) {
						this.set(x, y, z, this.rubble(x, y, z));
					}
				}
			}
		}
	}

	private BlockState rubble(int x, int y, int z) {
		double c = this.chance(x, y, z, 2);
		return c < 0.04 ? this.ore(x, y, z) : c < 0.25 ? Blocks.GRAVEL.getDefaultState() : c < 0.33 ? Blocks.COBBLESTONE.getDefaultState() : this.stone;
	}

	// ------------------------------------------------------------------ lava flow

	/**
	 * A river of lava across the land from a vent (the course from LandmarkStructure), set into the ground, with walls of
	 * magma and basalt and 3 to 6 blocks of charred ground either side: coal blocks, basalt, gravel, coarse dirt with
	 * dead bushes, and burnt stumps. Obsidian where it meets water.
	 */
	private void lavaFlow() {
		int[] path = this.piece.path;
		double width = 1.3;
		for (int x = this.box.getMinX(); x <= this.box.getMaxX(); x++) {
			for (int z = this.box.getMinZ(); z <= this.box.getMaxZ(); z++) {
				double best = Double.MAX_VALUE;
				int index = 0;
				for (int i = 0; i + 2 < path.length; i += 3) {
					double dx = x + 0.5 - (path[i] + 0.5);
					double dz = z + 0.5 - (path[i + 2] + 0.5);
					double d = dx * dx + dz * dz;
					if (d < best) {
						best = d;
						index = i / 3;
					}
				}
				double d = Math.sqrt(best);
				double margin = 3 + 3 * (0.5 + 0.5 * this.n2(x * 0.1, z * 0.1));
				boolean vent = index < 3 && d < 3;
				if (d > width + margin + 0.5 && !vent) {
					continue;
				}
				int ground = this.ground(x, z);
				int top = ground - 1;
				for (int y = ground; y <= ground + 2; y++) {
					this.set(x, y, z, Blocks.AIR.getDefaultState());
				}
				if (vent || d <= width) {
					this.lava(x, top, z);
					if (vent) {
						this.lava(x, top - 1, z);
						this.set(x, top - 2, z, Blocks.MAGMA_BLOCK.getDefaultState());
					} else {
						this.set(x, top - 1, z, Blocks.BASALT.getDefaultState());
					}
				} else if (d <= width + 1.5) {
					this.set(x, top, z, this.chance(x, top, z, 4) < 0.4 ? Blocks.MAGMA_BLOCK.getDefaultState() : Blocks.BASALT.getDefaultState());
				} else {
					double c = this.chance(x, top, z, 5);
					boolean inner = d < width + 3;
					BlockState ground2 = inner
							? (c < 0.2 ? Blocks.COAL_BLOCK : c < 0.55 ? Blocks.BASALT : Blocks.GRAVEL).getDefaultState()
							: (c < 0.4 ? Blocks.COARSE_DIRT : c < 0.7 ? Blocks.GRAVEL : c < 0.8 ? Blocks.COAL_BLOCK : Blocks.BASALT).getDefaultState();
					this.set(x, top, z, ground2);
					double plant = this.chance(x, ground, z, 6);
					if (ground2.isOf(Blocks.COARSE_DIRT) && plant < 0.15) {
						this.set(x, ground, z, Blocks.DEAD_BUSH.getDefaultState());
					} else if (!inner && plant > 0.985) {
						this.set(x, ground, z, Blocks.DARK_OAK_LOG.getDefaultState());
						if (plant > 0.993) {
							this.set(x, ground + 1, z, Blocks.DARK_OAK_LOG.getDefaultState());
						}
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ volcano

	/**
	 * A huge cone (size: its radius at the foot) of the local stone streaked with basalt and tuff, rising up to 80 blocks
	 * from the ground at its centre: ash and basalt high up, coarse dirt and gravel on the middle slopes, grass on the
	 * lower slopes so the land runs up into it. A crater at the top holds a lava lake ringed with magma; two or three lava
	 * flows run down the sides.
	 */
	private void volcano() {
		double radius = this.piece.size;
		double height = Math.min(80, radius * 0.8);
		double craterRadius = radius * 0.17;
		double craterDepth = height * 0.28;
		double rimTop = this.cy + height * Math.pow(1 - craterRadius / radius, 1.5);
		int lavaLevel = (int) (rimTop - craterDepth * 0.45);
		int flows = 2 + this.random.nextInt(2);
		double[] flowAngles = new double[flows];
		for (int i = 0; i < flows; i++) {
			flowAngles[i] = this.random.nextDouble() * Math.PI * 2;
		}
		for (int x = this.box.getMinX(); x <= this.box.getMaxX(); x++) {
			for (int z = this.box.getMinZ(); z <= this.box.getMaxZ(); z++) {
				double dx = x + 0.5 - this.cx;
				double dz = z + 0.5 - this.cz;
				double d = Math.sqrt(dx * dx + dz * dz);
				double angle = Math.atan2(dz, dx);
				double edge = radius * (1 + 0.12 * this.n2(Math.cos(angle) * 1.7, Math.sin(angle) * 1.7));
				double dd = d / edge;
				if (dd >= 1) {
					continue;
				}
				double top;
				boolean crater = d < craterRadius;
				if (crater) {
					top = rimTop - craterDepth * Math.sqrt(1 - (d / craterRadius) * (d / craterRadius));
				} else {
					top = this.cy + height * Math.pow(1 - dd, 1.5) + height * 0.05 * this.n2(x * 0.06, z * 0.06) * (1 - dd);
				}
				int ground = this.ground(x, z);
				// The foot eases down to the land round it (the cone stands on the centre's ground, and where the land
				// falls away its edge stood as a wall).
				if (!crater && dd > 0.5 && top > ground) {
					double t = (1 - dd) / 0.5;
					top = ground + (top - ground) * t * t * (3 - 2 * t);
				}
				int peak = (int) Math.round(top);
				for (int y = ground - 1; y <= peak; y++) {
					double m = this.n3(x * 0.07, y * 0.07, z * 0.07);
					this.set(x, y, z, m > 0.45 ? Blocks.BASALT.getDefaultState() : m < -0.55 ? Blocks.TUFF.getDefaultState() : this.stone);
				}
				if (crater) {
					for (int y = peak + 1; y <= Math.max(ground, (int) rimTop) + 2; y++) {
						this.set(x, y, z, y <= lavaLevel ? Blocks.LAVA.getDefaultState() : Blocks.AIR.getDefaultState());
					}
					this.set(x, peak, z, this.chance(x, peak, z, 8) < 0.5 ? Blocks.MAGMA_BLOCK.getDefaultState() : Blocks.BASALT.getDefaultState());
					continue;
				}
				if (peak < ground - 1) {
					continue;
				}
				// The surface by height on the cone.
				double c = this.chance(x, peak, z, 9);
				if (dd < 0.35) {
					this.set(x, peak, z, (d < craterRadius * 1.4 && c < 0.3 ? Blocks.MAGMA_BLOCK : c < 0.45 ? Blocks.BASALT : c < 0.8 ? Blocks.GRAVEL : Blocks.TUFF).getDefaultState());
				} else if (dd < 0.7) {
					this.set(x, peak, z, (c < 0.4 ? Blocks.COARSE_DIRT : c < 0.65 ? Blocks.GRAVEL : c < 0.85 ? this.stone.getBlock() : Blocks.TUFF).getDefaultState());
				} else {
					this.set(x, peak, z, Blocks.GRASS_BLOCK.getDefaultState());
					for (int y = peak - 3; y < peak; y++) {
						this.set(x, y, z, Blocks.DIRT.getDefaultState());
					}
				}
				// Lava flows down the sides.
				if (d > craterRadius && d < radius * 0.75) {
					for (double a : flowAngles) {
						double off = Math.abs(Math.atan2(Math.sin(angle - a - 0.25 * this.n2(d * 0.05, a)), Math.cos(angle - a))) * d;
						if (off < 1.3) {
							this.lava(x, peak, z);
							this.set(x, peak - 1, z, Blocks.BASALT.getDefaultState());
						} else if (off < 2.6) {
							this.set(x, peak, z, (c < 0.5 ? Blocks.MAGMA_BLOCK : Blocks.BASALT).getDefaultState());
						}
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ glacier

	/**
	 * A tongue of ice (size: its length) lying on the land, widest and thickest at its head, narrowing to a sheer front:
	 * a core of hardened packed ice, packed ice near the surface, veins of blue ice, snow on top, and crevasses across it.
	 */
	private void glacier() {
		double length = this.piece.size;
		double angle = this.random.nextDouble() * Math.PI * 2;
		double ux = Math.cos(angle);
		double uz = Math.sin(angle);
		double widest = length * 0.18 + 8;
		double thickest = 5 + length / 18.0;
		for (int x = this.box.getMinX(); x <= this.box.getMaxX(); x++) {
			for (int z = this.box.getMinZ(); z <= this.box.getMaxZ(); z++) {
				double rx = x + 0.5 - this.cx;
				double rz = z + 0.5 - this.cz;
				double along = rx * ux + rz * uz;
				double across = -rx * uz + rz * ux;
				if (Math.abs(along) > length / 2) {
					continue;
				}
				double s = (along + length / 2) / length;
				double width = widest * (1 - 0.45 * s) * (1 + 0.1 * this.n2(along * 0.05, across * 0.05));
				if (Math.abs(across) >= width) {
					continue;
				}
				double profile = Math.sqrt(1 - (across / width) * (across / width));
				double head = Math.min(1, s / 0.12);
				// A dome across (not a half circle, which stood at its sides as a wall of ice).
				double thick = thickest * (1 - 0.35 * s) * profile * profile * head * (1 + 0.15 * this.n2(x * 0.07, z * 0.07));
				if (thick < 1) {
					continue;
				}
				int ground = this.ground(x, z);
				int top = ground - 1 + (int) Math.round(thick);
				boolean crevasse = Math.sin(along * 0.45 + 2 * this.n2(across * 0.08, 50)) > 0.9 && profile > 0.35 && s > 0.15 && s < 0.92;
				for (int y = ground - 1; y <= top; y++) {
					int fromTop = top - y;
					if (crevasse && fromTop < thick * 0.75 && y >= ground) {
						this.set(x, y, z, Blocks.AIR.getDefaultState());
						continue;
					}
					this.set(x, y, z, this.ice(x, y, z, fromTop, false));
				}
			}
		}
	}

	private BlockState ice(int x, int y, int z, int fromTop, boolean face) {
		if (fromTop == 0) {
			return Blocks.SNOW_BLOCK.getDefaultState();
		}
		if (this.n3(x * 0.12, y * 0.2, z * 0.12) > 0.6) {
			return Blocks.BLUE_ICE.getDefaultState();
		}
		return fromTop <= 2 || face ? Blocks.PACKED_ICE.getDefaultState() : HARDENED_PACKED_ICE.get().getDefaultState();
	}

	// ------------------------------------------------------------------ ice wall

	/**
	 * A long, wandering wall of ice (size: its length), 3 to 6 thick and 12 to 30 high with a ragged top: packed ice
	 * faces round a core of hardened packed ice, blue ice veins, snow on top.
	 */
	private void iceWall() {
		double length = this.piece.size;
		double angle = this.random.nextDouble() * Math.PI * 2;
		double ux = Math.cos(angle);
		double uz = Math.sin(angle);
		double thickness = 3 + this.random.nextInt(4);
		double height = 12 + this.random.nextInt(19);
		for (int x = this.box.getMinX(); x <= this.box.getMaxX(); x++) {
			for (int z = this.box.getMinZ(); z <= this.box.getMaxZ(); z++) {
				double rx = x + 0.5 - this.cx;
				double rz = z + 0.5 - this.cz;
				double along = rx * ux + rz * uz;
				double across = -rx * uz + rz * ux;
				if (Math.abs(along) > length / 2) {
					continue;
				}
				double middle = 4 * this.n2(along * 0.04, 20);
				double off = Math.abs(across - middle);
				if (off > thickness / 2) {
					continue;
				}
				double h = height * (1 - Math.pow(2 * Math.abs(along) / length, 4)) * (1 + 0.2 * this.n2(along * 0.12, 40));
				if (h < 2) {
					continue;
				}
				int ground = this.ground(x, z);
				int top = ground - 1 + (int) Math.round(h);
				for (int y = ground - 2; y <= top; y++) {
					this.set(x, y, z, this.ice(x, y, z, top - y, off > thickness / 2 - 1));
				}
			}
		}
	}
}
