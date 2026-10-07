package com.eternalreturn.gametest;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.fishing.Bait;
import com.eternalreturn.fishing.DeepWaterLootCondition;
import com.eternalreturn.fishing.FishingItems;
import com.eternalreturn.fishing.FishingLoot;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.GameMode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Fishing and villager trades: loot odds, bait, full catch cycles in water and lava, worms, recipes, trades. */
public class FishingTests implements FabricGameTest {
	// ---------- helpers ----------

	private static void check(TestContext ctx, boolean condition, String message) {
		ctx.assertTrue(condition, message);
	}

	private static void withFishing(Consumer<EternalReturnConfig.FishingTweaks> change, Runnable body) {
		EternalReturnConfig cfg = EternalReturnConfig.get();
		EternalReturnConfig.FishingTweaks saved = cfg.fishing;
		EternalReturnConfig.FishingTweaks temp = new EternalReturnConfig.FishingTweaks();
		change.accept(temp);
		cfg.fishing = temp;
		try {
			body.run();
		} finally {
			cfg.fishing = saved;
		}
	}

	private static Object field(Object target, String name) {
		try {
			Field f = target.getClass().getDeclaredField(name);
			f.setAccessible(true);
			return f.get(target);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static void setField(Object target, String name, Object value) {
		try {
			Field f = target.getClass().getDeclaredField(name);
			f.setAccessible(true);
			f.set(target, value);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static void fillBiome(TestContext ctx, String biome) {
		MinecraftServer server = ctx.getWorld().getServer();
		BlockPos a = ctx.getAbsolutePos(new BlockPos(-4, -4, -4));
		BlockPos b = ctx.getAbsolutePos(new BlockPos(11, 20, 11));
		server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(),
				"fillbiome " + a.getX() + " " + a.getY() + " " + a.getZ() + " " + b.getX() + " " + b.getY() + " " + b.getZ() + " " + biome);
	}

	/** Rolls a fishing loot table the way FishingBobberEntity.use does, with the given bait on the hook. */
	private static Map<Item, Integer> sample(TestContext ctx, RegistryKey<LootTable> key, Item bait, int rolls) {
		return sample(ctx, key, bait, rolls, new BlockPos(3, 1, 3));
	}

	/** Same, with the hook at a given test-relative position (the deep water tests put it on a real water column). */
	private static Map<Item, Integer> sample(TestContext ctx, RegistryKey<LootTable> key, Item bait, int rolls, BlockPos hookAt) {
		ServerWorld world = ctx.getWorld();
		PlayerEntity angler = ctx.createMockPlayer(GameMode.SURVIVAL);
		FishingBobberEntity hook = new FishingBobberEntity(angler, world, 0, 0);
		hook.setPosition(Vec3d.ofBottomCenter(ctx.getAbsolutePos(hookAt)));
		setField(hook, "eternalreturn$bait", bait == null ? ItemStack.EMPTY : new ItemStack(bait));
		LootTable table = world.getServer().getReloadableRegistries().getLootTable(key);
		Map<Item, Integer> counts = new HashMap<>();
		for (int i = 0; i < rolls; i++) {
			LootContextParameterSet params = new LootContextParameterSet.Builder(world)
					.add(LootContextParameters.ORIGIN, hook.getPos())
					.add(LootContextParameters.TOOL, new ItemStack(Items.FISHING_ROD))
					.add(LootContextParameters.THIS_ENTITY, hook)
					.luck(0)
					.build(LootContextTypes.FISHING);
			for (ItemStack stack : table.generateLoot(params)) {
				counts.merge(stack.getItem(), 1, Integer::sum);
			}
		}
		return counts;
	}

	private static int n(Map<Item, Integer> counts, Item item) {
		return counts.getOrDefault(item, 0);
	}

	private static int fishCount(Map<Item, Integer> counts) {
		return counts.entrySet().stream().filter(e -> e.getKey().getDefaultStack().isIn(FishingItems.RAW_FISH)).mapToInt(Map.Entry::getValue).sum();
	}

	static ServerPlayerEntity survivalPlayer(TestContext ctx, BlockPos rel) {
		ServerWorld world = ctx.getWorld();
		ConnectedClientData data = ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(), "test-angler"), false);
		ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, data.gameProfile(), data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(connection);
		world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
		player.changeGameMode(GameMode.SURVIVAL);
		Vec3d p = Vec3d.ofBottomCenter(ctx.getAbsolutePos(rel));
		player.teleport(world, p.x, p.y, p.z, 0.0F, 0.0F);
		return player;
	}

	static void disconnect(ServerPlayerEntity player) {
		player.getServer().getPlayerManager().remove(player);
	}

	// ---------- loot tables ----------

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "fishing_loot", tickLimit = 200)
	public void waterFishOdds(TestContext ctx) {
		RegistryKey<LootTable> water = LootTables.FISHING_GAMEPLAY;
		fillBiome(ctx, "minecraft:plains");
		Map<Item, Integer> none = sample(ctx, water, null, 3000);
		check(ctx, fishCount(none) == 0, "no bait must catch no fish: " + none);
		check(ctx, n(none, Items.LILY_PAD) + n(none, Items.BONE) + n(none, Items.STICK) > 0, "no bait still catches junk: " + none);

		Map<Item, Integer> worm = sample(ctx, water, FishingItems.WORM, 3000);
		double ratio = (double) n(worm, FishingItems.PERCH) / Math.max(1, n(worm, Items.COD));
		check(ctx, ratio > 0.75 && ratio < 1.33, "perch should be as common as cod, perch/cod = " + ratio + " " + worm);
		check(ctx, n(worm, FishingItems.PIKE) > 0, "pike anywhere");
		for (Item absent : List.of(FishingItems.KOI, FishingItems.ANGLERFISH, FishingItems.TUNA, FishingItems.CATFISH,
				FishingItems.ICEFISH, FishingItems.ELECTRIC_EEL, FishingItems.MAGMA_EEL)) {
			check(ctx, n(worm, absent) == 0, absent + " should not bite on worms in plains: " + worm);
		}

		Map<Item, Integer> melon = sample(ctx, water, Items.GLISTERING_MELON_SLICE, 3000);
		check(ctx, n(melon, FishingItems.KOI) > 0, "koi with glistering melon: " + melon);
		double pikeBoost = (double) n(melon, FishingItems.PIKE) / Math.max(1, n(worm, FishingItems.PIKE));
		check(ctx, pikeBoost > 1.35, "glistering melon should boost uncommon fish, pike x" + pikeBoost);

		fillBiome(ctx, "minecraft:deep_ocean");
		Map<Item, Integer> glowDeep = sample(ctx, water, FishingItems.GLOW_BAIT, 4000);
		check(ctx, n(glowDeep, FishingItems.TUNA) > 0 && n(glowDeep, FishingItems.ELECTRIC_EEL) > 0, "ocean fish: " + glowDeep);
		check(ctx, n(glowDeep, FishingItems.ANGLERFISH) == 0, "no anglerfish without deep water under the hook: " + glowDeep);

		fillBiome(ctx, "minecraft:swamp");
		check(ctx, n(sample(ctx, water, FishingItems.WORM, 2000), FishingItems.CATFISH) > 0, "catfish in swamps");
		fillBiome(ctx, "minecraft:snowy_plains");
		check(ctx, n(sample(ctx, water, FishingItems.WORM, 2000), FishingItems.ICEFISH) > 0, "icefish in the cold");
		ctx.complete();
	}

