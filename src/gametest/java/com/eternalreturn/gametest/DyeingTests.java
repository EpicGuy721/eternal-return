package com.eternalreturn.gametest;

import com.eternalreturn.blocks.Dyeing;
import com.eternalreturn.logic.ArmorStandPoses;
import com.eternalreturn.mobs.ai.BreakLightsGoal;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.block.RedstoneLampBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Dyed water cauldrons and redstone lamps, armor stand poses, how close zombies get to break things. */
public class DyeingTests implements FabricGameTest {
	private static void use(TestContext ctx, ServerPlayerEntity player, BlockPos rel, ItemStack stack) {
		BlockPos pos = ctx.getAbsolutePos(rel);
		player.setStackInHand(Hand.MAIN_HAND, stack);
		ctx.getBlockState(rel).onUseWithItem(stack, ctx.getWorld(), player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
	}

	private static int color(TestContext ctx, BlockPos rel) {
		return ctx.getWorld().getBlockEntity(ctx.getAbsolutePos(rel)) instanceof Dyeing.DyedBlockEntity dyed ? dyed.color() : -1;
	}

	/** Dye colours a water cauldron, more dye mixes in, a leather helmet dipped in takes the colour (a level used), a water bucket washes it out. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "dyeing")
	public void dyedCauldronWater(TestContext ctx) {
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(3, 1, 3));
		try {
			BlockPos cauldron = new BlockPos(1, 1, 1);
			ctx.setBlockState(cauldron, Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, 3));
			use(ctx, player, cauldron, new ItemStack(Items.RED_DYE));
			int red = DyeColor.RED.getEntityColor() & 0xFFFFFF;
			ctx.assertTrue(ctx.getBlockState(cauldron).isOf(Dyeing.DYED_WATER_CAULDRON) && color(ctx, cauldron) == red, "red water: " + Integer.toHexString(color(ctx, cauldron)));
			use(ctx, player, cauldron, new ItemStack(Items.BLUE_DYE));
			int mixed = color(ctx, cauldron);
			ctx.assertTrue(mixed == Dyeing.mix(red, DyeColor.BLUE.getEntityColor() & 0xFFFFFF) && mixed != red, "blue mixed in: " + Integer.toHexString(mixed));
			ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
			use(ctx, player, cauldron, helmet);
			ctx.assertTrue(DyedColorComponent.getColor(player.getMainHandStack(), 0) == (0xFF000000 | mixed), "the helmet takes the colour");
			ctx.assertTrue(ctx.getBlockState(cauldron).get(LeveledCauldronBlock.LEVEL) == 2, "a level used");
			use(ctx, player, cauldron, new ItemStack(Items.WATER_BUCKET));
			ctx.assertTrue(ctx.getBlockState(cauldron).isOf(Blocks.WATER_CAULDRON) && ctx.getBlockState(cauldron).get(LeveledCauldronBlock.LEVEL) == 3,
					"a water bucket washes the dye out: " + ctx.getBlockState(cauldron));
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	/** A redstone lamp dyes in a crafting grid; placed, it's a dyed lamp of that colour that lights with redstone; broken, it drops itself dyed. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "dyeing", tickLimit = 40)
	public void dyedRedstoneLamps(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		CraftingRecipeInput input = CraftingRecipeInput.create(2, 1, List.of(new ItemStack(Items.REDSTONE_LAMP), new ItemStack(Items.LIME_DYE)));
		ItemStack lamp = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world).map(r -> r.value().craft(input, world.getRegistryManager()))
				.orElse(ItemStack.EMPTY);
		ctx.assertTrue(lamp.isOf(Items.REDSTONE_LAMP) && lamp.contains(DataComponentTypes.DYED_COLOR), "a lime lamp: " + lamp);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(5, 1, 5));
		try {
			ctx.setBlockState(new BlockPos(2, 0, 2), Blocks.STONE);
			BlockPos below = ctx.getAbsolutePos(new BlockPos(2, 0, 2));
			player.setStackInHand(Hand.MAIN_HAND, lamp.copy());
			lamp.useOnBlock(new ItemPlacementContext(player, Hand.MAIN_HAND, player.getMainHandStack(),
					new BlockHitResult(Vec3d.ofCenter(below).add(0, 0.5, 0), Direction.UP, below, false)));
			BlockPos placed = new BlockPos(2, 1, 2);
			ctx.assertTrue(ctx.getBlockState(placed).isOf(Dyeing.DYED_REDSTONE_LAMP), "placed as a dyed lamp: " + ctx.getBlockState(placed));
			ctx.assertTrue(color(ctx, placed) == (DyedColorComponent.getColor(lamp, 0) & 0xFFFFFF), "in its colour: " + Integer.toHexString(color(ctx, placed)));
			ctx.setBlockState(new BlockPos(3, 1, 2), Blocks.REDSTONE_BLOCK);
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.runAtTick(10, () -> {
			ctx.assertTrue(ctx.getBlockState(new BlockPos(2, 1, 2)).get(RedstoneLampBlock.LIT), "lit by redstone");
			BlockPos placed = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
			world.breakBlock(placed, true);
			List<ItemEntity> drops = world.getEntitiesByClass(ItemEntity.class, new Box(placed).expand(2.0), item -> item.getStack().isOf(Items.REDSTONE_LAMP));
			ctx.assertTrue(drops.size() == 1 && DyedColorComponent.getColor(drops.get(0).getStack(), 0) == DyedColorComponent.getColor(lamp, 0),
					"it drops itself, dyed: " + drops);
			drops.forEach(ItemEntity::discard);
			ctx.complete();
		});
	}

	/** Armor stands have arms; sneaking and using one steps it through its poses; a redstone signal sets the pose by strength. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "dyeing", tickLimit = 40)
	public void armorStandPoses(TestContext ctx) {
		for (BlockPos pos : BlockPos.iterate(new BlockPos(0, 0, 0), new BlockPos(5, 0, 5))) {
			ctx.setBlockState(pos, Blocks.STONE);
		}
		ArmorStandEntity stand = ctx.spawnEntity(EntityType.ARMOR_STAND, new BlockPos(2, 1, 2));
		ctx.assertTrue(stand.shouldShowArms(), "arms");
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(4, 1, 2));
		try {
			player.setSneaking(true);
			stand.interactAt(player, Vec3d.ZERO, Hand.MAIN_HAND);
			ArmorStandPoses.Pose none = ArmorStandPoses.POSES.get(1);
			ctx.assertTrue(stand.getRightArmRotation().equals(none.rightArm()) && ((ArmorStandPoses.Holder) stand).eternalreturn$pose() == 1,
					"sneak-used, on to the next pose");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.setBlockState(new BlockPos(3, 1, 2), Blocks.REDSTONE_BLOCK);
		ctx.runAtTick(10, () -> {
			ArmorStandPoses.Pose powered = ArmorStandPoses.POSES.get(12);
			ctx.assertTrue(stand.getHeadRotation().equals(powered.head()), "a full signal: the last pose");
			ctx.setBlockState(new BlockPos(3, 1, 2), Blocks.AIR);
		});
		ctx.runAtTick(25, () -> {
			ctx.assertTrue(stand.getRightArmRotation().equals(ArmorStandPoses.POSES.get(1).rightArm()), "the signal gone, its own pose back");
			stand.discard();
			ctx.complete();
		});
	}

	/** A zombie reaches a block only right beside it, and not through a wall. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "dyeing")
	public void zombiesReachOnlyNear(TestContext ctx) {
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		zombie.setAiDisabled(true);
		ctx.setBlockState(new BlockPos(2, 1, 1), Blocks.TORCH);
		ctx.setBlockState(new BlockPos(4, 1, 1), Blocks.TORCH);
		ctx.setBlockState(new BlockPos(1, 1, 3), Blocks.TORCH);
		ctx.setBlockState(new BlockPos(1, 2, 2), Blocks.STONE);
		ctx.setBlockState(new BlockPos(1, 1, 2), Blocks.STONE);
		ctx.assertTrue(BreakLightsGoal.canReach(zombie, ctx.getAbsolutePos(new BlockPos(2, 1, 1))), "a torch beside it");
		ctx.assertFalse(BreakLightsGoal.canReach(zombie, ctx.getAbsolutePos(new BlockPos(4, 1, 1))), "not one 3 blocks off");
		ctx.assertFalse(BreakLightsGoal.canReach(zombie, ctx.getAbsolutePos(new BlockPos(1, 1, 3))), "nor one behind a wall");
		zombie.discard();
		ctx.complete();
	}
}
