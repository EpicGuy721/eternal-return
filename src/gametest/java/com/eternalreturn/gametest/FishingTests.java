package com.eternalreturn.gametest;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.fishing.Bait;
import com.eternalreturn.fishing.DeepWaterLootCondition;
import com.eternalreturn.fishing.FishingItems;
import com.eternalreturn.fishing.FishingLoot;
import com.eternalreturn.gametest.mixin.TestContextAccessor;
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
import net.minecraft.test.GameTestState;
import net.minecraft.test.TestContext;
import net.minecraft.test.TestListener;
import net.minecraft.test.TestRunContext;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Fishing and villager trades: bait, full catch cycles in water and lava, recipes, items, trades (each fish: FishTableTests). */
public class FishingTests implements FabricGameTest {
	private static final AtomicInteger PLAYERS = new AtomicInteger();

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

	/**
	 * A survival player at rel (a connected one: it isn't ticked; see the gametest notes). Each has a name of its own, so a
	 * command naming one can't reach another test's; and it's disconnected when its test ends, passed or failed, so one
	 * left behind by a failed test can't get in later tests' way.
	 */
	static ServerPlayerEntity survivalPlayer(TestContext ctx, BlockPos rel) {
		ServerWorld world = ctx.getWorld();
		String name = "test-angler" + PLAYERS.incrementAndGet();
		ConnectedClientData data = ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, data.gameProfile(), data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(connection);
		world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
		player.changeGameMode(GameMode.SURVIVAL);
		Vec3d p = Vec3d.ofBottomCenter(ctx.getAbsolutePos(rel));
		player.teleport(world, p.x, p.y, p.z, 0.0F, 0.0F);
		((TestContextAccessor) ctx).eternalreturn$test().addListener(new TestListener() {
			@Override
			public void onStarted(GameTestState test) {
			}

			@Override
			public void onPassed(GameTestState test, TestRunContext context) {
				disconnect(player);
			}

			@Override
			public void onFailed(GameTestState test, TestRunContext context) {
				disconnect(player);
			}

			@Override
			public void onRetry(GameTestState prevState, GameTestState nextState, TestRunContext context) {
				disconnect(player);
			}
		});
		return player;
	}

	/** Disconnects a survivalPlayer (once: again does nothing). */
	static void disconnect(ServerPlayerEntity player) {
		if (player.getServer().getPlayerManager().getPlayer(player.getUuid()) == player) {
			player.getServer().getPlayerManager().remove(player);
		}
	}

	// ---------- loot tables ----------

