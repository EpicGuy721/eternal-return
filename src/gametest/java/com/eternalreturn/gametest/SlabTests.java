package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.blocks.MixedSlabs;
import com.eternalreturn.blocks.Slabs;
import com.eternalreturn.gametest.mixin.FireSpreadInvoker;
import com.eternalreturn.gametest.mixin.StepSoundInvoker;
import com.eternalreturn.items.BlockTools;
import com.eternalreturn.logic.BlockLocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FluidFillable;
import net.minecraft.block.MapColor;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.Waterloggable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.WallShape;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldEvents;
import net.minecraft.world.explosion.ExplosionBehavior;

import java.util.List;

/**
 * Vertical and mixed slabs (Slabs, MixedSlabs): the wrench moving slabs every way and turning pairs, placing mixed slabs,
 * mining the half aimed at and what it drops, fire taking one half, water, fences and the like joining, saving, pistons
 * and turned builds, and the sounds of the half concerned.
 */
public class SlabTests implements FabricGameTest {
	private static final String BATCH = "slabs";
	/** 16 by 40 blocks of stone floor (at y 1 here: stand on y 2), 14 high. */
	private static final String ARENA = "eternalreturn_test:arena";

	private static Block woolSlab() {
		return Registries.BLOCK.get(EternalReturn.id("white_wool_slab"));
	}

	/** The middle of a block's side. */
	private static Vec3d face(BlockPos pos, Direction side) {
		return Vec3d.ofCenter(pos).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
	}