	/**
	 * Anglerfish: any ocean biome, glow bait, and at least fishing.anglerfishMinWaterDepth (10) blocks of
	 * water under the hook. deep_pool is a 5x14x5 column of water.
	 */
	@GameTest(templateName = "eternalreturn_test:deep_pool", batchId = "fishing_loot_deep", tickLimit = 200)
	public void anglerfishNeedDeepWater(TestContext ctx) {
		RegistryKey<LootTable> water = LootTables.FISHING_GAMEPLAY;
		BlockPos deep = new BlockPos(2, 14, 2);
		BlockPos shallow = new BlockPos(2, 5, 2);
		check(ctx, DeepWaterLootCondition.waterDepth(ctx.getWorld(), ctx.getAbsolutePos(deep)) == 14, "deep spot depth "
				+ DeepWaterLootCondition.waterDepth(ctx.getWorld(), ctx.getAbsolutePos(deep)));
		check(ctx, DeepWaterLootCondition.waterDepth(ctx.getWorld(), ctx.getAbsolutePos(shallow)) == 5, "shallow spot depth");

		fillBiome(ctx, "minecraft:ocean");
		check(ctx, n(sample(ctx, water, FishingItems.GLOW_BAIT, 4000, deep), FishingItems.ANGLERFISH) > 0, "anglerfish over 14 deep ocean water");
		check(ctx, n(sample(ctx, water, FishingItems.GLOW_BAIT, 4000, shallow), FishingItems.ANGLERFISH) == 0, "no anglerfish over 5 deep water");
		check(ctx, n(sample(ctx, water, FishingItems.WORM, 4000, deep), FishingItems.ANGLERFISH) == 0, "anglerfish need glow bait");
		EternalReturnConfig.FishingTweaks cfg = EternalReturnConfig.get().fishing;
		int saved = cfg.anglerfishMinWaterDepth;
		cfg.anglerfishMinWaterDepth = 15;
		try {
			check(ctx, n(sample(ctx, water, FishingItems.GLOW_BAIT, 4000, deep), FishingItems.ANGLERFISH) == 0, "config minimum 15 rules out 14 deep");
		} finally {
			cfg.anglerfishMinWaterDepth = saved;
		}
		fillBiome(ctx, "minecraft:plains");
		check(ctx, n(sample(ctx, water, FishingItems.GLOW_BAIT, 4000, deep), FishingItems.ANGLERFISH) == 0, "no anglerfish outside ocean biomes");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "fishing_loot_lava", tickLimit = 200)
	public void lavaFishOdds(TestContext ctx) {
		fillBiome(ctx, "minecraft:soul_sand_valley");
		Map<Item, Integer> none = sample(ctx, FishingLoot.LAVA_FISHING, null, 2000);
		check(ctx, n(none, FishingItems.MAGMA_EEL) + n(none, FishingItems.SOULFIN) == 0, "no bait, no nether fish: " + none);
		check(ctx, n(none, Items.SOUL_SAND) + n(none, Items.BASALT) + n(none, Items.BONE) > 0, "nether junk: " + none);
		check(ctx, n(none, Items.GOLD_NUGGET) > n(none, Items.BLAZE_ROD), "gold more common than blaze rods: " + none);
		Map<Item, Integer> baited = sample(ctx, FishingLoot.LAVA_FISHING, FishingItems.INFERNAL_BAIT, 3000);
		check(ctx, n(baited, FishingItems.MAGMA_EEL) > 0 && n(baited, FishingItems.SOULFIN) > 0, "soul sand valley fish: " + baited);
		check(ctx, n(baited, FishingItems.ASHFIN) == 0, "ashfin only in basalt deltas: " + baited);
		check(ctx, n(baited, Items.COD) + n(baited, FishingItems.PERCH) == 0, "no water fish from lava: " + baited);
		fillBiome(ctx, "minecraft:basalt_deltas");
		check(ctx, n(sample(ctx, FishingLoot.LAVA_FISHING, FishingItems.INFERNAL_BAIT, 2000), FishingItems.ASHFIN) > 0, "ashfin in basalt deltas");
		ctx.complete();
	}

