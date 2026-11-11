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
			case BUTTE -> this.butte(1.6, false);
			case TABLE -> this.butte(0.45, true);
			case HOODOOS -> this.hoodoos();
			case ISLETS -> this.islets();
			case REEF -> this.reef();
			case HOT_SPRING -> HotSprings.build(this.piece, this.world, this.box);
			case ELDER_TREE -> ElderTrees.build(this.piece, this.world, this.box);
			case ICEBERG -> this.iceberg();
			case SEA_STACKS -> this.seaStacks();
			case SHOALS -> this.shoals();
			case ATOLL -> this.atoll();
			case SEAMOUNT -> this.seamount();
		}
	}

	// ------------------------------------------------------------------ mesa rock forms (phase 6)

	/** The block at this height: the mesa style's band (MesaBands), or the landmark's stone outside a mesa style. */
	private BlockState band(int y) {
		MesaBands.Style style = MesaBands.of(this.piece.style);
		return style == null ? this.stone : style.at(y);
	}

	/**
	 * A butte (size: its radius; its height is the radius times tall) or, flat and wide, a table: steep
	 * sides of banded rock, a ragged outline that narrows a little upward, a flat top (a table's top is the style's "top",
	 * grass on Mesa Verde) and a slope of fallen rock round its foot.
	 */
	private void butte(double tall, boolean table) {
		MesaBands.Style style = MesaBands.of(this.piece.style);
		double radius = this.piece.size;
		int height = (int) Math.round(radius * tall) + 4;
		int topY = this.cy + height;
		double talus = 3 + radius * 0.25;
		for (int x = this.box.getMinX(); x <= this.box.getMaxX(); x++) {
			for (int z = this.box.getMinZ(); z <= this.box.getMaxZ(); z++) {
				double dx = x + 0.5 - this.cx;
				double dz = z + 0.5 - this.cz;
				double d = Math.sqrt(dx * dx + dz * dz);
				double angle = Math.atan2(dz, dx);
				double edge = radius * (1 + 0.18 * this.n2(Math.cos(angle) * 1.7, Math.sin(angle) * 1.7) + 0.08 * this.n2(x * 0.15, z * 0.15));
				int top;
				if (d <= edge) {
					// Narrowing upward: the rim is pulled in by up to a tenth near the top.
					top = topY - (d > edge * 0.9 ? (int) ((d - edge * 0.9) / (edge * 0.1) * height * 0.15) : 0);
				} else if (d <= edge + talus) {
					top = this.cy + (int) (height * 0.3 * (1 - (d - edge) / talus) * (0.8 + 0.2 * this.n2(x * 0.3, z * 0.3)));
				} else {
					continue;
				}
				int bottom = Math.min(this.ground(x, z) - 1, this.cy - 2);
				for (int y = bottom; y < top; y++) {
					this.set(x, y, z, this.band(y));
				}
				if (table && d <= edge && top >= topY - 1 && style != null && style.top() != null) {
					this.set(x, top - 1, z, style.top());
				}
			}
		}
	}

	/**
	 * A field of hoodoos (size: the field's radius): thin banded spires, 5 to 18 tall, waisted, each under a wider cap of
	 * the style's cap rock, standing on their own ground.
	 */
	private void hoodoos() {
		MesaBands.Style style = MesaBands.of(this.piece.style);
		BlockState cap = style != null && style.cap() != null ? style.cap() : this.stone;
		int count = 4 + this.piece.size / 2;
		for (int i = 0; i < count; i++) {
			double angle = this.random.nextDouble() * Math.PI * 2;
			double dist = Math.sqrt(this.random.nextDouble()) * this.piece.size;
			double hx = this.cx + Math.cos(angle) * dist;
			double hz = this.cz + Math.sin(angle) * dist;
			double r = 1.1 + this.random.nextDouble() * 1.4;
			int height = 5 + this.random.nextInt(14);
			for (int x = (int) Math.floor(hx - r - 2); x <= (int) Math.ceil(hx + r + 2); x++) {
				for (int z = (int) Math.floor(hz - r - 2); z <= (int) Math.ceil(hz + r + 2); z++) {
					if (!this.box.contains(x, this.box.getMinY(), z)) {
						continue;
					}
					double dx = x + 0.5 - hx;
					double dz = z + 0.5 - hz;
					double d2 = dx * dx + dz * dz;
					int ground = this.ground(x, z);
					for (int y = ground - 2; y < ground + height; y++) {
						double t = (y - ground) / (double) height;
						// Wide at the foot, waisted two thirds up.
						double rr = r * (1.25 - 0.45 * Math.sin(Math.PI * Math.min(1, t * 1.2))) + 0.35 * this.n3(x * 0.4, y * 0.3, z * 0.4);
						if (d2 < rr * rr) {
							this.set(x, y, z, this.band(y));
						}
					}
					double cr = r + 0.6;
					if (d2 < cr * cr) {
						this.set(x, ground + height, z, cap);
						if (d2 < r * r) {
							this.set(x, ground + height + 1, z, cap);
						}
					}
				}
			}
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
	 * A huge cone (size: its radius at the foot) of the local stone streaked with basalt and blackstone, rising up to 80 blocks
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
					this.set(x, y, z, m > 0.45 ? Blocks.BASALT.getDefaultState() : m < -0.55 ? Blocks.BLACKSTONE.getDefaultState() : this.stone);
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
					this.set(x, peak, z, (d < craterRadius * 1.4 && c < 0.3 ? Blocks.MAGMA_BLOCK : c < 0.45 ? Blocks.BASALT : c < 0.8 ? Blocks.GRAVEL : Blocks.BLACKSTONE).getDefaultState());
				} else if (dd < 0.7) {
					this.set(x, peak, z, (c < 0.4 ? Blocks.COARSE_DIRT : c < 0.65 ? Blocks.GRAVEL : c < 0.85 ? this.stone.getBlock() : Blocks.BLACKSTONE).getDefaultState());
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
	// ------------------------------------------------------------------ the warm seas (phase 6, batch D)

	/**
	 * A chain of three to seven small islands across the sea (size: the chain's length), strung along a gentle curve, one
	 * of them the largest: each a mound of the stone from the sea floor up, ragged at the edge, grass over dirt where it
	 * rises two or more above the sea, sand beaches round that, a sandy slope under the water, and mud flats in the
	 * shallows round some of them (where mangroves take root). The base (centre y) is the sea's surface. Never lowers the floor.
	 */
	private void islets() {
		int sea = this.cy;
		int count = 3 + this.random.nextInt(5);
		double angle = this.random.nextDouble() * Math.PI * 2;
		double ux = Math.cos(angle);
		double uz = Math.sin(angle);
		double bend = (this.random.nextDouble() - 0.5) * 0.03;
		int big = this.random.nextInt(count);
		double[][] isles = new double[count][];
		for (int i = 0; i < count; i++) {
			double s = (i / (double) (count - 1) - 0.5) * this.piece.size;
			double side = (this.random.nextDouble() - 0.5) * 14 + bend * s * s;
			double radius = i == big ? 9 + this.random.nextDouble() * 7 : 3.5 + this.random.nextDouble() * 5.5;
			double height = i == big ? 4 + this.random.nextDouble() * 5 : 1 + this.random.nextDouble() * radius * 0.45;
			isles[i] = new double[]{this.cx + ux * s - uz * side, this.cz + uz * s + ux * side, radius, height};
		}
		for (int x = this.box.getMinX(); x <= this.box.getMaxX(); x++) {
			for (int z = this.box.getMinZ(); z <= this.box.getMaxZ(); z++) {
				double best = -1e9;
				for (int i = 0; i < count; i++) {
					double[] isle = isles[i];
					double dx = x + 0.5 - isle[0];
					double dz = z + 0.5 - isle[1];
					double d = Math.sqrt(dx * dx + dz * dz);
					double edge = isle[2] * (1 + 0.3 * this.n2(x * 0.08 + i * 7, z * 0.08));
					double t = d < edge ? sea - 0.5 + (isle[3] + 0.5) * (1 - (d / edge) * (d / edge)) : sea - 0.5 - (d - edge) * 0.9;
					best = Math.max(best, t);
				}
				int top = (int) Math.floor(best);
				int ground = this.ground(x, z);
				if (top < ground) {
					continue;
				}
				boolean mudFlat = top >= sea - 3 && top < sea && this.n2(x * 0.05 + 50, z * 0.05) > 0.1;
				for (int y = ground - 1; y <= top; y++) {
					int depth = top - y;
					BlockState state;
					if (top >= sea + 2) {
						state = depth == 0 ? Blocks.GRASS_BLOCK.getDefaultState() : depth <= 2 ? Blocks.DIRT.getDefaultState() : this.stone;
					} else if (mudFlat) {
						state = depth <= 1 ? Blocks.MUD.getDefaultState() : depth <= 3 ? Blocks.SAND.getDefaultState() : this.stone;
					} else {
						state = depth <= 2 ? Blocks.SAND.getDefaultState() : depth <= 4 ? Blocks.SANDSTONE.getDefaultState() : this.stone;
					}
					this.set(x, y, z, state);
				}
			}
		}
	}

	private static final Block[] CORAL_BLOCKS = {Blocks.BRAIN_CORAL_BLOCK, Blocks.TUBE_CORAL_BLOCK, Blocks.FIRE_CORAL_BLOCK,
			Blocks.HORN_CORAL_BLOCK, Blocks.BUBBLE_CORAL_BLOCK};

	/**
	 * A coral reef shelf (size: its radius): a ragged, flat-topped platform of the stone rising from the sea floor to 3 to
	 * 6 blocks under the surface, its top sand with gravel and coral blocks in patches, deeper lagoons in it, its sides
	 * sloping down to the floor. The coral ocean's coral and sea pickles grow on it. The base (centre y) is the sea's surface.
	 */
	/**
	 * How far a sea landmark's outer slope has fallen this many blocks past its edge: steepening as it goes, so it meets the
	 * deep floor (y -30, 90 below the surface) within about 35 blocks, inside the landmark's box (LandmarkPiece), never cut
	 * off there as a flat wall.
	 */
	private static double apron(double out, double slope) {
		return out * slope + out * out * 0.05;
	}

	/** Whether a landmark at sea may put a block here: only into the sea's water or the air over it. */
	private boolean open(int x, int y, int z) {
		BlockState state = this.get(x, y, z);
		return state.isAir() || state.isOf(Blocks.WATER) || state.isOf(Blocks.ICE) || state.isOf(Blocks.SNOW) || state.isOf(Blocks.SEAGRASS)
				|| state.isOf(Blocks.TALL_SEAGRASS) || state.isOf(Blocks.KELP) || state.isOf(Blocks.KELP_PLANT);
	}

	/**
	 * An iceberg (phase 6, batch 1; size: its radius above water): a ragged hump of packed ice standing up to half
	 * of its radius out of the sea, two and a half times as deep and wider under water, a core of blue ice, snow on top.
	 */
	private void iceberg() {
		int sea = this.cy;
		double radius = this.piece.size;
		double high = 2 + radius * 0.4;
		double deep = high * 2.5;
		double wide = radius * 1.4;
		double coreY = sea - deep * 0.35;
		for (int x = this.box.getMinX(); x <= this.box.getMaxX(); x++) {
			for (int z = this.box.getMinZ(); z <= this.box.getMaxZ(); z++) {
				double dx = x + 0.5 - this.cx;
				double dz = z + 0.5 - this.cz;
				double d = Math.sqrt(dx * dx + dz * dz);
				double angle = Math.atan2(dz, dx);
				double above = radius * (1 + 0.3 * this.n2(Math.cos(angle) * 1.5, Math.sin(angle) * 1.5) + 0.1 * this.n2(x * 0.2, z * 0.2));
				double below = wide * (1 + 0.25 * this.n2(Math.cos(angle) * 1.5 + 40, Math.sin(angle) * 1.5));
				if (d >= below) {
					continue;
				}
				double t;
				if (d < above) {
					double f = d / above;
					t = sea + high * (1 - f * f) * (0.7 + 0.3 * this.n2(x * 0.15 + 20, z * 0.15)) - 1;
				} else {
					t = sea - 2 - (d - above) / Math.max(1, below - above) * deep * 0.5;
				}
				double b = sea - 1 - deep * Math.sqrt(Math.max(0, 1 - (d / below) * (d / below)));
				int top = (int) Math.floor(t);
				int bottom = Math.max((int) Math.ceil(b), this.ground(x, z));
				for (int y = bottom; y <= top; y++) {
					if (!this.open(x, y, z)) {
						continue;
					}
					double cy = (y - coreY) / ((high + deep) * 0.45);
					double cd = d / (below * 0.55);
					BlockState state;
					if (cd * cd + cy * cy < 1) {
						state = Blocks.BLUE_ICE.getDefaultState();
					} else if (y == top && y >= sea && this.chance(x, y, z, 31) < 0.7) {
						state = Blocks.SNOW_BLOCK.getDefaultState();
					} else {
						state = Blocks.PACKED_ICE.getDefaultState();
					}
					this.set(x, y, z, state);
				}
			}
		}
	}

	/**
	 * Sea stacks (phase 6, batch 1; size: the cluster's radius): two to five towers of the landmark's stone standing from
	 * the sea floor 8 to 28 blocks out of the sea, steep with a smooth outline and a nearly flat top, wider under water, patches
	 * of moss at the waterline, grass (a clump or two of gravel) on top where seabirds roost; in half the clusters an arch
	 * joins the first two.
	 */
	private void seaStacks() {
		int sea = this.cy;
		int count = 2 + this.random.nextInt(4);
		double[][] stacks = new double[count][];
		for (int i = 0; i < count; i++) {
			double angle = this.random.nextDouble() * Math.PI * 2;
			double r = (i == 0 ? 0 : 0.4 + this.random.nextDouble() * 0.6) * this.piece.size * 0.8;
			double radius = 2 + this.random.nextDouble() * 2.5;
			double height = 8 + this.random.nextDouble() * 12 + radius * 2;
			stacks[i] = new double[]{this.cx + Math.cos(angle) * r, this.cz + Math.sin(angle) * r, radius, height};
		}
		boolean arch = count >= 2 && this.random.nextBoolean();
		double archY = arch ? sea + Math.min(stacks[0][3], stacks[1][3]) * 0.6 : 0;
		BlockState grass = Blocks.GRASS_BLOCK.getDefaultState();
		for (int x = this.box.getMinX(); x <= this.box.getMaxX(); x++) {
			for (int z = this.box.getMinZ(); z <= this.box.getMaxZ(); z++) {
				double best = -1e9;
				for (int i = 0; i < count; i++) {
					double[] stack = stacks[i];
					double dx = x + 0.5 - stack[0];
					double dz = z + 0.5 - stack[1];
					double d = Math.sqrt(dx * dx + dz * dz);
					// A smooth outline (by the angle round the stack, not block by block) and a nearly flat top that rounds
					// off a block or two at the rim, tilting a little across the cluster.
					double angle = Math.atan2(dz, dx);
					double edge = stack[2] * (1 + 0.2 * this.n2(Math.cos(angle) * 1.2 + i * 13, Math.sin(angle) * 1.2));
					double t;
					if (d < edge) {
						double rim = d / edge > 0.7 ? (d / edge - 0.7) * 5 : 0;
						t = sea + stack[3] - rim + 1.5 * this.n2(x * 0.06 + i * 7, z * 0.06);
					} else if (d < edge * 1.8) {
						// Wider under water: an apron of rock down to the floor.
						t = sea - 2 - (d - edge) * 2.5;
					} else {
						continue;
					}
					best = Math.max(best, t);
				}
				int ground = this.ground(x, z);
				int top = (int) Math.floor(best);
				for (int y = ground; y <= top; y++) {
					if (this.open(x, y, z)) {
						BlockState state = this.stone;
						if (y == top && y >= sea + 2) {
							// Grass on top, gravel in a clump or two.
							state = this.n2(x * 0.18 + 50, z * 0.18) > 0.55 ? Blocks.GRAVEL.getDefaultState() : grass;
						} else if (y >= sea - 2 && y <= sea + 1 && this.n3(x * 0.2, y * 0.3, z * 0.2) > 0.25) {
							// Worn at the waterline: mossy cobblestone in patches.
							state = Blocks.MOSSY_COBBLESTONE.getDefaultState();
						}
						this.set(x, y, z, state);
					}
				}
				if (arch) {
					// The arch: a thick span between the first two stacks.
					double ax = stacks[1][0] - stacks[0][0];
					double az = stacks[1][1] - stacks[0][1];
					double length = Math.max(1, Math.sqrt(ax * ax + az * az));
					double along = ((x + 0.5 - stacks[0][0]) * ax + (z + 0.5 - stacks[0][1]) * az) / (length * length);
					if (along > 0 && along < 1) {
						double px = stacks[0][0] + ax * along;
						double pz = stacks[0][1] + az * along;
						double off = Math.sqrt(Math.pow(x + 0.5 - px, 2) + Math.pow(z + 0.5 - pz, 2));
						if (off < 1.8) {
							double sag = Math.sin(along * Math.PI) * 2;
							int low = (int) Math.floor(archY + sag - 1);
							for (int y = low; y <= low + 2; y++) {
								if (this.open(x, y, z)) {
									this.set(x, y, z, y == low + 2 ? grass : this.stone);
								}
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Shoals (phase 6, batch 2; size: the field's radius): a broad sandy platform 3 to 6 under the surface, its edges
	 * sloping to the floor, and on it two to four long sandbars whose crests break the surface, gravel in patches.
	 */
	private void shoals() {
		int sea = this.cy;
		double radius = this.piece.size;
		int count = 2 + this.random.nextInt(3);
		double[][] bars = new double[count][];
		for (int i = 0; i < count; i++) {
			double angle = this.random.nextDouble() * Math.PI * 2;
			double r = this.random.nextDouble() * radius * 0.6;
			double heading = this.random.nextDouble() * Math.PI;
			double length = radius * (0.5 + this.random.nextDouble() * 0.5);
			double width = 2.5 + this.random.nextDouble() * 3;
			bars[i] = new double[]{this.cx + Math.cos(angle) * r, this.cz + Math.sin(angle) * r, Math.cos(heading), Math.sin(heading), length, width};
		}
		for (int x = this.box.getMinX(); x <= this.box.getMaxX(); x++) {
			for (int z = this.box.getMinZ(); z <= this.box.getMaxZ(); z++) {
				double dx = x + 0.5 - this.cx;
				double dz = z + 0.5 - this.cz;
				double d = Math.sqrt(dx * dx + dz * dz);
				double angle = Math.atan2(dz, dx);
				double edge = radius * (1 + 0.25 * this.n2(Math.cos(angle) * 1.3, Math.sin(angle) * 1.3));
				// The platform: 3 to 6 under the surface, sloping away past its edge.
				double t = d < edge ? sea - 4.5 + 1.5 * this.n2(x * 0.05 + 40, z * 0.05) : sea - 4.5 - apron(d - edge, 1.2);
				for (double[] bar : bars) {
					double bx = x + 0.5 - bar[0];
					double bz = z + 0.5 - bar[1];
					double along = (bx * bar[2] + bz * bar[3]) / bar[4];
					double across = (-bx * bar[3] + bz * bar[2]) / bar[5];
					double e = along * along + across * across;
					if (e < 1) {
						// Crest just above the water, sloping down to the platform.
						t = Math.max(t, sea + 0.3 - e * 5);
					} else {
						// Past its end (where it runs off the platform), on down to the floor rather than stopping in a wall: the
						// distance out from the bar's outline along the line from its middle.
						double out = Math.sqrt(bx * bx + bz * bz) * (1 - 1 / Math.sqrt(e));
						t = Math.max(t, sea - 4.7 - apron(out, 1.2));
					}
				}
				int top = (int) Math.floor(t);
				int ground = this.ground(x, z);
				for (int y = ground; y <= top; y++) {
					if (this.open(x, y, z)) {
						boolean gravel = y == top && y < sea - 1 && this.n2(x * 0.15 + 70, z * 0.15) > 0.5;
						this.set(x, y, z, (gravel ? Blocks.GRAVEL : y >= top - 3 ? Blocks.SAND : this.stone.getBlock()).getDefaultState());
					}
				}
			}
		}
	}

	/**
	 * An atoll (phase 6, batch 3; size: the ring's radius): a ring of reef 4 to 7 wide just at the surface round a calm sandy
	 * lagoon 4 to 8 deep, its outside sloping to the floor; on the ring a sandy islet or two rising a few blocks out of the
	 * sea (the atoll's palms grow on them), coral blocks on the reef.
	 */
	private void atoll() {
		int sea = this.cy;
		double radius = this.piece.size;
		double width = 4 + this.random.nextDouble() * 3;
		int islets = 1 + this.random.nextInt(2);
		double[] isletAngles = new double[islets];
		for (int i = 0; i < islets; i++) {
			isletAngles[i] = this.random.nextDouble() * Math.PI * 2;
		}
		for (int x = this.box.getMinX(); x <= this.box.getMaxX(); x++) {
			for (int z = this.box.getMinZ(); z <= this.box.getMaxZ(); z++) {
				double dx = x + 0.5 - this.cx;
				double dz = z + 0.5 - this.cz;
				double d = Math.sqrt(dx * dx + dz * dz);
				double angle = Math.atan2(dz, dx);
				double ring = radius * (1 + 0.15 * this.n2(Math.cos(angle) * 1.4, Math.sin(angle) * 1.4));
				double across = Math.abs(d - ring);
				double t;
				boolean reefTop = false;
				if (across < width / 2) {
					// The reef: just under the surface, the islets out of it.
					t = sea - 1.5 + 0.8 * this.n2(x * 0.2, z * 0.2);
					reefTop = true;
					for (double a : isletAngles) {
						double off = Math.abs(Math.atan2(Math.sin(angle - a), Math.cos(angle - a))) * ring;
						if (off < 9) {
							t = Math.max(t, sea + 2.5 * (1 - off / 9) - across * 0.3);
						}
					}
				} else if (d < ring) {
					// The lagoon: a calm sandy floor, shallower toward the reef.
					t = sea - 8 + 4 * (1 - (ring - d) / ring) + this.n2(x * 0.05 + 30, z * 0.05);
				} else {
					t = sea - 2 - apron(across - width / 2, 1.4);
				}
				int top = (int) Math.floor(t);
				int ground = this.ground(x, z);
				for (int y = ground; y <= top; y++) {
					if (this.open(x, y, z)) {
						BlockState state;
						if (y == top && reefTop && y < sea - 1 && this.chance(x, y, z, 51) < 0.35) {
							state = CORAL_BLOCKS[(int) (this.chance(x, y, z, 52) * CORAL_BLOCKS.length)].getDefaultState();
						} else {
							state = y >= top - 3 ? Blocks.SAND.getDefaultState() : this.stone;
						}
						this.set(x, y, z, state);
					}
				}
			}
		}
	}

	/**
	 * A seamount (phase 6, batch 3; size: its radius at the foot): an undersea mountain from the deep floor to 4 to 9 blocks
	 * under the surface, steep and ridged, of the landmark's stone with gravel and sand on its flat top.
	 */
	private void seamount() {
		int sea = this.cy;
		double radius = this.piece.size;
		int summit = sea - 4 - this.random.nextInt(6);
		for (int x = this.box.getMinX(); x <= this.box.getMaxX(); x++) {
			for (int z = this.box.getMinZ(); z <= this.box.getMaxZ(); z++) {
				double dx = x + 0.5 - this.cx;
				double dz = z + 0.5 - this.cz;
				double d = Math.sqrt(dx * dx + dz * dz);
				double angle = Math.atan2(dz, dx);
				double edge = radius * (1 + 0.2 * this.n2(Math.cos(angle) * 2, Math.sin(angle) * 2) + 0.08 * this.n2(x * 0.08, z * 0.08));
				if (d >= edge) {
					continue;
				}
				int ground = this.ground(x, z);
				double f = d / edge;
				// A broad flat top (a fifth of the radius), then flanks steepening outward.
				double t = f < 0.2 ? summit : summit - (summit - ground) * Math.pow((f - 0.2) / 0.8, 0.8);
				int top = (int) Math.floor(t + 1.5 * this.n2(x * 0.15 + 9, z * 0.15));
				for (int y = ground; y <= top; y++) {
					if (this.open(x, y, z)) {
						boolean flat = f < 0.25 && y >= top - 1;
						this.set(x, y, z, flat ? (this.chance(x, y, z, 61) < 0.5 ? Blocks.GRAVEL : Blocks.SAND).getDefaultState() : this.stone);
					}
				}
			}
		}
	}

	private void reef() {
		int sea = this.cy;
		double radius = this.piece.size;
		for (int x = this.box.getMinX(); x <= this.box.getMaxX(); x++) {
			for (int z = this.box.getMinZ(); z <= this.box.getMaxZ(); z++) {
				double dx = x + 0.5 - this.cx;
				double dz = z + 0.5 - this.cz;
				double d = Math.sqrt(dx * dx + dz * dz);
				double edge = radius * (1 + 0.35 * this.n2(x * 0.04, z * 0.04));
				double t;
				if (d < edge) {
					t = sea - 3 - 1.5 * (1 + this.n2(x * 0.1 + 30, z * 0.1));
					if (this.n2(x * 0.07 + 90, z * 0.07) > 0.5) {
						t -= 4;
					}
				} else {
					t = sea - 3 - apron(d - edge, 1.3);
				}
				int top = (int) Math.floor(t);
				int ground = this.ground(x, z);
				if (top < ground) {
					continue;
				}
				for (int y = ground - 1; y <= top; y++) {
					int depth = top - y;
					BlockState state;
					if (depth == 0 && this.chance(x, y, z, 11) < 0.08) {
						state = CORAL_BLOCKS[(int) (this.chance(x, y, z, 12) * CORAL_BLOCKS.length)].getDefaultState();
					} else if (depth == 0 && this.n2(x * 0.12 + 70, z * 0.12) > 0.45) {
						state = Blocks.GRAVEL.getDefaultState();
					} else {
						state = depth <= 2 ? Blocks.SAND.getDefaultState() : this.stone;
					}
					this.set(x, y, z, state);
				}
			}
		}
	}
}
