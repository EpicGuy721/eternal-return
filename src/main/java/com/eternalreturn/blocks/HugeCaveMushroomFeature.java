package com.eternalreturn.blocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.MushroomBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.HashSet;
import java.util.Set;

/**
 * Huge mushrooms of any cap block (eternalreturn:huge_cave_mushroom; CaveMushrooms): "flat" is vanilla's huge brown
 * mushroom (a flat cap of radius 3 on a stem 4 to 6 tall, one in 12 twice as tall), "dome" vanilla's huge red one (a dome of
 * radius 2), "cone" the blue mushroom's (the owner's photo of Entoloma hochstetteri): a thin stem 8 to 14 tall under a
 * small, steep, pointed cone 5 wide and 4 or 5 tall, hollow underneath (its pores, the gills). Cap faces touching another
 * cap block or the stem are pores, as vanilla's are where blocks meet. "any_ground" lets it stand on any solid floor (the
 * mushroom caves, which have no mycelium), and there a shorter stem (to vanilla's shortest, 4, or the cone's 8) is tried
 * where the drawn one doesn't fit under the ceiling, and the cap may press into the cave's wall (the blocks that would be
 * inside the rock left out) as long as the middle over the stem and 6 in 10 of it are clear; otherwise it needs dirt or mycelium, as vanilla's (bone meal).
 */
public class HugeCaveMushroomFeature extends Feature<HugeCaveMushroomFeature.Config> {
	public enum Shape implements StringIdentifiable {
		FLAT, DOME, CONE;

		public static final Codec<Shape> CODEC = StringIdentifiable.createCodec(Shape::values);

