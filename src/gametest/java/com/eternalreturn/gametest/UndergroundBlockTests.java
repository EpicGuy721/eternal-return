package com.eternalreturn.gametest;

import com.eternalreturn.blocks.CaveMushrooms;
import com.eternalreturn.blocks.GasMushroomBlock;
import com.eternalreturn.blocks.MushroomGas;
import com.eternalreturn.blocks.StoneDripstoneBlock;
import com.eternalreturn.blocks.StoneDripstones;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.block.enums.Thickness;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;
import net.minecraft.world.biome.BiomeKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * Stone dripstone and the cave mushrooms (owner's request, 2026-11-05), flat runs (batch underground_blocks): the 27
 * dripstones take their segments, fall and hurt, fill a cauldron from the water over their stone, grow under their own
 * stone, and are matched to the stone (and its ores) they touch; a puffed purple or blue mushroom lets out its gas when
 * mined without Silk Touch and drops the unpuffed one, with Silk Touch drops itself quietly, and an unpuffed one is quiet;
 * the gas poisons (purple) or gives night vision (blue) and doesn't pass a wall; the green glows at 8 and spreads in the
 * dark in spite of its own light; spreading makes puffed mushrooms; bone meal grows each huge mushroom (the blue a cone),
 * the puffed ones quietly; the mushroom islands have no rain slimes.
 */
public class UndergroundBlockTests implements FabricGameTest {
	public static final String BATCH = "underground_blocks";

	private static BlockState piece(StoneDripstoneBlock block, Thickness thickness, Direction pointing) {
		return block.getDefaultState().with(StoneDripstoneBlock.VERTICAL_DIRECTION, pointing).with(StoneDripstoneBlock.THICKNESS, thickness);
	}

	private static void hang(TestContext ctx, StoneDripstoneBlock block, BlockPos top, int n) {
		for (int i = 1; i <= n; i++) {
			ctx.setBlockState(top.down(i), piece(block, Thickness.TIP, Direction.DOWN));
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void stoneDripstonesAndTheirStones(TestContext ctx) {
		List<String> problems = new ArrayList<>();
		if (StoneDripstones.all().size() != 27) {
			problems.add(StoneDripstones.all().size() + " stone dripstones");
		}
		for (StoneDripstoneBlock block : StoneDripstones.all().values()) {
			Identifier id = Registries.BLOCK.getId(block);
			if (Registries.ITEM.get(id) == Items.AIR || !block.getDefaultState().isIn(net.minecraft.registry.tag.BlockTags.PICKAXE_MINEABLE)) {
				problems.add(id + " has no item or isn't the pickaxe's");
			}
			if (block.getLootTableKey().getValue().getPath().isEmpty() || ctx.getWorld().getServer().getReloadableRegistries()
					.getLootTable(block.getLootTableKey()) == net.minecraft.loot.LootTable.EMPTY) {
				problems.add(id + " has no loot table");
			}
		}
		Object[][] expect = {{Blocks.STONE, "stone"}, {Blocks.GRANITE, "granite"}, {Blocks.DEEPSLATE_IRON_ORE, "deepslate"}, {Blocks.COAL_ORE, "stone"},
				{Blocks.NETHER_QUARTZ_ORE, "netherrack"}, {Blocks.RED_TERRACOTTA, "red_terracotta"}, {Blocks.BASALT, "basalt"},
				{com.eternalreturn.worldgen.stone.HardenedBlocks.get("hardened_sandstone"), "sandstone"},
				{Registries.BLOCK.get(Identifier.of("eternalreturn", "granite_iron_ore")), "granite"}};
		for (Object[] pair : expect) {
			StoneDripstoneBlock got = StoneDripstones.forHost((Block) pair[0]);
			if (got != StoneDripstones.get((String) pair[1])) {
				problems.add(Registries.BLOCK.getId((Block) pair[0]) + " takes " + (got == null ? "none" : got.kind));
			}
		}
		for (Block none : List.of(Blocks.DIRT, Blocks.GRAVEL, Blocks.PACKED_ICE, Blocks.OAK_PLANKS)) {
			if (StoneDripstones.forHost(none) != null) {
				problems.add(Registries.BLOCK.getId(none) + " takes a dripstone");
			}
		}
		// A column takes its segments; a stalagmite meeting it merges.
		StoneDripstoneBlock granite = StoneDripstones.get("granite");
		BlockPos top = new BlockPos(2, 7, 2);
		ctx.setBlockState(top, Blocks.GRANITE);
		hang(ctx, granite, top, 4);
		List<Thickness> expected = List.of(Thickness.BASE, Thickness.MIDDLE, Thickness.FRUSTUM, Thickness.TIP);
		for (int i = 0; i < 4; i++) {
			Thickness got = ctx.getBlockState(top.down(i + 1)).get(StoneDripstoneBlock.THICKNESS);
			if (got != expected.get(i)) {
				problems.add("segment " + (i + 1) + " is " + got);
			}
		}
		ctx.assertTrue(!granite.getDefaultState().with(StoneDripstoneBlock.VERTICAL_DIRECTION, Direction.DOWN)
				.canPlaceAt(ctx.getWorld(), ctx.getAbsolutePos(new BlockPos(5, 4, 5))), "a dripstone hangs from thin air");
		ctx.assertTrue(problems.isEmpty(), String.join("; ", problems));
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 200)
	public void stoneDripstoneFallsAndHurts(TestContext ctx) {
		StoneDripstoneBlock block = StoneDripstones.get("sandstone");
		BlockPos top = new BlockPos(2, 7, 2);
		ctx.setBlockState(top, Blocks.SANDSTONE);
		hang(ctx, block, top, 3);
		ctx.setBlockState(new BlockPos(2, 1, 2), Blocks.STONE);
		PigEntity pig = ctx.spawnEntity(EntityType.PIG, new BlockPos(2, 2, 2));
		pig.setAiDisabled(true);
		float health = pig.getHealth();
		ctx.setBlockState(top, Blocks.AIR);
		ctx.waitAndRun(60, () -> {
			for (int y = 2; y <= 6; y++) {
				ctx.assertTrue(!ctx.getBlockState(new BlockPos(2, y, 2)).isOf(block), "a dripstone is left at y " + y);
			}
			ctx.assertTrue(ctx.getWorld().getEntitiesByClass(FallingBlockEntity.class, new Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(10), e -> true).isEmpty(),
					"falling dripstone still in the air");
			ctx.assertTrue(pig.getHealth() < health, "the pig wasn't hurt: " + pig.getHealth());
			ctx.complete();
		});
	}

	/** Water on the stone over a stalactite: a drip fills the cauldron under it; under its own stone it grows. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 300, skyAccess = true)
	public void stoneDripstoneDripsAndGrows(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		StoneDripstoneBlock block = StoneDripstones.get("stone");
		BlockPos top = new BlockPos(2, 8, 2);
		ctx.setBlockState(top, Blocks.STONE);
		ctx.setBlockState(top.up(), Blocks.WATER);
		for (Direction side : Direction.Type.HORIZONTAL) {
			ctx.setBlockState(top.up().offset(side), Blocks.STONE);
		}
		ctx.setBlockState(top.up(2), Blocks.STONE);
		hang(ctx, block, top, 2);
		ctx.setBlockState(new BlockPos(2, 1, 2), Blocks.CAULDRON);
		BlockPos tip = ctx.getAbsolutePos(top.down(2));
		block.dripTick(world.getBlockState(tip), world, tip, 0.0F);
		// Growth (a stalactite under stone with still water on it), as a run of random ticks would make it; the cauldron
		// is in the way of a stalagmite, so this one grows at the tip.
		BlockPos grow = new BlockPos(5, 8, 5);
		ctx.setBlockState(grow, Blocks.STONE);
		ctx.setBlockState(grow.up(), Blocks.WATER);
		for (Direction side : Direction.Type.HORIZONTAL) {
			ctx.setBlockState(grow.up().offset(side), Blocks.STONE);
		}
		hang(ctx, block, grow, 1);
		ctx.setBlockState(new BlockPos(5, 1, 5), Blocks.STONE);
		Random random = Random.create(7);
		BlockPos growTip = ctx.getAbsolutePos(grow.down());
		for (int i = 0; i < 40; i++) {
			block.tryGrow(world.getBlockState(growTip), world, growTip, random);
		}
		int pieces = 0;
		for (int y = 2; y <= 7; y++) {
			if (ctx.getBlockState(new BlockPos(5, y, 5)).isOf(block)) {
				pieces++;
			}
		}
		int grown = pieces;
		ctx.assertTrue(grown >= 3, "only " + grown + " pieces after 40 growths");
		// Under granite (not its stone) a stone dripstone never grows.
		BlockPos other = new BlockPos(5, 8, 2);
		ctx.setBlockState(other, Blocks.GRANITE);
		ctx.setBlockState(other.up(), Blocks.WATER);
		for (Direction side : Direction.Type.HORIZONTAL) {
			ctx.setBlockState(other.up().offset(side), Blocks.STONE);
		}
		hang(ctx, block, other, 1);
		BlockPos otherTip = ctx.getAbsolutePos(other.down());
		for (int i = 0; i < 40; i++) {
			block.tryGrow(world.getBlockState(otherTip), world, otherTip, random);
		}
		ctx.assertTrue(!ctx.getBlockState(other.down(2)).isOf(block), "it grew under granite");
		ctx.waitAndRun(80, () -> {
			BlockState cauldron = ctx.getBlockState(new BlockPos(2, 1, 2));
			ctx.assertTrue(cauldron.isOf(Blocks.WATER_CAULDRON) && cauldron.get(LeveledCauldronBlock.LEVEL) >= 1, "the cauldron is " + cauldron);
			ctx.complete();
		});
	}

	// ------------------------------------------------------------------ mushrooms

	/** The test's own area (tests of a batch stand side by side). */
	private static Box area(TestContext ctx) {
		return Box.enclosing(ctx.getAbsolutePos(BlockPos.ORIGIN), ctx.getAbsolutePos(new BlockPos(7, 24, 7)));
	}

	private static int gasClouds(TestContext ctx) {
		return ctx.getWorld().getEntitiesByClass(MushroomGas.Cloud.class, area(ctx), e -> true).size();
	}

	private static List<ItemStack> mine(TestContext ctx, ServerPlayerEntity player, Block block, BlockPos rel, ItemStack tool) {
		ServerWorld world = ctx.getWorld();
		BlockPos abs = ctx.getAbsolutePos(rel);
		ctx.setBlockState(rel.down(), Blocks.MYCELIUM);
		ctx.setBlockState(rel, block.getDefaultState());
		player.setStackInHand(Hand.MAIN_HAND, tool.copy());
		player.interactionManager.tryBreakBlock(abs);
		List<ItemStack> drops = new ArrayList<>();
		for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, new Box(abs).expand(2), entity -> true)) {
			drops.add(item.getStack().copy());
			item.discard();
		}
		return drops;
	}

	private static String describe(List<ItemStack> drops) {
		return drops.stream().map(s -> s.getCount() + " " + Registries.ITEM.getId(s.getItem())).toList().toString();
	}

	private static boolean only(List<ItemStack> drops, Block block) {
		return drops.size() == 1 && drops.get(0).isOf(block.asItem()) && drops.get(0).getCount() == 1;
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 320)
	public void puffedMushroomsReleaseGas(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(6, 2, 6));
		ItemStack silk = new ItemStack(Items.DIAMOND_PICKAXE);
		silk.addEnchantment(world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.SILK_TOUCH), 1);
		List<String> problems = new ArrayList<>();
		try {
			// Unpuffed: quiet, drops itself.
			List<ItemStack> drops = mine(ctx, player, CaveMushrooms.PURPLE_MUSHROOM, new BlockPos(1, 2, 1), ItemStack.EMPTY);
			if (!only(drops, CaveMushrooms.PURPLE_MUSHROOM) || gasClouds(ctx) != 0) {
				problems.add("unpuffed purple: " + describe(drops) + ", " + gasClouds(ctx) + " clouds");
			}
			// Puffed with Silk Touch: quiet, drops itself puffed.
			drops = mine(ctx, player, CaveMushrooms.PUFFED_PURPLE_MUSHROOM, new BlockPos(1, 2, 1), silk);
			if (!only(drops, CaveMushrooms.PUFFED_PURPLE_MUSHROOM) || gasClouds(ctx) != 0) {
				problems.add("puffed purple with Silk Touch: " + describe(drops) + ", " + gasClouds(ctx) + " clouds");
			}
			// Puffed by hand: gas, and drops the unpuffed one.
			drops = mine(ctx, player, CaveMushrooms.PUFFED_PURPLE_MUSHROOM, new BlockPos(1, 2, 1), ItemStack.EMPTY);
			if (!only(drops, CaveMushrooms.PURPLE_MUSHROOM) || gasClouds(ctx) != 1) {
				problems.add("puffed purple by hand: " + describe(drops) + ", " + gasClouds(ctx) + " clouds");
			}
			drops = mine(ctx, player, CaveMushrooms.PUFFED_BLUE_MUSHROOM, new BlockPos(5, 2, 1), ItemStack.EMPTY);
			if (!only(drops, CaveMushrooms.BLUE_MUSHROOM) || gasClouds(ctx) != 2) {
				problems.add("puffed blue by hand: " + describe(drops) + ", " + gasClouds(ctx) + " clouds");
			}
			drops = mine(ctx, player, CaveMushrooms.GREEN_MUSHROOM, new BlockPos(5, 2, 4), ItemStack.EMPTY);
			if (!only(drops, CaveMushrooms.GREEN_MUSHROOM) || gasClouds(ctx) != 2) {
				problems.add("green: " + describe(drops) + ", " + gasClouds(ctx) + " clouds");
			}
			// Cap blocks: a puffed one by hand lets out gas and drops 0 to 2 small unpuffed mushrooms; with Silk Touch it
			// drops itself, puffed, quietly; an unpuffed one is quiet.
			drops = mine(ctx, player, CaveMushrooms.PUFFED_PURPLE_MUSHROOM_BLOCK, new BlockPos(1, 2, 5), ItemStack.EMPTY);
			if (gasClouds(ctx) != 3 || drops.stream().anyMatch(d -> !d.isOf(CaveMushrooms.PURPLE_MUSHROOM.asItem()))) {
				problems.add("puffed purple cap by hand: " + describe(drops) + ", " + gasClouds(ctx) + " clouds");
			}
			drops = mine(ctx, player, CaveMushrooms.PUFFED_BLUE_MUSHROOM_BLOCK, new BlockPos(1, 2, 5), silk);
			if (!only(drops, CaveMushrooms.PUFFED_BLUE_MUSHROOM_BLOCK) || gasClouds(ctx) != 3) {
				problems.add("puffed blue cap with Silk Touch: " + describe(drops) + ", " + gasClouds(ctx) + " clouds");
			}
			drops = mine(ctx, player, CaveMushrooms.BLUE_MUSHROOM_BLOCK, new BlockPos(1, 2, 5), silk);
			if (!only(drops, CaveMushrooms.BLUE_MUSHROOM_BLOCK) || gasClouds(ctx) != 3) {
				problems.add("unpuffed blue cap: " + describe(drops) + ", " + gasClouds(ctx) + " clouds");
			}
			// The names say which is which.
			for (Block block : List.of(CaveMushrooms.PUFFED_PURPLE_MUSHROOM, CaveMushrooms.PUFFED_BLUE_MUSHROOM_BLOCK)) {
				if (!block.asItem().getName().getString().startsWith("Puffed ")) {
					problems.add(Registries.BLOCK.getId(block) + " is named " + block.asItem().getName().getString());
				}
			}
			for (Block block : List.of(CaveMushrooms.PURPLE_MUSHROOM, CaveMushrooms.BLUE_MUSHROOM_BLOCK)) {
				if (!block.asItem().getName().getString().startsWith("Unpuffed ")) {
					problems.add(Registries.BLOCK.getId(block) + " is named " + block.asItem().getName().getString());
				}
			}
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.assertTrue(problems.isEmpty(), String.join("; ", problems));
		ctx.waitAndRun(MushroomGas.LIFETIME + 5, () -> {
			ctx.assertTrue(gasClouds(ctx) == 0, "the gas is still there after " + MushroomGas.LIFETIME + " ticks");
			ctx.complete();
		});
	}

	/** The purple's gas poisons a pig in it, the blue's gives night vision; a wall keeps both out. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 100)
	public void gasPoisonsAndGivesNightVision(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		// Two cells, each a 3 x 3 room with a pig, and a pig behind the wall of the first.
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				ctx.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
				ctx.setBlockState(new BlockPos(x, 4, z), Blocks.STONE);
				boolean wall = x == 0 || x == 4 || x == 7 || z == 0 || z == 4 || z == 7;
				for (int y = 2; y <= 3; y++) {
					ctx.setBlockState(new BlockPos(x, y, z), wall ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState());
				}
			}
		}
		PigEntity poisoned = ctx.spawnEntity(EntityType.PIG, new BlockPos(2, 2, 2));
		PigEntity seeing = ctx.spawnEntity(EntityType.PIG, new BlockPos(5, 2, 2));
		PigEntity walled = ctx.spawnEntity(EntityType.PIG, new BlockPos(2, 2, 5));
		for (PigEntity pig : List.of(poisoned, seeing, walled)) {
			pig.setAiDisabled(true);
		}
		MushroomGas.release(world, ctx.getAbsolutePos(new BlockPos(1, 2, 1)), MushroomGas.Kind.POISON);
		MushroomGas.release(world, ctx.getAbsolutePos(new BlockPos(6, 2, 1)), MushroomGas.Kind.NIGHT_VISION);
		ctx.waitAndRun(40, () -> {
			ctx.assertTrue(poisoned.hasStatusEffect(StatusEffects.POISON), "the pig in the purple gas isn't poisoned");
			ctx.assertTrue(seeing.hasStatusEffect(StatusEffects.NIGHT_VISION), "the pig in the blue gas has no night vision");
			ctx.assertTrue(!walled.hasStatusEffect(StatusEffects.POISON) && !walled.hasStatusEffect(StatusEffects.NIGHT_VISION), "the gas passed a wall");
			for (MushroomGas.Cloud cloud : world.getEntitiesByClass(MushroomGas.Cloud.class, area(ctx), e -> true)) {
				ctx.assertTrue(cloud.cells().size() >= 4 && cloud.cells().size() <= MushroomGas.CELLS, "a cloud fills " + cloud.cells().size() + " blocks");
				cloud.discard();
			}
			ctx.complete();
		});
	}

	/** The green mushroom glows at 8 and still spreads in a dark room; a purple one spreads puffed. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 200)
	public void greenGlowsAndMushroomsSpread(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				ctx.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
				ctx.setBlockState(new BlockPos(x, 4, z), Blocks.STONE);
				boolean wall = x == 0 || x == 7 || z == 0 || z == 7 || x == 4;
				for (int y = 2; y <= 3; y++) {
					ctx.setBlockState(new BlockPos(x, y, z), wall ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState());
				}
			}
		}
		BlockPos green = new BlockPos(2, 2, 3);
		BlockPos purple = new BlockPos(5, 2, 3);
		ctx.setBlockState(green, CaveMushrooms.GREEN_MUSHROOM);
		ctx.setBlockState(purple, CaveMushrooms.PURPLE_MUSHROOM);
		ctx.waitAndRun(20, () -> {
			int light = world.getLightLevel(LightType.BLOCK, ctx.getAbsolutePos(green));
			ctx.assertTrue(light == 8, "the green mushroom's light is " + light);
			ctx.assertTrue(CaveMushrooms.GREEN_MUSHROOM_BLOCK.getDefaultState().getLuminance() == 8, "the green cap block doesn't glow at 8");
			Random random = Random.create(3);
			for (int i = 0; i < 3000; i++) {
				BlockPos at = ctx.getAbsolutePos(green);
				world.getBlockState(at).randomTick(world, at, random);
				BlockPos p = ctx.getAbsolutePos(purple);
				world.getBlockState(p).randomTick(world, p, random);
			}
			int greens = 0;
			int puffed = 0;
			for (BlockPos pos : BlockPos.iterate(ctx.getAbsolutePos(new BlockPos(1, 2, 1)), ctx.getAbsolutePos(new BlockPos(6, 3, 6)))) {
				BlockState state = world.getBlockState(pos);
				greens += state.isOf(CaveMushrooms.GREEN_MUSHROOM) ? 1 : 0;
				puffed += state.isOf(CaveMushrooms.PUFFED_PURPLE_MUSHROOM) ? 1 : 0;
			}
			ctx.assertTrue(greens >= 2, "the green mushroom didn't spread in the dark: " + greens);
			ctx.assertTrue(puffed >= 1, "the purple mushroom spread no puffed ones");
			ctx.complete();
		});
	}

	/** Bone meal on mycelium: each colour grows its huge mushroom (purple flat, green dome, blue a tall cone), quietly. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 100, skyAccess = true)
	public void boneMealGrowsHugeMushrooms(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		Object[][] cases = {{CaveMushrooms.PUFFED_PURPLE_MUSHROOM, CaveMushrooms.PUFFED_PURPLE_MUSHROOM_BLOCK, 20},
				{CaveMushrooms.GREEN_MUSHROOM, CaveMushrooms.GREEN_MUSHROOM_BLOCK, 15}, {CaveMushrooms.PUFFED_BLUE_MUSHROOM, CaveMushrooms.PUFFED_BLUE_MUSHROOM_BLOCK, 20}};
		List<String> problems = new ArrayList<>();
		Random random = Random.create(11);
		for (Object[] c : cases) {
			// Clear the area for each in turn.
			for (BlockPos pos : BlockPos.iterate(ctx.getAbsolutePos(new BlockPos(0, 1, 0)), ctx.getAbsolutePos(new BlockPos(7, 22, 7)))) {
				world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
			}
			// Clearing the last one's puffed caps lets out their gas: clear that too.
			world.getEntitiesByClass(MushroomGas.Cloud.class, area(ctx), e -> true).forEach(net.minecraft.entity.Entity::discard);
			BlockPos rel = new BlockPos(3, 2, 3);
			ctx.setBlockState(rel.down(), Blocks.MYCELIUM);
			ctx.setBlockState(rel, (Block) c[0]);
			BlockPos abs = ctx.getAbsolutePos(rel);
			GasMushroomBlock mushroom = (GasMushroomBlock) c[0];
			boolean grown = false;
			for (int i = 0; i < 20 && !grown; i++) {
				grown = mushroom.trySpawningBigMushroom(world, abs, world.getBlockState(abs), random);
			}
			if (gasClouds(ctx) != 0) {
				problems.add("growing " + Registries.BLOCK.getId((Block) c[0]).getPath() + " let out gas");
			}
			int caps = 0;
			int stems = 0;
			int top = 0;
			for (BlockPos pos : BlockPos.iterate(ctx.getAbsolutePos(new BlockPos(0, 2, 0)), ctx.getAbsolutePos(new BlockPos(7, 22, 7)))) {
				BlockState state = world.getBlockState(pos);
				if (state.isOf((Block) c[1])) {
					caps++;
					top = Math.max(top, pos.getY() - abs.getY());
				}
				stems += state.isOf(Blocks.MUSHROOM_STEM) ? 1 : 0;
			}
			String name = Registries.BLOCK.getId((Block) c[0]).getPath();
			if (!grown || caps < (int) c[2] || stems < 3) {
				problems.add(name + ": grown " + grown + ", " + caps + " cap blocks, " + stems + " stem");
			}
			if (c[1] == CaveMushrooms.PUFFED_BLUE_MUSHROOM_BLOCK && (top < 10 || stems < 8)) {
				problems.add("the blue cone's top is " + top + " up on a stem of " + stems);
			}
		}
		ctx.assertTrue(problems.isEmpty(), String.join("; ", problems));
		ctx.complete();
	}

	/**
	 * A mushroom can't be placed under the open sky (unless on mycelium or podzol), so it never shows for a tick and goes;
	 * under a roof in the dark it can.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, skyAccess = true, tickLimit = 100)
	public void noMushroomsUnderTheOpenSky(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ctx.setBlockState(new BlockPos(1, 1, 1), Blocks.STONE);
		ctx.setBlockState(new BlockPos(3, 1, 1), Blocks.MYCELIUM);
		for (int x = 4; x <= 6; x++) {
			for (int z = 4; z <= 6; z++) {
				ctx.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
				ctx.setBlockState(new BlockPos(x, 4, z), Blocks.STONE);
				for (int y = 2; y <= 3; y++) {
					boolean wall = x != 5 || z != 5;
					ctx.setBlockState(new BlockPos(x, y, z), wall ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState());
				}
			}
		}
		ctx.waitAndRun(20, () -> {
			for (Block mushroom : List.of(Blocks.RED_MUSHROOM, CaveMushrooms.PURPLE_MUSHROOM, CaveMushrooms.GREEN_MUSHROOM)) {
				String name = Registries.BLOCK.getId(mushroom).getPath();
				ctx.assertTrue(!mushroom.getDefaultState().canPlaceAt(world, ctx.getAbsolutePos(new BlockPos(1, 2, 1))), name + " goes on stone under the sky");
				ctx.assertTrue(mushroom.getDefaultState().canPlaceAt(world, ctx.getAbsolutePos(new BlockPos(3, 2, 1))), name + " won't go on mycelium");
				ctx.assertTrue(mushroom.getDefaultState().canPlaceAt(world, ctx.getAbsolutePos(new BlockPos(5, 2, 5))), name + " won't go on stone in a dark room");
			}
			ctx.complete();
		});
	}

	/**
	 * A piston crushing a puffed cap block against obsidian lets out its gas, which spreads from beside the spot (the next
	 * block of the row moves into it); a piston only pushing one along lets nothing out.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 100)
	public void pistonsCrushingPuffedCapsLetOutGas(TestContext ctx) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				ctx.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
		net.minecraft.block.BlockState piston = Blocks.PISTON.getDefaultState().with(net.minecraft.block.FacingBlock.FACING, Direction.EAST);
		// Crushed: piston, puffed cap, obsidian.
		ctx.setBlockState(new BlockPos(1, 2, 2), piston);
		ctx.setBlockState(new BlockPos(2, 2, 2), CaveMushrooms.PUFFED_PURPLE_MUSHROOM_BLOCK);
		ctx.setBlockState(new BlockPos(3, 2, 2), Blocks.OBSIDIAN);
		// Pushed: piston, puffed cap, open air.
		ctx.setBlockState(new BlockPos(1, 2, 5), piston);
		ctx.setBlockState(new BlockPos(2, 2, 5), CaveMushrooms.PUFFED_BLUE_MUSHROOM_BLOCK);
		ctx.setBlockState(new BlockPos(0, 2, 2), Blocks.REDSTONE_BLOCK);
		ctx.setBlockState(new BlockPos(0, 2, 5), Blocks.REDSTONE_BLOCK);
		ctx.waitAndRun(20, () -> {
			List<MushroomGas.Cloud> clouds = ctx.getWorld().getEntitiesByClass(MushroomGas.Cloud.class, area(ctx), e -> true);
			ctx.assertTrue(!ctx.getBlockState(new BlockPos(2, 2, 2)).isOf(CaveMushrooms.PUFFED_PURPLE_MUSHROOM_BLOCK), "the cap wasn't crushed");
			ctx.assertTrue(ctx.getBlockState(new BlockPos(3, 2, 5)).isOf(CaveMushrooms.PUFFED_BLUE_MUSHROOM_BLOCK), "the blue cap wasn't pushed");
			ctx.assertTrue(clouds.size() == 1 && clouds.get(0).kind() == MushroomGas.Kind.POISON, clouds.size() + " clouds");
			ctx.assertTrue(clouds.get(0).cells().size() >= 4, "the crushed cap's gas fills " + clouds.get(0).cells().size() + " blocks");
			clouds.forEach(net.minecraft.entity.Entity::discard);
			ctx.complete();
		});
	}

	/** No rain slimes on the mushroom islands; elsewhere they still come. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void noRainSlimesOnMushroomIslands(TestContext ctx) {
		var biomes = ctx.getWorld().getRegistryManager().get(RegistryKeys.BIOME);
		boolean mushroom = biomes.get(BiomeKeys.MUSHROOM_FIELDS).getSpawnSettings().getSpawnEntries(SpawnGroup.MONSTER).getEntries().stream()
				.anyMatch(entry -> entry.type == EntityType.SLIME);
		boolean plains = biomes.get(BiomeKeys.PLAINS).getSpawnSettings().getSpawnEntries(SpawnGroup.MONSTER).getEntries().stream()
				.anyMatch(entry -> entry.type == EntityType.SLIME);
		ctx.assertTrue(!mushroom, "mushroom fields spawn slimes");
		ctx.assertTrue(plains, "plains lost their slimes");
		ctx.complete();
	}

	/** Breezes spawn naturally in overworld caves (any overworld biome, the cave biomes too), only below y 0, not in the Nether. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void breezesSpawnInDeepCaves(TestContext ctx) {
		var biomes = ctx.getWorld().getRegistryManager().get(RegistryKeys.BIOME);
		java.util.function.Predicate<net.minecraft.world.biome.Biome> breezes = biome -> biome.getSpawnSettings().getSpawnEntries(SpawnGroup.MONSTER).getEntries()
				.stream().anyMatch(entry -> entry.type == EntityType.BREEZE);
		ctx.assertTrue(breezes.test(biomes.get(BiomeKeys.PLAINS)), "no breezes in plains");
		ctx.assertTrue(breezes.test(biomes.get(BiomeKeys.DRIPSTONE_CAVES)), "no breezes in dripstone caves");
		// The cave biomes exist only with Moderner Beta.
		var mushroomCaves = biomes.get(Identifier.of("eternalreturn", "mushroom_caves"));
		ctx.assertTrue(mushroomCaves == null || breezes.test(mushroomCaves), "no breezes in mushroom caves");
		ctx.assertTrue(!breezes.test(biomes.get(BiomeKeys.NETHER_WASTES)), "breezes in the Nether");
		ServerWorld world = ctx.getWorld();
		ctx.assertTrue(net.minecraft.entity.SpawnRestriction.getLocation(EntityType.BREEZE) == net.minecraft.entity.SpawnLocationTypes.ON_GROUND,
				"breezes have no ground spawn rule");
		ctx.assertTrue(!net.minecraft.entity.SpawnRestriction.canSpawn(EntityType.BREEZE, world, net.minecraft.entity.SpawnReason.NATURAL,
				new BlockPos(0, 40, 0), world.getRandom()), "a natural breeze above y 0");
		ctx.complete();
	}
}
