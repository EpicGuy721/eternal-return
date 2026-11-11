package com.eternalreturn.gametest;

import com.eternalreturn.blocks.CactusArmBlock;
import com.eternalreturn.blocks.CactusArms;
import com.eternalreturn.worldgen.cactus.CactusShapes;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cactus arms and cactus shapes (worldgen expansion 2, phase 2; CactusArmBlock, CactusShapes): an arm goes on the side
 * of a cactus (joined to it) or on top of an arm (then joined to nothing, even beside a trunk), nowhere else; cactus
 * keeps vanilla's rules (no cactus beside cactus); break the trunk and its arms come down and drop; every shape's plan
 * is a trunk on the sand with arms out one and up; grown a block at a time, each shape ends as the shape placed whole.
 * Flat runs (batch cacti).
 */
public class CactusTests implements FabricGameTest {
	private static final String BATCH = "cacti";
	private static final String ARENA = "eternalreturn_test:arena";

	private static void sand(TestContext ctx) {
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 40; z++) {
				// Stone under the sand, or it falls (and the cacti with it).
				ctx.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
				ctx.setBlockState(new BlockPos(x, 1, z), Blocks.SAND);
				for (int y = 2; y < 14; y++) {
					ctx.setBlockState(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
	}

	private static CactusArmBlock arm() {
		return (CactusArmBlock) CactusArms.CACTUS_ARM;
	}

	/** An arm as a player places it: joined as it stands, the neighbours told. False if it can't go there. */
	private static boolean placeArm(ServerWorld world, BlockPos pos) {
		BlockState state = arm().joined(arm().getDefaultState(), world, pos);
		if (!state.canPlaceAt(world, pos)) {
			return false;
		}
		world.setBlockState(pos, state, Block.NOTIFY_ALL);
		return true;
	}

	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true, tickLimit = 100)
	public void armsGoOnTrunksAndArms(TestContext ctx) {
		sand(ctx);
		ServerWorld world = ctx.getWorld();
		BlockPos base = ctx.getAbsolutePos(new BlockPos(6, 2, 6));
		for (int y = 0; y < 3; y++) {
			world.setBlockState(base.up(y), Blocks.CACTUS.getDefaultState(), Block.NOTIFY_ALL);
		}
		BlockPos out = base.up().east();
		ctx.assertTrue(placeArm(world, out), "an arm won't go on the side of a cactus");
		ctx.assertTrue(world.getBlockState(out).get(CactusArmBlock.side(Direction.WEST)), "the arm isn't joined to the trunk");
		ctx.assertTrue(placeArm(world, out.up()), "an arm won't go on top of an arm");
		BlockState upright = world.getBlockState(out.up());
		ctx.assertTrue(!upright.get(CactusArmBlock.side(Direction.WEST)), "an arm on an arm joined the trunk beside it");
		ctx.assertTrue(!placeArm(world, base.up(3)), "an arm went on top of a cactus");
		ctx.assertTrue(!placeArm(world, ctx.getAbsolutePos(new BlockPos(12, 2, 12))), "an arm went on the bare sand");
		ctx.assertTrue(!placeArm(world, out.east()), "an arm went on the side of an arm");
		// Cactus keeps vanilla's rules: none beside a cactus, but one beside an arm is fine.
		ctx.assertTrue(!Blocks.CACTUS.getDefaultState().canPlaceAt(world, base.west()), "a cactus can stand beside a cactus");
		BlockPos low = base.west();
		ctx.assertTrue(placeArm(world, low), "an arm won't go on the side of a trunk's foot");
		ctx.assertTrue(Blocks.CACTUS.getDefaultState().canPlaceAt(world, low.west()), "a cactus can't stand beside an arm");
		boolean[] broke = {false};
		ctx.waitAndRun(5, () -> {
			// Once: the trunk is broken on purpose below.
			if (broke[0]) {
				return;
			}
			broke[0] = true;
			ctx.assertTrue(CactusArms.isArm(world.getBlockState(out)) && CactusArms.isArm(world.getBlockState(out.up())), "a held arm fell");
			world.breakBlock(base, true);
			ctx.runAtEveryTick(() -> {
				for (BlockPos pos : List.of(base.up(), base.up(2), out, out.up(), low)) {
					if (!world.getBlockState(pos).isAir()) {
						return;
					}
				}
				ctx.assertTrue(dropped(world, base, Items.CACTUS) == 3 && dropped(world, base, CactusArms.CACTUS_ARM_ITEM) == 3,
						"3 cacti and 3 arms dropped, got " + dropped(world, base, Items.CACTUS) + " and " + dropped(world, base, CactusArms.CACTUS_ARM_ITEM));
				ctx.complete();
			});
		});
	}

	private static int dropped(ServerWorld world, BlockPos near, Item item) {
		int count = 0;
		for (ItemEntity entity : world.getEntitiesByClass(ItemEntity.class, new Box(near).expand(6), entity -> entity.getStack().isOf(item))) {
			count += entity.getStack().getCount();
		}
		return count;
	}

	/** Every kind's plans: a trunk of cactus on the root, arms out one from it and up from arms, no cactus beside cactus. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void plansAreTrunksWithArms(TestContext ctx) {
		for (CactusShapes.Kind kind : CactusShapes.Kind.values()) {
			CactusShapes.Shape shape = new CactusShapes.Shape(kind, kind == CactusShapes.Kind.BARREL ? 1 : kind == CactusShapes.Kind.SAGUARO ? 5 : 3, kind == CactusShapes.Kind.SAGUARO ? 9 : 5, 2, 5);
			for (int seed = 0; seed < 40; seed++) {
				List<CactusShapes.Node> plan = CactusShapes.plan(shape, new BlockPos(seed * 37, 64, seed * -91));
				Map<BlockPos, CactusShapes.Node> seen = new HashMap<>();
				int arms = 0;
				for (CactusShapes.Node node : plan) {
					ctx.assertTrue(!seen.containsKey(node.at()), kind + ": two blocks at " + node.at().toShortString());
					seen.put(node.at(), node);
					ctx.assertTrue(node.at().getY() >= 0, kind + ": under the ground");
					if (node.from() == null) {
						ctx.assertTrue(node.at().equals(BlockPos.ORIGIN) && !node.arm(), kind + ": a second root");
						continue;
					}
					CactusShapes.Node from = seen.get(node.from());
					ctx.assertTrue(from != null, kind + ": " + node.at().toShortString() + " grows from nothing before it");
					if (node.arm()) {
						arms++;
						boolean out = !from.arm() && node.from().getY() == node.at().getY() && node.from().getManhattanDistance(node.at()) == 1;
						boolean up = from.arm() && node.from().equals(node.at().down());
						ctx.assertTrue(out || up, kind + ": an arm at " + node.at().toShortString() + " neither out of the trunk nor on an arm");
					} else {
						ctx.assertTrue(!from.arm() && node.from().equals(node.at().down()) && node.at().getX() == 0 && node.at().getZ() == 0,
								kind + ": cactus off the trunk at " + node.at().toShortString());
					}
				}
				ctx.assertTrue(arms > 0, kind + ": no arms");
			}
		}
		ctx.complete();
	}

	/** Grown a block at a time from one cactus, each shape ends as the shape placed whole, and every piece of it holds. */
	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true)
	public void growingEndsAsTheWholeShape(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		for (CactusShapes.Kind kind : CactusShapes.Kind.values()) {
			// Each shape on its own, the arena cleared between.
			sand(ctx);
			CactusShapes.Shape shape = new CactusShapes.Shape(kind, kind == CactusShapes.Kind.BARREL ? 1 : kind == CactusShapes.Kind.SAGUARO ? 5 : 3, kind == CactusShapes.Kind.SAGUARO ? 9 : 5, 2, 4);
			BlockPos root = ctx.getAbsolutePos(new BlockPos(7, 2, 20));
			world.setBlockState(root, Blocks.CACTUS.getDefaultState(), Block.NOTIFY_ALL);
			int steps = 0;
			while (CactusShapes.growNext(world, root, shape) && steps < 200) {
				steps++;
			}
			Map<BlockPos, Boolean> grown = blocks(world, root);
			for (BlockPos pos : grown.keySet()) {
				ctx.assertTrue(world.getBlockState(root.add(pos)).canPlaceAt(world, root.add(pos)), kind + ": a grown piece at " + pos.toShortString() + " isn't held");
				ctx.assertTrue(root.equals(CactusShapes.root(world, root.add(pos))), kind + ": " + pos.toShortString() + " doesn't lead back to the root");
			}
			sand(ctx);
			ctx.assertTrue(CactusShapes.placeWhole(world, root, shape), kind + ": nothing placed");
			Map<BlockPos, Boolean> whole = blocks(world, root);
			ctx.assertTrue(CactusShapes.next(world, root, shape) == null, kind + ": the whole shape still has a block to grow");
			ctx.assertTrue(grown.equals(whole) && grown.size() > 1, kind + ": grown " + grown.size() + " blocks, whole " + whole.size());
		}
		ctx.complete();
	}

	/** Every cactus (false) and arm (true) round "root", as positions from it. */
	private static Map<BlockPos, Boolean> blocks(ServerWorld world, BlockPos root) {
		Map<BlockPos, Boolean> found = new HashMap<>();
		for (BlockPos pos : BlockPos.iterate(root.add(-4, 0, -4), root.add(4, 12, 4))) {
			BlockState state = world.getBlockState(pos);
			if (state.isOf(Blocks.CACTUS) || CactusArms.isArm(state)) {
				found.put(pos.subtract(root).toImmutable(), CactusArms.isArm(state));
			}
		}
		return found;
	}
}
