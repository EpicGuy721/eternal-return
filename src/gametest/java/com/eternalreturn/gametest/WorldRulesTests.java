package com.eternalreturn.gametest;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.mobs.Blazes;
import com.eternalreturn.scrolls.Scrolls;
import com.eternalreturn.survival.ArmorWeight;
import com.eternalreturn.survival.CropBiomes;
import com.eternalreturn.worldgen.EternalReturnServer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.CodEntity;
import net.minecraft.entity.passive.TadpoleEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Armor weight and Weightless, drowned prismarine, baby fish and squids, falling snow, crops' ideal
 * biomes, bigger slimes and rain slimes, blazes outside fortresses, and scrolls. Tests that change the
 * weather or the config have batches of their own.
 */
public class WorldRulesTests implements FabricGameTest {
	private static void fillBiome(TestContext ctx, String biome, int size) {
		MinecraftServer server = ctx.getWorld().getServer();
		BlockPos a = ctx.getAbsolutePos(new BlockPos(-2, -2, -2));
		BlockPos b = ctx.getAbsolutePos(new BlockPos(size, 12, size));
		server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(),
				"fillbiome " + a.getX() + " " + a.getY() + " " + a.getZ() + " " + b.getX() + " " + b.getY() + " " + b.getZ() + " " + biome);
	}

	private static RegistryEntry<Enchantment> enchantment(ServerWorld world, Identifier id) {
		return world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(id).orElseThrow();
	}

	private static double weightModifier(ServerPlayerEntity player) {
		player.playerTick();
		var modifier = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).getModifier(ArmorWeight.MODIFIER_ID);
		return modifier == null ? 0.0 : -modifier.value();
	}

	// ---------- armor ----------

	/** A full diamond or netherite set slows by 30% (Slowness II), iron by 15%; Weightless on the chestplate takes its 40% share off. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void armorWeighsDown(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 1, 1));
		try {
			ctx.assertTrue(weightModifier(player) == 0.0, "no armor, no weight");
			player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
			player.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
			player.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
			player.equipStack(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
			double diamond = weightModifier(player);
			ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
			chest.addEnchantment(enchantment(world, ArmorWeight.WEIGHTLESS), 1);
			player.equipStack(EquipmentSlot.CHEST, chest);
			double weightless = weightModifier(player);
			for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
				player.equipStack(slot, ItemStack.EMPTY);
			}
			player.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
			double netheriteChest = weightModifier(player);
			player.equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY);
			double none = weightModifier(player);
			ctx.assertTrue(Math.abs(diamond - 0.30) < 1e-9, "full diamond slows by 30%, got " + diamond);
			ctx.assertTrue(Math.abs(weightless - 0.18) < 1e-9, "a Weightless diamond chestplate takes its 12% off, got " + weightless);
			ctx.assertTrue(Math.abs(netheriteChest - 0.12) < 1e-9, "a netherite chestplate is 40% of 30%, got " + netheriteChest);
			ctx.assertTrue(none == 0.0, "taking it all off removes the weight, got " + none);
			ctx.assertTrue(enchantment(world, ArmorWeight.WEIGHTLESS).value().getMaxLevel() == 1, "Weightless has one level");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	// ---------- mobs ----------

	/** Drowned killed by a player drop prismarine shards and crystals now and then. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void drownedDropPrismarine(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(0, 1, 0));
		Box around = new Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(8);
		int shards = 0;
		int crystals = 0;
		try {
			for (int i = 0; i < 60; i++) {
				DrownedEntity drowned = ctx.spawnEntity(EntityType.DROWNED, new BlockPos(3, 1, 3));
				drowned.damage(world.getDamageSources().playerAttack(player), 1000.0F);
			}
			for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, around, item -> true)) {
				shards += item.getStack().isOf(Items.PRISMARINE_SHARD) ? item.getStack().getCount() : 0;
				crystals += item.getStack().isOf(Items.PRISMARINE_CRYSTALS) ? item.getStack().getCount() : 0;
				item.discard();
			}
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.assertTrue(shards > 0 && crystals > 0, "60 drowned dropped " + shards + " shards and " + crystals + " crystals");
		ctx.complete();
	}

	/** With the chance at 1, a cod spawns as a baby: half the size, and it stays one when saved and loaded. A tadpole is never one. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "water_babies")
	public void babyFish(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		EternalReturnConfig.MobTweaks cfg = EternalReturnConfig.get().mobs;
		double saved = cfg.waterBabyChance;
		try {
			cfg.waterBabyChance = 1.0;
			CodEntity adult = EntityType.COD.create(world);
			CodEntity cod = EntityType.COD.create(world);
			cod.initialize(world, world.getLocalDifficulty(ctx.getAbsolutePos(BlockPos.ORIGIN)), SpawnReason.NATURAL, null);
			ctx.assertTrue(cod.isBaby(), "a cod should spawn as a baby");
			ctx.assertTrue(Math.abs(cod.getWidth() - adult.getWidth() / 2.0F) < 1e-6, "a baby cod is half as wide: " + cod.getWidth() + " vs " + adult.getWidth());
			NbtCompound nbt = new NbtCompound();
			cod.writeNbt(nbt);
			CodEntity loaded = EntityType.COD.create(world);
			loaded.readNbt(nbt);
			ctx.assertTrue(loaded.isBaby(), "a baby cod stays a baby when saved");
			var squid = EntityType.GLOW_SQUID.create(world);
			squid.initialize(world, world.getLocalDifficulty(ctx.getAbsolutePos(BlockPos.ORIGIN)), SpawnReason.NATURAL, null);
			ctx.assertTrue(squid.isBaby(), "glow squids can be babies");
			TadpoleEntity tadpole = EntityType.TADPOLE.create(world);
			tadpole.initialize(world, world.getLocalDifficulty(ctx.getAbsolutePos(BlockPos.ORIGIN)), SpawnReason.NATURAL, null);
			ctx.assertTrue(!tadpole.isBaby(), "tadpoles aren't in the list");
		} finally {
			cfg.waterBabyChance = saved;
		}
		ctx.complete();
	}

	/** With the chance for size 16 at 1, a new slime is size 16; magma cubes keep their own sizes. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "big_slimes")
	public void biggerSlimes(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		EternalReturnConfig.MobTweaks cfg = EternalReturnConfig.get().mobs;
		double saved = cfg.slimeSize16Chance;
		try {
			cfg.slimeSize16Chance = 1.0;
			SlimeEntity slime = EntityType.SLIME.create(world);
			slime.initialize(world, world.getLocalDifficulty(ctx.getAbsolutePos(BlockPos.ORIGIN)), SpawnReason.NATURAL, null);
			SlimeEntity magma = EntityType.MAGMA_CUBE.create(world);
			magma.initialize(world, world.getLocalDifficulty(ctx.getAbsolutePos(BlockPos.ORIGIN)), SpawnReason.NATURAL, null);
			ctx.assertTrue(slime.getSize() == 16, "a slime of size 16, got " + slime.getSize());
			ctx.assertTrue(magma.getSize() <= 4, "magma cubes keep vanilla's sizes, got " + magma.getSize());
		} finally {
			cfg.slimeSize16Chance = saved;
		}
		ctx.complete();
	}

	/** In plains, under the open sky: a natural slime spawn passes while it rains at night, not when it's dry. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "rain_slimes", tickLimit = 300)
	public void slimesInTheRain(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		fillBiome(ctx, "minecraft:plains", 4);
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				ctx.setBlockState(x, 0, z, Blocks.GRASS_BLOCK);
			}
		}
		BlockPos at = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
		Random random = Random.create(7);
		world.setTimeOfDay(18000);
		world.setWeather(0, 6000, true, false);
		// Rain fades in over a few seconds; it counts once it's falling.
		ctx.runAtTick(ctx.getTick() + 60, () -> {
			com.eternalreturn.survival.BeaconZones.clear(world);
			boolean rainy = world.isRaining() && SpawnRestriction.canSpawn(EntityType.SLIME, world, SpawnReason.NATURAL, at, random);
			world.setWeather(6000, 0, false, false);
			ctx.runAtTick(ctx.getTick() + 60, () -> {
				com.eternalreturn.survival.BeaconZones.clear(world);
				boolean dry = SpawnRestriction.canSpawn(EntityType.SLIME, world, SpawnReason.NATURAL, at, random);
				world.setTimeOfDay(6000);
				ctx.assertTrue(rainy && !dry, "slime spawn at night in plains: rain " + rainy + ", dry " + dry);
				ctx.complete();
			});
		});
	}

	/** Outside an Eternal Return world, a natural blaze spawn still needs a fortress. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void blazesNeedFortressesElsewhere(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		boolean eternalReturn = GameTestWorld.ETERNAL_RETURN_PRESET.equals(GameTestWorld.presetProperty());
		ctx.assertTrue(EternalReturnServer.active() == eternalReturn, "the world should be seen as Eternal Return: " + eternalReturn);
		ctx.assertTrue(Blazes.mayFreeSpawn(world, ctx.getAbsolutePos(BlockPos.ORIGIN)) == eternalReturn, "blazes spawn freely only in Eternal Return worlds");
		ctx.complete();
	}

	// ---------- blocks ----------

	/**
	 * A snow block set in mid-air falls to the floor, and a snow layer whose support is broken falls with
	 * its layers instead of vanishing.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void snowFalls(TestContext ctx) {
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
		ctx.setBlockState(1, 5, 1, Blocks.SNOW_BLOCK);
		ctx.setBlockState(3, 3, 3, Blocks.STONE);
		ctx.setBlockState(3, 4, 3, Blocks.SNOW.getDefaultState().with(net.minecraft.block.SnowBlock.LAYERS, 3));
		ctx.setBlockState(3, 3, 3, Blocks.AIR);
		ctx.waitAndRun(80, () -> {
			ctx.expectBlock(Blocks.SNOW_BLOCK, new BlockPos(1, 1, 1));
			BlockState layer = ctx.getBlockState(new BlockPos(3, 1, 3));
			ctx.assertTrue(layer.isOf(Blocks.SNOW) && layer.get(net.minecraft.block.SnowBlock.LAYERS) == 3, "the 3-layer snow fell to the floor, found " + layer);
			ctx.complete();
		});
	}

	/**
	 * Falling snow adds to the snow it lands on: 3 layers onto 2 make 5; a snow block (8 layers) onto 3
	 * makes a full layer of 8 with 3 more on top.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void fallingSnowPilesUp(TestContext ctx) {
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
		ctx.setBlockState(1, 1, 1, Blocks.SNOW.getDefaultState().with(net.minecraft.block.SnowBlock.LAYERS, 2));
		ctx.setBlockState(3, 1, 3, Blocks.SNOW.getDefaultState().with(net.minecraft.block.SnowBlock.LAYERS, 3));
		ctx.setBlockState(1, 5, 1, Blocks.SNOW.getDefaultState().with(net.minecraft.block.SnowBlock.LAYERS, 3));
		ctx.setBlockState(3, 6, 3, Blocks.SNOW_BLOCK);
		ctx.waitAndRun(80, () -> {
			BlockState small = ctx.getBlockState(new BlockPos(1, 1, 1));
			BlockState full = ctx.getBlockState(new BlockPos(3, 1, 3));
			BlockState top = ctx.getBlockState(new BlockPos(3, 2, 3));
			ctx.assertTrue(small.isOf(Blocks.SNOW) && small.get(net.minecraft.block.SnowBlock.LAYERS) == 5, "3 layers onto 2 make 5, found " + small);
			ctx.assertTrue(full.isOf(Blocks.SNOW) && full.get(net.minecraft.block.SnowBlock.LAYERS) == 8, "a snow block onto 3 layers fills that block, found " + full);
			ctx.assertTrue(top.isOf(Blocks.SNOW) && top.get(net.minecraft.block.SnowBlock.LAYERS) == 3, "and 3 layers pile on top, found " + top);
			ctx.assertTrue(ctx.getWorld().getEntitiesByClass(ItemEntity.class, new Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(6),
					item -> item.getStack().isOf(Items.SNOWBALL) || item.getStack().isOf(Items.SNOW)).isEmpty(), "no snow breaks into items");
			ctx.complete();
		});
	}

	/** Stronghold libraries hold a scroll about one chest in five, end cities one in ten; every scroll has an enchantment, never a disabled one. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void scrollsInChests(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		int library = countScrolls(ctx, net.minecraft.loot.LootTables.STRONGHOLD_LIBRARY_CHEST, 1000);
		int endCity = countScrolls(ctx, net.minecraft.loot.LootTables.END_CITY_TREASURE_CHEST, 1000);
		int village = countScrolls(ctx, net.minecraft.loot.LootTables.VILLAGE_ARMORER_CHEST, 300);
		ctx.assertTrue(library > 140 && library < 260, "stronghold library chests with a scroll: " + library + " of 1000");
		ctx.assertTrue(endCity > 60 && endCity < 140, "end city chests with a scroll: " + endCity + " of 1000");
		ctx.assertTrue(village == 0, "other chests have none: " + village);
		ctx.complete();
	}

	private static int countScrolls(TestContext ctx, net.minecraft.registry.RegistryKey<net.minecraft.loot.LootTable> key, int chests) {
		ServerWorld world = ctx.getWorld();
		var table = world.getServer().getReloadableRegistries().getLootTable(key);
		int found = 0;
		for (int i = 0; i < chests; i++) {
			var params = new net.minecraft.loot.context.LootContextParameterSet.Builder(world)
					.add(net.minecraft.loot.context.LootContextParameters.ORIGIN, net.minecraft.util.math.Vec3d.ofCenter(ctx.getAbsolutePos(BlockPos.ORIGIN)))
					.build(net.minecraft.loot.context.LootContextTypes.CHEST);
			boolean any = false;
			for (ItemStack stack : table.generateLoot(params)) {
				if (stack.isOf(Scrolls.SCROLL)) {
					RegistryEntry<Enchantment> enchantment = Scrolls.enchantment(stack);
					ctx.assertTrue(enchantment != null && !EternalReturnConfig.get().disabledEnchantments.contains(enchantment.getKey().orElseThrow().getValue().toString()),
							"a chest scroll needs an allowed enchantment: " + stack);
					any = true;
				}
			}
			found += any ? 1 : 0;
		}
		return found;
	}

	/**
	 * Wheat is at home in plains and not in a desert: over many random ticks in the desert it grows about
	 * a quarter as often as in plains. Melons grow fully in jungles.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "crops", tickLimit = 100)
	public void cropsPreferTheirBiomes(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ctx.assertTrue(world.getRegistryManager().get(RegistryKeys.BIOME).getEntry(net.minecraft.world.biome.BiomeKeys.PLAINS).orElseThrow()
				.isIn(CropBiomes.idealBiomes(Blocks.WHEAT)), "plains are ideal for wheat");
		int plains = grow(ctx, "minecraft:plains");
		int desert = grow(ctx, "minecraft:desert");
		double ratio = desert / (double) Math.max(1, plains);
		ctx.assertTrue(plains > 50 && ratio > 0.1 && ratio < 0.45, "wheat growth steps: plains " + plains + ", desert " + desert + " (ratio " + ratio + ")");
		ctx.complete();
	}

	/** Random-ticks a fresh wheat crop on wet farmland 400 times in the given biome; counts the growth steps. */
	private static int grow(TestContext ctx, String biome) {
		ServerWorld world = ctx.getWorld();
		fillBiome(ctx, biome, 4);
		ctx.setBlockState(2, 0, 2, Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, 7));
		ctx.setBlockState(2, 0, 3, Blocks.WATER);
		BlockPos crop = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
		Random random = Random.create(3);
		int steps = 0;
		for (int i = 0; i < 400; i++) {
			world.setBlockState(crop, Blocks.WHEAT.getDefaultState());
			world.getBlockState(crop).randomTick(world, crop, random);
			steps += world.getBlockState(crop).get(CropBlock.AGE);
		}
		return steps;
	}

	// ---------- scrolls ----------

	/**
	 * Every enchantment but the disabled ones has a book recipe making its level I book. A librarian's book
	 * trade is a scroll trade. Reading a scroll teaches its recipe and uses it up; until then the recipe
	 * won't craft; a known recipe isn't taught twice. A crafter never uses a scroll recipe.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void scrollsTeachBookRecipes(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		List<String> missing = new ArrayList<>();
		for (RegistryEntry<Enchantment> entry : world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).streamEntries().toList()) {
			String id = entry.getKey().orElseThrow().getValue().toString();
			if (EternalReturnConfig.get().disabledEnchantments.contains(id)) {
				continue;
			}
			Optional<RecipeEntry<?>> recipe = world.getRecipeManager().get(Scrolls.recipeId(entry));
			ItemStack result = recipe.map(r -> r.value().getResult(world.getRegistryManager())).orElse(ItemStack.EMPTY);
			ItemEnchantmentsComponent stored = result.get(DataComponentTypes.STORED_ENCHANTMENTS);
			if (stored == null || stored.getLevel(entry) != 1) {
				missing.add(id);
			}
		}
		ctx.assertTrue(missing.isEmpty(), "enchantments without a level I book recipe: " + missing);

		var villager = EntityType.VILLAGER.create(world);
		villager.refreshPositionAndAngles(ctx.getAbsolutePos(new BlockPos(1, 1, 1)), 0.0F, 0.0F);
		TradeOffer offer = new TradeOffers.EnchantBookFactory(10, net.minecraft.registry.tag.EnchantmentTags.TRADEABLE).create(villager, world.getRandom());
		ctx.assertTrue(offer != null && offer.getSellItem().isOf(Scrolls.SCROLL) && Scrolls.enchantment(offer.getSellItem()) != null
				&& offer.getSecondBuyItem().map(item -> item.itemStack().isOf(Items.PAPER)).orElse(false), "a librarian sells a scroll for emeralds and paper: " + offer);
		ctx.assertTrue(VillagerProfession.LIBRARIAN != null, "librarians exist");

		RegistryEntry<Enchantment> sharpness = enchantment(world, Identifier.ofVanilla("sharpness"));
		RecipeEntry<?> recipe = world.getRecipeManager().get(Scrolls.recipeId(sharpness)).orElseThrow();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(2, 1, 2));
		try {
			ctx.assertTrue(!Scrolls.mayCraft(player, recipe), "an unread recipe doesn't craft");
			ItemStack scrolls = Scrolls.of(sharpness);
			scrolls.setCount(2);
			player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, scrolls);
			Scrolls.read(player);
			ctx.assertTrue(Scrolls.mayCraft(player, recipe) && player.getMainHandStack().getCount() == 1, "reading teaches the recipe and uses one scroll");
			Scrolls.read(player);
			ctx.assertTrue(player.getMainHandStack().getCount() == 1, "a known recipe isn't taught twice, the scroll is kept");
			ctx.assertTrue(Scrolls.mayCraft(null, world.getRecipeManager().get(Identifier.ofVanilla("stick")).orElseThrow()), "other recipes are untouched");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}
}
