package com.eternalreturn.worldgen.variants;

import com.eternalreturn.EternalReturn;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Biome variants (worldgen expansion phase 3, docs/worldgen.md): peaks, edges and rivers, each a real biome linked to
 * its base. The list is eternalreturn/biome_variants.json, written by tools/worldgen/build_variants.py from
 * tools/worldgen/variants.json, with each variant's base, kind (peak, edge, river) and boulders. A variant joins every
 * biome tag its base is in through the built-in data pack biome_variants (always on); code that goes by biome rather
 * than by tag asks this class for the base (tree tables, boulder density, villager regions, mobs added to one biome).
 */
public final class BiomeVariants {
	/** coastal: a sea sub-biome only within this many blocks of land, its parent sea beyond (the mangrove lagoon). */
	public record Variant(Identifier base, String kind, @Nullable Float boulders, @Nullable Integer coastal) {
	}

	private static final Map<Identifier, Variant> VARIANTS = load();

	private BiomeVariants() {
	}

	private static Map<Identifier, Variant> load() {
		Map<Identifier, Variant> map = new HashMap<>();
		try (InputStream in = BiomeVariants.class.getResourceAsStream("/eternalreturn/biome_variants.json")) {
			if (in == null) {
				return Map.of();
			}
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
				JsonObject v = entry.getValue().getAsJsonObject();
				map.put(Identifier.of(entry.getKey()), new Variant(Identifier.of(v.get("base").getAsString()), v.get("kind").getAsString(),
						v.has("boulders") ? v.get("boulders").getAsFloat() : null, v.has("coastal") ? v.get("coastal").getAsInt() : null));
			}
		} catch (Exception e) {
			EternalReturn.LOGGER.error("Couldn't read the biome variants", e);
		}
		return Map.copyOf(map);
	}

	/** Switches on the built-in pack that puts each variant in its base's biome tags. */
	public static void register() {
		FabricLoader.getInstance().getModContainer(EternalReturn.MOD_ID).ifPresent(mod -> ResourceManagerHelper.registerBuiltinResourcePack(
				EternalReturn.id("biome_variants"), mod, Text.literal("Eternal Return biome variants"), ResourcePackActivationType.ALWAYS_ENABLED));
	}

	@Nullable
	public static Variant get(Identifier biome) {
		return VARIANTS.get(biome);
	}

	@Nullable
	public static Variant get(RegistryEntry<Biome> biome) {
		return biome.getKey().map(key -> VARIANTS.get(key.getValue())).orElse(null);
	}

	/** The base of a variant, or the biome itself. */
	public static Identifier baseOf(Identifier biome) {
		Variant variant = VARIANTS.get(biome);
		return variant == null ? biome : variant.base();
	}

	/** The variants of these biomes. */
	public static Set<RegistryKey<Biome>> variantsOf(Set<Identifier> bases) {
		return VARIANTS.entrySet().stream().filter(entry -> bases.contains(entry.getValue().base()))
				.map(entry -> RegistryKey.of(RegistryKeys.BIOME, entry.getKey())).collect(Collectors.toSet());
	}

	/** Every variant (for biome modifications meant for all overworld biomes). */
	public static Predicate<BiomeSelectionContext> any() {
		return context -> VARIANTS.containsKey(context.getBiomeKey().getValue());
	}

	/** These biomes and their variants. */
	@SafeVarargs
	public static Predicate<BiomeSelectionContext> withVariants(RegistryKey<Biome>... bases) {
		Set<Identifier> ids = java.util.Arrays.stream(bases).map(RegistryKey::getValue).collect(Collectors.toSet());
		return context -> ids.contains(baseOf(context.getBiomeKey().getValue()));
	}
}
