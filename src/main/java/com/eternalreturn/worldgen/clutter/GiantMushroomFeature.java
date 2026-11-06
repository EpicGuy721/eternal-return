package com.eternalreturn.worldgen.clutter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ConnectingBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.HashMap;
import java.util.Map;

/**
 * A giant mushroom (feature eternalreturn:giant_mushroom, worldgen expansion phase 6, batch D: the giant mushroom island
 * and the roofed fungi forest), far taller than vanilla's huge mushrooms: a stem of mushroom stem, one block thick or a
 * plus of five from 16 tall, leaning a little the taller it is; a red cap is a dome with a skirt, a brown cap a wide flat
 * disc drooping at the rim, and tall brown ones carry bracket shelves on the stem. Faces touching another mushroom
 * block are pores, as vanilla's are. Stands on any dirt (mycelium, podzol, grass); grows through leaves, never through
 * logs or other solid blocks (it gives up if its stem is blocked). Config: "cap" (red or brown), "min_height", "max_height".
 */
public final class GiantMushroomFeature extends Feature<GiantMushroomFeature.Config> {
	public record Config(String cap, int minHeight, int maxHeight) implements FeatureConfig {
		public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("cap").forGetter(Config::cap),
				Codec.intRange(4, 64).fieldOf("min_height").forGetter(Config::minHeight),
				Codec.intRange(4, 64).fieldOf("max_height").forGetter(Config::maxHeight)).apply(instance, Config::new));
	}

	public GiantMushroomFeature() {
		super(Config.CODEC);
	}

	private static boolean free(StructureWorldAccess world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return state.isAir() || state.isReplaceable() || state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.FLOWERS)
				|| state.isOf(Blocks.RED_MUSHROOM) || state.isOf(Blocks.BROWN_MUSHROOM);
	}

	@Override
	public boolean generate(FeatureContext<Config> context) {
		StructureWorldAccess world = context.getWorld();
		Random random = context.getRandom();
		Config config = context.getConfig();
		BlockPos origin = context.getOrigin();
		if (!world.getBlockState(origin.down()).isIn(BlockTags.DIRT)) {
			return false;
		}
		int height = config.minHeight() + random.nextInt(Math.max(1, config.maxHeight() - config.minHeight() + 1));
		if (origin.getY() + height + 12 >= world.getTopY()) {
			return false;
		}
		boolean red = config.cap().equals("red");
		boolean thick = height >= 16;
		double lean = height >= 12 ? random.nextDouble() * Math.min(2.5, height / 10.0) : 0;
		double leanAngle = random.nextDouble() * Math.PI * 2;
		Map<BlockPos, Block> blocks = new HashMap<>();
		// The cap's centre: where the stem ends.
		int capRadius = red ? (int) Math.round(3 + height / 6.0) : (int) Math.round(4 + height / 4.0);
		int stemTop = red ? height - (int) (capRadius / 1.3) : height - 1;
		int topX = 0;
		int topZ = 0;
		for (int y = 0; y <= stemTop; y++) {
			double frac = y / (double) height;
			int ox = (int) Math.round(Math.cos(leanAngle) * lean * frac * frac);
			int oz = (int) Math.round(Math.sin(leanAngle) * lean * frac * frac);
			topX = ox;
			topZ = oz;
			for (int[] o : thick ? new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}} : new int[][]{{0, 0}}) {
				BlockPos p = origin.add(ox + o[0], y, oz + o[1]);
				if (!free(world, p)) {
					return false;
				}
				blocks.put(p, Blocks.MUSHROOM_STEM);
			}
		}
		BlockPos top = origin.add(topX, height, topZ);
		if (red) {
			for (int dx = -capRadius; dx <= capRadius; dx++) {
				for (int dz = -capRadius; dz <= capRadius; dz++) {
					for (int dy = -2; dy <= capRadius; dy++) {
						double flat = Math.sqrt(dx * dx + dz * dz);
						double e = Math.sqrt(dx * dx + dz * dz + Math.max(0, dy) * Math.max(0, dy) * 1.69);
						boolean shell = dy >= 0 ? e < capRadius && e >= capRadius - 1.3 : flat < capRadius && flat >= capRadius - 1.3;
						// The very top is closed.
						shell |= dy >= 0 && e < capRadius && (dy + 1) * 1.3 >= capRadius - 0.3;
						if (shell) {
							blocks.put(top.add(dx, dy - (int) (capRadius / 1.3), dz), Blocks.RED_MUSHROOM_BLOCK);
						}
					}
				}
			}
		} else {
			for (int dx = -capRadius; dx <= capRadius; dx++) {
				for (int dz = -capRadius; dz <= capRadius; dz++) {
					double flat = Math.sqrt(dx * dx + dz * dz);
					if (flat >= capRadius + 0.3) {
						continue;
					}
					blocks.put(top.add(dx, flat >= capRadius - 1.2 ? -1 : 0, dz), Blocks.BROWN_MUSHROOM_BLOCK);
				}
			}
			// Bracket shelves on a tall stem: half discs sticking out on one side each.
			if (height >= 16) {
				int shelves = 1 + random.nextInt(3);
				for (int i = 0; i < shelves; i++) {
					int y = (int) (height * (0.3 + random.nextDouble() * 0.4));
					Direction side = Direction.Type.HORIZONTAL.random(random);
					int r = 2 + random.nextInt(2);
					double frac = y / (double) height;
					BlockPos at = origin.add((int) Math.round(Math.cos(leanAngle) * lean * frac * frac), y, (int) Math.round(Math.sin(leanAngle) * lean * frac * frac));
					for (int dx = -r - 1; dx <= r + 1; dx++) {
						for (int dz = -r - 1; dz <= r + 1; dz++) {
							int out = dx * side.getOffsetX() + dz * side.getOffsetZ();
							if (out >= 1 && dx * dx + dz * dz <= (r + 1) * (r + 1)) {
								blocks.putIfAbsent(at.add(dx, 0, dz), Blocks.BROWN_MUSHROOM_BLOCK);
							}
						}
					}
				}
			}
		}
		for (Map.Entry<BlockPos, Block> entry : blocks.entrySet()) {
			BlockPos p = entry.getKey();
			if (entry.getValue() != Blocks.MUSHROOM_STEM && !free(world, p)) {
				continue;
			}
			BlockState state = entry.getValue().getDefaultState();
			for (Direction d : Direction.values()) {
				state = state.with(ConnectingBlock.FACING_PROPERTIES.get(d), !blocks.containsKey(p.offset(d)));
			}
			world.setBlockState(p, state, Block.NOTIFY_LISTENERS);
		}
		return true;
	}
}
