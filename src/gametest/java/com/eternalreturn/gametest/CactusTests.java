package com.eternalreturn.gametest;

import com.eternalreturn.blocks.CactusArms;
import com.eternalreturn.worldgen.cactus.CactusShapes;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cacti that join sideways and grow their biome's shape (worldgen expansion 2, phase 2; CactusArms, CactusShapes): a
 * cactus placed against another joins it and stays; an unjoined one beside another stays too; other blocks beside it
 * still break it; break the trunk and the arms it held break and drop, loops included; every shape's plan holds
 * together, keeps its root, and grown a block at a time ends as the whole shape placed at once. Flat runs (batch cacti).
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

	/** Places a cactus as a player would: joined to every cactus beside it, the neighbours told. */
	private static void place(ServerWorld world, BlockPos pos) {
		world.setBlockState(pos, CactusArms.placed(Blocks.CACTUS.getDefaultState(), world, pos), Block.NOTIFY_ALL);
	}

	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true, tickLimit = 100)
	public void armsJoinAndFallWithTheTrunk(TestContext ctx) {
		sand(ctx);
		ServerWorld world = ctx.getWorld();
		BlockPos base = ctx.getAbsolutePos(new BlockPos(6, 2, 6));
		place(world, base);
		place(world, base.up());
		place(world, base.up(2));
		BlockPos arm = base.up(2).east();
		place(world, arm);
		place(world, arm.up());
		BlockPos far = arm.east();
		place(world, far);
		ctx.assertTrue(world.getBlockState(arm).get(CactusArms.side(Direction.WEST)) && world.getBlockState(base.up(2)).get(CactusArms.side(Direction.EAST)),
				"the arm and the trunk aren't joined both ways");
		ctx.assertTrue(world.getBlockState(far).get(CactusArms.side(Direction.WEST)) && world.getBlockState(arm).get(CactusArms.side(Direction.EAST)),
				"the arm's second piece isn't joined");
		boolean[] broke = {false};
		ctx.waitAndRun(10, () -> {
			// Once: the trunk is broken on purpose below.
			if (broke[0]) {
				return;
			}
			broke[0] = true;
			for (BlockPos pos : List.of(base, base.up(), base.up(2), arm, arm.up(), far)) {
				ctx.assertTrue(world.getBlockState(pos).isOf(Blocks.CACTUS), "a held piece broke at " + pos.toShortString());
			}
			world.breakBlock(base, true);
			ctx.runAtEveryTick(() -> {
				for (BlockPos pos : List.of(base.up(), base.up(2), arm, arm.up(), far)) {
					if (world.getBlockState(pos).isOf(Blocks.CACTUS)) {
						return;
					}
				}
				int dropped = 0;
				for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, new Box(base).expand(6), item -> item.getStack().isOf(Items.CACTUS))) {
					dropped += item.getStack().getCount();
				}
				ctx.assertTrue(dropped == 6, "6 cacti dropped, got " + dropped);
				ctx.complete();
			});
		});
	}

	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true, tickLimit = 60)
	public void cactusBesideCactusStays(TestContext ctx) {
		sand(ctx);
		ServerWorld world = ctx.getWorld();
		BlockPos one = ctx.getAbsolutePos(new BlockPos(4, 2, 4));
		BlockPos two = one.east();
		BlockPos lone = ctx.getAbsolutePos(new BlockPos(10, 2, 4));
		// Unjoined, side by side (as an arm's upright beside its trunk).
		world.setBlockState(one, Blocks.CACTUS.getDefaultState(), Block.NOTIFY_ALL);
		world.setBlockState(two, Blocks.CACTUS.getDefaultState(), Block.NOTIFY_ALL);
		world.setBlockState(lone, Blocks.CACTUS.getDefaultState(), Block.NOTIFY_ALL);
		world.setBlockState(lone.east(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
		ctx.waitAndRun(10, () -> {
			ctx.assertTrue(world.getBlockState(one).isOf(Blocks.CACTUS) && world.getBlockState(two).isOf(Blocks.CACTUS), "cactus beside cactus broke");
			ctx.assertTrue(!CactusArms.anyJoin(world.getBlockState(one)), "two cacti put side by side joined by themselves");
			ctx.assertTrue(!world.getBlockState(lone).isOf(Blocks.CACTUS), "cactus beside stone stayed");
			ctx.complete();
		});
	}

	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true, tickLimit = 100)
	public void joinedLoopFallsWithoutItsTrunk(TestContext ctx) {
		sand(ctx);
		ServerWorld world = ctx.getWorld();
		BlockPos base = ctx.getAbsolutePos(new BlockPos(6, 2, 6));
		place(world, base);
		place(world, base.up());
		// A ring of arms round the top: each joined to the next, the first to the trunk.
		List<BlockPos> ring = List.of(base.up().east(), base.up().east().south(), base.up().south());
		for (BlockPos pos : ring) {
			place(world, pos);
		}
		boolean[] broke = {false};
		ctx.waitAndRun(5, () -> {
			if (broke[0]) {
				return;
			}
			broke[0] = true;
			for (BlockPos pos : ring) {
				ctx.assertTrue(world.getBlockState(pos).isOf(Blocks.CACTUS), "a ring piece fell while held");
			}
			world.breakBlock(base, true);
			ctx.runAtEveryTick(() -> {
				for (BlockPos pos : ring) {
					if (world.getBlockState(pos).isOf(Blocks.CACTUS)) {
						return;
					}
				}
				ctx.assertTrue(!world.getBlockState(base.up()).isOf(Blocks.CACTUS), "the trunk's top stayed");
				ctx.complete();
			});
		});
	}

	/** Every kind's plans: one root, every block grows from one before it, nothing doubled, the root first of its ground blocks. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void plansHoldTogether(TestContext ctx) {
		for (CactusShapes.Kind kind : CactusShapes.Kind.values()) {
			CactusShapes.Shape shape = new CactusShapes.Shape(kind, kind == CactusShapes.Kind.BARREL ? 1 : 3, kind == CactusShapes.Kind.SAGUARO ? 9 : 5, 2, 5);
			for (int seed = 0; seed < 40; seed++) {
				BlockPos root = new BlockPos(seed * 37, 64, seed * -91);
				List<CactusShapes.Node> plan = CactusShapes.plan(shape, root);
				Set<BlockPos> seen = new HashSet<>();
				for (CactusShapes.Node node : plan) {
					ctx.assertTrue(seen.add(node.at()), kind + ": two blocks at " + node.at().toShortString());
					if (node.from() == null) {
						ctx.assertTrue(node.at().equals(BlockPos.ORIGIN), kind + ": a second root");
						continue;
					}
					ctx.assertTrue(seen.contains(node.from()) && node.from().getManhattanDistance(node.at()) == 1,
							kind + ": " + node.at().toShortString() + " grows from nothing next to it");
					ctx.assertTrue(node.at().getY() > 0 || node.at().getX() > 0 || (node.at().getX() == 0 && node.at().getZ() > 0),
							kind + ": a ground block before the root");
					ctx.assertTrue(node.at().getY() >= 0, kind + ": under the ground");
				}
			}
		}
		ctx.complete();
	}

	/** Grown a block at a time from one cactus, each shape ends as the shape placed whole, and every piece of it holds. */
	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true)
	public void growingEndsAsTheWholeShape(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		for (CactusShapes.Kind kind : CactusShapes.Kind.values()) {
			// Each shape on its own, the arena cleared between, so no shape grows into another.
			sand(ctx);
			CactusShapes.Shape shape = new CactusShapes.Shape(kind, kind == CactusShapes.Kind.BARREL ? 1 : 3, kind == CactusShapes.Kind.SAGUARO ? 9 : 5, 2, 4);
			BlockPos root = ctx.getAbsolutePos(new BlockPos(5, 2, 20));
			world.setBlockState(root, Blocks.CACTUS.getDefaultState(), Block.NOTIFY_ALL);
			int steps = 0;
			while (CactusShapes.growNext(world, root, shape) && steps < 200) {
				steps++;
			}
			Set<BlockPos> grown = blocks(world, root);
			ctx.assertTrue(root.equals(CactusShapes.root(world, root)), kind + ": the root moved");
			for (BlockPos pos : grown) {
				ctx.assertTrue(world.getBlockState(root.add(pos)).canPlaceAt(world, root.add(pos)), kind + ": a grown piece at " + pos.toShortString() + " isn't held");
			}
			sand(ctx);
			ctx.assertTrue(CactusShapes.placeWhole(world, root, shape, null), kind + ": nothing placed");
			Set<BlockPos> whole = blocks(world, root);
			ctx.assertTrue(CactusShapes.next(world, root, shape) == null, kind + ": the whole shape still has a block to grow");
			ctx.assertTrue(grown.equals(whole) && grown.size() > 1, kind + ": grown " + grown.size() + " blocks, whole " + whole.size());
		}
		ctx.complete();
	}

	/** Every cactus within 8 blocks across and 12 up of "root", as positions from it. */
	private static Set<BlockPos> blocks(ServerWorld world, BlockPos root) {
		Set<BlockPos> found = new HashSet<>();
		for (BlockPos pos : BlockPos.iterate(root.add(-4, 0, -8), root.add(8, 12, 8))) {
			if (world.getBlockState(pos).isOf(Blocks.CACTUS)) {
				found.add(pos.subtract(root));
			}
		}
		return found;
	}
}
