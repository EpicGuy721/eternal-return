package com.eternalreturn.gametest;

import com.eternalreturn.worldgen.ores.OreVariants;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.block.RedstoneOreBlock;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Every ore variant (OreVariants) against its vanilla stone ore: the same hardness, blast resistance,
 * sounds, tool tier and tags; the same drops and experience (vanilla's loot table with the same random
 * seeds, plain and with Fortune III, and real mining by a survival player), with Silk Touch giving the
 * variant itself; the same smelting and blasting; pick-block, creative tab and name; redstone variants
 * light up. Plus the textures: every ore's overlay has exactly the same shape.
 */
public class OreVariantTests implements FabricGameTest {
	/** Loot seeds 1 to SEEDS (seed 0 means "no seed" to the loot code, which then uses each table's own random sequence). */
	private static final int SEEDS = 40;

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void variantsBehaveLikeTheirVanillaOre(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		List<String> problems = new ArrayList<>();
		ctx.assertTrue(OreVariants.variants().size() == OreVariants.ores().size() * OreVariants.hosts().size() && OreVariants.variants().size() >= 48,
				"expected every ore in every host, got " + OreVariants.variants().size());
		List<TagKey<Block>> tierTags = List.of(BlockTags.NEEDS_STONE_TOOL, BlockTags.NEEDS_IRON_TOOL, BlockTags.NEEDS_DIAMOND_TOOL, BlockTags.PICKAXE_MINEABLE);
		for (OreVariants.Variant variant : OreVariants.variants()) {
			Block block = variant.block();
			Block vanilla = variant.ore().vanilla();
			String name = Registries.BLOCK.getId(block).getPath();
			check(problems, block.getHardness() == vanilla.getHardness(), name + " hardness " + block.getHardness());
			check(problems, block.getBlastResistance() == vanilla.getBlastResistance(), name + " blast resistance");
			check(problems, block.getDefaultState().isToolRequired() == vanilla.getDefaultState().isToolRequired(), name + " tool requirement");
			check(problems, block.getDefaultState().getSoundGroup() == vanilla.getDefaultState().getSoundGroup(), name + " sounds");
			check(problems, block.getClass().getSuperclass() == vanilla.getClass(), name + " is a " + block.getClass().getSuperclass().getSimpleName());
			if (vanilla instanceof ExperienceDroppingBlock vanillaXp) {
				ExperienceDroppingBlock xp = (ExperienceDroppingBlock) block;
				check(problems, xp.experienceDropped.getMin() == vanillaXp.experienceDropped.getMin()
						&& xp.experienceDropped.getMax() == vanillaXp.experienceDropped.getMax(), name + " experience " + xp.experienceDropped);
			}
			for (TagKey<Block> tag : tierTags) {
				check(problems, block.getDefaultState().isIn(tag) == vanilla.getDefaultState().isIn(tag), name + " tag " + tag.id());
			}
			String ore = variant.ore().name();
			TagKey<Block> oreTag = TagKey.of(RegistryKeys.BLOCK, Identifier.ofVanilla(ore + "_ores"));
			TagKey<net.minecraft.item.Item> oreItemTag = TagKey.of(RegistryKeys.ITEM, Identifier.ofVanilla(ore + "_ores"));
			TagKey<Block> conventionTag = TagKey.of(RegistryKeys.BLOCK, Identifier.of("c", "ores/" + ore));
			TagKey<Block> conventionOres = TagKey.of(RegistryKeys.BLOCK, Identifier.of("c", "ores"));
			check(problems, vanilla.getDefaultState().isIn(oreTag), "vanilla " + vanilla + " not in " + oreTag.id());
			check(problems, block.getDefaultState().isIn(oreTag) && new ItemStack(block).isIn(oreItemTag), name + " not in " + oreTag.id());
			check(problems, block.getDefaultState().isIn(conventionTag) && block.getDefaultState().isIn(conventionOres), name + " not in c:ores/" + ore);
			ItemStack pick = block.getPickStack(world, BlockPos.ORIGIN, block.getDefaultState());
			check(problems, pick.isOf(block.asItem()), name + " pick-block gives " + pick);
			check(problems, block.getName().getContent() instanceof TranslatableTextContent text && text.getKey().equals("block.eternalreturn.ore_variant")
					&& text.getArgs().length == 2, name + " name " + block.getName());
		}
		ctx.assertTrue(problems.isEmpty(), problems.size() + " problems: " + String.join("; ", problems.subList(0, Math.min(problems.size(), 12))));
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void variantLootMatchesVanilla(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ItemStack plain = new ItemStack(Items.NETHERITE_PICKAXE);
		ItemStack fortune = new ItemStack(Items.NETHERITE_PICKAXE);
		fortune.addEnchantment(enchantment(world, Enchantments.FORTUNE), 3);
		ItemStack silk = new ItemStack(Items.NETHERITE_PICKAXE);
		silk.addEnchantment(enchantment(world, Enchantments.SILK_TOUCH), 1);
		List<String> problems = new ArrayList<>();
		for (OreVariants.Variant variant : OreVariants.variants()) {
			Block block = variant.block();
			Block vanilla = variant.ore().vanilla();
			String name = Registries.BLOCK.getId(block).getPath();
			LootTable table = world.getServer().getReloadableRegistries().getLootTable(block.getLootTableKey());
			LootTable vanillaTable = world.getServer().getReloadableRegistries().getLootTable(vanilla.getLootTableKey());
			check(problems, table != LootTable.EMPTY, name + " has no loot table " + block.getLootTableKey().getValue());
			for (ItemStack tool : List.of(plain, fortune)) {
				for (long seed = 1; seed <= SEEDS; seed++) {
					String drops = describe(table.generateLoot(params(world, block, tool), seed));
					String vanillaDrops = describe(vanillaTable.generateLoot(params(world, vanilla, tool), seed));
					if (!drops.equals(vanillaDrops)) {
						problems.add(name + " with " + tool + " seed " + seed + ": " + drops + " vs vanilla " + vanillaDrops);
						break;
					}
				}
			}
			String silkDrops = describe(table.generateLoot(params(world, block, silk), 1));
			check(problems, silkDrops.equals("1 " + Registries.ITEM.getId(block.asItem())), name + " with Silk Touch drops " + silkDrops);
		}
		ctx.assertTrue(problems.isEmpty(), problems.size() + " problems: " + String.join("; ", problems.subList(0, Math.min(problems.size(), 8))));
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void variantsMineLikeVanilla(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ItemStack silk = new ItemStack(Items.DIAMOND_PICKAXE);
		silk.addEnchantment(enchantment(world, Enchantments.SILK_TOUCH), 1);
		List<ItemStack> pickaxes = List.of(new ItemStack(Items.WOODEN_PICKAXE), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_PICKAXE),
				new ItemStack(Items.DIAMOND_PICKAXE));
		BlockPos rel = new BlockPos(1, 2, 1);
		BlockPos abs = ctx.getAbsolutePos(rel);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 2, 3));
		List<String> problems = new ArrayList<>();
		try {
			for (OreVariants.Variant variant : OreVariants.variants()) {
				Block block = variant.block();
				Block vanilla = variant.ore().vanilla();
				String name = Registries.BLOCK.getId(block).getPath();
				for (ItemStack tool : pickaxes) {
					player.setStackInHand(Hand.MAIN_HAND, tool.copy());
					check(problems, player.canHarvest(block.getDefaultState()) == player.canHarvest(vanilla.getDefaultState()), name + " harvest with " + tool);
				}
				// Ten breaks with a diamond pickaxe: drops are the vanilla ore's item, experience in its range.
				int minXp = vanilla instanceof ExperienceDroppingBlock xp ? xp.experienceDropped.getMin() : 1;
				int maxXp = vanilla instanceof ExperienceDroppingBlock xp ? xp.experienceDropped.getMax() : 5;
				int totalXp = 0;
				for (int i = 0; i < 10; i++) {
					Mined mined = mine(ctx, player, block, rel, abs, new ItemStack(Items.DIAMOND_PICKAXE));
					totalXp += mined.xp();
					ItemStack expected = vanilla.getDefaultState().getDroppedStacks(new LootContextParameterSet.Builder(world)
							.add(LootContextParameters.ORIGIN, Vec3d.ofCenter(abs)).add(LootContextParameters.TOOL, new ItemStack(Items.DIAMOND_PICKAXE))).get(0);
					check(problems, mined.drops().stream().allMatch(stack -> stack.isOf(expected.getItem())) && !mined.drops().isEmpty(),
							name + " dropped " + mined.drops() + ", vanilla drops " + expected);
				}
				check(problems, totalXp >= 10 * minXp && totalXp <= 10 * maxXp, name + " gave " + totalXp + " experience in 10 breaks, vanilla range " + minXp + "-" + maxXp);
				Mined silkMined = mine(ctx, player, block, rel, abs, silk);
				check(problems, silkMined.drops().size() == 1 && silkMined.drops().get(0).isOf(block.asItem()) && silkMined.xp() == 0,
						name + " with Silk Touch dropped " + silkMined.drops() + " and " + silkMined.xp() + " experience");
			}
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.assertTrue(problems.isEmpty(), problems.size() + " problems: " + String.join("; ", problems.subList(0, Math.min(problems.size(), 8))));
		ctx.complete();
	}

