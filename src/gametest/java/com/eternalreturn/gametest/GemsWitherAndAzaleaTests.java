package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.blocks.Woods;
import com.eternalreturn.items.Gems;
import com.eternalreturn.logic.EnchantCaps;
import com.eternalreturn.logic.InherentEnchantments;
import com.eternalreturn.worldgen.AzaleaTrees;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.feature.PlacedFeature;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Rubies and sapphires (ores, anvil uses, illager drops), gold swords' Looting, bone spiders' bones, the Wither, azalea. */
public class GemsWitherAndAzaleaTests implements FabricGameTest {
	private static void floor(TestContext ctx, Block block) {
		for (int x = -1; x <= 8; x++) {
			for (int z = -1; z <= 8; z++) {
				ctx.setBlockState(x, 0, z, block);
			}
		}
	}

	private static ItemStack craft(ServerWorld world, int width, int height, List<ItemStack> grid) {
		CraftingRecipeInput input = CraftingRecipeInput.create(width, height, grid);
		return world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world)
				.map(e -> ((CraftingRecipe) e.value()).craft(input, world.getRegistryManager())).orElse(ItemStack.EMPTY);
	}

	private static RegistryEntry<Enchantment> enchantment(ServerWorld world, String id) {
		return world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(Identifier.of(id)).orElseThrow();
	}

	/**
	 * Gems: blocks of 9; ruby and sapphire ores in every host stone; their ores drop gems (Silk Touch the ore); the ore
	 * features place nothing outside Eternal Return worlds.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void gemsAndOres(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ItemStack r = new ItemStack(Gems.RUBY);
		ItemStack block = craft(world, 3, 3, List.of(r, r, r, r, r, r, r, r, r));
		ctx.assertTrue(block.isOf(Gems.RUBY_BLOCK.asItem()), "9 rubies make a block: " + block);
		ItemStack back = craft(world, 1, 1, List.of(new ItemStack(Gems.SAPPHIRE_BLOCK)));
		ctx.assertTrue(back.isOf(Gems.SAPPHIRE) && back.getCount() == 9, "a sapphire block makes 9: " + back);
		for (String host : List.of("andesite", "diorite", "granite", "red_sandstone", "hardened_sandstone", "hardened_packed_ice")) {
			for (String gem : List.of("ruby", "sapphire")) {
				ctx.assertTrue(Registries.BLOCK.containsId(EternalReturn.id(host + "_" + gem + "_ore")), host + " " + gem + " ore exists");
			}
		}
		ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
		List<ItemStack> drops = Block.getDroppedStacks(Gems.RUBY_ORE.getDefaultState(), world, ctx.getAbsolutePos(BlockPos.ORIGIN), null, null, pick);
		ctx.assertTrue(drops.size() == 1 && drops.get(0).isOf(Gems.RUBY), "ruby ore drops a ruby: " + drops);
		drops = Block.getDroppedStacks(Registries.BLOCK.get(EternalReturn.id("andesite_sapphire_ore")).getDefaultState(), world,
				ctx.getAbsolutePos(BlockPos.ORIGIN), null, null, pick);
		ctx.assertTrue(drops.size() == 1 && drops.get(0).isOf(Gems.SAPPHIRE), "andesite sapphire ore drops a sapphire: " + drops);
		ctx.assertTrue(!new ItemStack(Items.STONE_PICKAXE).isSuitableFor(Gems.RUBY_ORE.getDefaultState()), "a stone pickaxe can't mine it");
		floor(ctx, Blocks.STONE);
		for (int y = 1; y < 6; y++) {
			for (int x = 0; x < 8; x++) {
				for (int z = 0; z < 8; z++) {
					ctx.setBlockState(x, y, z, Blocks.STONE);
				}
			}
		}
		PlacedFeature ruby = world.getRegistryManager().get(RegistryKeys.PLACED_FEATURE).get(EternalReturn.id("ore_ruby"));
		ctx.assertTrue(!ruby.generateUnregistered(world, world.getChunkManager().getChunkGenerator(), Random.create(1), ctx.getAbsolutePos(new BlockPos(4, 3, 4))),
				"outside an Eternal Return world the ruby ore feature places nothing");
		ctx.complete();
	}

	/**
	 * A ruby in an anvil: the result only says the inherent enchantment is rerolled; taking it rerolls it (a gold
	 * pickaxe's Silk Touch becomes another enchantment, which stays; any that goes on the item can come up). A sapphire: one more point of capacity, once.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void anvilGems(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(2, 1, 2));
		try {
			ItemStack pick = new ItemStack(Items.GOLDEN_PICKAXE);
			InherentEnchantments.apply(pick, world.getRegistryManager());
			var silk = enchantment(world, "minecraft:silk_touch");
			ctx.assertTrue(EnchantmentHelper.getLevel(silk, pick) == 1, "a gold pickaxe comes with Silk Touch");
			AnvilScreenHandler anvil = new AnvilScreenHandler(1, player.getInventory(), ScreenHandlerContext.create(world, ctx.getAbsolutePos(BlockPos.ORIGIN)));
			anvil.getSlot(0).setStack(pick);
			anvil.getSlot(1).setStack(new ItemStack(Gems.RUBY, 2));
			ItemStack preview = anvil.getSlot(2).getStack();
			ctx.assertTrue(Gems.pendingReroll(preview) && EnchantmentHelper.getLevel(silk, preview) == 1, "the preview doesn't show the new one");
			anvil.getSlot(2).onTakeItem(player, preview);
			ctx.assertTrue(!Gems.pendingReroll(preview), "taken, the reroll is done");
			ctx.assertTrue(EnchantmentHelper.getLevel(silk, preview) == 0 && !preview.getEnchantments().isEmpty(), "Silk Touch made way for another: "
					+ preview.getEnchantments());
			ctx.assertTrue(anvil.getSlot(1).getStack().getCount() == 1, "one ruby used");
			ItemStack before = preview.copy();
			InherentEnchantments.apply(preview, world.getRegistryManager());
			ctx.assertTrue(EnchantmentHelper.getLevel(silk, preview) == 0 && ItemStack.areEqual(before, preview), "and the material's own doesn't come back");
			// Any enchantment that goes on the item can come up, not only the material table's (but no curses).
			ItemStack goldSword = new ItemStack(Items.GOLDEN_SWORD);
			InherentEnchantments.apply(goldSword, world.getRegistryManager());
			List<Gems.Choice> choices = Gems.rerollChoices(goldSword, world.getRegistryManager());
			ctx.assertTrue(choices.stream().anyMatch(c -> c.replacement().matchesKey(enchantment(world, "minecraft:fire_aspect").getKey().orElseThrow())),
					"a gold sword's Looting can become Fire Aspect: " + choices.stream().map(c -> c.replacement().getIdAsString()).toList());
			ctx.assertTrue(choices.stream().noneMatch(c -> c.replacement().isIn(net.minecraft.registry.tag.EnchantmentTags.CURSE)), "never a curse");
			// Netherite: always the new one's maximum level (Unbreaking V never turns into a level-1 enchantment's level 1 and stays there).
			ItemStack netheritePick = new ItemStack(Items.NETHERITE_PICKAXE);
			InherentEnchantments.apply(netheritePick, world.getRegistryManager());
			List<Gems.Choice> netherite = Gems.rerollChoices(netheritePick, world.getRegistryManager());
			ctx.assertTrue(!netherite.isEmpty() && netherite.stream().allMatch(c -> c.level() == c.replacement().value().getMaxLevel()),
					"netherite rerolls come out at the maximum: " + netherite.stream().map(c -> c.replacement().getIdAsString() + " " + c.level()).toList());
			ItemStack nr = Gems.withRuby(netheritePick, world.getRegistryManager());
			Gems.finishReroll(nr, world.getRegistryManager(), Random.create(3));
			var unbreaking = enchantment(world, "minecraft:unbreaking");
			ctx.assertTrue(EnchantmentHelper.getLevel(unbreaking, nr) == 0 && nr.getEnchantments().getEnchantmentEntries().stream()
					.allMatch(e -> e.getIntValue() == e.getKey().value().getMaxLevel()), "rerolled netherite at the maximum: " + nr.getEnchantments());
			// A bow has no inherent enchantment: a ruby gives it one, at level 1, which then stays as its own.
			ItemStack bow = new ItemStack(Items.BOW);
			ctx.assertTrue(!InherentEnchantments.hasRules(bow), "a bow has no inherent enchantment of its own");
			ItemStack rubyBow = Gems.withRuby(bow, world.getRegistryManager());
			ctx.assertTrue(Gems.pendingReroll(rubyBow), "a ruby takes a bow");
			Gems.finishReroll(rubyBow, world.getRegistryManager(), Random.create(5));
			ctx.assertTrue(rubyBow.getEnchantments().getSize() == 1 && rubyBow.getEnchantments().getEnchantmentEntries().iterator().next().getIntValue() == 1,
					"the bow gained one enchantment at level 1: " + rubyBow.getEnchantments());
			ctx.assertTrue(InherentEnchantments.isOnlyInherent(rubyBow), "and it's the bow's inherent enchantment now: " + rubyBow.getEnchantments());
			// Diamond swords come with Sharpness I.
			ItemStack diamondSword = new ItemStack(Items.DIAMOND_SWORD);
			InherentEnchantments.apply(diamondSword, world.getRegistryManager());
			ctx.assertTrue(EnchantmentHelper.getLevel(enchantment(world, "minecraft:sharpness"), diamondSword) == 1, "a diamond sword has Sharpness I: "
					+ diamondSword.getEnchantments());
			// Sapphires.
			ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
			int cap = EnchantCaps.capFor(sword);
			ItemStack better = Gems.withSapphire(sword);
			ctx.assertTrue(EnchantCaps.capFor(better) == cap + 1, "a sapphire adds a point: " + EnchantCaps.capFor(better));
			ctx.assertTrue(Gems.withSapphire(better).isEmpty(), "only once");
			ctx.assertTrue(Gems.withSapphire(new ItemStack(Items.STICK)).isEmpty(), "and only on capped items");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	/** Gold swords come with Looting I; bone spiders drop bones; illagers drop rubies, not emeralds; vampires rubies too. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void dropsAndLooting(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ItemStack sword = new ItemStack(Items.GOLDEN_SWORD);
		InherentEnchantments.apply(sword, world.getRegistryManager());
		ctx.assertTrue(EnchantmentHelper.getLevel(enchantment(world, "minecraft:looting"), sword) == 1, "gold swords: Looting I");
		ItemStack rose = new ItemStack(com.eternalreturn.items.Materials.ROSEGOLD.get(0));
		InherentEnchantments.apply(rose, world.getRegistryManager());
		ctx.assertTrue(EnchantmentHelper.getLevel(enchantment(world, "minecraft:looting"), rose) == 1, "rose gold swords too");
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(2, 1, 2));
		try {
			for (var type : List.of(EntityType.VINDICATOR, EntityType.EVOKER, com.eternalreturn.mobs.NewMobs.BONE_SPIDER,
					com.eternalreturn.mobs.ModEntities.VAMPIRE)) {
				var mob = type.create(world);
				mob.refreshPositionAndAngles(ctx.getAbsolutePos(new BlockPos(4, 1, 4)), 0.0F, 0.0F);
				LootTable table = world.getServer().getReloadableRegistries().getLootTable(type.getLootTableId());
				LootContextParameterSet params = new LootContextParameterSet.Builder(world).add(LootContextParameters.THIS_ENTITY, mob)
						.add(LootContextParameters.ORIGIN, mob.getPos()).add(LootContextParameters.DAMAGE_SOURCE, world.getDamageSources().playerAttack(player))
						.add(LootContextParameters.ATTACKING_ENTITY, player).add(LootContextParameters.LAST_DAMAGE_PLAYER, player).build(LootContextTypes.ENTITY);
				Set<String> seen = new HashSet<>();
				for (int i = 0; i < 40; i++) {
					table.generateLoot(params).forEach(stack -> seen.add(Registries.ITEM.getId(stack.getItem()).toString()));
				}
				ctx.assertTrue(!seen.contains("minecraft:emerald"), type.getUntranslatedName() + " drops no emeralds: " + seen);
				String wanted = type == com.eternalreturn.mobs.NewMobs.BONE_SPIDER ? "minecraft:bone" : "eternalreturn:ruby";
				ctx.assertTrue(seen.contains(wanted), type.getUntranslatedName() + " drops " + wanted + ": " + seen);
			}
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	/** At half health the Wither explodes, calls three wither skeletons, and can't heal back above half. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "wither_phase", tickLimit = 100)
	public void witherSecondPhase(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, Blocks.STONE);
		WitherEntity wither = ctx.spawnEntity(EntityType.WITHER, new BlockPos(4, 2, 4));
		wither.setInvulTimer(0);
		wither.setAiDisabled(false);
		wither.setHealth(wither.getMaxHealth() / 2.0F - 1.0F);
		ctx.addInstantFinalTask(() -> ctx.killAllEntities());
		ctx.runAtTick(ctx.getTick() + 20, () -> {
			List<WitherSkeletonEntity> skeletons = world.getEntitiesByClass(WitherSkeletonEntity.class, wither.getBoundingBox().expand(8.0), s -> true);
			ctx.assertTrue(skeletons.size() == 3, "three wither skeletons: " + skeletons.size());
			wither.setHealth(wither.getMaxHealth());
		});
		ctx.runAtTick(ctx.getTick() + 40, () -> {
			ctx.assertTrue(wither.getHealth() <= wither.getMaxHealth() / 2.0F, "it can't heal back above half: " + wither.getHealth());
			ctx.complete();
		});
	}

	/** Bone meal on an azalea bush grows an azalea tree with an azalea trunk, not an oak one. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "trees", skyAccess = true)
	public void bonemealedAzalea(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, Blocks.GRASS_BLOCK);
		BlockPos at = ctx.getAbsolutePos(new BlockPos(4, 1, 4));
		boolean grown = false;
		for (int i = 0; i < 20 && !grown; i++) {
			world.setBlockState(at, Blocks.AZALEA.getDefaultState());
			((net.minecraft.block.AzaleaBlock) Blocks.AZALEA).grow(world, Random.create(i), at, Blocks.AZALEA.getDefaultState());
			grown = !world.getBlockState(at).isOf(Blocks.AZALEA);
		}
		ctx.assertTrue(grown && world.getBlockState(at).isOf(Woods.AZALEA.log), "a bone-mealed azalea grows an azalea trunk: " + world.getBlockState(at));
		ctx.complete();
	}

	/** The azalea tree has an azalea trunk and azalea leaves; oaks stay oaks outside Eternal Return worlds. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "trees", skyAccess = true)
	public void azaleaTrees(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, Blocks.GRASS_BLOCK);
		var tree = world.getRegistryManager().get(RegistryKeys.CONFIGURED_FEATURE).get(AzaleaTrees.TREE);
		BlockPos at = ctx.getAbsolutePos(new BlockPos(4, 1, 4));
		boolean grown = false;
		for (int i = 0; i < 5 && !grown; i++) {
			grown = tree.generate(world, world.getChunkManager().getChunkGenerator(), Random.create(i), at);
		}
		ctx.assertTrue(grown && world.getBlockState(at).isOf(Woods.AZALEA.log), "an azalea trunk: " + world.getBlockState(at));
		boolean leaves = false;
		for (BlockPos pos : BlockPos.iterate(at.add(-3, 1, -3), at.add(3, 8, 3))) {
			leaves |= world.getBlockState(pos).isOf(Blocks.AZALEA_LEAVES) || world.getBlockState(pos).isOf(Blocks.FLOWERING_AZALEA_LEAVES);
		}
		ctx.assertTrue(leaves, "with azalea leaves");
		ctx.complete();
	}
}
