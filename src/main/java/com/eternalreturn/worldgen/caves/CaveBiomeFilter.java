package com.eternalreturn.worldgen.caves;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Where each of this mod's cave biomes may lie (ice, mushroom and magma caves). Moderner Beta's cave biome map is one
 * pattern for all the land, so its cave biome injector (ModernerBetaCaveBiomeMixin) asks this whether the biome it found
 * may be under the land the column has, and at that height; if not, there is no cave biome there. The table,
 * eternalreturn/cave_biomes.json, is written by tools/worldgen/build_preset.py: "under" (only under these biomes),
 * "not_under" (never under these), "max_y" (only at or below). Moderner Beta's own cave biomes, and the other presets
 * that use them, are never touched.
 */
public final class CaveBiomeFilter {
	private record Rule(@Nullable Set<Identifier> under, @Nullable Set<Identifier> notUnder, @Nullable Integer maxY) {
	}

	private static final Map<Identifier, Rule> RULES = read();

	private CaveBiomeFilter() {
	}

	private static Map<Identifier, Rule> read() {
		Map<Identifier, Rule> rules = new HashMap<>();
		try (InputStream in = CaveBiomeFilter.class.getResourceAsStream("/eternalreturn/cave_biomes.json")) {
			if (in == null) {
				return rules;
			}
			JsonObject table = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : table.entrySet()) {
				if (entry.getKey().startsWith("_")) {
					continue;
				}
				JsonObject spec = entry.getValue().getAsJsonObject();
				rules.put(Identifier.of(entry.getKey()), new Rule(set(spec, "under"), set(spec, "not_under"),
						spec.has("max_y") ? spec.get("max_y").getAsInt() : null));
			}
		} catch (Exception e) {
			throw new IllegalStateException("cannot read eternalreturn/cave_biomes.json", e);
		}
		return rules;
	}

	@Nullable
	private static Set<Identifier> set(JsonObject spec, String key) {
		if (!spec.has(key)) {
			return null;
		}
		Set<Identifier> out = new HashSet<>();
		spec.getAsJsonArray(key).forEach(id -> out.add(Identifier.of(id.getAsString())));
		return out;
	}

	/** Whether the cave biome may be here, under a column of this biome, at this height (block y). */
	public static boolean allows(RegistryEntry<Biome> cave, @Nullable RegistryEntry<Biome> land, int y) {
		Identifier id = cave.getKey().map(key -> key.getValue()).orElse(null);
		Rule rule = id == null ? null : RULES.get(id);
		if (rule == null) {
			return true;
		}
		if (rule.maxY() != null && y > rule.maxY()) {
			return false;
		}
		Identifier ground = land == null ? null : land.getKey().map(key -> key.getValue()).orElse(null);
		if (rule.under() != null && (ground == null || !rule.under().contains(ground))) {
			return false;
		}
		return rule.notUnder() == null || ground == null || !rule.notUnder().contains(ground);
	}
}
