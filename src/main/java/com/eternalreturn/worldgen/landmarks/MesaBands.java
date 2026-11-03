package com.eternalreturn.worldgen.landmarks;

import com.eternalreturn.EternalReturn;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The mesa styles' rock bands (worldgen expansion phase 6, batch B): eternalreturn/mesa_bands.json, written by
 * tools/worldgen/build_variants.py from the "bands" of each mesa sub-biome in tools/worldgen/variants.json. The ground's
 * surface rules (build_preset.py) and the buttes, tables and hoodoos built on it (LandmarkShapes) take the same block at
 * the same height, so a rock form's stripes carry on in the land round it.
 */
public final class MesaBands {
	/** One style: its bands from base_y up, repeating; "cap" for hoodoo caps and "top" for table tops, if any. */
	public record Style(List<BlockState> layers, @Nullable BlockState cap, @Nullable BlockState top) {
		public BlockState at(int y) {
			return this.layers.get(Math.floorMod(y - baseY, this.layers.size()));
		}
	}

	private static int baseY = 58;
	private static final Map<String, Style> STYLES = load();

	private MesaBands() {
	}

	private static BlockState block(String id) {
		return Registries.BLOCK.get(Identifier.of(id)).getDefaultState();
	}

	private static Map<String, Style> load() {
		Map<String, Style> map = new HashMap<>();
		try (InputStream in = MesaBands.class.getResourceAsStream("/eternalreturn/mesa_bands.json")) {
			if (in == null) {
				return Map.of();
			}
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			baseY = json.get("base_y").getAsInt();
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("styles").entrySet()) {
				JsonObject style = entry.getValue().getAsJsonObject();
				List<String> layers = new ArrayList<>();
				for (JsonElement band : style.getAsJsonArray("bands")) {
					JsonArray b = band.getAsJsonArray();
					for (int i = 0; i < b.get(1).getAsInt(); i++) {
						layers.add(b.get(0).getAsString());
					}
				}
				map.put(entry.getKey(), new Style(layers.stream().map(MesaBands::block).toList(),
						style.has("cap") ? block(style.get("cap").getAsString()) : null, style.has("top") ? block(style.get("top").getAsString()) : null));
			}
		} catch (Exception e) {
			EternalReturn.LOGGER.error("Couldn't read the mesa bands", e);
		}
		return map;
	}

	/** The style of this biome, or null for a biome with no bands. */
	@Nullable
	public static Style of(String biome) {
		return STYLES.get(biome);
	}
}
