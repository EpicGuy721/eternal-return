package com.eternalreturn.gametest;

import com.eternalreturn.blocks.CopperRail;
import com.eternalreturn.logic.Leads;
import com.eternalreturn.logic.SwimKey;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PoweredRailBlock;
import net.minecraft.block.RailBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Copper rails and 1.21.2 minecarts, 1.21.6 leads, and the swim key. */
public class SystemTests implements FabricGameTest {
	private static final String BATCH = "systems";
	/** 16 by 40 blocks of stone floor (at y 1 here: stand on y 2), 14 high. */
	private static final String ARENA = "eternalreturn_test:arena";

	/** A block set as given, without its neighbours reshaping it (rails). */
	private static void place(TestContext ctx, BlockPos pos, BlockState state) {
		ctx.getWorld().setBlockState(ctx.getAbsolutePos(pos), state, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
	}

	/** Two carts sent off at the same speed: on iron rails one goes much further in a second than on copper. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void copperRailsKeepVanillaSpeed(TestContext ctx) {
		for (int z = 2; z <= 38; z++) {
			place(ctx, new BlockPos(4, 2, z), CopperRail.BLOCK.getDefaultState().with(RailBlock.SHAPE, RailShape.NORTH_SOUTH));
			place(ctx, new BlockPos(10, 2, z), Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.NORTH_SOUTH));
		}
		AbstractMinecartEntity copper = ctx.spawnEntity(EntityType.MINECART, new BlockPos(4, 2, 3));
		AbstractMinecartEntity iron = ctx.spawnEntity(EntityType.MINECART, new BlockPos(10, 2, 3));
		double start = copper.getZ();
		copper.setVelocity(0.0, 0.0, 0.4);
		iron.setVelocity(0.0, 0.0, 0.4);
		ctx.runAtTick(20, () -> {
			double slow = copper.getZ() - start;
			double fast = iron.getZ() - start;
			ctx.assertTrue(slow > 2.0 && fast > slow * 1.3, "iron rails " + fast + " blocks, copper " + slow);
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/**
	 * A cart run up a slope that ends flies on up past its top. The ramp is built as a player would: powered rails on
	 * redstone blocks, a rail up onto a block makes the slope, then the rail on top is taken away.
	 */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 80)
	public void cartsJumpOffSlopes(TestContext ctx) {
		for (int z = 3; z <= 9; z++) {
			ctx.setBlockState(new BlockPos(8, 1, z), Blocks.REDSTONE_BLOCK.getDefaultState());
		}
		for (int z = 3; z <= 8; z++) {
			ctx.setBlockState(new BlockPos(8, 2, z), Blocks.POWERED_RAIL.getDefaultState());
		}
		ctx.setBlockState(new BlockPos(8, 2, 10), Blocks.STONE.getDefaultState());
		ctx.setBlockState(new BlockPos(8, 3, 10), Blocks.RAIL.getDefaultState());
		ctx.setBlockState(new BlockPos(8, 2, 9), Blocks.POWERED_RAIL.getDefaultState());
		ctx.setBlockState(new BlockPos(8, 3, 10), Blocks.AIR.getDefaultState());
		BlockState slope = ctx.getWorld().getBlockState(ctx.getAbsolutePos(new BlockPos(8, 2, 9)));
		ctx.assertTrue(slope.isOf(Blocks.POWERED_RAIL) && slope.get(PoweredRailBlock.SHAPE) == RailShape.ASCENDING_SOUTH && slope.get(PoweredRailBlock.POWERED),
				"a powered slope ending in the air: " + slope);
		AbstractMinecartEntity cart = ctx.spawnEntity(EntityType.MINECART, new BlockPos(8, 2, 4));
		cart.setVelocity(0.0, 0.0, 0.4);
		double top = ctx.getAbsolutePos(new BlockPos(8, 3, 9)).getY();
		double[] highest = {-1.0};
		ctx.runAtEveryTick(() -> highest[0] = Math.max(highest[0], cart.getY() - top));
		ctx.runAtTick(60, () -> {
			ctx.assertTrue(highest[0] > 0.5, "it flew up off the top: " + highest[0]);
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/** A cart standing across a north-south rail turns to face along it, and so keeps facing that way when the rail goes. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void cartsFaceAlongTheirRail(TestContext ctx) {
		place(ctx, new BlockPos(8, 2, 10), Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.NORTH_SOUTH));
		AbstractMinecartEntity cart = ctx.spawnEntity(EntityType.MINECART, new BlockPos(8, 2, 10));
		cart.setYaw(0.0F);
		ctx.runAtTick(5, () -> {
			float off = Math.abs(MathHelper.wrapDegrees(cart.getYaw() - 90.0F));
			ctx.assertTrue(off < 1.0F || off > 179.0F, "facing along the rail: " + cart.getYaw());
			ctx.setBlockState(new BlockPos(8, 2, 10), Blocks.AIR.getDefaultState());
		});
		ctx.runAtTick(15, () -> {
			float off = Math.abs(MathHelper.wrapDegrees(cart.getYaw() - 90.0F));
			ctx.assertTrue(off < 1.0F || off > 179.0F, "still, with the rail gone: " + cart.getYaw());
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void leadsAsIn1216(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 4));
		try {
			ChickenEntity far = ctx.spawnEntity(EntityType.CHICKEN, new BlockPos(8, 2, 15));
			far.setAiDisabled(true);
			far.attachLeash(player, true);
			// 11 blocks: vanilla's lead would have snapped at 10.
			far.baseTick();
			ctx.assertTrue(far.isLeashed(), "a lead holds at 11 blocks");
			far.refreshPositionAndAngles(far.getX(), far.getY(), far.getZ() + 2.0, 0.0F, 0.0F);
			far.baseTick();
			ctx.assertTrue(!far.isLeashed(), "and snaps past 12");

			ChickenEntity a = ctx.spawnEntity(EntityType.CHICKEN, new BlockPos(6, 2, 6));
			ChickenEntity b = ctx.spawnEntity(EntityType.CHICKEN, new BlockPos(10, 2, 6));
			CowEntity cow = ctx.spawnEntity(EntityType.COW, new BlockPos(8, 2, 8));
			a.setAiDisabled(true);
			b.setAiDisabled(true);
			cow.setAiDisabled(true);
			a.attachLeash(player, true);
			b.attachLeash(player, true);
			player.setSneaking(true);
			player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
			Leads.use(player, world, Hand.MAIN_HAND, cow, null);
			player.setSneaking(false);
			ctx.assertTrue(a.getLeashHolder() == cow && b.getLeashHolder() == cow, "sneak-clicking a cow ties the chickens to it");

			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.LEAD, 2));
			Leads.use(player, world, Hand.MAIN_HAND, a, null);
			ctx.assertTrue(a.getLeashHolder() == player, "a lead on a chicken tied to the cow ties it to the player");
			ctx.assertTrue(player.getMainHandStack().getCount() == 1, "using up a lead");

			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SHEARS));
			Leads.use(player, world, Hand.MAIN_HAND, b, null);
			ctx.assertTrue(!b.isLeashed(), "shears cut a lead");
			ctx.assertTrue(world.getEntitiesByClass(ItemEntity.class, new Box(b.getBlockPos()).expand(2.0), item -> item.getStack().isOf(Items.LEAD))
					.size() >= 1, "and it drops");

			ItemStack string = new ItemStack(Items.STRING);
			CraftingRecipeInput input = CraftingRecipeInput.create(3, 3,
					List.of(string, string, ItemStack.EMPTY, string, string, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, string));
			ItemStack leads = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world)
					.map(recipe -> recipe.value().craft(input, world.getRegistryManager())).orElse(ItemStack.EMPTY);
			ctx.assertTrue(leads.isOf(Items.LEAD) && leads.getCount() == 2, "5 string make 2 leads: " + leads);
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.killAllEntities();
		ctx.complete();
	}

	/** Under water, sprinting no longer swims, the swim key does; on land the key crawls. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void theSwimKeySwimsAndCrawls(TestContext ctx) {
		for (BlockPos pos : BlockPos.iterate(2, 2, 6, 6, 5, 10)) {
			ctx.setBlockState(pos.toImmutable(), Blocks.WATER.getDefaultState());
		}
		ServerPlayerEntity diver = FishingTests.survivalPlayer(ctx, new BlockPos(4, 2, 8));
		ServerPlayerEntity walker = FishingTests.survivalPlayer(ctx, new BlockPos(10, 2, 8));
		try {
			for (int i = 0; i < 2; i++) {
				diver.baseTick();
			}
			ctx.assertTrue(diver.isSubmergedInWater(), "under water");
			diver.setSprinting(true);
			diver.updateSwimming();
			ctx.assertTrue(!diver.isSwimming(), "sprinting doesn't swim");
			((SwimKey.Holder) diver).eternalreturn$setSwimKey(true);
			diver.updateSwimming();
			ctx.assertTrue(diver.isSwimming(), "the swim key does");
			walker.setOnGround(true);
			walker.updateSwimming();
			ctx.assertTrue(!walker.isSwimming(), "on land, standing");
			((SwimKey.Holder) walker).eternalreturn$setSwimKey(true);
			walker.updateSwimming();
			ctx.assertTrue(walker.isSwimming() && !walker.isTouchingWater(), "the swim key crawls");
		} finally {
			FishingTests.disconnect(diver);
			FishingTests.disconnect(walker);
		}
		ctx.killAllEntities();
		ctx.complete();
	}
}