	/** A player's right-click with an item on a block's side, at a point. */
	private static ActionResult use(ServerPlayerEntity player, ItemStack stack, BlockPos pos, Direction side, Vec3d hit) {
		player.setStackInHand(Hand.MAIN_HAND, stack);
		return stack.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, new BlockHitResult(hit, side, pos, false)));
	}

	/** A mixed slab of two slabs, the first filling the given side. */
	private static void mixed(ServerWorld world, BlockPos pos, Block first, Block second, Direction firstSide) {
		world.setBlockState(pos, MixedSlabs.MIXED_SLAB.getDefaultState().with(Slabs.AXIS, firstSide.getAxis())
				.with(Slabs.FLIPPED, firstSide.getDirection() == Direction.AxisDirection.POSITIVE));
		((MixedSlabs.MixedSlabEntity) world.getBlockEntity(pos)).setHalves(Slabs.normalized(first.getDefaultState()), Slabs.normalized(second.getDefaultState()));
	}

	/** The slab filling a mixed slab's positive or negative half (air if it isn't one). */
	private static Block half(ServerWorld world, BlockPos pos, boolean positive) {
		BlockState state = world.getBlockState(pos);
		BlockState half = state.isOf(MixedSlabs.MIXED_SLAB) ? MixedSlabs.half(state, MixedSlabs.halves(world, pos), positive) : null;
		return half == null ? Blocks.AIR : half.getBlock();
	}

	private static int dropped(ServerWorld world, BlockPos pos, Item item) {
		return world.getEntitiesByClass(ItemEntity.class, new Box(pos).expand(1.5), entity -> entity.getStack().isOf(item)).stream()
				.mapToInt(entity -> entity.getStack().getCount()).sum();
	}

	private static SoundEvent lastHeard(Vec3d pos) {
		List<SoundEvent> heard = Heard.near(pos);
		return heard.isEmpty() ? null : heard.get(heard.size() - 1);
	}

	/** A slab moved by the wrench to each side in turn: it fills that half, solid on its back and open opposite. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 40)
	public void wrenchMovesSlabsEveryWay(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 6));
		try {
			BlockPos pos = ctx.getAbsolutePos(new BlockPos(8, 3, 10));
			world.setBlockState(pos, Blocks.STONE_SLAB.getDefaultState());
			ItemStack wrench = new ItemStack(BlockTools.WRENCH);
			ctx.assertTrue(!use(player, wrench, pos, Direction.DOWN, face(pos, Direction.DOWN)).isAccepted(), "a bottom slab clicked underneath stays put");
			for (Direction side : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN)) {
				ctx.assertTrue(use(player, wrench, pos, side, face(pos, side)).isAccepted(), "the slab moves " + side);
				BlockState state = world.getBlockState(pos);
				ctx.assertTrue(state.isOf(Blocks.STONE_SLAB) && Slabs.side(state) == side, "to fill the " + side + " half: " + state);
				ctx.assertTrue(state.isSideSolidFullSquare(world, pos, side) && !state.isSideSolidFullSquare(world, pos, side.getOpposite()),
						"its back, " + side + ", is a full face and its open side isn't");
				Box box = state.getOutlineShape(world, pos).getBoundingBox();
				ctx.assertTrue(Math.abs(box.getLengthX() * box.getLengthY() * box.getLengthZ() - 0.5) < 1.0E-6, "half a block: " + box);
			}
			ctx.assertTrue(wrench.getDamage() == 6, "six moves, six uses: " + wrench.getDamage());
			ctx.assertTrue(!BlockLocks.isLocked(world, pos), "a moved slab isn't locked");
			ctx.assertTrue(!use(player, new ItemStack(BlockTools.HAMMER), pos, Direction.UP, face(pos, Direction.UP)).isAccepted(),
					"the hammer leaves slabs to the wrench");
			ctx.assertTrue(!BlockTools.hammerable(world.getBlockState(pos), Slabs.AXIS), "not even which way they lie");
			ctx.assertTrue(!ControlFingerprintTests.stateName(Blocks.STONE_SLAB.getDefaultState()).contains("slab_axis")
					&& ControlFingerprintTests.stateName(Slabs.onSide(Blocks.STONE_SLAB.getDefaultState(), Direction.NORTH)).contains("slab_axis=z"),
					"the fingerprints write a flat slab as vanilla's");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.killAllEntities();
		ctx.complete();
	}

	/** A pair (vanilla's double slab, or a mixed one) turns a quarter turn, the half clicked going to the side clicked. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 40)
	public void wrenchTurnsPairs(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 6));
		try {
			ItemStack wrench = new ItemStack(BlockTools.WRENCH);
			BlockPos pair = ctx.getAbsolutePos(new BlockPos(6, 3, 10));
			world.setBlockState(pair, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE));
			ctx.assertTrue(!use(player, wrench, pair, Direction.UP, face(pair, Direction.UP)).isAccepted(), "a flat pair clicked on top stays");
			ctx.assertTrue(use(player, wrench, pair, Direction.NORTH, face(pair, Direction.NORTH).add(0.0, 0.25, 0.0)).isAccepted(), "a pair turns");
			BlockState turned = world.getBlockState(pair);
			ctx.assertTrue(turned.isOf(Blocks.STONE_SLAB) && turned.get(SlabBlock.TYPE) == SlabType.DOUBLE && turned.get(Slabs.AXIS) == Direction.Axis.Z,
					"still vanilla's double slab, split north and south: " + turned);
			use(player, wrench, pair, Direction.EAST, face(pair, Direction.EAST));
			ctx.assertTrue(world.getBlockState(pair).get(Slabs.AXIS) == Direction.Axis.X, "then east and west");
			use(player, wrench, pair, Direction.UP, face(pair, Direction.UP).add(0.25, 0.0, 0.0));
			ctx.assertTrue(world.getBlockState(pair).get(Slabs.AXIS) == Direction.Axis.Y, "and flat again");

			BlockPos mixed = ctx.getAbsolutePos(new BlockPos(10, 3, 10));
			mixed(world, mixed, Blocks.OAK_SLAB, Blocks.STONE_SLAB, Direction.DOWN);
			// Clicked high on its north side, on the stone: the stone goes north.
			ctx.assertTrue(use(player, wrench, mixed, Direction.NORTH, face(mixed, Direction.NORTH).add(0.0, 0.25, 0.0)).isAccepted(), "a mixed pair turns");
			ctx.assertTrue(world.getBlockState(mixed).get(Slabs.AXIS) == Direction.Axis.Z && half(world, mixed, false) == Blocks.STONE_SLAB
					&& half(world, mixed, true) == Blocks.OAK_SLAB, "the stone half to the north, the oak to the south");
			// Clicked on top over the oak (the south part): the oak goes up.
			use(player, wrench, mixed, Direction.UP, face(mixed, Direction.UP).add(0.0, 0.0, 0.25));
			ctx.assertTrue(world.getBlockState(mixed).get(Slabs.AXIS) == Direction.Axis.Y && half(world, mixed, true) == Blocks.OAK_SLAB
					&& half(world, mixed, false) == Blocks.STONE_SLAB, "the oak on top, the stone below: " + world.getBlockState(mixed));
			ctx.assertTrue(!use(player, wrench, mixed, Direction.UP, face(mixed, Direction.UP)).isAccepted(), "the oak clicked on top is already there");
			// Clicked low on its west side, on the stone: the stone goes west.
			use(player, wrench, mixed, Direction.WEST, face(mixed, Direction.WEST).add(0.0, -0.25, 0.0));
			ctx.assertTrue(world.getBlockState(mixed).get(Slabs.AXIS) == Direction.Axis.X && half(world, mixed, false) == Blocks.STONE_SLAB
					&& half(world, mixed, true) == Blocks.OAK_SLAB, "the stone to the west, the oak to the east");
			ctx.assertTrue(!use(player, wrench, mixed, Direction.EAST, face(mixed, Direction.EAST)).isAccepted(), "the oak clicked on the east is already there");
			MixedSlabs.Halves halves = MixedSlabs.halves(world, mixed);
			ctx.assertTrue(halves != null && halves.first().isOf(Blocks.OAK_SLAB) && halves.second().isOf(Blocks.STONE_SLAB), "turning keeps the halves");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.killAllEntities();
		ctx.complete();
	}

	/** A different slab placed into a slab's empty half makes a mixed slab; the same slab, vanilla's double. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 40)
	public void mixedSlabPlacement(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 4));
		try {
			ItemStack oak = new ItemStack(Items.OAK_SLAB, 8);
			BlockPos onTop = ctx.getAbsolutePos(new BlockPos(4, 2, 10));
			world.setBlockState(onTop, Blocks.STONE_SLAB.getDefaultState());
			ctx.assertTrue(use(player, oak, onTop, Direction.UP, Vec3d.ofCenter(onTop)).isAccepted(), "an oak slab placed on top of a stone one");
			ctx.assertTrue(world.getBlockState(onTop).isOf(MixedSlabs.MIXED_SLAB) && half(world, onTop, false) == Blocks.STONE_SLAB
					&& half(world, onTop, true) == Blocks.OAK_SLAB, "makes a mixed slab, stone below and oak above: " + world.getBlockState(onTop));
			ctx.assertTrue(oak.getCount() == 7, "one oak slab used: " + oak.getCount());
			List<SoundEvent> heard = Heard.near(Vec3d.ofCenter(onTop));
			ctx.assertTrue(heard.contains(SoundEvents.BLOCK_WOOD_PLACE) && !heard.contains(SoundEvents.BLOCK_STONE_PLACE), "and sounds as oak placed: " + heard);

			BlockPos same = ctx.getAbsolutePos(new BlockPos(6, 2, 10));
			world.setBlockState(same, Blocks.STONE_SLAB.getDefaultState());
			use(player, new ItemStack(Items.STONE_SLAB, 4), same, Direction.UP, Vec3d.ofCenter(same));
			BlockState pair = world.getBlockState(same);
			ctx.assertTrue(pair.isOf(Blocks.STONE_SLAB) && pair.get(SlabBlock.TYPE) == SlabType.DOUBLE, "two stone slabs are vanilla's double slab: " + pair);

			// Into a vertical slab's open side, clicked on its inner face.
			BlockPos onEdge = ctx.getAbsolutePos(new BlockPos(8, 2, 10));
			world.setBlockState(onEdge, Slabs.onSide(Blocks.STONE_SLAB.getDefaultState(), Direction.NORTH));
			ctx.assertTrue(use(player, oak, onEdge, Direction.SOUTH, Vec3d.ofCenter(onEdge)).isAccepted(), "an oak slab placed against a vertical stone one");
			ctx.assertTrue(world.getBlockState(onEdge).isOf(MixedSlabs.MIXED_SLAB) && world.getBlockState(onEdge).get(Slabs.AXIS) == Direction.Axis.Z
					&& half(world, onEdge, false) == Blocks.STONE_SLAB && half(world, onEdge, true) == Blocks.OAK_SLAB, "stands on edge with it, stone north, oak south");

			// From a neighbour's face: the top of the floor under a top slab fills its bottom half.
			BlockPos under = ctx.getAbsolutePos(new BlockPos(10, 2, 10));
			world.setBlockState(under, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP));
			ctx.assertTrue(use(player, oak, under.down(), Direction.UP, face(under.down(), Direction.UP)).isAccepted(), "an oak slab placed under a top stone slab");
			ctx.assertTrue(half(world, under, false) == Blocks.OAK_SLAB && half(world, under, true) == Blocks.STONE_SLAB, "fills its bottom half");

			// Nothing goes into a full pair: the slab goes on top of it.
			MixedSlabs.Halves before = MixedSlabs.halves(world, onTop);
			use(player, oak, onTop, Direction.UP, face(onTop, Direction.UP));
			ctx.assertTrue(world.getBlockState(onTop).isOf(MixedSlabs.MIXED_SLAB) && before.equals(MixedSlabs.halves(world, onTop)), "a full pair is left as it is");
			ctx.assertTrue(world.getBlockState(onTop.up()).isOf(Blocks.OAK_SLAB), "the slab goes on top of it");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.killAllEntities();
		ctx.complete();
	}

	/** Mining a mixed slab: the half aimed at, at its speed and with its tool; it breaks, drops and sounds as itself. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 40)
	public void miningTheAimedHalf(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 7));
		try {
			Block wool = woolSlab();
			BlockPos pos = ctx.getAbsolutePos(new BlockPos(8, 3, 9));
			mixed(world, pos, wool, Blocks.STONE_SLAB, Direction.DOWN);
			BlockState state = world.getBlockState(pos);
			BlockState woolHalf = MixedSlabs.half(state, MixedSlabs.halves(world, pos), false);
			BlockState stoneHalf = MixedSlabs.half(state, MixedSlabs.halves(world, pos), true);
			// High and low on its north side.
			Vec3d top = Vec3d.of(pos).add(0.5, 0.8, 0.0);
			Vec3d bottom = Vec3d.of(pos).add(0.5, 0.2, 0.0);
			player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, top);
			float stoneSpeed = state.calcBlockBreakingDelta(player, world, pos);
			ctx.assertTrue(stoneSpeed == stoneHalf.calcBlockBreakingDelta(player, world, pos), "aimed at the top, it's mined as stone: " + stoneSpeed);
			player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, bottom);
			float woolSpeed = state.calcBlockBreakingDelta(player, world, pos);
			ctx.assertTrue(woolSpeed == woolHalf.calcBlockBreakingDelta(player, world, pos) && woolSpeed > stoneSpeed,
					"aimed at the bottom, as wool, quicker by hand: " + woolSpeed + " against " + stoneSpeed);

			player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, top);
			player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
			ctx.assertTrue(player.interactionManager.tryBreakBlock(pos), "the stone half breaks");
			BlockState left = world.getBlockState(pos);
			ctx.assertTrue(left.isOf(wool) && left.get(SlabBlock.TYPE) == SlabType.BOTTOM, "leaving the wool half: " + left);
			ctx.assertTrue(dropped(world, pos, Items.STONE_SLAB) == 0, "stone broken by hand drops nothing");
			ctx.assertTrue(Heard.events(pos, WorldEvents.BLOCK_BROKEN).contains(Block.getRawIdFromState(stoneHalf)), "it breaks with stone's particles and sound");

			// A pickaxe on a stone half: it drops, the pickaxe wears, and it counts as stone slab mined.
			BlockPos picked = ctx.getAbsolutePos(new BlockPos(4, 3, 9));
			mixed(world, picked, Blocks.OAK_SLAB, Blocks.STONE_SLAB, Direction.DOWN);
			ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
			player.setStackInHand(Hand.MAIN_HAND, pickaxe);
			player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, Vec3d.of(picked).add(0.5, 0.8, 0.0));
			int mined = player.getStatHandler().getStat(Stats.MINED, Blocks.STONE_SLAB);
			player.interactionManager.tryBreakBlock(picked);
			ctx.assertTrue(world.getBlockState(picked).isOf(Blocks.OAK_SLAB), "the oak half is left: " + world.getBlockState(picked));
			ctx.assertTrue(dropped(world, picked, Items.STONE_SLAB) == 1 && dropped(world, picked, Items.OAK_SLAB) == 0, "one stone slab drops, no oak");
			ctx.assertTrue(pickaxe.getDamage() == 1, "the pickaxe wears: " + pickaxe.getDamage());
			ctx.assertTrue(player.getStatHandler().getStat(Stats.MINED, Blocks.STONE_SLAB) == mined + 1, "a stone slab mined");

			// By hand on an oak half: oak drops, the stone half is left.
			BlockPos byHand = ctx.getAbsolutePos(new BlockPos(12, 3, 9));
			mixed(world, byHand, Blocks.OAK_SLAB, Blocks.STONE_SLAB, Direction.DOWN);
			player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
			player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, Vec3d.of(byHand).add(0.5, 0.2, 0.0));
			player.interactionManager.tryBreakBlock(byHand);
			BlockState stone = world.getBlockState(byHand);
			ctx.assertTrue(stone.isOf(Blocks.STONE_SLAB) && stone.get(SlabBlock.TYPE) == SlabType.TOP, "the stone half is left: " + stone);
			ctx.assertTrue(dropped(world, byHand, Items.OAK_SLAB) == 1, "one oak slab drops");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.killAllEntities();
		ctx.complete();
	}

	/** Taken whole, a mixed slab is its harder and stronger half, shows its top half on maps and drops both halves. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 40)
	public void mixedSlabsWhole(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 7));
		try {
			Block wool = woolSlab();
			BlockPos pos = ctx.getAbsolutePos(new BlockPos(4, 2, 10));
			mixed(world, pos, wool, Blocks.STONE_SLAB, Direction.DOWN);
			BlockState state = world.getBlockState(pos);
			ctx.assertTrue(state.getHardness(world, pos) == 2.0F, "as hard as its stone half: " + state.getHardness(world, pos));
			float resistance = new ExplosionBehavior().getBlastResistance(null, world, pos, state, state.getFluidState()).orElse(0.0F);
			ctx.assertTrue(resistance == Blocks.STONE_SLAB.getBlastResistance(), "withstands a blast as stone: " + resistance);
			ctx.assertTrue(state.getMapColor(world, pos) == MapColor.STONE_GRAY, "on a map, its top half: " + state.getMapColor(world, pos));
			BlockPos onEdge = ctx.getAbsolutePos(new BlockPos(8, 2, 13));
			mixed(world, onEdge, wool, Blocks.STONE_SLAB, Direction.WEST);
			ctx.assertTrue(world.getBlockState(onEdge).getMapColor(world, onEdge) == wool.getDefaultMapColor(), "on edge, its west half");

			world.breakBlock(pos, true);
			ctx.assertTrue(world.getBlockState(pos).isAir() && dropped(world, pos, wool.asItem()) == 1 && dropped(world, pos, Items.STONE_SLAB) == 1,
					"broken whole, both halves drop");

			// Vanilla's double slab mined: two slabs, as ever.
			BlockPos pair = ctx.getAbsolutePos(new BlockPos(12, 3, 9));
			world.setBlockState(pair, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE));
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
			player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, Vec3d.of(pair).add(0.5, 0.8, 0.0));
			player.interactionManager.tryBreakBlock(pair);
			ctx.assertTrue(world.getBlockState(pair).isAir() && dropped(world, pair, Items.STONE_SLAB) == 2, "a double slab breaks whole into two");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.killAllEntities();
		ctx.complete();
	}

	/** Fire takes a mixed slab's flammable half and leaves the other; a stone pair never catches; wooden vertical slabs burn. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 40)
	public void burningOneHalf(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		FireSpreadInvoker fire = (FireSpreadInvoker) Blocks.FIRE;
		BlockPos oakOnStone = ctx.getAbsolutePos(new BlockPos(4, 2, 10));
		mixed(world, oakOnStone, Blocks.STONE_SLAB, Blocks.OAK_SLAB, Direction.DOWN);
		ctx.assertTrue(fire.eternalreturn$burnChanceAt(world, oakOnStone.up()) > 0, "fire can start beside its oak half");
		// A spread factor of 1: the fire always takes it.
		fire.eternalreturn$trySpreadingFire(world, oakOnStone, 1, world.getRandom(), 0);
		BlockState left = world.getBlockState(oakOnStone);
		ctx.assertTrue(left.isOf(Blocks.STONE_SLAB) && left.get(SlabBlock.TYPE) == SlabType.BOTTOM, "the oak half burns away, the stone half stays: " + left);

		BlockPos stonePair = ctx.getAbsolutePos(new BlockPos(8, 2, 10));
		mixed(world, stonePair, Blocks.STONE_SLAB, Blocks.COBBLESTONE_SLAB, Direction.DOWN);
		ctx.assertTrue(fire.eternalreturn$burnChanceAt(world, stonePair.up()) == 0, "nothing beside a stone pair catches");
		fire.eternalreturn$trySpreadingFire(world, stonePair, 1, world.getRandom(), 0);
		ctx.assertTrue(world.getBlockState(stonePair).isOf(MixedSlabs.MIXED_SLAB), "and it doesn't burn");

		BlockPos bothBurn = ctx.getAbsolutePos(new BlockPos(12, 2, 10));
		mixed(world, bothBurn, Blocks.OAK_SLAB, woolSlab(), Direction.DOWN);
		fire.eternalreturn$trySpreadingFire(world, bothBurn, 1, world.getRandom(), 0);
		ctx.assertTrue(world.getBlockState(bothBurn).isOf(Blocks.OAK_SLAB), "of oak and wool, the wool (quicker to catch) burns first: " + world.getBlockState(bothBurn));

		BlockPos onEdge = ctx.getAbsolutePos(new BlockPos(8, 2, 14));
		world.setBlockState(onEdge, Slabs.onSide(Blocks.OAK_SLAB.getDefaultState(), Direction.NORTH));
		fire.eternalreturn$trySpreadingFire(world, onEdge, 1, world.getRandom(), 0);
		ctx.assertTrue(!world.getBlockState(onEdge).isOf(Blocks.OAK_SLAB), "a vertical oak slab burns");
		for (BlockPos pos : List.of(oakOnStone, stonePair, bothBurn, onEdge)) {
			world.setBlockState(pos, Blocks.AIR.getDefaultState());
		}
		ctx.complete();
	}

	/** Vertical slabs hold water like flat ones; a pair never does. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 40)
	public void verticalSlabsHoldWater(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 6));
		BlockPos wet = ctx.getAbsolutePos(new BlockPos(8, 2, 10));
		BlockPos dry = ctx.getAbsolutePos(new BlockPos(12, 2, 10));
		try {
			world.setBlockState(wet, Blocks.WATER.getDefaultState());
			ItemStack stone = new ItemStack(Items.STONE_SLAB, 4);
			use(player, stone, wet.down(), Direction.UP, face(wet.down(), Direction.UP));
			ctx.assertTrue(world.getBlockState(wet).isOf(Blocks.STONE_SLAB) && world.getBlockState(wet).get(SlabBlock.WATERLOGGED), "a slab placed in water holds it");
			use(player, new ItemStack(BlockTools.WRENCH), wet, Direction.NORTH, face(wet, Direction.NORTH));
			BlockState onEdge = world.getBlockState(wet);
			ctx.assertTrue(Slabs.side(onEdge) == Direction.NORTH && onEdge.get(SlabBlock.WATERLOGGED) && world.getFluidState(wet).isOf(Fluids.WATER),
					"and keeps it stood on edge: " + onEdge);
			use(player, stone, wet, Direction.SOUTH, Vec3d.ofCenter(wet));
			BlockState pair = world.getBlockState(wet);
			ctx.assertTrue(pair.get(SlabBlock.TYPE) == SlabType.DOUBLE && pair.get(Slabs.AXIS) == Direction.Axis.Z && !pair.get(SlabBlock.WATERLOGGED),
					"filled, it's a dry pair on edge: " + pair);

			world.setBlockState(dry, Slabs.onSide(Blocks.STONE_SLAB.getDefaultState(), Direction.EAST));
			ctx.assertTrue(((Waterloggable) Blocks.STONE_SLAB).tryFillWithFluid(world, dry, world.getBlockState(dry), Fluids.WATER.getStill(false))
					&& world.getBlockState(dry).get(SlabBlock.WATERLOGGED), "a bucket of water fills a dry vertical slab");
			ctx.assertTrue(!((FluidFillable) Blocks.STONE_SLAB).canFillWithFluid(player, world, wet, pair, Fluids.WATER), "but not a pair on edge");

			BlockPos mixed = ctx.getAbsolutePos(new BlockPos(4, 2, 10));
			mixed(world, mixed, Blocks.STONE_SLAB, Blocks.OAK_SLAB, Direction.NORTH);
			ctx.assertTrue(!(MixedSlabs.MIXED_SLAB instanceof FluidFillable) && world.getFluidState(mixed).isEmpty(), "nor a mixed one");
		} finally {
			FishingTests.disconnect(player);
			// (no water left to run into the tests alongside)
			world.setBlockState(wet, Blocks.AIR.getDefaultState());
			world.setBlockState(dry, Blocks.AIR.getDefaultState());
		}
		ctx.killAllEntities();
		ctx.complete();
	}

	private static BlockState joined(ServerWorld world, BlockPos pos, Block block) {
		BlockState state = Block.postProcessState(block.getDefaultState(), world, pos);
		world.setBlockState(pos, state);
		return state;
	}

	/** Fences, walls and panes join a vertical slab's back and narrow edges, not its open side; a flat slab, as in vanilla. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 40)
	public void fencesWallsPanesJoin(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos north = ctx.getAbsolutePos(new BlockPos(6, 2, 10));
		world.setBlockState(north, Slabs.onSide(Blocks.STONE_SLAB.getDefaultState(), Direction.NORTH));
		ctx.assertTrue(joined(world, north.north(), Blocks.OAK_FENCE).get(FenceBlock.SOUTH), "a fence joins a vertical slab's back");
		ctx.assertTrue(joined(world, north.east(), Blocks.OAK_FENCE).get(FenceBlock.WEST), "and its narrow edge");
		ctx.assertTrue(!joined(world, north.south(), Blocks.OAK_FENCE).get(FenceBlock.NORTH), "not its open side");
		ctx.assertTrue(joined(world, north.west(), Blocks.COBBLESTONE_WALL).get(WallBlock.EAST_SHAPE) != WallShape.NONE, "a wall joins its edge");

		BlockPos west = ctx.getAbsolutePos(new BlockPos(10, 2, 10));
		world.setBlockState(west, Slabs.onSide(Blocks.STONE_SLAB.getDefaultState(), Direction.WEST));
		ctx.assertTrue(joined(world, west.north(), Blocks.GLASS_PANE).get(PaneBlock.SOUTH), "a pane joins a vertical slab's edge");
		ctx.assertTrue(!joined(world, west.east(), Blocks.GLASS_PANE).get(PaneBlock.WEST), "not its open side");

		BlockPos flat = ctx.getAbsolutePos(new BlockPos(13, 2, 14));
		world.setBlockState(flat, Blocks.STONE_SLAB.getDefaultState());
		ctx.assertTrue(!joined(world, flat.west(), Blocks.OAK_FENCE).get(FenceBlock.EAST), "a flat slab's side, as in vanilla, doesn't");
		ctx.complete();
	}

	/** A mixed slab keeps its halves when saved, pushed by a piston, or turned and mirrored with a build. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void mixedSlabKeepsItsHalves(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos pos = ctx.getAbsolutePos(new BlockPos(6, 2, 10));
		mixed(world, pos, Blocks.STONE_SLAB, Blocks.OAK_SLAB, Direction.NORTH);
		BlockState state = world.getBlockState(pos);
		MixedSlabs.Halves halves = MixedSlabs.halves(world, pos);
		NbtCompound saved = world.getBlockEntity(pos).createNbtWithIdentifyingData(world.getRegistryManager());
		ctx.assertTrue(BlockEntity.createFromNbt(pos, state, saved, world.getRegistryManager()) instanceof MixedSlabs.MixedSlabEntity loaded
				&& halves.equals(loaded.halves()), "saved and loaded, it keeps its halves");

		BlockState turned = state.rotate(BlockRotation.CLOCKWISE_90);
		ctx.assertTrue(turned.get(Slabs.AXIS) == Direction.Axis.X && MixedSlabs.half(turned, halves, true).isOf(Blocks.STONE_SLAB),
				"a build turned a quarter takes its stone half from north to east: " + turned);
		BlockState mirrored = state.mirror(BlockMirror.LEFT_RIGHT);
		ctx.assertTrue(mirrored.get(Slabs.AXIS) == Direction.Axis.Z && MixedSlabs.half(mirrored, halves, true).isOf(Blocks.STONE_SLAB),
				"mirrored, from north to south: " + mirrored);
		BlockState vertical = Slabs.onSide(Blocks.STONE_SLAB.getDefaultState(), Direction.NORTH);
		ctx.assertTrue(Slabs.side(vertical.rotate(BlockRotation.CLOCKWISE_90)) == Direction.EAST, "a vertical slab turns with a build");
		ctx.assertTrue(Blocks.STONE_SLAB.getDefaultState().rotate(BlockRotation.CLOCKWISE_90) == Blocks.STONE_SLAB.getDefaultState(), "a flat one stays as it is");

		BlockPos pushed = ctx.getAbsolutePos(new BlockPos(10, 2, 10));
		mixed(world, pushed, Blocks.STONE_SLAB, Blocks.OAK_SLAB, Direction.DOWN);
		MixedSlabs.Halves pushedHalves = MixedSlabs.halves(world, pushed);
		world.setBlockState(pushed.west(), Blocks.PISTON.getDefaultState().with(PistonBlock.FACING, Direction.EAST));
		world.setBlockState(pushed.west(2), Blocks.REDSTONE_BLOCK.getDefaultState());
		ctx.runAtTick(20, () -> {
			BlockPos moved = pushed.east();
			ctx.assertTrue(world.getBlockState(moved).isOf(MixedSlabs.MIXED_SLAB) && pushedHalves.equals(MixedSlabs.halves(world, moved)),
					"a piston pushes it with both halves: " + world.getBlockState(moved));
			ctx.complete();
		});
	}

	/** Footsteps sound of the half underfoot: the top one, or on edge the one under the middle of the feet. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 40)
	public void stepsSoundOfTheHalfUnderfoot(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 6));
		try {
			Block wool = woolSlab();
			BlockPos flat = ctx.getAbsolutePos(new BlockPos(4, 2, 10));
			mixed(world, flat, wool, Blocks.STONE_SLAB, Direction.DOWN);
			player.teleport(world, flat.getX() + 0.5, flat.getY() + 1.0, flat.getZ() + 0.5, 0.0F, 0.0F);
			((StepSoundInvoker) player).eternalreturn$playStepSounds(flat, world.getBlockState(flat));
			ctx.assertTrue(lastHeard(player.getPos()) == SoundEvents.BLOCK_STONE_STEP, "on top, the stone half: " + lastHeard(player.getPos()));

			BlockPos onEdge = ctx.getAbsolutePos(new BlockPos(10, 2, 10));
			mixed(world, onEdge, wool, Blocks.STONE_SLAB, Direction.WEST);
			player.teleport(world, onEdge.getX() + 0.25, onEdge.getY() + 1.0, onEdge.getZ() + 0.5, 0.0F, 0.0F);
			((StepSoundInvoker) player).eternalreturn$playStepSounds(onEdge, world.getBlockState(onEdge));
			ctx.assertTrue(lastHeard(player.getPos()) == SoundEvents.BLOCK_WOOL_STEP, "on edge, over the west half, wool: " + lastHeard(player.getPos()));
			player.teleport(world, onEdge.getX() + 0.75, onEdge.getY() + 1.0, onEdge.getZ() + 0.5, 0.0F, 0.0F);
			((StepSoundInvoker) player).eternalreturn$playStepSounds(onEdge, world.getBlockState(onEdge));
			ctx.assertTrue(lastHeard(player.getPos()) == SoundEvents.BLOCK_STONE_STEP, "over the east half, stone: " + lastHeard(player.getPos()));
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.killAllEntities();
		ctx.complete();
	}
}
