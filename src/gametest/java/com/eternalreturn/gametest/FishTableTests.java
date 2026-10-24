package com.eternalreturn.gametest;

import com.eternalreturn.fishing.FishTable;
import com.eternalreturn.fishing.FishingItems;
import com.eternalreturn.fishing.FishingLoot;
import com.eternalreturn.villager.FishermanTrades;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
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
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;
import net.minecraft.world.GameMode;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The fish table (eternalreturn/fish.json), checked fish by fish against the catch tables tools/fish/build_fish.py
 * wrote: in its own setting (a biome of one of its habitats, its time, weather, moon, height, under a roof or not)
 * each fish bites, and with any one of those changed it never does. Then the fishermen: who buys what, from which
 * level, the baits they sell, and older fishermen being topped up.
 */
public class FishTableTests implements FabricGameTest {
	private static final int ROLLS = 3000;
	private static final int ROLLS_NEVER = 1000;
	/** Biomes to try, in order, when a fish must be somewhere it doesn't live. */
	private static final List<String> ELSEWHERE = List.of("minecraft:plains", "minecraft:desert", "minecraft:ocean", "minecraft:jungle",
			"minecraft:snowy_plains", "minecraft:swamp", "minecraft:nether_wastes", "minecraft:soul_sand_valley", "minecraft:basalt_deltas");

