package com.eternalreturn.gametest;

import com.eternalreturn.items.BlockTools;
import com.eternalreturn.items.Shuriken;
import com.eternalreturn.logic.BlockLocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Shurikens; the wrench and the hammer, and the blocks they lock. */
public class NewItemTests implements FabricGameTest {
	private static final String BATCH = "new_items";
	/** 16 by 40 blocks of stone floor (at y 1 here: stand on y 2), 14 high. */
	private static final String ARENA = "eternalreturn_test:arena";

	private static ActionResult useOn(ServerPlayerEntity player, ItemStack tool, BlockPos pos) {
		player.setStackInHand(Hand.MAIN_HAND, tool);
		BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
		return tool.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void recipes(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ItemStack n = new ItemStack(Items.IRON_NUGGET);
		CraftingRecipeInput cross = CraftingRecipeInput.create(3, 3,
				List.of(n, ItemStack.EMPTY, n, ItemStack.EMPTY, n, ItemStack.EMPTY, n, ItemStack.EMPTY, n));
		ItemStack shurikens = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, cross, world)
				.map(recipe -> recipe.value().craft(cross, world.getRegistryManager())).orElse(ItemStack.EMPTY);
		ctx.assertTrue(shurikens.isOf(Shuriken.ITEM) && shurikens.getCount() == 4, "five nuggets in an X make 4 shurikens: " + shurikens);
		ctx.complete();
	}

