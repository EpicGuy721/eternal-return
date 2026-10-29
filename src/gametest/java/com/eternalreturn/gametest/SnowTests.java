package com.eternalreturn.gametest;

import com.eternalreturn.blocks.MixedSlabs;
import com.eternalreturn.blocks.Slabs;
import com.eternalreturn.blocks.SnowCover;
import com.eternalreturn.gametest.mixin.StepSoundInvoker;
import com.eternalreturn.items.BlockTools;
import com.eternalreturn.weather.Storm;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.Waterloggable;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.GameRules;
import net.minecraft.world.WorldEvents;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.List;
import java.util.Optional;

/**
 * Snow on slabs, stairs, walls, fences and gates (SnowCover): every such block holds it and hides no more of its
 * neighbours for it; how much each holds and how it collides; putting it on by hand; caps knocked off by what's put
 * above; gates knocking theirs off as they open; digging it off with a shovel and what everything drops; falling snow
 * piling on; snowfall, world generation and melting.
 */
public class SnowTests implements FabricGameTest {
	private static final String BATCH = "snow";
	/** Snowfall changes the weather and the snow-piling game rule. */
	private static final String WEATHER_BATCH = "snow_weather";
	/** Melting ticks the test's chunk at random, many times over. */
	private static final String MELT_BATCH = "snow_melt";
	/** The freezing feature covers 16 by 16 columns from the test's corner. */
	private static final String WORLDGEN_BATCH = "snow_worldgen";
	/** 16 by 40 blocks of stone floor (at y 1 here: stand on y 2), 14 high. */
	private static final String ARENA = "eternalreturn_test:arena";

	private static BlockState snowed(BlockState state, int snow) {
		return SnowCover.with(state, snow);
	}