	/** Without bait, nothing but junk and treasure bites, in water or lava (each fish's own odds: FishTableTests). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "fishing_loot", tickLimit = 200)
	public void noBaitNoFish(TestContext ctx) {
		Map<Item, Integer> water = sample(ctx, LootTables.FISHING_GAMEPLAY, null, 2000);
		check(ctx, fishCount(water) == 0, "no bait, no fish: " + water);
		check(ctx, fishCount(sample(ctx, LootTables.FISHING_GAMEPLAY, FishingItems.CHUM, 2000)) > 1000, "chum brings fish");
		fillBiome(ctx, "minecraft:nether_wastes");
		Map<Item, Integer> lava = sample(ctx, FishingLoot.LAVA_FISHING, null, 2000);
		check(ctx, fishCount(lava) == 0, "no magma cream, no Nether fish: " + lava);
		Map<Item, Integer> baited = sample(ctx, FishingLoot.LAVA_FISHING, Items.MAGMA_CREAM, 2000);
		check(ctx, fishCount(baited) > 1000 && n(baited, Items.COD) == 0, "Nether fish with magma cream, no water fish: " + baited);
		ctx.complete();
	}

	// ---------- bait ----------

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "bait", tickLimit = 40)
	public void baitSelectionAndLure(TestContext ctx) {
		PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
		player.getInventory().main.set(5, new ItemStack(FishingItems.CHUM, 3));
		check(ctx, Bait.find(player, false).isOf(FishingItems.CHUM), "finds chum in the inventory");
		check(ctx, Bait.find(player, true).isEmpty(), "chum doesn't work in lava");
		player.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.GLISTERING_MELON_SLICE, 2));
		check(ctx, Bait.find(player, false).isOf(Items.GLISTERING_MELON_SLICE), "off hand bait wins");
		player.getInventory().main.set(20, new ItemStack(Items.MAGMA_CREAM, 1));
		check(ctx, Bait.find(player, true).isOf(Items.MAGMA_CREAM), "magma cream in lava");
		check(ctx, !Bait.find(player, false).isOf(Items.MAGMA_CREAM), "magma cream isn't water bait");
		Bait.consumeOne(player, new ItemStack(Items.GLISTERING_MELON_SLICE));
		check(ctx, player.getOffHandStack().getCount() == 1, "one melon slice used");
		check(ctx, Bait.lureTicks(new ItemStack(FishingItems.CHUM), ctx.getWorld()) == 200, "chum lure 10 s");
		check(ctx, Bait.lureTicks(new ItemStack(Items.GLISTERING_MELON_SLICE), ctx.getWorld()) == 100, "glistering melon lure 5 s");
		check(ctx, Bait.lureTicks(new ItemStack(Items.MAGMA_CREAM), ctx.getWorld()) == 100, "magma cream lure 5 s");
		check(ctx, Bait.lureTicks(ItemStack.EMPTY, ctx.getWorld()) == 0, "no bait no lure");

		PlayerEntity creative = ctx.createMockPlayer(GameMode.CREATIVE);
		creative.getAbilities().creativeMode = true;
		creative.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, new ItemStack(FishingItems.CHUM, 1));
		Bait.consumeOne(creative, new ItemStack(FishingItems.CHUM));
		check(ctx, creative.getOffHandStack().getCount() == 1, "creative keeps bait");
		ctx.complete();
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
		// These check the items that come up: no monster catches (HostileCatches) while they run.
		EternalReturnConfig.FishingTweaks fishing = EternalReturnConfig.get().fishing;
		double hostileChance = fishing.hostileCatchChance;
		fishing.hostileCatchChance = 0.0;
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
				// Building the pool can knock the test frame's start button off: that isn't a catch.
				List<ItemEntity> caught = ctx.getWorld().getEntitiesByType(EntityType.ITEM, around, e -> !e.getStack().isOf(Items.STONE_BUTTON));
				int baitLeft = bait == null ? 0 : player.getInventory().main.get(9).getCount();
				ctx.runAtTick(ctx.getTick() + 25, () -> {
					fishing.hostileCatchChance = hostileChance;
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
	public void lavaCatchWithMagmaCream(TestContext ctx) {
		EternalReturnConfig.FishingTweaks saved = EternalReturnConfig.get().fishing;
		EternalReturnConfig.FishingTweaks lavaAnywhere = new EternalReturnConfig.FishingTweaks();
		lavaAnywhere.lavaFishingOutsideNether = true;
		EternalReturnConfig.get().fishing = lavaAnywhere;
		catchCycle(ctx, Blocks.LAVA, Items.MAGMA_CREAM, r -> {
			EternalReturnConfig.get().fishing = saved;
			check(ctx, r.hookY() > r.surfaceY() + 0.2, "hook should float in the lava, not rest on the floor, y " + r.hookY() + " vs block " + r.surfaceY());
			check(ctx, !r.caught().isEmpty(), "something should come out of the lava");
			check(ctx, r.caught().stream().allMatch(ItemEntity::isInvulnerable), "lava catches are protected from burning: " + r.caught().stream().map(e -> e.getStack() + " inv " + e.isInvulnerable() + " at " + e.getBlockPos()).toList());
			check(ctx, r.survivedOrCollected(), "the catch burned up: " + r.caught());
			check(ctx, r.baitLeft() == 2, "one magma cream used, left " + r.baitLeft());
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "catch_water", tickLimit = 600)
	public void waterCatchUsesChum(TestContext ctx) {
		catchCycle(ctx, Blocks.WATER, FishingItems.CHUM, r -> {
			check(ctx, !r.caught().isEmpty(), "something should be caught");
			check(ctx, r.baitLeft() == 2, "one chum used, left " + r.baitLeft());
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "catch_water_nobait", tickLimit = 600)
	public void waterCatchWithoutBaitIsNeverFish(TestContext ctx) {
		catchCycle(ctx, Blocks.WATER, null, r -> {
			check(ctx, !r.caught().isEmpty(), "junk or treasure should still bite");
			check(ctx, r.caught().stream().noneMatch(e -> e.getStack().isIn(FishingItems.RAW_FISH)), "no fish without bait: " + r.caught());
		});
	}

	// ---------- recipes, items ----------

	/** Chum from any raw fish; every fish fills like raw salmon, can't be cooked, and Nether fish don't burn. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void recipesAndItems(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		for (Item fish : List.of(Items.COD, FishingItems.FISH.get(0), FishingItems.FISH.get(FishingItems.FISH.size() - 1))) {
			CraftingRecipeInput input = CraftingRecipeInput.create(2, 1, List.of(new ItemStack(fish), new ItemStack(Items.BONE_MEAL)));
			ItemStack out = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world)
					.map(e -> ((CraftingRecipe) e.value()).craft(input, world.getRegistryManager())).orElse(ItemStack.EMPTY);
			check(ctx, out.isOf(FishingItems.CHUM) && out.getCount() == 4, fish + " + bone meal -> " + out);
		}
		check(ctx, FishingItems.FISH.size() == com.eternalreturn.fishing.FishTable.fish().size() && FishingItems.FISH.size() == 53, "53 fish: " + FishingItems.FISH.size());
		for (Item fish : FishingItems.FISH) {
			ItemStack stack = new ItemStack(fish);
			var food = stack.get(DataComponentTypes.FOOD);
			check(ctx, food != null && food.nutrition() == 2 && food.saturation() == new ItemStack(Items.SALMON).get(DataComponentTypes.FOOD).saturation(), fish + " fills like raw salmon");
			check(ctx, stack.isIn(ItemTags.FISHES) && stack.isIn(FishingItems.RAW_FISH), fish + " tags");
			net.minecraft.recipe.input.SingleStackRecipeInput single = new net.minecraft.recipe.input.SingleStackRecipeInput(stack);
			for (RecipeType<?> type : List.of(RecipeType.SMELTING, RecipeType.SMOKING, RecipeType.CAMPFIRE_COOKING)) {
				@SuppressWarnings("unchecked")
				var cooking = (RecipeType<net.minecraft.recipe.AbstractCookingRecipe>) type;
				check(ctx, world.getRecipeManager().getFirstMatch(cooking, single, world).isEmpty(), fish + " can't be cooked");
			}
			boolean nether = ((FishingItems.FishItem) fish).fish().nether();
			check(ctx, stack.contains(DataComponentTypes.FIRE_RESISTANT) == nether, fish + (nether ? " is" : " isn't") + " fireproof");
		}
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
		for (VillagerProfession profession : Registries.VILLAGER_PROFESSION) {
			if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT) {
				continue;
			}
			int offers = 0;
			int samples = profession == VillagerProfession.FISHERMAN ? 40 : 15;
			for (int sampleNo = 0; sampleNo < samples; sampleNo++) {
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
					}
				}
			}
			check(ctx, offers > 0, profession.id() + " has no trades left at all");
		}
		// Vanilla's own fish trades are still picked now and then; the mod's fish are always there (FishTableTests).
		for (Item fish : List.of(Items.COD, Items.SALMON, FishingItems.FISH.get(0))) {
			check(ctx, fishBought.containsKey(fish), "no fisherman ever bought " + fish + ": " + fishBought);
		}
		ctx.complete();
	}
}