		@Override
		public String asString() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public record Config(Shape shape, BlockState cap, BlockState stem, boolean anyGround) implements FeatureConfig {
		public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Shape.CODEC.fieldOf("shape").forGetter(Config::shape),
				BlockState.CODEC.fieldOf("cap").forGetter(Config::cap),
				BlockState.CODEC.fieldOf("stem").forGetter(Config::stem),
				Codec.BOOL.optionalFieldOf("any_ground", false).forGetter(Config::anyGround)).apply(instance, Config::new));
	}

	public HugeCaveMushroomFeature() {
		super(Config.CODEC);
	}

	private static boolean free(StructureWorldAccess world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return state.isAir() || state.isIn(BlockTags.LEAVES) || (state.isReplaceable() && state.getFluidState().isEmpty());
	}

	@Override
	public boolean generate(FeatureContext<Config> context) {
		StructureWorldAccess world = context.getWorld();
		BlockPos origin = context.getOrigin();
		Random random = context.getRandom();
		Config config = context.getConfig();
		BlockState ground = world.getBlockState(origin.down());
		boolean soil = isSoil(ground) || ground.isIn(BlockTags.MUSHROOM_GROW_BLOCK);
		if (!soil && !(config.anyGround() && ground.isSideSolidFullSquare(world, origin.down(), Direction.UP))) {
			return false;
		}
		// The height drawn first; in the caves ("any_ground") a shorter stem is tried when that doesn't fit under the
		// ceiling, down to vanilla's shortest (4; the cone 8).
		boolean cone = config.shape() == Shape.CONE;
		int drawn = cone ? 8 + random.nextInt(7) : vanillaHeight(random);
		int skirt = random.nextInt(2) + 1;
		int lowest = config.anyGround() ? (cone ? 8 : 4) : drawn;
		Set<BlockPos> cap = null;
		int stemHeight = drawn;
		for (int h = drawn; h >= lowest && cap == null; h--) {
			if (origin.getY() <= world.getBottomY() || origin.getY() + h + 4 >= world.getTopY()) {
				return false;
			}
			Set<BlockPos> shape = capShape(config.shape(), origin, h, skirt);
			boolean fits = true;
			for (int y = 0; y < h && fits; y++) {
				fits = free(world, origin.up(y));
			}
			// In the caves the cap may press into the cave's wall (its blocks inside the rock left out), as long as its
			// middle (the 3 x 3 over the stem) is clear and most of it is; elsewhere all of it must be clear.
			Set<BlockPos> open = new HashSet<>();
			for (BlockPos pos : shape) {
				boolean middle = Math.abs(pos.getX() - origin.getX()) <= 1 && Math.abs(pos.getZ() - origin.getZ()) <= 1;
				if (free(world, pos)) {
					open.add(pos);
				} else if (middle || !config.anyGround()) {
					fits = false;
				}
			}
			if (fits && open.size() * 10 >= shape.size() * 6) {
				cap = open;
				stemHeight = h;
			}
		}
		if (cap == null) {
			return false;
		}
		Set<BlockPos> stem = new HashSet<>();
		for (int y = 0; y < stemHeight; y++) {
			stem.add(origin.up(y));
		}
		for (BlockPos pos : cap) {
			BlockState state = config.cap();
			for (Direction direction : Direction.values()) {
				BlockPos next = pos.offset(direction);
				// Undersides show pores (the gills), as vanilla's caps do.
				boolean skin = direction != Direction.DOWN && !cap.contains(next) && !stem.contains(next);
				net.minecraft.state.property.BooleanProperty face = face(direction);
				state = state.contains(face) ? state.with(face, skin) : state;
			}
			this.setBlockState(world, pos, state);
		}
		for (BlockPos pos : stem) {
			if (!cap.contains(pos)) {
				this.setBlockState(world, pos, config.stem());
			}
		}
		return true;
	}

	/** The cap's blocks for a stem this tall. */
	private static Set<BlockPos> capShape(Shape shape, BlockPos origin, int stemHeight, int skirt) {
		Set<BlockPos> cap = new HashSet<>();
		switch (shape) {
			case FLAT -> {
				int r = 3;
				for (int x = -r; x <= r; x++) {
					for (int z = -r; z <= r; z++) {
						if (!(Math.abs(x) == r && Math.abs(z) == r)) {
							cap.add(origin.add(x, stemHeight, z));
						}
					}
				}
			}
			case DOME -> {
				int r = 2;
				for (int y = stemHeight - 3; y <= stemHeight; y++) {
					int j = y < stemHeight ? r : r - 1;
					for (int x = -j; x <= j; x++) {
						for (int z = -j; z <= j; z++) {
							boolean edgeX = Math.abs(x) == j;
							boolean edgeZ = Math.abs(z) == j;
							if (y >= stemHeight || edgeX != edgeZ) {
								cap.add(origin.add(x, y, z));
							}
						}
					}
				}
			}
			default -> {
				// Cone: the stem runs up into the hollow skirt; from the top down, an apex, a plus, a 3 x 3 and one or two
				// rings 5 wide without corners.
				int apex = stemHeight + 2;
				cap.add(origin.up(apex));
				for (Direction d : Direction.Type.HORIZONTAL) {
					cap.add(origin.up(apex - 1).offset(d));
				}
				cap.add(origin.up(apex - 1));
				for (int x = -1; x <= 1; x++) {
					for (int z = -1; z <= 1; z++) {
						cap.add(origin.add(x, apex - 2, z));
					}
				}
				for (int s = 0; s < skirt; s++) {
					for (int x = -2; x <= 2; x++) {
						for (int z = -2; z <= 2; z++) {
							boolean ring = Math.abs(x) == 2 || Math.abs(z) == 2;
							if (ring && !(Math.abs(x) == 2 && Math.abs(z) == 2)) {
								cap.add(origin.add(x, apex - 3 - s, z));
							}
						}
					}
				}
			}
		}
		return cap;
	}

	/** Vanilla's huge mushroom height: 4 to 6, one in 12 twice that. */
	private static int vanillaHeight(Random random) {
		int height = random.nextInt(3) + 4;
		return random.nextInt(12) == 0 ? height * 2 : height;
	}

	private static net.minecraft.state.property.BooleanProperty face(Direction direction) {
		return switch (direction) {
			case UP -> MushroomBlock.UP;
			case DOWN -> MushroomBlock.DOWN;
			case NORTH -> MushroomBlock.NORTH;
			case SOUTH -> MushroomBlock.SOUTH;
			case EAST -> MushroomBlock.EAST;
			case WEST -> MushroomBlock.WEST;
		};
	}
}
