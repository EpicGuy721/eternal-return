package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.worldgen.ores.OreVariants;
import com.eternalreturn.worldgen.stone.HardenedBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

/**
 * The hardened host stones (HardenedBlocks): they behave like stone, a pickaxe (any, Silk Touch included)
 * mines them into the plain vanilla block, nothing else gets anything, and survival has no way to get the
 * hardened block itself. Plus the progression changes: every host stone that drops something other than
 * cobblestone makes stone tools, furnaces and the redstone parts that used to need cobblestone.
 */
public class StoneBlockTests implements FabricGameTest {
	private static final TagKey<Block> CARVABLE = TagKey.of(RegistryKeys.BLOCK, EternalReturn.id("carvable"));

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void hardenedBlocksBehaveLikeStone(TestContext ctx) {
		BlockState stone = Blocks.STONE.getDefaultState();
		for (HardenedBlocks.Definition definition : HardenedBlocks.DEFINITIONS) {
			Block block = HardenedBlocks.get(definition.name());
			BlockState state = block.getDefaultState();
			String name = definition.name();
			ctx.assertTrue(block.getHardness() == Blocks.STONE.getHardness(), name + " hardness " + block.getHardness());
			ctx.assertTrue(block.getBlastResistance() == Blocks.STONE.getBlastResistance(), name + " blast resistance " + block.getBlastResistance());
			ctx.assertTrue(state.isToolRequired(), name + " needs no tool");
			ctx.assertTrue(state.getSoundGroup() == BlockSoundGroup.STONE, name + " sound");
			ctx.assertTrue(state.isOpaqueFullCube(ctx.getWorld(), BlockPos.ORIGIN) && stone.isOpaqueFullCube(ctx.getWorld(), BlockPos.ORIGIN), name + " not opaque");
			ctx.assertTrue(block.getSlipperiness() == Blocks.STONE.getSlipperiness(), name + " slippery " + block.getSlipperiness());
			ctx.assertTrue(!state.hasRandomTicks(), name + " ticks (could melt)");
			for (TagKey<Block> tag : List.of(BlockTags.PICKAXE_MINEABLE, BlockTags.BASE_STONE_OVERWORLD, CARVABLE)) {
				ctx.assertTrue(state.isIn(tag), name + " not in " + tag.id());
			}
			// Ores reach it as a host stone with its own ore variants, not through the vanilla stone ore tag.
			ctx.assertTrue(OreVariants.isHost(state) && !state.isIn(BlockTags.STONE_ORE_REPLACEABLES), name + " is not an ore host, or is in the vanilla stone ore tag");
			ctx.assertTrue(block.getDefaultMapColor() == definition.base().getDefaultMapColor(), name + " map colour");
		}
		for (Block host : List.of(Blocks.RED_SANDSTONE, Blocks.SANDSTONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE)) {
			ctx.assertTrue(host.getDefaultState().isIn(CARVABLE), host + " not carvable");
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void hardenedBlocksDropTheVanillaBlock(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ItemStack silkTouch = new ItemStack(Items.DIAMOND_PICKAXE);
		silkTouch.addEnchantment(world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.SILK_TOUCH), 1);
		List<ItemStack> pickaxes = List.of(new ItemStack(Items.WOODEN_PICKAXE), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_PICKAXE),
				new ItemStack(Items.GOLDEN_PICKAXE), new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.NETHERITE_PICKAXE), silkTouch);
		List<ItemStack> others = List.of(ItemStack.EMPTY, new ItemStack(Items.DIAMOND_SHOVEL), new ItemStack(Items.DIAMOND_AXE),
				new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.DIAMOND_HOE), new ItemStack(Items.SHEARS));
		BlockPos rel = new BlockPos(1, 2, 1);
		BlockPos abs = ctx.getAbsolutePos(rel);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 2, 3));
		List<String> problems = new ArrayList<>();
		try {
			for (HardenedBlocks.Definition definition : HardenedBlocks.DEFINITIONS) {
				Block block = HardenedBlocks.get(definition.name());
				for (ItemStack tool : pickaxes) {
					List<ItemStack> drops = mine(ctx, player, block, rel, abs, tool);
					if (drops.size() != 1 || !drops.get(0).isOf(definition.drop()) || drops.get(0).getCount() != 1) {
						problems.add(definition.name() + " with " + tool + " dropped " + drops);
					}
				}
				for (ItemStack tool : others) {
					List<ItemStack> drops = mine(ctx, player, block, rel, abs, tool);
					if (!drops.isEmpty()) {
						problems.add(definition.name() + " with " + tool + " dropped " + drops);
					}
				}
			}
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.assertTrue(problems.isEmpty(), String.join("; ", problems));
		ctx.complete();
	}

	private static List<ItemStack> mine(TestContext ctx, ServerPlayerEntity player, Block block, BlockPos rel, BlockPos abs, ItemStack tool) {
		ServerWorld world = ctx.getWorld();
		ctx.setBlockState(rel, block.getDefaultState());
		player.setStackInHand(Hand.MAIN_HAND, tool.copy());
		player.interactionManager.tryBreakBlock(abs);
		ctx.assertTrue(world.getBlockState(abs).isAir(), block + " not broken with " + tool);
		List<ItemStack> drops = new ArrayList<>();
		for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, new Box(abs).expand(3), entity -> true)) {
			drops.add(item.getStack().copy());
			item.discard();
		}
		return drops;
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void hardenedBlocksAreNotObtainable(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		for (HardenedBlocks.Definition definition : HardenedBlocks.DEFINITIONS) {
			Block block = HardenedBlocks.get(definition.name());
			Item item = block.asItem();
			for (RecipeEntry<?> recipe : world.getRecipeManager().values()) {
				ItemStack result = recipe.value().getResult(world.getRegistryManager());
				ctx.assertTrue(!result.isOf(item), definition.name() + " is made by recipe " + recipe.id());
			}
			ctx.assertTrue(world.getServer().getReloadableRegistries().getLootTable(block.getLootTableKey()) == LootTable.EMPTY,
					definition.name() + " has a loot table");
			ctx.assertTrue(!item.getDefaultStack().isIn(ItemTags.STONE_TOOL_MATERIALS) && !item.getDefaultStack().isIn(ItemTags.STONE_CRAFTING_MATERIALS),
					definition.name() + " is a crafting material");
		}
		// In the creative inventory, next to the block it looks like.
		ItemGroups.updateDisplayContext(world.getEnabledFeatures(), false, world.getRegistryManager());
		ItemGroup natural = Registries.ITEM_GROUP.get(ItemGroups.NATURAL);
		for (HardenedBlocks.Definition definition : HardenedBlocks.DEFINITIONS) {
			Item item = HardenedBlocks.get(definition.name()).asItem();
			ctx.assertTrue(natural.getDisplayStacks().stream().anyMatch(stack -> stack.isOf(item)), definition.name() + " missing from the creative inventory");
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void hostStonesMakeStoneTools(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		List<String> problems = new ArrayList<>();
		Item stick = Items.STICK;
		for (Item host : List.of(Items.COBBLESTONE, Items.SANDSTONE, Items.RED_SANDSTONE, Items.GRANITE, Items.DIORITE, Items.ANDESITE)) {
			Item[][] recipes = {
					{host, host, host, null, stick, null, null, stick, null, Items.STONE_PICKAXE},
					{host, host, null, host, stick, null, null, stick, null, Items.STONE_AXE},
					{null, host, null, null, stick, null, null, stick, null, Items.STONE_SHOVEL},
					{null, host, null, null, host, null, null, stick, null, Items.STONE_SWORD},
					{host, host, null, null, stick, null, null, stick, null, Items.STONE_HOE},
					{host, host, host, host, null, host, host, host, host, Items.FURNACE},
					{null, Items.BLAZE_ROD, null, host, host, host, null, null, null, Items.BREWING_STAND},
					{host, host, host, host, Items.BOW, host, host, Items.REDSTONE, host, Items.DISPENSER},
					{host, host, host, host, null, host, host, Items.REDSTONE, host, Items.DROPPER},
					{null, stick, null, null, host, null, null, null, null, Items.LEVER},
					{host, host, host, Items.REDSTONE, Items.REDSTONE, Items.QUARTZ, host, host, host, Items.OBSERVER},
					{Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS, host, Items.IRON_INGOT, host, host, Items.REDSTONE, host, Items.PISTON},
			};
			for (Item[] recipe : recipes) {
				Item made = craft(world, recipe);
				if (made != recipe[9]) {
					problems.add(host + " -> " + recipe[9] + " gave " + made);
				}
			}
		}
		// Packed ice (what hardened packed ice drops) stays out on purpose.
		Item made = craft(world, new Item[]{Items.PACKED_ICE, Items.PACKED_ICE, Items.PACKED_ICE, null, stick, null, null, stick, null});
		ctx.assertTrue(made == Items.AIR, "packed ice makes " + made);
		ctx.assertTrue(problems.isEmpty(), String.join("; ", problems));
		ctx.complete();
	}

	private static Item craft(ServerWorld world, Item[] grid) {
		List<ItemStack> stacks = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			stacks.add(grid[i] == null ? ItemStack.EMPTY : new ItemStack(grid[i]));
		}
		CraftingRecipeInput input = CraftingRecipeInput.create(3, 3, stacks);
		return world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world)
				.map(entry -> ((CraftingRecipe) entry.value()).craft(input, world.getRegistryManager()).getItem()).orElse(Items.AIR);
	}
}