	// ---------- bait ----------

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "bait", tickLimit = 40)
	public void baitSelectionAndLure(TestContext ctx) {
		PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
		player.getInventory().main.set(5, new ItemStack(FishingItems.WORM, 3));
		check(ctx, Bait.find(player, false).isOf(FishingItems.WORM), "finds worms in the inventory");
		check(ctx, Bait.find(player, true).isEmpty(), "worms don't work in lava");
		player.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, new ItemStack(FishingItems.CHUM, 2));
		check(ctx, Bait.find(player, false).isOf(FishingItems.CHUM), "off hand bait wins");
		player.getInventory().main.set(20, new ItemStack(FishingItems.INFERNAL_BAIT, 1));
		check(ctx, Bait.find(player, true).isOf(FishingItems.INFERNAL_BAIT), "infernal bait in lava");
		Bait.consumeOne(player, new ItemStack(FishingItems.CHUM));
		check(ctx, player.getOffHandStack().getCount() == 1, "one chum used");
		check(ctx, Bait.lureTicks(new ItemStack(FishingItems.CHUM), ctx.getWorld()) == 200, "chum lure 10 s");
		check(ctx, Bait.lureTicks(ItemStack.EMPTY, ctx.getWorld()) == 0, "no bait no lure");

		PlayerEntity creative = ctx.createMockPlayer(GameMode.CREATIVE);
		creative.getAbilities().creativeMode = true;
		creative.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, new ItemStack(FishingItems.WORM, 1));
		Bait.consumeOne(creative, new ItemStack(FishingItems.WORM));
		check(ctx, creative.getOffHandStack().getCount() == 1, "creative keeps bait");

		ctx.getWorld().setTimeOfDay(6000);
		ctx.runAtTick(5, () -> {
			check(ctx, Bait.lureTicks(new ItemStack(FishingItems.GLOW_BAIT), ctx.getWorld()) == 100, "glow bait by day = worms");
			ctx.getWorld().setTimeOfDay(18000);
		});
		ctx.runAtTick(10, () -> {
			check(ctx, Bait.lureTicks(new ItemStack(FishingItems.GLOW_BAIT), ctx.getWorld()) == 300, "glow bait at night is the strongest");
			ctx.complete();
		});
	}

	// ---------- full catch cycles ----------

	private static void pool(TestContext ctx, Block fluid) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
				boolean inside = x >= 3 && x <= 5 && z >= 3 && z <= 5;
				boolean rim = x >= 2 && x <= 6 && z >= 2 && z <= 6 && !inside;
				ctx.setBlockState(x, 1, z, inside ? fluid : rim ? Blocks.STONE : Blocks.AIR);
			}
		}
		ctx.setBlockState(1, 1, 4, Blocks.STONE);
	}

	/** Casts a real hook into the pool and reels it in once something bites. */
	private static void catchCycle(TestContext ctx, Block fluid, Item bait, Consumer<CatchResult> verify) {
		ctx.getWorld().setTimeOfDay(18000);
		pool(ctx, fluid);
		ServerPlayerEntity player = survivalPlayer(ctx, new BlockPos(1, 2, 4));
		player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
		if (bait != null) {
			player.getInventory().main.set(9, new ItemStack(bait, 3));
		}
		FishingBobberEntity hook = new FishingBobberEntity(player, ctx.getWorld(), 0, 0);
		Vec3d over = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(4, 2, 4)));
		hook.refreshPositionAndAngles(over.x, over.y, over.z, 0.0F, 0.0F);
		hook.setVelocity(Vec3d.ZERO);
		ctx.getWorld().spawnEntity(hook);
		double surfaceY = ctx.getAbsolutePos(new BlockPos(4, 1, 4)).getY();
		boolean[] reeled = {false};
		ctx.runAtEveryTick(() -> {
			if (reeled[0]) {
				return;
			}
			check(ctx, !hook.isRemoved(), "hook was removed before a bite (state " + field(hook, "state") + ")");
			if (!"BOBBING".equals(String.valueOf(field(hook, "state")))) {
				return;
			}
			int wait = (int) field(hook, "waitCountdown");
			if (wait > 5) {
				setField(hook, "waitCountdown", 5);
			}
			if ((int) field(hook, "hookCountdown") > 0) {
				reeled[0] = true;
				double hookY = hook.getY();
				hook.use(player.getMainHandStack());
				Box around = new Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(10);
				List<ItemEntity> caught = ctx.getWorld().getEntitiesByType(EntityType.ITEM, around, e -> true);
				int baitLeft = bait == null ? 0 : player.getInventory().main.get(9).getCount();
				ctx.runAtTick(ctx.getTick() + 25, () -> {
					verify.accept(new CatchResult(player, hookY, surfaceY, caught, baitLeft));
					disconnect(player);
					ctx.complete();
				});
			}
		});
	}

	record CatchResult(ServerPlayerEntity player, double hookY, double surfaceY, List<ItemEntity> caught, int baitLeft) {
		boolean survivedOrCollected() {
			for (ItemEntity item : this.caught) {
				Item type = item.getStack().getItem();
				if (!item.isRemoved() || this.player.getInventory().contains(new ItemStack(type))) {
					return true;
				}
			}
			return false;
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "catch_lava", tickLimit = 600)
	public void lavaCatchWithInfernalBait(TestContext ctx) {
		EternalReturnConfig.FishingTweaks saved = EternalReturnConfig.get().fishing;
		EternalReturnConfig.FishingTweaks lavaAnywhere = new EternalReturnConfig.FishingTweaks();
		lavaAnywhere.lavaFishingOutsideNether = true;
		EternalReturnConfig.get().fishing = lavaAnywhere;
		catchCycle(ctx, Blocks.LAVA, FishingItems.INFERNAL_BAIT, r -> {
			EternalReturnConfig.get().fishing = saved;
			check(ctx, r.hookY() > r.surfaceY() + 0.2, "hook should float in the lava, not rest on the floor, y " + r.hookY() + " vs block " + r.surfaceY());
			check(ctx, !r.caught().isEmpty(), "something should come out of the lava");
			check(ctx, r.caught().stream().allMatch(ItemEntity::isInvulnerable), "lava catches are protected from burning");
			check(ctx, r.survivedOrCollected(), "the catch burned up: " + r.caught());
			check(ctx, r.baitLeft() == 2, "one infernal bait used, left " + r.baitLeft());
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "catch_water", tickLimit = 600)
	public void waterCatchUsesWorm(TestContext ctx) {
		catchCycle(ctx, Blocks.WATER, FishingItems.WORM, r -> {
			check(ctx, !r.caught().isEmpty(), "something should be caught");
			check(ctx, r.baitLeft() == 2, "one worm used, left " + r.baitLeft());
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "catch_water_nobait", tickLimit = 600)
	public void waterCatchWithoutBaitIsNeverFish(TestContext ctx) {
		catchCycle(ctx, Blocks.WATER, null, r -> {
			check(ctx, !r.caught().isEmpty(), "junk or treasure should still bite");
			check(ctx, r.caught().stream().noneMatch(e -> e.getStack().isIn(FishingItems.RAW_FISH)), "no fish without bait: " + r.caught());
		});
	}

	// ---------- worms, recipes, items ----------

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "worms", tickLimit = 40)
	public void wormsFromDirt(TestContext ctx) {
		ServerPlayerEntity player = survivalPlayer(ctx, new BlockPos(0, 1, 0));
		withFishing(f -> f.wormDropChance = 1.0, () -> {
			for (int i = 0; i < 4; i++) {
				Block block = List.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.MUD, Blocks.STONE).get(i);
				BlockPos rel = new BlockPos(1 + 2 * i, 1, 4);
				ctx.setBlockState(rel, block);
				player.interactionManager.tryBreakBlock(ctx.getAbsolutePos(rel));
			}
		});
		ctx.runAtTick(2, () -> {
			for (int i = 0; i < 4; i++) {
				BlockPos abs = ctx.getAbsolutePos(new BlockPos(1 + 2 * i, 1, 4));
				boolean worm = !ctx.getWorld().getEntitiesByType(EntityType.ITEM, new Box(abs).expand(0.6), e -> e.getStack().isOf(FishingItems.WORM)).isEmpty();
				check(ctx, worm == (i < 3), "worm drop at block " + i + ": " + worm);
			}
			disconnect(player);
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void recipesAndItems(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		Object[][] recipes = {
				{Items.COD, Items.BONE_MEAL, FishingItems.CHUM},
				{FishingItems.KOI, Items.BONE_MEAL, FishingItems.CHUM},
				{Items.SLIME_BALL, Items.GLOWSTONE_DUST, FishingItems.GLOW_BAIT},
				{Items.ROTTEN_FLESH, Items.MAGMA_CREAM, FishingItems.INFERNAL_BAIT},
		};
		for (Object[] r : recipes) {
			CraftingRecipeInput input = CraftingRecipeInput.create(2, 1, List.of(new ItemStack((Item) r[0]), new ItemStack((Item) r[1])));
			ItemStack out = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world)
					.map(e -> ((CraftingRecipe) e.value()).craft(input, world.getRegistryManager())).orElse(ItemStack.EMPTY);
			check(ctx, out.isOf((Item) r[2]) && out.getCount() == 4, r[0] + " + " + r[1] + " -> " + out);
		}
		for (Item fish : List.of(FishingItems.PERCH, FishingItems.KOI, FishingItems.SOULFIN)) {
			ItemStack stack = new ItemStack(fish);
			check(ctx, stack.get(DataComponentTypes.FOOD) != null && stack.get(DataComponentTypes.FOOD).nutrition() == 2, fish + " food");
			check(ctx, stack.isIn(ItemTags.FISHES) && stack.isIn(FishingItems.RAW_FISH), fish + " tags");
		}
		check(ctx, new ItemStack(FishingItems.MAGMA_EEL).contains(DataComponentTypes.FIRE_RESISTANT), "nether fish are fireproof");
		ctx.complete();
	}

	// ---------- villagers ----------

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "trades", tickLimit = 100)
	public void onlyFishermenBuyFish(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		Method fill;
		try {
			fill = VillagerEntity.class.getDeclaredMethod("fillRecipes");
			fill.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
		Map<Item, Integer> fishBought = new HashMap<>();
		int koiAtMaster = 0;
		for (VillagerProfession profession : Registries.VILLAGER_PROFESSION) {
			if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT) {
				continue;
			}
			int offers = 0;
			for (int sampleNo = 0; sampleNo < 15; sampleNo++) {
				VillagerEntity villager = EntityType.VILLAGER.create(world);
				villager.setVillagerData(villager.getVillagerData().withProfession(profession).withLevel(1));
				villager.getOffers();
				for (int level = 2; level <= 5; level++) {
					villager.setVillagerData(villager.getVillagerData().withLevel(level));
					try {
						fill.invoke(villager);
					} catch (ReflectiveOperationException e) {
						throw new RuntimeException(e);
					}
				}
				for (TradeOffer offer : villager.getOffers()) {
					offers++;
					if (offer.getSellItem().isOf(Items.EMERALD)) {
						ItemStack wanted = offer.getOriginalFirstBuyItem();
						check(ctx, profession == VillagerProfession.FISHERMAN && wanted.isIn(FishingItems.RAW_FISH),
								profession.id() + " still buys " + wanted + " for emeralds");
						fishBought.merge(wanted.getItem(), 1, Integer::sum);
						if (wanted.isOf(FishingItems.KOI) && offer.getSellItem().getCount() == 10) {
							koiAtMaster++;
						}
					}
				}
			}
			check(ctx, offers > 0, profession.id() + " has no trades left at all");
		}
		check(ctx, koiAtMaster == 15, "every master fisherman buys koi for 10 emeralds, got " + koiAtMaster);
		for (Item fish : List.of(Items.COD, Items.SALMON, FishingItems.PERCH, FishingItems.PIKE, FishingItems.TUNA, FishingItems.SOULFIN)) {
			check(ctx, fishBought.containsKey(fish), "no fisherman ever bought " + fish + ": " + fishBought);
		}
		ctx.complete();
	}
}