	private static BlockState stairs() {
		return Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH);
	}

	/** A slab standing on edge, filling a side. */
	private static BlockState vertical(Direction side) {
		return Slabs.onSide(Blocks.STONE_SLAB.getDefaultState(), side);
	}

	private static double top(VoxelShape shape) {
		return shape.isEmpty() ? 0.0 : shape.getMax(Direction.Axis.Y);
	}

	private static boolean sameShape(VoxelShape a, VoxelShape b) {
		return !VoxelShapes.matchesAnywhere(a, b, BooleanBiFunction.NOT_SAME);
	}

	private static int dropped(ServerWorld world, BlockPos pos, Item item) {
		return world.getEntitiesByClass(ItemEntity.class, new Box(pos).expand(1.5), entity -> entity.getStack().isOf(item)).stream()
				.mapToInt(entity -> entity.getStack().getCount()).sum();
	}

	/** A player's right-click on a block's top with what's in its hand, using the block first as a player's click does. */
	private static ActionResult clickTop(ServerPlayerEntity player, ServerWorld world, ItemStack stack, BlockPos pos) {
		player.setStackInHand(Hand.MAIN_HAND, stack);
		return player.interactionManager.interactBlock(player, world, stack, Hand.MAIN_HAND,
				new BlockHitResult(Vec3d.ofCenter(pos).add(0.0, 0.49, 0.0), Direction.UP, pos, false));
	}

	/** An item used on a block's side without using the block (as when sneaking). */
	private static ActionResult useOn(ServerPlayerEntity player, ItemStack stack, BlockPos pos, Direction side) {
		player.setStackInHand(Hand.MAIN_HAND, stack);
		Vec3d hit = Vec3d.ofCenter(pos).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
		return stack.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, new BlockHitResult(hit, side, pos, false)));
	}

	/** A biome over the test's own area, from the bottom of the world up. */
	private static void fillBiome(TestContext ctx, int size, String biome) {
		MinecraftServer server = ctx.getWorld().getServer();
		BlockPos a = ctx.getAbsolutePos(BlockPos.ORIGIN);
		BlockPos b = ctx.getAbsolutePos(new BlockPos(size, 0, size));
		int bottom = ctx.getWorld().getBottomY();
		int top = Math.max(bottom + 30, ctx.getWorld().getSeaLevel() + 8);
		server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(), "fillbiome " + Math.min(a.getX(), b.getX()) + " " + bottom + " "
				+ Math.min(a.getZ(), b.getZ()) + " " + Math.max(a.getX(), b.getX()) + " " + Math.max(top, a.getY() + 10) + " " + Math.max(a.getZ(), b.getZ()) + " " + biome);
	}

	/** A stone floor at the test's y 0 over x and z 0..7. */
	private static void floor(TestContext ctx) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
	}

	/**
	 * Every stair, slab, wall, fence and gate in the game (this mod's and others' too) holds snow, none by default; and
	 * with snow on it, it hides exactly what it hides bare, lets the same light through and stands the same against
	 * neighbours (what leaves holes in other snow mods' blocks: neighbours leaving out faces the snow doesn't cover).
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void everyShapeHoldsSnowAndHidesNoMore(TestContext ctx) {
		int blocks = 0;
		int states = 0;
		for (Block block : Registries.BLOCK) {
			BlockState plain = block.getDefaultState();
			boolean kind = block instanceof SlabBlock || block instanceof StairsBlock || block instanceof WallBlock || block instanceof FenceBlock
					|| block instanceof FenceGateBlock || block == MixedSlabs.MIXED_SLAB;
			if (!kind) {
				ctx.assertTrue(SnowCover.property(plain) == null, Registries.BLOCK.getId(block) + " doesn't hold snow");
				continue;
			}
			blocks++;
			ctx.assertTrue(SnowCover.property(plain) != null && SnowCover.raw(plain) == 0, Registries.BLOCK.getId(block) + " holds snow, none by default");
			for (BlockState state : block.getStateManager().getStates()) {
				if (SnowCover.raw(state) == 0) {
					continue;
				}
				states++;
				BlockState bare = SnowCover.bare(state);
				for (Direction side : Direction.values()) {
					ctx.assertTrue(sameShape(state.getCullingFace(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, side),
							bare.getCullingFace(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, side)), state + " hides the same toward " + side);
					ctx.assertTrue(state.isSideSolidFullSquare(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, side)
							== bare.isSideSolidFullSquare(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, side), state + " stands the same on its " + side);
				}
				ctx.assertTrue(state.getOpacity(EmptyBlockView.INSTANCE, BlockPos.ORIGIN) == bare.getOpacity(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)
						&& state.isOpaqueFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN) == bare.isOpaqueFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN),
						state + " lets the same light through");
			}
		}
		// Vanilla's 56 stairs, 60 slabs, 25 walls, 12 fences and 11 gates at the least.
		ctx.assertTrue(blocks >= 164, "every such block holds snow: " + blocks);
		ctx.assertTrue(BlockTools.hammerable(snowed(Blocks.STONE_SLAB.getDefaultState(), 1), SnowCover.LAYERS) == false, "the hammer leaves snow alone");
		ctx.assertTrue(states > 0, "snowy states: " + states);
		ctx.complete();
	}

	/**
	 * How much snow each shape holds: 4 layers in a bottom slab's or upright stairs' open space, one on a vertical slab's
	 * top edge and one cap on a wall, fence or gate; none in a top, double or mixed slab or upside-down stairs (snow layers
	 * lie on those), a waterlogged block or an open gate. Snow in the open space collides as snow layers (a layer lower
	 * than drawn); caps never do.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void eachShapeHoldsItsOwnAndCollidesAsSnow(TestContext ctx) {
		BlockState slab = Blocks.STONE_SLAB.getDefaultState();
		ctx.assertTrue(SnowCover.capacity(slab) == 4 && SnowCover.capacity(stairs()) == 4, "4 layers in a bottom slab and upright stairs");
		ctx.assertTrue(SnowCover.capacity(vertical(Direction.NORTH)) == 1 && SnowCover.capOnly(vertical(Direction.NORTH)), "one layer on a vertical slab's edge");
		for (BlockState cap : List.of(Blocks.OAK_FENCE.getDefaultState(), Blocks.COBBLESTONE_WALL.getDefaultState(), Blocks.OAK_FENCE_GATE.getDefaultState())) {
			ctx.assertTrue(SnowCover.capacity(cap) == 1 && SnowCover.capOnly(cap), "one cap on " + cap);
		}
		for (BlockState whole : List.of(slab.with(SlabBlock.TYPE, SlabType.TOP), slab.with(SlabBlock.TYPE, SlabType.DOUBLE),
				stairs().with(StairsBlock.HALF, BlockHalf.TOP), MixedSlabs.MIXED_SLAB.getDefaultState())) {
			ctx.assertTrue(SnowCover.capacity(whole) == 0 && SnowCover.wholeTop(whole), "snow lies on top of " + whole);
		}
		ctx.assertTrue(SnowCover.capacity(slab.with(SlabBlock.WATERLOGGED, true)) == 0, "none in water");
		ctx.assertTrue(SnowCover.capacity(Blocks.OAK_FENCE_GATE.getDefaultState().with(FenceGateBlock.OPEN, true)) == 0, "none on an open gate");

		for (int layers = 1; layers <= 4; layers++) {
			BlockState snowy = snowed(slab, layers);
			double collision = top(snowy.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN));
			double outline = top(snowy.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN));
			ctx.assertTrue(Math.abs(collision - (8 + 2 * (layers - 1)) / 16.0) < 1.0E-6, layers + " layers on a slab collide at " + collision);
			ctx.assertTrue(Math.abs(outline - (8 + 2 * layers) / 16.0) < 1.0E-6, layers + " layers on a slab are aimed at up to " + outline);
			int[] steps = SnowCover.topHeights(snowed(stairs(), layers).getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN));
			// Facing north, the upper step is at the north.
			ctx.assertTrue(steps[8 + 16 * 15] == 8 + 2 * (layers - 1) && steps[8] == 16, layers + " layers on stairs: the lower step at " + steps[8 + 16 * 15] + ", the upper at " + steps[8]);
		}
		ctx.assertTrue(SnowCover.full(snowed(slab, 4)) && !SnowCover.full(snowed(slab, 3)), "a slab is full with 4 layers");
		for (BlockState cap : List.of(Blocks.OAK_FENCE.getDefaultState(), Blocks.COBBLESTONE_WALL.getDefaultState(), Blocks.OAK_FENCE_GATE.getDefaultState(),
				vertical(Direction.EAST))) {
			ctx.assertTrue(sameShape(snowed(cap, 1).getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN), cap.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN))
					&& sameShape(snowed(cap, 1).getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN), cap.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)),
					"a cap on " + cap + " doesn't change its shape");
		}
		ctx.complete();
	}

	/** Something standing on snow in a slab stands on the snow: 3 layers, a layer lower as snow layers are. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 80)
	public void standingOnSnowInASlab(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos pos = ctx.getAbsolutePos(new BlockPos(4, 2, 4));
		world.setBlockState(pos, snowed(Blocks.STONE_SLAB.getDefaultState(), 3));
		ArmorStandEntity stand = EntityType.ARMOR_STAND.create(world);
		stand.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 0.0F, 0.0F);
		world.spawnEntity(stand);
		ctx.runAtTick(60, () -> {
			ctx.assertTrue(Math.abs(stand.getY() - (pos.getY() + 0.75)) < 1.0E-3, "it stands on the snow, at " + (stand.getY() - pos.getY()));
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/**
	 * Snow put on by hand, clicking a block's top: a layer a click into a slab or stairs up to 4, after which a snow layer
	 * lies on it; one on a vertical slab, a fence, a wall and a gate (which stays shut); snow layers on a top slab, which
	 * then says snow lies on it. Not on the side (snow goes beside, as vanilla's), not in water, not under a block.
	 */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 40)
	public void puttingSnowOnByHand(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 2));
		try {
			ItemStack snow = new ItemStack(Items.SNOW, 64);
			BlockPos slab = ctx.getAbsolutePos(new BlockPos(2, 2, 6));
			world.setBlockState(slab, Blocks.STONE_SLAB.getDefaultState());
			for (int layers = 1; layers <= 4; layers++) {
				ctx.assertTrue(clickTop(player, world, snow, slab).isAccepted(), "a click puts on layer " + layers);
				ctx.assertTrue(SnowCover.held(world.getBlockState(slab)) == layers, layers + " layers on the slab: " + world.getBlockState(slab));
			}
			ctx.assertTrue(snow.getCount() == 60, "a snow layer used a layer: " + snow.getCount());
			clickTop(player, world, snow, slab);
			BlockState above = world.getBlockState(slab.up());
			ctx.assertTrue(SnowCover.held(world.getBlockState(slab)) == 4 && above.isOf(Blocks.SNOW) && above.get(SnowBlock.LAYERS) == 1,
					"the slab full, a snow layer lies on it: " + above);

			BlockPos stairs = ctx.getAbsolutePos(new BlockPos(4, 2, 6));
			world.setBlockState(stairs, stairs());
			useOn(player, snow, stairs, Direction.UP);
			useOn(player, snow, stairs, Direction.UP);
			ctx.assertTrue(SnowCover.held(world.getBlockState(stairs)) == 2, "sneaking, it goes on too: " + world.getBlockState(stairs));

			for (int x : new int[] {6, 8, 10, 12}) {
				BlockPos pos = ctx.getAbsolutePos(new BlockPos(x, 2, 6));
				BlockState state = switch (x) {
					case 6 -> vertical(Direction.NORTH);
					case 8 -> Blocks.OAK_FENCE.getDefaultState();
					case 10 -> Blocks.COBBLESTONE_WALL.getDefaultState();
					default -> Blocks.OAK_FENCE_GATE.getDefaultState();
				};
				world.setBlockState(pos, state);
				ctx.assertTrue(clickTop(player, world, snow, pos).isAccepted(), "a cap goes on " + state);
				BlockState capped = world.getBlockState(pos);
				ctx.assertTrue(SnowCover.held(capped) == 1, "one on " + capped);
				if (capped.contains(FenceGateBlock.OPEN)) {
					// (Clicked again, a gate with its cap opens, as it would for anything in hand.)
					ctx.assertTrue(!capped.get(FenceGateBlock.OPEN), "the gate stays shut");
					continue;
				}
				clickTop(player, world, snow, pos);
				ctx.assertTrue(SnowCover.held(world.getBlockState(pos)) == 1 && world.getBlockState(pos.up()).isAir(), "and no more on " + capped);
			}

			BlockPos top = ctx.getAbsolutePos(new BlockPos(2, 2, 10));
			world.setBlockState(top, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP));
			clickTop(player, world, snow, top);
			ctx.assertTrue(world.getBlockState(top.up()).isOf(Blocks.SNOW) && SnowCover.snowOnTop(world.getBlockState(top)),
					"a snow layer on a top slab, which says snow lies on it: " + world.getBlockState(top));

			BlockPos side = ctx.getAbsolutePos(new BlockPos(4, 2, 10));
			world.setBlockState(side, Blocks.STONE_SLAB.getDefaultState());
			useOn(player, snow, side, Direction.EAST);
			ctx.assertTrue(SnowCover.held(world.getBlockState(side)) == 0 && world.getBlockState(side.east()).isOf(Blocks.SNOW), "clicked on its side, snow goes beside it");

			BlockPos wet = ctx.getAbsolutePos(new BlockPos(8, 2, 10));
			world.setBlockState(wet, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.WATERLOGGED, true));
			clickTop(player, world, snow, wet);
			ctx.assertTrue(SnowCover.held(world.getBlockState(wet)) == 0, "none in water");

			BlockPos covered = ctx.getAbsolutePos(new BlockPos(10, 2, 10));
			world.setBlockState(covered, Blocks.OAK_FENCE.getDefaultState());
			world.setBlockState(covered.up(), Blocks.STONE.getDefaultState());
			clickTop(player, world, snow, covered);
			ctx.assertTrue(SnowCover.held(world.getBlockState(covered)) == 0, "no cap with a block on top");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.killAllEntities();
		ctx.complete();
	}

	/**
	 * Anything put in the space above a cap knocks it off (a fence's, a wall's, a vertical slab's); the snow in stairs'
	 * open space stays under a block. A whole top says whether snow layers lie on it as they come and go.
	 */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 20)
	public void whatsAboveSaysWhatStays(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos fence = ctx.getAbsolutePos(new BlockPos(2, 2, 4));
		BlockPos wall = ctx.getAbsolutePos(new BlockPos(4, 2, 4));
		BlockPos edge = ctx.getAbsolutePos(new BlockPos(6, 2, 4));
		BlockPos stairs = ctx.getAbsolutePos(new BlockPos(8, 2, 4));
		BlockPos top = ctx.getAbsolutePos(new BlockPos(10, 2, 4));
		BlockPos mixed = ctx.getAbsolutePos(new BlockPos(12, 2, 4));
		world.setBlockState(fence, snowed(Blocks.OAK_FENCE.getDefaultState(), 1));
		world.setBlockState(wall, snowed(Blocks.COBBLESTONE_WALL.getDefaultState(), 1));
		world.setBlockState(edge, snowed(vertical(Direction.SOUTH), 1));
		world.setBlockState(stairs, snowed(stairs(), 2));
		world.setBlockState(top, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP));
		world.setBlockState(mixed, MixedSlabs.MIXED_SLAB.getDefaultState());
		world.setBlockState(fence.up(), Blocks.TORCH.getDefaultState());
		world.setBlockState(wall.up(), Blocks.STONE.getDefaultState());
		world.setBlockState(edge.up(), Blocks.STONE.getDefaultState());
		world.setBlockState(stairs.up(), Blocks.STONE.getDefaultState());
		world.setBlockState(top.up(), Blocks.SNOW.getDefaultState());
		world.setBlockState(mixed.up(), Blocks.SNOW.getDefaultState());
		ctx.assertTrue(SnowCover.raw(world.getBlockState(fence)) == 0, "a torch on a fence post knocks its cap off");
		ctx.assertTrue(SnowCover.raw(world.getBlockState(wall)) == 0, "a block on a wall knocks its cap off");
		ctx.assertTrue(SnowCover.raw(world.getBlockState(edge)) == 0, "a block on a vertical slab knocks its snow off");
		ctx.assertTrue(SnowCover.held(world.getBlockState(stairs)) == 2, "the snow on stairs' lower step stays under a block");
		ctx.assertTrue(SnowCover.snowOnTop(world.getBlockState(top)) && SnowCover.snowOnTop(world.getBlockState(mixed)),
				"a top slab and a mixed slab say snow lies on them");
		world.setBlockState(top.up(), Blocks.AIR.getDefaultState());
		ctx.assertTrue(!SnowCover.snowOnTop(world.getBlockState(top)), "and not once it's gone");
		ctx.complete();
	}

	/** Opening a gate with snow on it knocks the snow off with a puff of snow: by hand and by redstone. It doesn't come back when shut. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 20)
	public void openingAGateKnocksItsSnowOff(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 2));
		try {
			BlockPos byHand = ctx.getAbsolutePos(new BlockPos(4, 2, 6));
			world.setBlockState(byHand, snowed(Blocks.OAK_FENCE_GATE.getDefaultState(), 1));
			clickTop(player, world, ItemStack.EMPTY, byHand);
			BlockState opened = world.getBlockState(byHand);
			ctx.assertTrue(opened.get(FenceGateBlock.OPEN) && SnowCover.raw(opened) == 0, "opened by hand, its snow is off: " + opened);
			ctx.assertTrue(Heard.events(byHand, WorldEvents.BLOCK_BROKEN).contains(Block.getRawIdFromState(Blocks.SNOW.getDefaultState())), "in a puff of snow");
			clickTop(player, world, ItemStack.EMPTY, byHand);
			ctx.assertTrue(!world.getBlockState(byHand).get(FenceGateBlock.OPEN) && SnowCover.raw(world.getBlockState(byHand)) == 0, "shut, it has none");

			BlockPos powered = ctx.getAbsolutePos(new BlockPos(10, 2, 6));
			world.setBlockState(powered, snowed(Blocks.SPRUCE_FENCE_GATE.getDefaultState(), 1));
			world.setBlockState(powered.east(), Blocks.REDSTONE_BLOCK.getDefaultState());
			BlockState open = world.getBlockState(powered);
			ctx.assertTrue(open.get(FenceGateBlock.OPEN) && SnowCover.raw(open) == 0, "opened by redstone, its snow is off: " + open);
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	/**
	 * A shovel digs the snow off, at snow's speed, as snow (its particles, sound and drops: a snowball a layer, snow
	 * layers with Silk Touch), and leaves the block; anything else breaks both, each dropping its own.
	 */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 40)
	public void aShovelDigsTheSnowOff(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 2));
		try {
			BlockPos dug = ctx.getAbsolutePos(new BlockPos(2, 2, 8));
			BlockState snowy = snowed(Blocks.STONE_SLAB.getDefaultState(), 3);
			world.setBlockState(dug, snowy);
			ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);
			player.setStackInHand(Hand.MAIN_HAND, shovel);
			float speed = snowy.calcBlockBreakingDelta(player, world, dug);
			ctx.assertTrue(speed == SnowCover.asSnowLayer(snowy).calcBlockBreakingDelta(player, world, dug) && speed > Blocks.STONE_SLAB.getDefaultState().calcBlockBreakingDelta(player, world, dug),
					"a shovel digs it at snow's speed: " + speed);
			ctx.assertTrue(player.interactionManager.tryBreakBlock(dug), "the snow is dug off");
			BlockState left = world.getBlockState(dug);
			ctx.assertTrue(left.isOf(Blocks.STONE_SLAB) && SnowCover.raw(left) == 0, "the slab stays: " + left);
			ctx.assertTrue(dropped(world, dug, Items.SNOWBALL) == 3 && dropped(world, dug, Items.STONE_SLAB) == 0, "3 snowballs, no slab");
			ctx.assertTrue(Heard.events(dug, WorldEvents.BLOCK_BROKEN).contains(Block.getRawIdFromState(SnowCover.asSnowLayer(snowy))), "it breaks as snow");
			ctx.assertTrue(shovel.getDamage() == 1, "the shovel wears a use: " + shovel.getDamage());

			BlockPos silk = ctx.getAbsolutePos(new BlockPos(6, 2, 8));
			world.setBlockState(silk, snowed(Blocks.OAK_SLAB.getDefaultState(), 2));
			ItemStack silky = new ItemStack(Items.IRON_SHOVEL);
			silky.addEnchantment(world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(net.minecraft.enchantment.Enchantments.SILK_TOUCH).orElseThrow(), 1);
			player.setStackInHand(Hand.MAIN_HAND, silky);
			player.interactionManager.tryBreakBlock(silk);
			ctx.assertTrue(dropped(world, silk, Items.SNOW) == 2 && world.getBlockState(silk).isOf(Blocks.OAK_SLAB), "with Silk Touch, 2 snow layers");

			BlockPos picked = ctx.getAbsolutePos(new BlockPos(10, 2, 8));
			world.setBlockState(picked, snowed(Blocks.STONE_SLAB.getDefaultState(), 2));
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
			player.interactionManager.tryBreakBlock(picked);
			ctx.assertTrue(world.getBlockState(picked).isAir() && dropped(world, picked, Items.STONE_SLAB) == 1 && dropped(world, picked, Items.SNOWBALL) == 2,
					"a pickaxe breaks both: the slab and 2 snowballs");

			BlockPos fence = ctx.getAbsolutePos(new BlockPos(14, 2, 8));
			world.setBlockState(fence, snowed(Blocks.OAK_FENCE.getDefaultState(), 1));
			player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
			player.interactionManager.tryBreakBlock(fence);
			ctx.assertTrue(dropped(world, fence, Items.OAK_FENCE) == 1 && dropped(world, fence, Items.SNOWBALL) == 1, "by hand, the fence and a snowball");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.killAllEntities();
		ctx.complete();
	}

	/** Filling a snowy slab's other half, or turning it with the wrench, shakes its snow off; water melts it. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 20)
	public void fillingTurningAndWaterTakeItOff(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 2));
		try {
			BlockPos filled = ctx.getAbsolutePos(new BlockPos(4, 2, 6));
			world.setBlockState(filled, snowed(Blocks.STONE_SLAB.getDefaultState(), 2));
			useOn(player, new ItemStack(Items.STONE_SLAB), filled, Direction.UP);
			BlockState pair = world.getBlockState(filled);
			ctx.assertTrue(pair.get(SlabBlock.TYPE) == SlabType.DOUBLE && SnowCover.raw(pair) == 0, "filled, no snow: " + pair);

			BlockPos turned = ctx.getAbsolutePos(new BlockPos(8, 2, 6));
			world.setBlockState(turned, snowed(Blocks.STONE_SLAB.getDefaultState(), 2));
			useOn(player, new ItemStack(BlockTools.WRENCH), turned, Direction.NORTH);
			BlockState edge = world.getBlockState(turned);
			ctx.assertTrue(Slabs.axis(edge) != Direction.Axis.Y && SnowCover.raw(edge) == 0, "turned on edge, no snow: " + edge);

			BlockPos wet = ctx.getAbsolutePos(new BlockPos(12, 2, 6));
			BlockState snowy = snowed(Blocks.STONE_SLAB.getDefaultState(), 2);
			world.setBlockState(wet, snowy);
			((Waterloggable) Blocks.STONE_SLAB).tryFillWithFluid(world, wet, snowy, Fluids.WATER.getStill(false));
			ctx.assertTrue(SnowCover.held(world.getBlockState(wet)) == 0, "water melts it");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	/** Falling snow (snow gravity) landing on a slab piles into it, any more lying on it; on a fence, a cap. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 120)
	public void fallingSnowPilesOn(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos slab = ctx.getAbsolutePos(new BlockPos(4, 2, 6));
		BlockPos full = ctx.getAbsolutePos(new BlockPos(8, 2, 6));
		BlockPos fence = ctx.getAbsolutePos(new BlockPos(12, 2, 6));
		world.setBlockState(slab, Blocks.STONE_SLAB.getDefaultState());
		world.setBlockState(full, snowed(Blocks.STONE_SLAB.getDefaultState(), 3));
		world.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState());
		world.setBlockState(slab.up(5), Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, 3));
		world.setBlockState(full.up(5), Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, 3));
		world.setBlockState(fence.up(5), Blocks.SNOW.getDefaultState());
		ctx.runAtTick(100, () -> {
			ctx.assertTrue(SnowCover.held(world.getBlockState(slab)) == 3, "3 layers fell into the slab: " + world.getBlockState(slab));
			BlockState above = world.getBlockState(full.up());
			ctx.assertTrue(SnowCover.held(world.getBlockState(full)) == 4 && above.isOf(Blocks.SNOW) && above.get(SnowBlock.LAYERS) == 2,
					"3 onto 3 fill it, 2 lie on it: " + above);
			ctx.assertTrue(SnowCover.held(world.getBlockState(fence)) == 1, "a cap on the fence");
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/** Snow counts as snow underfoot: footsteps on a snowy slab sound of snow; on maps it shows white. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 20)
	public void snowUnderfootAndOnMaps(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 2));
		try {
			BlockPos pos = ctx.getAbsolutePos(new BlockPos(4, 2, 6));
			world.setBlockState(pos, snowed(Blocks.STONE_SLAB.getDefaultState(), 2));
			player.teleport(world, pos.getX() + 0.5, pos.getY() + 0.625, pos.getZ() + 0.5, 0.0F, 0.0F);
			((StepSoundInvoker) player).eternalreturn$playStepSounds(pos, world.getBlockState(pos));
			List<SoundEvent> heard = Heard.near(player.getPos());
			ctx.assertTrue(!heard.isEmpty() && heard.get(heard.size() - 1) == SoundEvents.BLOCK_SNOW_STEP, "steps sound of snow: " + heard);
			ctx.assertTrue(world.getBlockState(pos).getMapColor(world, pos) == MapColor.WHITE, "white on maps");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	/**
	 * Snowfall in a snowy biome under a storm settles on these blocks where it would on a full block: a layer into a slab
	 * and stairs, a cap on a fence, wall and gate, snow layers on a top slab; with the snow-piling game rule up to 4 in a
	 * slab, then snow layers on it.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = WEATHER_BATCH, skyAccess = true, tickLimit = 40)
	public void snowfallSettlesOnThem(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx);
		fillBiome(ctx, 7, "minecraft:snowy_plains");
		BlockPos slab = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
		BlockPos stairs = ctx.getAbsolutePos(new BlockPos(3, 1, 2));
		BlockPos fence = ctx.getAbsolutePos(new BlockPos(4, 1, 2));
		BlockPos wall = ctx.getAbsolutePos(new BlockPos(5, 1, 2));
		BlockPos gate = ctx.getAbsolutePos(new BlockPos(2, 1, 4));
		BlockPos top = ctx.getAbsolutePos(new BlockPos(3, 1, 4));
		BlockPos edge = ctx.getAbsolutePos(new BlockPos(4, 1, 4));
		world.setBlockState(slab, Blocks.STONE_SLAB.getDefaultState());
		world.setBlockState(stairs, stairs());
		world.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState());
		world.setBlockState(wall, Blocks.COBBLESTONE_WALL.getDefaultState());
		world.setBlockState(gate, Blocks.OAK_FENCE_GATE.getDefaultState());
		world.setBlockState(top, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP));
		world.setBlockState(edge, vertical(Direction.WEST));
		Storm storm = Weathers.rain(world, Vec3d.ofCenter(slab), false);
		GameRules.IntRule piling = world.getGameRules().get(GameRules.SNOW_ACCUMULATION_HEIGHT);
		ctx.runAtTick(20, () -> {
			try {
				List<BlockPos> all = List.of(slab, stairs, fence, wall, gate, top, edge);
				for (int i = 0; i < 3; i++) {
					all.forEach(world::tickIceAndSnow);
				}
				ctx.assertTrue(SnowCover.held(world.getBlockState(slab)) == 1 && SnowCover.held(world.getBlockState(stairs)) == 1,
						"a layer in the slab and the stairs (the game rule piles 1): " + world.getBlockState(slab));
				for (BlockPos cap : List.of(fence, wall, gate, edge)) {
					ctx.assertTrue(SnowCover.held(world.getBlockState(cap)) == 1, "a cap on " + world.getBlockState(cap));
				}
				ctx.assertTrue(world.getBlockState(top.up()).isOf(Blocks.SNOW) && SnowCover.snowOnTop(world.getBlockState(top)), "a snow layer on the top slab");
				piling.set(8, world.getServer());
				// 3 more layers fill it, then a snow layer on it grows to 2.
				for (int i = 0; i < 5; i++) {
					world.tickIceAndSnow(slab);
				}
				BlockState above = world.getBlockState(slab.up());
				ctx.assertTrue(SnowCover.held(world.getBlockState(slab)) == 4 && above.isOf(Blocks.SNOW) && above.get(SnowBlock.LAYERS) == 2,
						"piling: 4 in the slab, then a snow layer on it: " + above);
			} finally {
				piling.set(1, world.getServer());
				Weathers.dry(world, storm);
			}
			ctx.complete();
		});
	}

	/** Snowfall doesn't settle by a light (block light 10 or more above the block), as it doesn't on full blocks. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = WEATHER_BATCH, skyAccess = true, tickLimit = 40)
	public void noSnowByALight(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx);
		fillBiome(ctx, 7, "minecraft:snowy_plains");
		BlockPos slab = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
		world.setBlockState(slab, Blocks.STONE_SLAB.getDefaultState());
		world.setBlockState(slab.up().east(), Blocks.GLOWSTONE.getDefaultState());
		Storm storm = Weathers.rain(world, Vec3d.ofCenter(slab), false);
		ctx.runAtTick(20, () -> {
			try {
				world.tickIceAndSnow(slab);
				ctx.assertTrue(SnowCover.raw(world.getBlockState(slab)) == 0, "no snow by the glowstone");
			} finally {
				Weathers.dry(world, storm);
			}
			ctx.complete();
		});
	}

	/** Snowfall where it's warm (plains) rains: nothing settles on them. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = WEATHER_BATCH, skyAccess = true, tickLimit = 40)
	public void noSnowWhereItsWarm(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx);
		fillBiome(ctx, 7, "minecraft:plains");
		BlockPos slab = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
		BlockPos fence = ctx.getAbsolutePos(new BlockPos(4, 1, 3));
		world.setBlockState(slab, Blocks.STONE_SLAB.getDefaultState());
		world.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState());
		Storm storm = Weathers.rain(world, Vec3d.ofCenter(slab), false);
		ctx.runAtTick(20, () -> {
			try {
				world.tickIceAndSnow(slab);
				world.tickIceAndSnow(fence);
				ctx.assertTrue(SnowCover.raw(world.getBlockState(slab)) == 0 && SnowCover.raw(world.getBlockState(fence)) == 0, "no snow in the rain");
			} finally {
				Weathers.dry(world, storm);
			}
			ctx.complete();
		});
	}

	/** Snowy biomes' generation lays a layer on these blocks, as it lays snow layers on full blocks (and on a top slab). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = WORLDGEN_BATCH, skyAccess = true, tickLimit = 40)
	public void generationSnowsOnThem(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx);
		fillBiome(ctx, 7, "minecraft:snowy_taiga");
		BlockPos slab = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
		BlockPos stairs = ctx.getAbsolutePos(new BlockPos(3, 1, 2));
		BlockPos fence = ctx.getAbsolutePos(new BlockPos(4, 1, 2));
		BlockPos top = ctx.getAbsolutePos(new BlockPos(5, 1, 2));
		BlockPos ground = ctx.getAbsolutePos(new BlockPos(2, 0, 5));
		world.setBlockState(slab, Blocks.SPRUCE_SLAB.getDefaultState());
		world.setBlockState(stairs, Blocks.SPRUCE_STAIRS.getDefaultState());
		world.setBlockState(fence, Blocks.SPRUCE_FENCE.getDefaultState());
		world.setBlockState(top, Blocks.SPRUCE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP));
		ctx.runAtTick(5, () -> {
			BlockPos corner = ctx.getAbsolutePos(BlockPos.ORIGIN);
			BlockPos origin = new BlockPos(Math.min(corner.getX(), ctx.getAbsolutePos(new BlockPos(7, 0, 7)).getX()), corner.getY(),
					Math.min(corner.getZ(), ctx.getAbsolutePos(new BlockPos(7, 0, 7)).getZ()));
			Feature.FREEZE_TOP_LAYER.generate(new FeatureContext<>(Optional.empty(), world, world.getChunkManager().getChunkGenerator(), world.random, origin,
					DefaultFeatureConfig.INSTANCE));
			ctx.assertTrue(world.getBlockState(ground.up()).isOf(Blocks.SNOW), "a snow layer on the ground");
			for (BlockPos pos : List.of(slab, stairs, fence)) {
				ctx.assertTrue(SnowCover.held(world.getBlockState(pos)) == 1, "a layer on " + world.getBlockState(pos));
			}
			ctx.assertTrue(world.getBlockState(top.up()).isOf(Blocks.SNOW) && SnowCover.snowOnTop(world.getBlockState(top)), "a snow layer on the top slab, which says so");
			ctx.complete();
		});
	}

	/**
	 * The snow melts as snow layers do, at random, where block light is above 11 (here, glowstone beside it), and drops
	 * nothing; in the dark it stays. A snowy block ticks at random only for its snow.
	 */
	@GameTest(templateName = ARENA, batchId = MELT_BATCH, tickLimit = 200)
	public void snowMeltsInTheLight(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos lit = ctx.getAbsolutePos(new BlockPos(4, 2, 6));
		BlockPos dark = ctx.getAbsolutePos(new BlockPos(12, 2, 30));
		world.setBlockState(lit, snowed(Blocks.OAK_STAIRS.getDefaultState(), 2));
		world.setBlockState(lit.east(), Blocks.GLOWSTONE.getDefaultState());
		world.setBlockState(dark, snowed(Blocks.OAK_FENCE.getDefaultState(), 1));
		ctx.assertTrue(world.getBlockState(lit).hasRandomTicks() && !Blocks.OAK_STAIRS.getDefaultState().hasRandomTicks(), "snowy stairs tick, bare ones don't");
		boolean[] done = {false};
		// Once the glowstone's light has spread (a while, with the arena's own light still settling).
		ctx.runAtEveryTick(() -> {
			if (done[0] || !SnowCover.meltsHere(world, lit)) {
				return;
			}
			done[0] = true;
			for (int i = 0; i < 3; i++) {
				world.tickChunk(world.getWorldChunk(lit), 40000);
				world.tickChunk(world.getWorldChunk(dark), 40000);
			}
			ctx.assertTrue(world.getBlockState(lit).isOf(Blocks.OAK_STAIRS) && SnowCover.raw(world.getBlockState(lit)) == 0, "it melted by the glowstone: " + world.getBlockState(lit));
			ctx.assertTrue(dropped(world, lit, Items.SNOWBALL) == 0, "dropping nothing");
			ctx.assertTrue(SnowCover.held(world.getBlockState(dark)) == 1, "in the dark it stays");
			ctx.complete();
		});
	}
}
