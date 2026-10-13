package com.eternalreturn.fishing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The fish table, src/main/resources/eternalreturn/fish.json: every fish's rarity, what the fisherman pays
 * for it and which fishermen buy it, and the baits fishermen sell. tools/fish/build_fish.py reads the same
 * table to write the fish's textures, names, tooltips and catch tables (where and when each one bites), so a
 * new fish is one entry there and a rerun of the script.
 */
public final class FishTable {
	/** A rarity: the fisherman's level it is bought from, and how often the trade can be used before a restock. */
	public record Rarity(String name, int level, int maxUses, int experience) {
	}

	/**
	 * A fish. regions are the villager regions (plains, desert, savanna, snow, taiga, jungle, swamp) whose
	 * fishermen buy it, or null for every fisherman.
	 */
	public record Fish(String id, Rarity rarity, boolean nether, int sellCount, int sellEmeralds, @Nullable Set<String> regions) {
	}

	/** A bait fishermen sell: count of it for so many emeralds. */
	public record BaitOffer(Identifier item, int count, int emeralds) {
	}

	private static final Map<String, Rarity> RARITIES = new LinkedHashMap<>();
	private static final List<Fish> FISH = new ArrayList<>();
	private static final List<BaitOffer> BAITS = new ArrayList<>();

	private FishTable() {
	}

	static {
		JsonObject table = read();
		table.getAsJsonObject("rarities").entrySet().forEach(entry -> {
			JsonObject rarity = entry.getValue().getAsJsonObject();
			RARITIES.put(entry.getKey(), new Rarity(entry.getKey(), rarity.get("level").getAsInt(), rarity.get("max_uses").getAsInt(),
					rarity.get("experience").getAsInt()));
		});
		JsonObject habitats = table.getAsJsonObject("habitats");
		table.getAsJsonObject("fish").entrySet().forEach(entry -> {
			JsonObject fish = entry.getValue().getAsJsonObject();
			boolean nether = fish.get("nether").getAsBoolean();
			JsonObject sell = fish.getAsJsonObject("sell");
			Rarity rarity = RARITIES.get(fish.get("rarity").getAsString());
			if (rarity == null) {
				throw new IllegalStateException("fish.json: " + entry.getKey() + " has an unknown rarity");
			}
			FISH.add(new Fish(entry.getKey(), rarity, nether, sell.get("count").getAsInt(), sell.get("emeralds").getAsInt(),
					regions(fish.getAsJsonArray("habitats"), habitats, nether)));
		});
		table.getAsJsonObject("baits").entrySet().forEach(entry -> {
			JsonObject bait = entry.getValue().getAsJsonObject();
			BAITS.add(new BaitOffer(Identifier.of(entry.getKey()), bait.get("count").getAsInt(), bait.get("emeralds").getAsInt()));
		});
	}

	/** Every fish, in table order. */
	public static List<Fish> fish() {
		return FISH;
	}

	public static List<BaitOffer> baits() {
		return BAITS;
	}

	/** The fishermen of every habitat; null (every fisherman) if a habitat has none, or for Nether and habitat-less (underground) fish. */
	@Nullable
	private static Set<String> regions(JsonArray fishHabitats, JsonObject habitats, boolean nether) {
		if (nether || fishHabitats.isEmpty()) {
			return null;
		}
		Set<String> regions = new LinkedHashSet<>();
		for (JsonElement name : fishHabitats) {
			JsonObject habitat = habitats.getAsJsonObject(name.getAsString());
			if (habitat == null) {
				throw new IllegalStateException("fish.json names unknown habitat " + name);
			}
			if (!habitat.has("fishermen")) {
				return null;
			}
			habitat.getAsJsonArray("fishermen").forEach(region -> regions.add(region.getAsString()));
		}
		return regions;
	}

	public static Item item(Fish fish) {
		return Registries.ITEM.get(Identifier.of("eternalreturn", fish.id()));
	}

	private static JsonObject read() {
		try (InputStream in = FishTable.class.getResourceAsStream("/eternalreturn/fish.json")) {
			if (in == null) {
				throw new IllegalStateException("eternalreturn/fish.json missing from the mod jar");
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			}
		} catch (IOException e) {
			throw new IllegalStateException("cannot read eternalreturn/fish.json", e);
		}
	}
}
