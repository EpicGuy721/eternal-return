package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.blocks.IceBlocks;
import com.eternalreturn.blocks.IcicleBlock;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.Thickness;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;

import java.util.ArrayList;
import java.util.List;

/**
 * The ice blocks (com.eternalreturn.blocks.IceBlocks, backported from Java 26.4 as minecraft.wiki describes them): an
 * icicle column takes its segments as dripstone does, falls and hurts what it lands on when its support goes, melts in
 * light, grows under packed ice to 5 long; the ice crystal attaches to a face and gives light 4; the building blocks have
 * their recipes; wandering traders sell icicles and crystals. Flat runs (batch ice_blocks).
 */
public class IceBlockTests implements FabricGameTest {
	public static final String BATCH = "ice_blocks";

	private static BlockState icicle(Thickness thickness, Direction pointing) {
		return IceBlocks.ICICLE.getDefaultState().with(IcicleBlock.VERTICAL_DIRECTION, pointing).with(IcicleBlock.THICKNESS, thickness);
	}

	/** Hangs n icicles under the block at top, each placed as a tip, as a player would (the rest follow by neighbour updates). */
	private static void hang(TestContext ctx, BlockPos top, int n) {
		for (int i = 1; i <= n; i++) {
			ctx.setBlockState(top.down(i), icicle(Thickness.TIP, Direction.DOWN));
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void icicleColumnTakesItsSegments(TestContext ctx) {
		BlockPos top = new BlockPos(2, 7, 2);
		ctx.setBlockState(top, Blocks.PACKED_ICE);
		hang(ctx, top, 4);
		List<Thickness> expected = List.of(Thickness.BASE, Thickness.MIDDLE, Thickness.FRUSTUM, Thickness.TIP);
		for (int i = 0; i < 4; i++) {
			Thickness got = ctx.getBlockState(top.down(i + 1)).get(IcicleBlock.THICKNESS);
			ctx.assertTrue(got == expected.get(i), "segment " + (i + 1) + " is " + got + ", expected " + expected.get(i));
		}
		// A stalagmite under it, tip to tip: both become merged tips.
		ctx.setBlockState(new BlockPos(2, 1, 2), Blocks.STONE);
		ctx.setBlockState(new BlockPos(2, 2, 2), icicle(Thickness.TIP_MERGE, Direction.UP));
		ctx.setBlockState(top.down(4), icicle(Thickness.TIP_MERGE, Direction.DOWN));
		ctx.assertTrue(ctx.getBlockState(new BlockPos(2, 2, 2)).get(IcicleBlock.THICKNESS) == Thickness.TIP_MERGE, "the stalagmite's tip didn't merge");
		ctx.assertTrue(!IceBlocks.ICICLE.getDefaultState().with(IcicleBlock.VERTICAL_DIRECTION, Direction.DOWN)
				.canPlaceAt(ctx.getWorld(), ctx.getAbsolutePos(new BlockPos(5, 4, 5))), "an icicle hangs from thin air");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 200)
	public void icicleFallsAndHurts(TestContext ctx) {
		BlockPos top = new BlockPos(2, 7, 2);
		ctx.setBlockState(top, Blocks.PACKED_ICE);
		hang(ctx, top, 3);
		// A floor under the pig: with its AI off it doesn't fall, and a falling block only hurts what it lands on.
		ctx.setBlockState(new BlockPos(2, 1, 2), Blocks.STONE);
		PigEntity pig = ctx.spawnEntity(EntityType.PIG, new BlockPos(2, 2, 2));
		pig.setAiDisabled(true);
		float health = pig.getHealth();
		ctx.setBlockState(top, Blocks.AIR);
		ctx.waitAndRun(60, () -> {
			for (int y = 2; y <= 6; y++) {
				ctx.assertTrue(!ctx.getBlockState(new BlockPos(2, y, 2)).isOf(IceBlocks.ICICLE), "an icicle is left at y " + y);
			}
			ctx.assertTrue(ctx.getWorld().getEntitiesByClass(FallingBlockEntity.class, new Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(10), e -> true).isEmpty(),
					"falling icicles still in the air");
			ctx.assertTrue(pig.getHealth() < health, "the pig wasn't hurt: " + pig.getHealth());
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 300)
	public void icicleMeltsInLight(TestContext ctx) {
		BlockPos top = new BlockPos(2, 7, 2);
		ctx.setBlockState(top, Blocks.STONE);
		hang(ctx, top, 2);
		ServerWorld world = ctx.getWorld();
		BlockPos root = ctx.getAbsolutePos(top.down());
		BlockPos tip = ctx.getAbsolutePos(top.down(2));
		ctx.assertTrue(!IcicleBlock.meltsAt(world, tip), "the icicle melts in the dark");
		ctx.setBlockState(new BlockPos(3, 5, 2), Blocks.GLOWSTONE);
		ctx.waitAndRun(20, () -> {
			ctx.assertTrue(IcicleBlock.meltsAt(world, tip), "light " + world.getLightLevel(net.minecraft.world.LightType.BLOCK, tip) + " doesn't melt it");
			// The root segment never melts; the tip does, and falls.
			world.getBlockState(root).randomTick(world, root, Random.create(1));
			ctx.assertTrue(world.getBlockState(root).isOf(IceBlocks.ICICLE), "the root segment melted");
			world.getBlockState(tip).randomTick(world, tip, Random.create(1));
			ctx.assertTrue(!world.getBlockState(tip).isOf(IceBlocks.ICICLE), "the tip didn't melt");
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void icicleGrowsUnderPackedIceToFive(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		IcicleBlock block = (IcicleBlock) IceBlocks.ICICLE;
		BlockPos top = new BlockPos(2, 8, 2);
		ctx.setBlockState(top, Blocks.PACKED_ICE);
		hang(ctx, top, 1);
		int grown = 0;
		BlockPos tip = ctx.getAbsolutePos(top.down());
		while (block.grow(world, tip)) {
			grown++;
			tip = tip.down();
		}
		ctx.assertTrue(grown == IcicleBlock.MAX_LENGTH - 1, "grew " + grown + " blocks, to " + (grown + 1));
		// Under stone it doesn't grow.
		BlockPos other = new BlockPos(5, 8, 5);
		ctx.setBlockState(other, Blocks.STONE);
		hang(ctx, other, 1);
		ctx.assertTrue(!block.grow(world, ctx.getAbsolutePos(other.down())), "an icicle grew under stone");
		ctx.assertTrue(IcicleBlock.GROWTH_CHANCE > 0.0113F && IcicleBlock.GROWTH_CHANCE < 0.0114F, "growth chance " + IcicleBlock.GROWTH_CHANCE);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void iceCrystalAndBuildingBlocks(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockState crystal = IceBlocks.ICE_CRYSTAL.getDefaultState();
		ctx.assertTrue(crystal.getLuminance() == 4, "crystal light " + crystal.getLuminance());
		ctx.assertTrue(crystal.isToolRequired() && crystal.isIn(BlockTags.PICKAXE_MINEABLE), "the crystal doesn't need a pickaxe");
		ctx.assertTrue(IceBlocks.ICE_CRYSTAL.getHardness() == 1.5F && IceBlocks.ICICLE.getHardness() == 0.5F, "hardness");
		ctx.setBlockState(new BlockPos(2, 2, 2), Blocks.STONE);
		BlockState onWall = crystal.with(AmethystClusterBlock.FACING, Direction.EAST);
		ctx.assertTrue(onWall.canPlaceAt(world, ctx.getAbsolutePos(new BlockPos(3, 2, 2))), "a crystal won't go on a wall");
		ctx.assertTrue(!onWall.canPlaceAt(world, ctx.getAbsolutePos(new BlockPos(5, 4, 5))), "a crystal floats in the air");
		List<String> missing = new ArrayList<>();
		for (String recipe : List.of("polished_ice", "ice_bricks", "chiseled_ice", "polished_ice_from_packed_ice_stonecutting",
				"ice_bricks_from_polished_ice_stonecutting", "chiseled_ice_from_ice_bricks_stonecutting")) {
			java.util.Optional<RecipeEntry<?>> found = world.getRecipeManager().get(EternalReturn.id(recipe));
			if (found.isEmpty()) {
				missing.add(recipe);
			}
		}
		ctx.assertTrue(missing.isEmpty(), "missing recipes " + missing);
		ctx.assertTrue(IceBlocks.POLISHED_ICE.getSlipperiness() == Blocks.PACKED_ICE.getSlipperiness(), "polished ice isn't slippery");
		// Their own sounds (the owner's clips), registered.
		ctx.assertTrue(IceBlocks.ICICLE.getDefaultState().getSoundGroup() == IceBlocks.ICICLE_SOUNDS
				&& IceBlocks.ICE_CRYSTAL.getDefaultState().getSoundGroup() == IceBlocks.ICE_CRYSTAL_SOUNDS, "the ice blocks' sounds");
		for (String sound : List.of("block.icicle.break", "block.icicle.land", "block.ice_crystal.ambient", "block.ice_crystal.step")) {
			ctx.assertTrue(net.minecraft.registry.Registries.SOUND_EVENT.containsId(EternalReturn.id(sound)), "no sound " + sound);
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void wanderingTradersSellIce(TestContext ctx) {
		WanderingTraderEntity trader = ctx.spawnEntity(EntityType.WANDERING_TRADER, new BlockPos(2, 2, 2));
		boolean icicles = false;
		boolean crystals = false;
		for (TradeOffers.Factory factory : TradeOffers.WANDERING_TRADER_TRADES.get(1)) {
			TradeOffer offer = factory.create(trader, Random.create(1));
			if (offer == null) {
				continue;
			}
			icicles |= offer.getSellItem().isOf(IceBlocks.ICICLE.asItem()) && offer.getSellItem().getCount() == 2 && offer.getOriginalFirstBuyItem().getCount() == 1;
			crystals |= offer.getSellItem().isOf(IceBlocks.ICE_CRYSTAL.asItem()) && offer.getOriginalFirstBuyItem().getCount() == 2;
		}
		trader.discard();
		ctx.assertTrue(icicles && crystals, "trades: icicles " + icicles + ", crystals " + crystals);
		ctx.complete();
	}

	static Identifier id(String path) {
		return EternalReturn.id(path);
	}
}
