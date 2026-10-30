package com.eternalreturn.gametest;

import com.eternalreturn.blocks.MixedSlabs;
import com.eternalreturn.blocks.Slabs;
import com.eternalreturn.blocks.SnowCover;
import com.eternalreturn.blocks.Snowlogging;
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
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
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
 * Snow on slabs, stairs, walls, fences and gates (SnowCover, Snowlogging, SnowCoverBlock): every such block holds it
 * and hides no more of its neighbours for it; how much each takes and how it collides; it's its own target (aimed at,
 * mined, dropped); putting it on by hand and placing blocks into snow; snow covers turning to snow layers and back; the
 * wrench and other changes keeping it; snowfall, world generation and melting; falling snow; gates.
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

	private static BlockState stairs() {
		return Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH);
	}

	/** A slab standing on edge, filling a side. */
	private static BlockState vertical(Direction side) {
		return Slabs.onSide(Blocks.STONE_SLAB.getDefaultState(), side);
	}

	private static int logged(ServerWorld world, BlockPos pos) {
		return SnowCover.layers(world, pos, world.getBlockState(pos));
	}

	/** A block put in place with snow logged in it. */
	private static void snowy(ServerWorld world, BlockPos pos, BlockState state, int layers) {
		world.setBlockState(pos, state);
		Snowlogging.snowlog(world, pos, layers, Block.NOTIFY_ALL);
	}

	private static int cover(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return SnowCover.isCover(state) ? state.get(SnowBlock.LAYERS) : 0;
	}

	private static int snowLayer(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return state.isOf(Blocks.SNOW) ? state.get(SnowBlock.LAYERS) : 0;
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

	/** A player's right-click on a block's top at a height (0 to 1) with what's in its hand, using the block first as a player's click does. */
	private static ActionResult clickTop(ServerPlayerEntity player, ServerWorld world, ItemStack stack, BlockPos pos, double height) {
		player.setStackInHand(Hand.MAIN_HAND, stack);
		return player.interactionManager.interactBlock(player, world, stack, Hand.MAIN_HAND,
				new BlockHitResult(Vec3d.of(pos).add(0.5, height, 0.5), Direction.UP, pos, false));
	}

	/** An item used on a block's side without using the block (as when sneaking). */
	private static ActionResult useOn(ServerPlayerEntity player, ItemStack stack, BlockPos pos, Direction side) {
		player.setStackInHand(Hand.MAIN_HAND, stack);
		Vec3d hit = Vec3d.ofCenter(pos).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
		return stack.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, new BlockHitResult(hit, side, pos, false)));
	}

	/** A player standing a few blocks south of a block, looking at a point of it. */
	private static void lookAt(ServerPlayerEntity player, ServerWorld world, BlockPos pos, Vec3d point, double above) {
		player.teleport(world, pos.getX() + 0.5, pos.getY() + above, pos.getZ() + 2.5, 180.0F, 0.0F);
		player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, Vec3d.of(pos).add(point));
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
	 * Every stair, slab, wall, fence and gate in the game (this mod's and others' too) and the mixed slab can be snowy,
	 * none by default; snowy, it hides exactly what it hides bare, lets the same light through and stands the same
	 * against neighbours (what leaves holes in other snow mods' blocks: neighbours leaving out faces the snow doesn't
	 * cover). The hammer leaves the snowy mark alone.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void everyShapeHoldsSnowAndHidesNoMore(TestContext ctx) {
		int blocks = 0;
		for (Block block : Registries.BLOCK) {
			BlockState plain = block.getDefaultState();
			boolean kind = block instanceof SlabBlock || block instanceof StairsBlock || block instanceof WallBlock || block instanceof FenceBlock
					|| block instanceof FenceGateBlock || block == MixedSlabs.MIXED_SLAB;
			if (!kind) {
				ctx.assertTrue(!SnowCover.isHost(plain), Registries.BLOCK.getId(block) + " doesn't hold snow");
				continue;
			}
			blocks++;
			ctx.assertTrue(SnowCover.isHost(plain) && !SnowCover.marked(plain), Registries.BLOCK.getId(block) + " holds snow, none by default");
			for (BlockState state : block.getStateManager().getStates()) {
				if (!SnowCover.marked(state)) {
					continue;
				}
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
		ctx.assertTrue(!BlockTools.hammerable(Blocks.STONE_SLAB.getDefaultState(), SnowCover.SNOWY), "the hammer leaves the snowy mark alone");
		ctx.complete();
	}

	/**
	 * How much snow each takes in its space: 4 layers on a bottom slab and upright stairs' lower step, 8 on the ground
	 * around a fence, wall or gate and beside a vertical slab; none in a top, double or mixed slab or upside-down stairs
	 * (snow layers lie on those) or in water. Its snow collides as snow layers (a layer lower than drawn), on the topmost
	 * thing in each column: none over a fence post.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void eachShapeTakesItsOwnAndCollidesAsSnow(TestContext ctx) {
		BlockState slab = Blocks.STONE_SLAB.getDefaultState();
		ctx.assertTrue(SnowCover.capacity(slab) == 4 && SnowCover.capacity(stairs()) == 4, "4 layers on a bottom slab and upright stairs");
		for (BlockState ground : List.of(vertical(Direction.NORTH), Blocks.OAK_FENCE.getDefaultState(), Blocks.COBBLESTONE_WALL.getDefaultState(),
				Blocks.OAK_FENCE_GATE.getDefaultState())) {
			ctx.assertTrue(SnowCover.capacity(ground) == 8, "8 on the ground in " + ground);
		}
		for (BlockState whole : List.of(slab.with(SlabBlock.TYPE, SlabType.TOP), slab.with(SlabBlock.TYPE, SlabType.DOUBLE),
				stairs().with(StairsBlock.HALF, BlockHalf.TOP), MixedSlabs.MIXED_SLAB.getDefaultState())) {
			ctx.assertTrue(SnowCover.capacity(whole) == 0 && SnowCover.whole(whole, 0), "snow layers lie on " + whole);
		}
		ctx.assertTrue(SnowCover.capacity(slab.with(SlabBlock.WATERLOGGED, true)) == 0, "none in water");
		ctx.assertTrue(!Snowlogging.SNOW_COVER.getDefaultState().with(SnowBlock.LAYERS, 8).shouldBlockVision(ctx.getWorld(), BlockPos.ORIGIN)
				&& Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, 8).shouldBlockVision(ctx.getWorld(), BlockPos.ORIGIN), "a full snow cover doesn't blind a head in it, as a full snow layer does");
		ctx.assertTrue(SnowCover.whole(slab, 4) && !SnowCover.whole(slab, 3) && SnowCover.whole(stairs(), 4), "a slab and stairs are whole with 4 layers");
		ctx.assertTrue(SnowCover.reachesTop(stairs()) && SnowCover.reachesTop(vertical(Direction.EAST)) && SnowCover.reachesTop(Blocks.OAK_FENCE.getDefaultState())
				&& !SnowCover.reachesTop(slab), "stairs' upper step, a vertical slab and a fence post reach the top; a bottom slab doesn't");
		for (int layers = 1; layers <= 4; layers++) {
			double collision = top(SnowCover.snowCollision(slab, layers));
			double outline = top(SnowCover.snowOutline(slab, layers));
			ctx.assertTrue(layers == 1 ? collision == 0.0 : Math.abs(collision - (8 + 2 * (layers - 1)) / 16.0) < 1.0E-6, layers + " layers on a slab collide at " + collision);
			ctx.assertTrue(Math.abs(outline - (8 + 2 * layers) / 16.0) < 1.0E-6, layers + " layers on a slab are aimed at up to " + outline);
			int[] steps = SnowCover.topHeights(SnowCover.snowOutline(stairs(), layers));
			// Facing north, the upper step is at the north: no snow there.
			ctx.assertTrue(steps[8 + 16 * 15] == 8 + 2 * layers && steps[8] == -1, layers + " layers on stairs: on the lower step up to " + steps[8 + 16 * 15]);
		}
		int[] fence = SnowCover.topHeights(SnowCover.snowOutline(Blocks.OAK_FENCE.getDefaultState(), 2));
		ctx.assertTrue(fence[0] == 4 && fence[8 + 16 * 8] == -1, "2 layers around a fence post, none on it: " + fence[0] + ", " + fence[8 + 16 * 8]);
		ctx.complete();
	}

	/** Something standing on snow logged in a slab stands on the snow: 3 layers, a layer lower as snow layers are. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 80)
	public void standingOnSnowInASlab(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos pos = ctx.getAbsolutePos(new BlockPos(4, 2, 4));
		snowy(world, pos, Blocks.STONE_SLAB.getDefaultState(), 3);
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
	 * The snow is its own target: aimed at, a player mines it at snow's speed and it breaks alone, as a snow layer
	 * (snowballs with a shovel, nothing by hand), leaving the block; aiming at the block breaks the block, and the snow
	 * stays, as a snow layer of its own: on the ground around a fence, and fallen from a slab.
	 */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 40)
	public void theSnowIsItsOwnTarget(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 2));
		BlockPos picked = ctx.getAbsolutePos(new BlockPos(10, 2, 8));
		try {
			BlockPos slab = ctx.getAbsolutePos(new BlockPos(2, 2, 8));
			snowy(world, slab, Blocks.STONE_SLAB.getDefaultState(), 3);
			lookAt(player, world, slab, new Vec3d(0.5, 0.8, 0.5), 1.0);
			ctx.assertTrue(SnowCover.aimsSnow(player, world, slab, world.getBlockState(slab)), "looking down on it, the snow is aimed at");
			ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);
			player.setStackInHand(Hand.MAIN_HAND, shovel);
			BlockState snowyState = world.getBlockState(slab);
			float speed = snowyState.calcBlockBreakingDelta(player, world, slab);
			ctx.assertTrue(speed == SnowCover.asSnowLayer(3).calcBlockBreakingDelta(player, world, slab), "a shovel digs it at snow's speed: " + speed);
			ctx.assertTrue(player.interactionManager.tryBreakBlock(slab), "the snow is dug off");
			ctx.assertTrue(world.getBlockState(slab).isOf(Blocks.STONE_SLAB) && logged(world, slab) == 0, "the slab stays: " + world.getBlockState(slab));
			ctx.assertTrue(dropped(world, slab, Items.SNOWBALL) == 3 && dropped(world, slab, Items.STONE_SLAB) == 0, "3 snowballs, no slab");
			ctx.assertTrue(Heard.events(slab, WorldEvents.BLOCK_BROKEN).contains(Block.getRawIdFromState(SnowCover.asSnowLayer(3))), "it breaks as snow");

			BlockPos byHand = ctx.getAbsolutePos(new BlockPos(6, 2, 8));
			snowy(world, byHand, Blocks.OAK_SLAB.getDefaultState(), 2);
			lookAt(player, world, byHand, new Vec3d(0.5, 0.7, 0.5), 1.0);
			player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
			player.interactionManager.tryBreakBlock(byHand);
			ctx.assertTrue(world.getBlockState(byHand).isOf(Blocks.OAK_SLAB) && logged(world, byHand) == 0 && dropped(world, byHand, Items.SNOWBALL) == 0,
					"by hand the snow goes and drops nothing, as a snow layer");

			snowy(world, picked, Blocks.STONE_SLAB.getDefaultState(), 3);
			lookAt(player, world, picked, new Vec3d(0.5, 0.25, 1.0), 0.0);
			ctx.assertTrue(!SnowCover.aimsSnow(player, world, picked, world.getBlockState(picked)), "looking at its side, the slab is aimed at");
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
			player.interactionManager.tryBreakBlock(picked);
			ctx.assertTrue(dropped(world, picked, Items.STONE_SLAB) == 1 && dropped(world, picked, Items.SNOWBALL) == 0, "the slab breaks alone");

			BlockPos fence = ctx.getAbsolutePos(new BlockPos(14, 2, 8));
			snowy(world, fence, Blocks.OAK_FENCE.getDefaultState(), 2);
			lookAt(player, world, fence, new Vec3d(0.5, 0.8, 0.5), 1.0);
			player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
			player.interactionManager.tryBreakBlock(fence);
			ctx.assertTrue(dropped(world, fence, Items.OAK_FENCE) == 1 && snowLayer(world, fence) == 2, "a fence pulled out of the snow leaves it: " + world.getBlockState(fence));
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.runAtTick(30, () -> {
			ctx.assertTrue(snowLayer(world, picked) == 3, "the slab's snow fell and lies where it was: " + world.getBlockState(picked));
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/**
	 * Snow put on by hand: clicking inside a block's space (a slab's top, the ground beside a fence), a layer logged in
	 * it, up to what it takes, then snow layers on a whole top; clicking the top of a post, a vertical slab's edge or the
	 * upper step, a snow cover over just those parts, thickening; a gate stays shut. Not in water.
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
				ctx.assertTrue(clickTop(player, world, snow, slab, 0.5 + 0.125 * (layers - 1)).isAccepted(), "a click logs layer " + layers);
				ctx.assertTrue(logged(world, slab) == layers, layers + " layers in the slab: " + logged(world, slab));
			}
			ctx.assertTrue(snow.getCount() == 60, "a snow layer used a layer: " + snow.getCount());
			clickTop(player, world, snow, slab, 1.0);
			ctx.assertTrue(snowLayer(world, slab.up()) == 1, "the slab whole, a snow layer lies on it: " + world.getBlockState(slab.up()));

			BlockPos fence = ctx.getAbsolutePos(new BlockPos(5, 2, 6));
			world.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState());
			useOn(player, snow, fence.down(), Direction.UP);
			useOn(player, snow, fence.down(), Direction.UP);
			ctx.assertTrue(logged(world, fence) == 2, "clicking the ground under it, snow is logged in the fence: " + logged(world, fence));
			clickTop(player, world, snow, fence, 1.0);
			clickTop(player, world, snow, fence, 1.0);
			ctx.assertTrue(cover(world, fence.up()) == 2 && logged(world, fence) == 2, "clicking the post's top, a snow cover on it: " + world.getBlockState(fence.up()));

			BlockPos stairs = ctx.getAbsolutePos(new BlockPos(8, 2, 6));
			world.setBlockState(stairs, stairs());
			clickTop(player, world, snow, stairs, 1.0);
			useOn(player, snow, stairs, Direction.UP);
			ctx.assertTrue(cover(world, stairs.up()) == 2, "on the upper step, a snow cover, by hand and sneaking: " + world.getBlockState(stairs.up()));

			BlockPos edge = ctx.getAbsolutePos(new BlockPos(11, 2, 6));
			world.setBlockState(edge, vertical(Direction.NORTH));
			clickTop(player, world, snow, edge, 1.0);
			useOn(player, snow, edge.down(), Direction.UP);
			ctx.assertTrue(cover(world, edge.up()) == 1 && logged(world, edge) == 1, "a vertical slab: a cover on its edge, snow on the ground beside it");

			BlockPos gate = ctx.getAbsolutePos(new BlockPos(14, 2, 6));
			world.setBlockState(gate, Blocks.OAK_FENCE_GATE.getDefaultState());
			ctx.assertTrue(clickTop(player, world, snow, gate, 1.0).isAccepted() && cover(world, gate.up()) == 1, "a cover on the gate's posts");
			ctx.assertTrue(!world.getBlockState(gate).get(FenceGateBlock.OPEN), "the gate stays shut");

			BlockPos top = ctx.getAbsolutePos(new BlockPos(2, 2, 10));
			world.setBlockState(top, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP));
			clickTop(player, world, snow, top, 1.0);
			ctx.assertTrue(snowLayer(world, top.up()) == 1 && SnowCover.marked(world.getBlockState(top)),
					"a snow layer on a top slab, which is snowy: " + world.getBlockState(top));

			BlockPos wet = ctx.getAbsolutePos(new BlockPos(5, 2, 10));
			world.setBlockState(wet, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.WATERLOGGED, true));
			clickTop(player, world, snow, wet, 0.5);
			ctx.assertTrue(logged(world, wet) == 0, "none in water");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.killAllEntities();
		ctx.complete();
	}

	/** A fence, wall or slab placed into a snow layer keeps the snow, logged in it (what lies above a slab's floor). */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 20)
	public void placingIntoSnowKeepsIt(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 2));
		try {
			BlockPos fence = ctx.getAbsolutePos(new BlockPos(3, 2, 6));
			BlockPos wall = ctx.getAbsolutePos(new BlockPos(6, 2, 6));
			BlockPos slab = ctx.getAbsolutePos(new BlockPos(9, 2, 6));
			world.setBlockState(fence, SnowCover.asSnowLayer(3));
			world.setBlockState(wall, SnowCover.asSnowLayer(2));
			world.setBlockState(slab, SnowCover.asSnowLayer(6));
			useOn(player, new ItemStack(Items.OAK_FENCE), fence, Direction.UP);
			useOn(player, new ItemStack(Items.COBBLESTONE_WALL), wall, Direction.UP);
			useOn(player, new ItemStack(Items.STONE_SLAB), slab, Direction.UP);
			ctx.assertTrue(world.getBlockState(fence).isOf(Blocks.OAK_FENCE) && logged(world, fence) == 3, "a fence put into 3 layers keeps them: " + logged(world, fence));
			ctx.assertTrue(world.getBlockState(wall).isOf(Blocks.COBBLESTONE_WALL) && logged(world, wall) == 2, "a wall into 2 keeps them");
			ctx.assertTrue(world.getBlockState(slab).isOf(Blocks.STONE_SLAB) && logged(world, slab) == 2, "a slab into 6 keeps the 2 above it: " + logged(world, slab));
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	/**
	 * Snow keeps through changes to its block: the wrench turning a snowy slab (its snow goes where the slab leaves room),
	 * stairs turned; a snow cover on a vertical slab turns into a snow layer when a slab fills its other half, and back
	 * into a cover when the half goes; with 4 layers on stairs, the cover on the upper step turns into a snow layer over
	 * the whole top; a cover with nothing below to lie on goes. Water melts logged snow.
	 */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 20)
	public void snowKeepsThroughChanges(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 2));
		try {
			BlockPos turned = ctx.getAbsolutePos(new BlockPos(2, 2, 6));
			snowy(world, turned, Blocks.STONE_SLAB.getDefaultState(), 2);
			useOn(player, new ItemStack(BlockTools.WRENCH), turned, Direction.NORTH);
			ctx.assertTrue(Slabs.axis(world.getBlockState(turned)) != Direction.Axis.Y && logged(world, turned) == 2,
					"turned on edge with the wrench, the slab keeps its snow: " + logged(world, turned));

			BlockPos edge = ctx.getAbsolutePos(new BlockPos(5, 2, 6));
			world.setBlockState(edge, vertical(Direction.NORTH));
			world.setBlockState(edge.up(), Snowlogging.SNOW_COVER.getDefaultState());
			world.setBlockState(edge, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE).with(Slabs.AXIS, Direction.Axis.Z));
			ctx.assertTrue(snowLayer(world, edge.up()) == 1, "filled to a pair, the cover is a snow layer on its whole top: " + world.getBlockState(edge.up()));
			world.setBlockState(edge, vertical(Direction.SOUTH));
			ctx.assertTrue(cover(world, edge.up()) == 1, "a half gone, the snow is a cover again: " + world.getBlockState(edge.up()));

			BlockPos stairs = ctx.getAbsolutePos(new BlockPos(8, 2, 6));
			world.setBlockState(stairs, stairs());
			world.setBlockState(stairs.up(), Snowlogging.SNOW_COVER.getDefaultState());
			Snowlogging.snowlog(world, stairs, 4, Block.NOTIFY_ALL);
			ctx.assertTrue(snowLayer(world, stairs.up()) == 1, "4 layers make the stairs' top whole: the cover is a snow layer: " + world.getBlockState(stairs.up()));
			world.setBlockState(stairs, world.getBlockState(stairs).with(StairsBlock.FACING, Direction.EAST));
			ctx.assertTrue(logged(world, stairs) == 4, "turned, the stairs keep their snow");

			BlockPos fence = ctx.getAbsolutePos(new BlockPos(11, 2, 6));
			world.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState());
			world.setBlockState(fence.up(), Snowlogging.SNOW_COVER.getDefaultState());
			world.setBlockState(fence, Blocks.AIR.getDefaultState());
			ctx.assertTrue(world.getBlockState(fence.up()).isAir(), "with nothing below it, the cover goes");

			BlockPos wet = ctx.getAbsolutePos(new BlockPos(14, 2, 6));
			snowy(world, wet, Blocks.STONE_SLAB.getDefaultState(), 2);
			((Waterloggable) Blocks.STONE_SLAB).tryFillWithFluid(world, wet, world.getBlockState(wet), Fluids.WATER.getStill(false));
			ctx.assertTrue(logged(world, wet) == 0, "water melts it");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	/** Opening a gate with snow on its rails knocks it off in a puff of snow (it lies on the ground): by hand and by redstone. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 20)
	public void openingAGateKnocksItsSnowOff(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 2));
		try {
			BlockPos byHand = ctx.getAbsolutePos(new BlockPos(4, 2, 6));
			snowy(world, byHand, Blocks.OAK_FENCE_GATE.getDefaultState(), 1);
			clickTop(player, world, ItemStack.EMPTY, byHand, 0.9);
			ctx.assertTrue(world.getBlockState(byHand).get(FenceGateBlock.OPEN), "opened by hand");
			ctx.assertTrue(Heard.events(byHand, WorldEvents.BLOCK_BROKEN).contains(Block.getRawIdFromState(Blocks.SNOW.getDefaultState())), "in a puff of snow");
			ctx.assertTrue(logged(world, byHand) == 1, "the snow lies on the ground");

			BlockPos powered = ctx.getAbsolutePos(new BlockPos(10, 2, 6));
			snowy(world, powered, Blocks.SPRUCE_FENCE_GATE.getDefaultState(), 1);
			world.setBlockState(powered.east(), Blocks.REDSTONE_BLOCK.getDefaultState());
			ctx.assertTrue(world.getBlockState(powered).get(FenceGateBlock.OPEN)
					&& Heard.events(powered, WorldEvents.BLOCK_BROKEN).contains(Block.getRawIdFromState(Blocks.SNOW.getDefaultState())), "opened by redstone, in a puff");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	/** Falling snow (snow gravity) landing on a slab is logged in it, any more lying on it; on a fence, around it. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 120)
	public void fallingSnowPilesOn(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos slab = ctx.getAbsolutePos(new BlockPos(4, 2, 6));
		BlockPos full = ctx.getAbsolutePos(new BlockPos(8, 2, 6));
		BlockPos fence = ctx.getAbsolutePos(new BlockPos(12, 2, 6));
		world.setBlockState(slab, Blocks.STONE_SLAB.getDefaultState());
		snowy(world, full, Blocks.STONE_SLAB.getDefaultState(), 3);
		world.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState());
		world.setBlockState(slab.up(5), SnowCover.asSnowLayer(3));
		world.setBlockState(full.up(5), SnowCover.asSnowLayer(3));
		world.setBlockState(fence.up(5), SnowCover.asSnowLayer(2));
		ctx.runAtTick(100, () -> {
			ctx.assertTrue(logged(world, slab) == 3, "3 layers fell into the slab: " + logged(world, slab));
			ctx.assertTrue(logged(world, full) == 4 && snowLayer(world, full.up()) == 2, "3 onto 3 fill it, 2 lie on it: " + world.getBlockState(full.up()));
			ctx.assertTrue(logged(world, fence) == 2, "2 around the fence: " + logged(world, fence));
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
			snowy(world, pos, Blocks.STONE_SLAB.getDefaultState(), 2);
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
	 * Snowfall in a snowy biome under a storm settles where it would on a full block: a layer in a slab and on stairs'
	 * lower step, on the ground around a fence, wall and gate and beside a vertical slab, with a snow cover on their
	 * parts reaching the top; snow layers on a top slab. With the snow-piling game rule, up to 4 in a slab, then snow
	 * layers on it.
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
				for (BlockPos pos : List.of(slab, stairs, fence, wall, gate, edge)) {
					ctx.assertTrue(logged(world, pos) == 1, "a layer logged in " + world.getBlockState(pos) + " (the game rule piles 1): " + logged(world, pos));
				}
				for (BlockPos pos : List.of(stairs, fence, wall, gate, edge)) {
					ctx.assertTrue(cover(world, pos.up()) == 1, "a snow cover on " + world.getBlockState(pos) + ": " + world.getBlockState(pos.up()));
				}
				ctx.assertTrue(snowLayer(world, top.up()) == 1 && SnowCover.marked(world.getBlockState(top)), "a snow layer on the top slab, which is snowy");
				piling.set(8, world.getServer());
				// 3 more layers make the slab whole, then a snow layer on it grows to 2.
				for (int i = 0; i < 5; i++) {
					world.tickIceAndSnow(slab);
				}
				ctx.assertTrue(logged(world, slab) == 4 && snowLayer(world, slab.up()) == 2, "piling: 4 in the slab, then a snow layer on it: " + world.getBlockState(slab.up()));
			} finally {
				piling.set(1, world.getServer());
				Weathers.dry(world, storm);
			}
			ctx.complete();
		});
	}

	/** Snowfall doesn't settle by a light (block light 10 or more above the block), as it doesn't on full blocks. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = WEATHER_BATCH, skyAccess = true, tickLimit = 200)
	public void noSnowByALight(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx);
		fillBiome(ctx, 7, "minecraft:snowy_plains");
		BlockPos slab = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
		world.setBlockState(slab, Blocks.STONE_SLAB.getDefaultState());
		world.setBlockState(slab.up().east(), Blocks.GLOWSTONE.getDefaultState());
		Storm storm = Weathers.rain(world, Vec3d.ofCenter(slab), false);
		boolean[] done = {false};
		// Once the glowstone's light has spread.
		ctx.runAtEveryTick(() -> {
			if (done[0] || world.getLightLevel(net.minecraft.world.LightType.BLOCK, slab.up()) < 10) {
				return;
			}
			done[0] = true;
			try {
				world.tickIceAndSnow(slab);
				ctx.assertTrue(logged(world, slab) == 0, "no snow by the glowstone");
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
				ctx.assertTrue(logged(world, slab) == 0 && logged(world, fence) == 0 && world.getBlockState(fence.up()).isAir(), "no snow in the rain");
			} finally {
				Weathers.dry(world, storm);
			}
			ctx.complete();
		});
	}

	/** Snowy biomes' generation lays a layer in and on these blocks, as it lays snow layers on full blocks (and on a top slab). */
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
			BlockPos far = ctx.getAbsolutePos(new BlockPos(7, 0, 7));
			BlockPos origin = new BlockPos(Math.min(corner.getX(), far.getX()), corner.getY(), Math.min(corner.getZ(), far.getZ()));
			Feature.FREEZE_TOP_LAYER.generate(new FeatureContext<>(Optional.empty(), world, world.getChunkManager().getChunkGenerator(), world.random, origin,
					DefaultFeatureConfig.INSTANCE));
			ctx.assertTrue(snowLayer(world, ground.up()) == 1, "a snow layer on the ground");
			for (BlockPos pos : List.of(slab, stairs, fence)) {
				ctx.assertTrue(logged(world, pos) == 1, "a layer logged in " + world.getBlockState(pos));
			}
			ctx.assertTrue(cover(world, stairs.up()) == 1 && cover(world, fence.up()) == 1, "snow covers on the upper step and the post");
			ctx.assertTrue(snowLayer(world, top.up()) == 1 && SnowCover.marked(world.getBlockState(top)), "a snow layer on the top slab, which is snowy");
			ctx.complete();
		});
	}

	/**
	 * Logged snow melts as snow layers do, at random, where block light is above 11 (glowstone beside it), dropping
	 * nothing; a snow cover too; in the dark it stays. A snowy block ticks at random only for its snow.
	 */
	@GameTest(templateName = ARENA, batchId = MELT_BATCH, tickLimit = 200)
	public void snowMeltsInTheLight(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos lit = ctx.getAbsolutePos(new BlockPos(4, 2, 6));
		BlockPos dark = ctx.getAbsolutePos(new BlockPos(12, 2, 30));
		snowy(world, lit, Blocks.OAK_FENCE.getDefaultState(), 2);
		world.setBlockState(lit.up(), Snowlogging.SNOW_COVER.getDefaultState());
		world.setBlockState(lit.east(), Blocks.GLOWSTONE.getDefaultState());
		world.setBlockState(lit.up().east(), Blocks.GLOWSTONE.getDefaultState());
		snowy(world, dark, Blocks.OAK_FENCE.getDefaultState(), 1);
		ctx.assertTrue(world.getBlockState(lit).hasRandomTicks() && !Blocks.OAK_FENCE.getDefaultState().hasRandomTicks(), "snowy fences tick, bare ones don't");
		boolean[] done = {false};
		// Once the glowstone's light has spread (a while, with the arena's own light still settling).
		ctx.runAtEveryTick(() -> {
			if (done[0] || !Snowlogging.meltsHere(world, lit) || !Snowlogging.meltsHere(world, lit.up())) {
				return;
			}
			done[0] = true;
			for (int i = 0; i < 3; i++) {
				world.tickChunk(world.getWorldChunk(lit), 40000);
				world.tickChunk(world.getWorldChunk(dark), 40000);
			}
			ctx.assertTrue(logged(world, lit) == 0 && world.getBlockState(lit).isOf(Blocks.OAK_FENCE), "it melted by the glowstone, leaving the fence");
			ctx.assertTrue(world.getBlockState(lit.up()).isAir(), "the cover melted too");
			ctx.assertTrue(dropped(world, lit, Items.SNOWBALL) == 0, "dropping nothing");
			ctx.assertTrue(logged(world, dark) == 1, "in the dark it stays");
			ctx.complete();
		});
	}
}