	private static JsonObject table() {
		try (var reader = new InputStreamReader(FishTableTests.class.getResourceAsStream("/eternalreturn/fish.json"), StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/** Where and when to fish: biome, day-clock time, weather, and the hook's absolute position. */
	private record Setting(String biome, long time, String weather, Vec3d hook, String what) {
		Setting with(String biome, long time, String weather, Vec3d hook, String what) {
			return new Setting(biome == null ? this.biome : biome, time < 0 ? this.time : time, weather == null ? this.weather : weather,
					hook == null ? this.hook : hook, what);
		}
	}

	/** A concrete biome of a habitat: its first plain entry, or a stand-in for a tag. */
	private static String biomeOf(JsonObject habitat) {
		for (JsonElement biome : habitat.getAsJsonArray("biomes")) {
			String id = biome.getAsString();
			if (id.equals("#eternalreturn:ocean_waters")) {
				return "minecraft:ocean";
			}
			if (id.equals("#minecraft:is_nether")) {
				return "minecraft:nether_wastes";
			}
			if (!id.startsWith("?") && !id.startsWith("#")) {
				return id;
			}
		}
		throw new IllegalStateException("no plain biome in " + habitat);
	}

	private static boolean livesIn(ServerWorld world, JsonObject fish, String biome) {
		var entry = world.getRegistryManager().get(RegistryKeys.BIOME).getEntry(RegistryKey.of(RegistryKeys.BIOME, Identifier.of(biome))).orElseThrow();
		for (JsonElement habitat : fish.getAsJsonArray("habitats")) {
			if (entry.isIn(TagKey.of(RegistryKeys.BIOME, Identifier.of("eternalreturn", "fish_habitat/" + habitat.getAsString())))) {
				return true;
			}
		}
		return false;
	}

	/** Night time on a day with the given moon (0 full ... 4 new). */
	private static long night(int moonPhase) {
		return moonPhase * 24000L + 18000L;
	}

	private static long timeFor(JsonObject fish) {
		int moon = !fish.has("moon") ? 0 : fish.get("moon").getAsString().equals("full") ? 0 : 4;
		String time = fish.has("time") ? fish.get("time").getAsString() : fish.has("moon") ? "night" : "day";
		return switch (time) {
			case "night" -> night(moon);
			case "dawn_dusk" -> 23500L;
			default -> 6000L;
		};
	}

	private static void setTime(ServerWorld world, long time) {
		world.setTimeOfDay(time);
	}

	/** The storm a setting's weather put over its hook, taken away when the weather changes. */
	private static com.eternalreturn.weather.Storm storm;

	/** The weather at the hook (and, without local weather, everywhere), at full strength at once. */
	private static void setWeather(ServerWorld world, String weather, Vec3d hook) {
		Weathers.dry(world, storm);
		storm = null;
		if (!weather.equals("clear")) {
			storm = Weathers.rain(world, hook, weather.equals("thunder"));
		}
	}

	/** Around the hook only: /fillbiome refuses more than 32,768 blocks at once. */
	private static void fillBiome(TestContext ctx, String biome, Vec3d hook) {
		MinecraftServer server = ctx.getWorld().getServer();
		// Not below the bottom of the world either, or the whole command fails.
		BlockPos a = BlockPos.ofFloored(hook).add(-6, -6, -6);
		a = a.withY(Math.max(a.getY(), ctx.getWorld().getBottomY()));
		BlockPos b = BlockPos.ofFloored(hook).add(6, 6, 6);
		server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(),
				"fillbiome " + a.getX() + " " + a.getY() + " " + a.getZ() + " " + b.getX() + " " + b.getY() + " " + b.getZ() + " " + biome);
	}

	/** Catches with the hook at an absolute spot, as FishingBobberEntity.use rolls them, counting one item. */
	private static int catches(TestContext ctx, Setting setting, Item fish, Item bait, RegistryKey<LootTable> key, int rolls) {
		ServerWorld world = ctx.getWorld();
		fillBiome(ctx, setting.biome(), setting.hook());
		String found = world.getBiome(BlockPos.ofFloored(setting.hook())).getKey().map(k -> k.getValue().toString()).orElse("?");
		if (!found.equals(setting.biome())) {
			throw new IllegalStateException("the biome at the hook is " + found + ", not " + setting.biome());
		}
		setTime(world, setting.time());
		setWeather(world, setting.weather(), setting.hook());
		PlayerEntity angler = ctx.createMockPlayer(GameMode.SURVIVAL);
		FishingBobberEntity hook = new FishingBobberEntity(angler, world, 0, 0);
		hook.setPosition(setting.hook());
		try {
			Field field = FishingBobberEntity.class.getDeclaredField("eternalreturn$bait");
			field.setAccessible(true);
			field.set(hook, new ItemStack(bait));
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
		LootTable table = world.getServer().getReloadableRegistries().getLootTable(key);
		int count = 0;
		for (int i = 0; i < rolls; i++) {
			LootContextParameterSet params = new LootContextParameterSet.Builder(world)
					.add(LootContextParameters.ORIGIN, setting.hook())
					.add(LootContextParameters.TOOL, new ItemStack(Items.FISHING_ROD))
					.add(LootContextParameters.THIS_ENTITY, hook)
					.luck(0)
					.build(LootContextTypes.FISHING);
			for (ItemStack stack : table.generateLoot(params)) {
				count += stack.isOf(fish) ? 1 : 0;
			}
		}
		return count;
	}

	/**
	 * Every fish of the table that passes the filter: bites in its own setting, and never with one thing changed.
	 * open is a hook spot under the open sky, roofed one under a roof, and deep / shallow (if not null) spots over
	 * deep and shallow water.
	 */
	private static void checkFish(TestContext ctx, boolean deepOnes, Vec3d open, Vec3d roofed, Vec3d shallow) {
		ServerWorld world = ctx.getWorld();
		JsonObject table = table();
		JsonObject habitats = table.getAsJsonObject("habitats");
		List<String> report = new ArrayList<>();
		List<String> failures = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : table.getAsJsonObject("fish").entrySet()) {
			JsonObject fish = e.getValue().getAsJsonObject();
			if (fish.has("min_depth") != deepOnes) {
				continue;
			}
			Item item = Registries.ITEM.get(Identifier.of("eternalreturn", e.getKey()));
			boolean nether = fish.get("nether").getAsBoolean();
			Item bait = nether ? Items.MAGMA_CREAM : Items.GLISTERING_MELON_SLICE;
			RegistryKey<LootTable> key = nether ? FishingLoot.LAVA_FISHING : LootTables.FISHING_GAMEPLAY;
			boolean underground = fish.has("underground") && fish.get("underground").getAsBoolean();
			String biome = fish.getAsJsonArray("habitats").isEmpty() ? (nether ? "minecraft:nether_wastes" : "minecraft:plains")
					: biomeOf(habitats.getAsJsonObject(fish.getAsJsonArray("habitats").get(0).getAsString()));
			Vec3d hook = underground ? roofed : open;
			if (fish.has("min_y")) {
				hook = hook.withAxis(net.minecraft.util.math.Direction.Axis.Y, fish.get("min_y").getAsInt() + 5);
			}
			String weather = fish.has("weather") ? fish.get("weather").getAsString() : "clear";
			Setting own = new Setting(biome, timeFor(fish), weather, hook, "its own setting");

			int bites = catches(ctx, own, item, bait, key, ROLLS);
			report.add(e.getKey() + " " + bites);
			if (bites == 0) {
				failures.add(e.getKey() + " never bit in " + own);
			}
			List<Setting> elsewhere = new ArrayList<>();
			if (!fish.getAsJsonArray("habitats").isEmpty()) {
				ELSEWHERE.stream().filter(b -> !livesIn(world, fish, b)).findFirst()
						.ifPresent(b -> elsewhere.add(own.with(b, -1, null, null, "biome " + b)));
			}
			if (fish.has("time")) {
				long other = fish.get("time").getAsString().equals("night") ? 6000L : 18000L;
				elsewhere.add(own.with(null, other, null, null, "time " + other));
			}
			if (fish.has("weather")) {
				String other = switch (weather) {
					case "clear", "thunder" -> "rain";
					default -> "clear";
				};
				elsewhere.add(own.with(null, -1, other, null, "weather " + other));
			}
			if (fish.has("moon")) {
				long other = night(fish.get("moon").getAsString().equals("full") ? 4 : 0);
				elsewhere.add(own.with(null, other, null, null, "the other moon"));
			}
			if (underground) {
				elsewhere.add(own.with(null, -1, null, open, "under the open sky"));
			}
			if (fish.has("min_y")) {
				elsewhere.add(own.with(null, -1, null, hook.withAxis(net.minecraft.util.math.Direction.Axis.Y, fish.get("min_y").getAsInt() - 30), "lower down"));
			}
			if (deepOnes) {
				elsewhere.add(own.with(null, -1, null, shallow, "over shallow water"));
			}
			for (Setting other : elsewhere) {
				int wrong = catches(ctx, other, item, bait, key, ROLLS_NEVER);
				if (wrong > 0) {
					failures.add(e.getKey() + " bit " + wrong + " times with " + other.what());
				}
			}
		}
		System.out.println("[gametest] fish bites in " + ROLLS + " rolls in their own setting (glistering melon or magma cream): " + report);
		setTime(world, 6000L);
		setWeather(world, "clear", Vec3d.ZERO);
		ctx.assertTrue(failures.isEmpty(), String.join("; ", failures));
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "fish_table", tickLimit = 400)
	public void everyFishBitesWhereItShould(TestContext ctx) {
		// A roof for the underground fish; the hook spots are checked once the light has caught up.
		for (int x = 0; x <= 2; x++) {
			for (int z = 0; z <= 2; z++) {
				ctx.setBlockState(x, 3, z, Blocks.STONE);
			}
		}
		Vec3d open = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(5, 1, 5)));
		Vec3d roofed = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(1, 1, 1)));
		boolean[] done = {false};
		ctx.runAtEveryTick(() -> {
			if (done[0] || ctx.getWorld().isSkyVisible(BlockPos.ofFloored(roofed))) {
				return;
			}
			done[0] = true;
			checkFish(ctx, false, open, roofed, null);
			ctx.complete();
		});
	}

	/** The deep-water fish, over deep_pool's 14 blocks of water, and never over 5. */
	@GameTest(templateName = "eternalreturn_test:deep_pool", batchId = "fish_table_deep", tickLimit = 200)
	public void deepFishNeedDeepWater(TestContext ctx) {
		Vec3d deep = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(2, 14, 2)));
		Vec3d shallow = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(2, 5, 2)));
		checkFish(ctx, true, deep, deep, shallow);
		ctx.complete();
	}

	// ---------- fishermen ----------

	private static VillagerEntity fisherman(TestContext ctx, VillagerType type, int level) {
		VillagerEntity villager = EntityType.VILLAGER.create(ctx.getWorld());
		villager.setVillagerData(villager.getVillagerData().withType(type).withProfession(VillagerProfession.FISHERMAN).withLevel(1));
		villager.getOffers();
		try {
			Method fill = VillagerEntity.class.getDeclaredMethod("fillRecipes");
			fill.setAccessible(true);
			for (int l = 2; l <= level; l++) {
				villager.setVillagerData(villager.getVillagerData().withLevel(l));
				fill.invoke(villager);
			}
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
		return villager;
	}

	/** Emeralds paid for count of the fish, or 0 if the fisherman doesn't buy it. */
	private static int pays(VillagerEntity villager, Item fish, int count) {
		for (TradeOffer offer : villager.getOffers()) {
			if (offer.getOriginalFirstBuyItem().isOf(fish) && offer.getOriginalFirstBuyItem().getCount() == count && offer.getSellItem().isOf(Items.EMERALD)) {
				return offer.getSellItem().getCount();
			}
		}
		return 0;
	}

	private static Item fish(String id) {
		return Registries.ITEM.get(Identifier.of("eternalreturn", id));
	}

	/**
	 * A fisherman buys his own region's freshwater fish and every sea, cherry, underground and Nether fish, each
	 * from the level of its rarity, at the table's price; from novice he sells every bait for one emerald.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "fish_trades", tickLimit = 40)
	public void fishermenBuyByRegionAndLevel(TestContext ctx) {
		VillagerEntity plainsMaster = fisherman(ctx, VillagerType.PLAINS, 5);
		VillagerEntity snowNovice = fisherman(ctx, VillagerType.SNOW, 1);
		VillagerEntity snowApprentice = fisherman(ctx, VillagerType.SNOW, 2);
		ctx.assertTrue(pays(plainsMaster, fish("bluegill"), 6) == 1, "a plains fisherman buys 6 bluegill for 1 emerald");
		ctx.assertTrue(pays(plainsMaster, fish("alaska_blackfish"), 2) == 0, "a plains fisherman doesn't buy alaska blackfish (snow)");
		ctx.assertTrue(pays(plainsMaster, fish("herring"), 8) == 1 && pays(plainsMaster, fish("oarfish"), 1) == 12, "every fisherman buys sea fish");
		ctx.assertTrue(pays(plainsMaster, fish("wither_bonefish"), 1) == 0, "but only a fisherman in the Nether buys Nether fish");
		ctx.assertTrue(com.eternalreturn.villager.FishermanTrades.offers("plains", 5, true).stream()
				.anyMatch(offer -> offer.getOriginalFirstBuyItem().isOf(fish("wither_bonefish"))), "one in the Nether buys wither bonefish");
		ctx.assertTrue(pays(snowNovice, fish("bluegill"), 6) == 0 && pays(snowNovice, fish("herring"), 8) == 1, "a snow fisherman: herring, not bluegill");
		ctx.assertTrue(pays(snowNovice, fish("alaska_blackfish"), 2) == 0 && pays(snowApprentice, fish("alaska_blackfish"), 2) == 1,
				"alaska blackfish (uncommon) from apprentice");
		ctx.assertTrue(pays(snowApprentice, fish("oarfish"), 1) == 0, "no legendary fish before master");
		for (VillagerEntity villager : List.of(plainsMaster, snowNovice)) {
			for (Item bait : List.of(FishingItems.CHUM, Items.GLISTERING_MELON_SLICE, Items.MAGMA_CREAM)) {
				boolean sells = villager.getOffers().stream().anyMatch(o -> o.getSellItem().isOf(bait) && o.getOriginalFirstBuyItem().isOf(Items.EMERALD)
						&& o.getOriginalFirstBuyItem().getCount() == 1);
				ctx.assertTrue(sells, villager.getVillagerData().getType() + " fisherman sells " + bait + " for an emerald");
			}
		}
		// Every fish is bought by some fisherman at master level, and at its table price.
		for (FishTable.Fish fish : FishTable.fish()) {
			boolean bought = false;
			for (VillagerType type : List.of(VillagerType.PLAINS, VillagerType.DESERT, VillagerType.SAVANNA, VillagerType.SNOW, VillagerType.TAIGA,
					VillagerType.JUNGLE, VillagerType.SWAMP)) {
				// Nether fish: by fishermen in the Nether.
				bought |= FishermanTrades.offers(Registries.VILLAGER_TYPE.getId(type).getPath(), 5, fish.nether()).stream()
						.anyMatch(o -> o.getOriginalFirstBuyItem().isOf(FishTable.item(fish)) && o.getSellItem().getCount() == fish.sellEmeralds());
			}
			ctx.assertTrue(bought, "nobody buys " + fish.id());
		}
		ctx.complete();
	}

	/** A fisherman from before these trades gets them when a player opens his trades; trades for items that are gone go. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "fish_trades", tickLimit = 40)
	public void olderFishermenGetTheTrades(TestContext ctx) {
		VillagerEntity villager = EntityType.VILLAGER.create(ctx.getWorld());
		villager.setVillagerData(villager.getVillagerData().withType(VillagerType.SWAMP).withProfession(VillagerProfession.FISHERMAN).withLevel(3));
		TradeOfferList old = new TradeOfferList();
		old.add(new TradeOffer(new TradedItem(Items.STRING, 20), new ItemStack(Items.EMERALD), 16, 2, 0.05F));
		old.add(new TradeOffer(new TradedItem(Items.EMERALD, 1), ItemStack.EMPTY, 16, 2, 0.05F));
		villager.setOffers(old);
		FishermanTrades.topUp(villager);
		ctx.assertTrue(villager.getOffers().stream().noneMatch(o -> o.getSellItem().isEmpty()), "the trade for a removed item is gone");
		ctx.assertTrue(pays(villager, fish("catfish"), 2) == 1 && pays(villager, fish("european_eel"), 1) == 2, "swamp journeyman: catfish and european eel");
		ctx.assertTrue(pays(villager, fish("sturgeon"), 1) == 0, "no epic fish at journeyman");
		int before = villager.getOffers().size();
		FishermanTrades.topUp(villager);
		ctx.assertTrue(villager.getOffers().size() == before, "topping up twice adds nothing");
		ctx.complete();
	}

	/** Moderner Beta's biomes give villagers vanilla's regions for their kind (not plains for all). */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void modernerBetaBiomesHaveRegions(TestContext ctx) {
		Consumer<Object[]> check = pair -> ctx.assertTrue(VillagerType.BIOME_TO_TYPE.get(RegistryKey.of(RegistryKeys.BIOME,
				Identifier.of("moderner_beta", (String) pair[0]))) == pair[1], pair[0] + " should be " + pair[1]);
		check.accept(new Object[]{"early_release_ice_plains", VillagerType.SNOW});
		check.accept(new Object[]{"early_release_taiga", VillagerType.TAIGA});
		check.accept(new Object[]{"early_release_extreme_hills", VillagerType.TAIGA});
		check.accept(new Object[]{"early_release_swampland", VillagerType.SWAMP});
		ctx.complete();
	}
}