	/** Four husks in a row: a shuriken thrown down the row cuts the first three and breaks; the fourth is untouched. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void shurikensPierceThree(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		List<ZombieEntity> husks = new ArrayList<>();
		for (int z = 12; z <= 18; z += 2) {
			ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, new BlockPos(8, 2, z));
			husk.setAiDisabled(true);
			husks.add(husk);
		}
		Shuriken.Thrown shuriken = new Shuriken.Thrown(Shuriken.ENTITY, world);
		Vec3d from = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(8, 3, 8)));
		shuriken.setPosition(from.x, from.y, from.z);
		shuriken.setVelocity(0.0, 0.0, 1.5);
		world.spawnEntity(shuriken);
		ctx.runAtTick(40, () -> {
			for (int i = 0; i < 3; i++) {
				ctx.assertTrue(husks.get(i).getHealth() < husks.get(i).getMaxHealth(), "husk " + (i + 1) + " was cut");
			}
			ctx.assertTrue(husks.get(3).getHealth() == husks.get(3).getMaxHealth(), "the fourth wasn't");
			ctx.assertTrue(shuriken.isRemoved(), "the shuriken broke");
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void shurikensBreakOnBlocks(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		for (int x = 6; x <= 10; x++) {
			for (int y = 2; y <= 5; y++) {
				ctx.setBlockState(new BlockPos(x, y, 14), Blocks.STONE.getDefaultState());
			}
		}
		Shuriken.Thrown shuriken = new Shuriken.Thrown(Shuriken.ENTITY, world);
		Vec3d from = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(8, 3, 8)));
		shuriken.setPosition(from.x, from.y, from.z);
		shuriken.setVelocity(0.0, 0.0, 1.5);
		world.spawnEntity(shuriken);
		ctx.runAtTick(30, () -> {
			ctx.assertTrue(shuriken.isRemoved(), "broken on the wall");
			ctx.assertTrue(world.getEntitiesByClass(ItemEntity.class, new Box(ctx.getAbsolutePos(new BlockPos(8, 3, 13))).expand(3.0), item -> true)
					.isEmpty(), "and nothing left to pick up");
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void wrenchTurnsBlocks(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 6));
		try {
			BlockPos furnace = ctx.getAbsolutePos(new BlockPos(6, 2, 10));
			BlockPos log = ctx.getAbsolutePos(new BlockPos(8, 2, 10));
			BlockPos door = ctx.getAbsolutePos(new BlockPos(10, 2, 10));
			world.setBlockState(furnace, Blocks.FURNACE.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
			world.setBlockState(log, Blocks.OAK_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.Y));
			Items.OAK_DOOR.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND,
					new BlockHitResult(Vec3d.ofCenter(door.down()), Direction.UP, door.down(), false)));
			ItemStack wrench = new ItemStack(BlockTools.WRENCH);
			ctx.assertTrue(useOn(player, wrench, furnace).isAccepted(), "the furnace turns");
			ctx.assertTrue(world.getBlockState(furnace).get(Properties.HORIZONTAL_FACING) != Direction.NORTH, "to face another way");
			ctx.assertTrue(BlockLocks.isLocked(world, furnace), "and is locked");
			useOn(player, wrench, log);
			ctx.assertTrue(world.getBlockState(log).get(Properties.AXIS) != Direction.Axis.Y, "the log lies another way");
			ctx.assertTrue(world.getBlockState(door).isOf(Blocks.OAK_DOOR), "a door was placed to try");
			ctx.assertTrue(!useOn(player, wrench, door).isAccepted(), "but doors don't turn");
			ctx.assertTrue(wrench.getDamage() == 2, "two uses worn: " + wrench.getDamage());
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.killAllEntities();
		ctx.complete();
	}

	/** A hammered fence keeps its odd shape when a neighbour comes, and loses its lock when broken. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void hammerSetsAndLocks(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 6));
		try {
			BlockPos fence = ctx.getAbsolutePos(new BlockPos(8, 2, 10));
			world.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState());
			ItemStack hammer = new ItemStack(BlockTools.HAMMER);
			ctx.assertTrue(useOn(player, hammer, fence).isAccepted(), "the hammer works on a fence");
			BlockState hammered = world.getBlockState(fence);
			ctx.assertTrue(hammered.get(FenceBlock.EAST), "its first property, east, set: " + hammered);
			ctx.assertTrue(BlockLocks.isLocked(world, fence), "locked");
			world.setBlockState(fence.north(), Blocks.STONE.getDefaultState());
			ctx.assertTrue(world.getBlockState(fence).equals(hammered), "a neighbour doesn't change it: " + world.getBlockState(fence));
			player.setSneaking(true);
			useOn(player, hammer, fence);
			player.setSneaking(false);
			useOn(player, hammer, fence);
			ctx.assertTrue(world.getBlockState(fence).get(FenceBlock.NORTH), "sneaking chose the next property, north");
			world.setBlockState(fence, Blocks.AIR.getDefaultState());
			ctx.assertTrue(!BlockLocks.isLocked(world, fence), "unlocked when broken");
			BlockPos wheat = ctx.getAbsolutePos(new BlockPos(10, 2, 10));
			world.setBlockState(wheat.down(), Blocks.FARMLAND.getDefaultState());
			world.setBlockState(wheat, Blocks.WHEAT.getDefaultState());
			ctx.assertTrue(!useOn(player, hammer, wheat).isAccepted() && world.getBlockState(wheat).get(Properties.AGE_7) == 0, "a crop can't be grown");
			// A lone wall: east, north, south, then up chosen; taking its post away would leave nothing to see or touch.
			BlockPos wall = ctx.getAbsolutePos(new BlockPos(12, 2, 10));
			world.setBlockState(wall, Blocks.COBBLESTONE_WALL.getDefaultState());
			ItemStack wallHammer = new ItemStack(BlockTools.HAMMER);
			player.setSneaking(true);
			for (int i = 0; i < 3; i++) {
				useOn(player, wallHammer, wall);
			}
			player.setSneaking(false);
			ctx.assertTrue(!useOn(player, wallHammer, wall).isAccepted(), "a wall can't lose its post with no sides");
			ctx.assertTrue(world.getBlockState(wall).get(Properties.UP), "it keeps its post: " + world.getBlockState(wall));
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.killAllEntities();
		ctx.complete();
	}

	/** Leaves hammered to decaying (not persistent, far from a log) don't decay: locked blocks don't tick. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void lockedBlocksDontTick(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 6));
		try {
			BlockPos leaves = ctx.getAbsolutePos(new BlockPos(8, 2, 10));
			world.setBlockState(leaves, Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true).with(LeavesBlock.DISTANCE, 7));
			useOn(player, new ItemStack(BlockTools.HAMMER), leaves);
			ctx.assertTrue(!world.getBlockState(leaves).get(LeavesBlock.PERSISTENT), "hammered to decaying leaves");
			world.getBlockState(leaves).randomTick(world, leaves, world.getRandom());
			ctx.assertTrue(world.getBlockState(leaves).isOf(Blocks.OAK_LEAVES), "but they don't decay");
			BlockPos free = ctx.getAbsolutePos(new BlockPos(10, 2, 10));
			world.setBlockState(free, Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.DISTANCE, 7), 18);
			world.getBlockState(free).randomTick(world, free, world.getRandom());
			ctx.assertTrue(world.getBlockState(free).isAir(), "unlocked ones do");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.killAllEntities();
		ctx.complete();
	}
}