	private record Mined(List<ItemStack> drops, int xp) {
	}

	private static Mined mine(TestContext ctx, ServerPlayerEntity player, Block block, BlockPos rel, BlockPos abs, ItemStack tool) {
		ServerWorld world = ctx.getWorld();
		ctx.setBlockState(rel, block.getDefaultState());
		player.setStackInHand(Hand.MAIN_HAND, tool.copy());
		player.interactionManager.tryBreakBlock(abs);
		List<ItemStack> drops = new ArrayList<>();
		Box box = new Box(abs).expand(3);
		for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, box, entity -> true)) {
			drops.add(item.getStack().copy());
			item.discard();
		}
		int xp = 0;
		for (ExperienceOrbEntity orb : world.getEntitiesByClass(ExperienceOrbEntity.class, box, entity -> true)) {
			xp += orb.getExperienceAmount();
			orb.discard();
		}
		return new Mined(drops, xp);
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void variantsSmeltLikeVanilla(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		List<String> problems = new ArrayList<>();
		for (OreVariants.Variant variant : OreVariants.variants()) {
			String name = Registries.BLOCK.getId(variant.block()).getPath();
			for (RecipeType<? extends AbstractCookingRecipe> type : List.of(RecipeType.SMELTING, RecipeType.BLASTING)) {
				AbstractCookingRecipe recipe = cooking(world, type, new ItemStack(variant.block()));
				AbstractCookingRecipe vanilla = cooking(world, type, new ItemStack(variant.ore().vanilla()));
				if (recipe == null || vanilla == null) {
					problems.add(name + " has no " + type + " recipe");
					continue;
				}
				ItemStack result = recipe.getResult(world.getRegistryManager());
				ItemStack vanillaResult = vanilla.getResult(world.getRegistryManager());
				check(problems, ItemStack.areEqual(result, vanillaResult) && recipe.getExperience() == vanilla.getExperience()
						&& recipe.getCookingTime() == vanilla.getCookingTime(), name + " " + type + " gives " + result + " (" + recipe.getExperience() + " xp, "
						+ recipe.getCookingTime() + " ticks), vanilla " + vanillaResult + " (" + vanilla.getExperience() + ", " + vanilla.getCookingTime() + ")");
			}
		}
		ctx.assertTrue(problems.isEmpty(), String.join("; ", problems));
		ctx.complete();
	}

	private static AbstractCookingRecipe cooking(ServerWorld world, RecipeType<? extends AbstractCookingRecipe> type, ItemStack input) {
		return world.getRecipeManager().getFirstMatch(type, new SingleStackRecipeInput(input), world).map(entry -> (AbstractCookingRecipe) entry.value()).orElse(null);
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void variantsAreInTheCreativeTab(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ItemGroups.updateDisplayContext(world.getEnabledFeatures(), false, world.getRegistryManager());
		ItemGroup natural = Registries.ITEM_GROUP.get(ItemGroups.NATURAL);
		List<ItemStack> stacks = new ArrayList<>(natural.getDisplayStacks());
		for (OreVariants.Variant variant : OreVariants.variants()) {
			int index = indexOf(stacks, variant.block().asItem());
			int deepslate = indexOf(stacks, variant.ore().deepslate().asItem());
			ctx.assertTrue(index > deepslate && deepslate >= 0 && index - deepslate <= OreVariants.hosts().size(),
					Registries.BLOCK.getId(variant.block()) + " at " + index + " in the natural blocks tab, its deepslate ore at " + deepslate);
		}
		ctx.complete();
	}

	private static int indexOf(List<ItemStack> stacks, net.minecraft.item.Item item) {
		for (int i = 0; i < stacks.size(); i++) {
			if (stacks.get(i).isOf(item)) {
				return i;
			}
		}
		return -1;
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void redstoneVariantsLightUp(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos rel = new BlockPos(1, 2, 1);
		BlockPos abs = ctx.getAbsolutePos(rel);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 2, 3));
		try {
			int litLight = net.minecraft.block.Blocks.REDSTONE_ORE.getDefaultState().with(RedstoneOreBlock.LIT, true).getLuminance();
			for (OreVariants.Variant variant : OreVariants.variants()) {
				if (!(variant.block() instanceof RedstoneOreBlock)) {
					continue;
				}
				ctx.setBlockState(rel, variant.block().getDefaultState());
				ctx.assertTrue(!world.getBlockState(abs).get(RedstoneOreBlock.LIT) && world.getBlockState(abs).getLuminance() == 0, variant.block() + " starts lit");
				world.getBlockState(abs).onBlockBreakStart(world, abs, player);
				BlockState lit = world.getBlockState(abs);
				ctx.assertTrue(lit.get(RedstoneOreBlock.LIT) && lit.getLuminance() == litLight && lit.hasRandomTicks(),
						variant.block() + " did not light up like redstone ore (light " + lit.getLuminance() + ", vanilla " + litLight + ")");
			}
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void overlaysShareOneShape(TestContext ctx) throws IOException {
		boolean[] shape = null;
		String first = null;
		for (OreVariants.Ore ore : OreVariants.ores()) {
			BufferedImage image;
			try (InputStream in = OreVariantTests.class.getResourceAsStream("/assets/eternalreturn/textures/block/ore_overlay/" + ore.name() + ".png")) {
				ctx.assertTrue(in != null, "no overlay texture for " + ore.name());
				image = ImageIO.read(in);
			}
			ctx.assertTrue(image.getWidth() == 16 && image.getHeight() == 16, ore.name() + " overlay is " + image.getWidth() + "x" + image.getHeight());
			boolean[] mask = new boolean[256];
			int opaque = 0;
			for (int i = 0; i < 256; i++) {
				int alpha = image.getRGB(i % 16, i / 16) >>> 24;
				ctx.assertTrue(alpha == 0 || alpha == 255, ore.name() + " overlay has half-transparent pixels");
				mask[i] = alpha == 255;
				opaque += mask[i] ? 1 : 0;
			}
			ctx.assertTrue(opaque > 20 && opaque < 128, ore.name() + " overlay covers " + opaque + " pixels");
			if (shape == null) {
				shape = mask;
				first = ore.name();
			} else {
				ctx.assertTrue(java.util.Arrays.equals(shape, mask), ore.name() + " overlay has a different shape from " + first);
			}
		}
		for (OreVariants.Variant variant : OreVariants.variants()) {
			String path = Registries.BLOCK.getId(variant.block()).getPath();
			for (String file : List.of("/assets/eternalreturn/blockstates/" + path + ".json", "/assets/eternalreturn/models/block/" + path + ".json",
					"/assets/eternalreturn/models/item/" + path + ".json")) {
				try (InputStream in = OreVariantTests.class.getResourceAsStream(file)) {
					ctx.assertTrue(in != null, "missing " + file);
				}
			}
		}
		ctx.complete();
	}

	private static LootContextParameterSet params(ServerWorld world, Block block, ItemStack tool) {
		return new LootContextParameterSet.Builder(world)
				.add(LootContextParameters.ORIGIN, Vec3d.ZERO)
				.add(LootContextParameters.TOOL, tool)
				.add(LootContextParameters.BLOCK_STATE, block.getDefaultState())
				.build(LootContextTypes.BLOCK);
	}

	private static String describe(List<ItemStack> stacks) {
		StringBuilder text = new StringBuilder();
		for (ItemStack stack : stacks) {
			text.append(text.isEmpty() ? "" : ", ").append(stack.getCount()).append(' ').append(Registries.ITEM.getId(stack.getItem()));
		}
		return text.toString();
	}

	private static RegistryEntry<Enchantment> enchantment(ServerWorld world, RegistryKey<Enchantment> key) {
		return world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(key);
	}

	private static void check(List<String> problems, boolean ok, String problem) {
		if (!ok) {
			problems.add(Objects.requireNonNull(problem));
		}
	}
}
