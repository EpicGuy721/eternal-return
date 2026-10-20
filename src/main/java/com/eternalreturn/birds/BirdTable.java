package com.eternalreturn.birds;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The bird table, src/main/resources/eternalreturn/birds.json: the palette, the shapes, the patterns and the presets
 * (real birds). tools/birds reads the same table to paint the textures and write the models' code, the names and
 * docs/birds.md. Palette, shapes and patterns are in id order, which saved birds rely on: append, never reorder.
 */
public final class BirdTable {
	/** A shape: its model (and texture folder), whether its tufts (an owl's ear tufts, a woodpecker's crest) show, its hitbox. */
	public record Shape(String id, String model, boolean tufts, float width, float height) {
	}

	/** A real bird: its id (for its name) and the variant it is. */
	public record Preset(String id, int variant) {
	}

	public static final List<String> COLOURS = new ArrayList<>();
	/** Each palette colour as 0xRRGGBB, by id. */
	public static final List<Integer> RGB = new ArrayList<>();
	public static final List<Shape> SHAPES = new ArrayList<>();
	/** The patterns; a variant's pattern number is its index here plus one (0: none). */
	public static final List<String> PATTERNS = new ArrayList<>();
	public static final List<Preset> PRESETS = new ArrayList<>();
	public static final double PRESET_SHARE;
	private static final Map<Integer, Preset> BY_VARIANT = new HashMap<>();

	private BirdTable() {
	}

	static {
		JsonObject table = read();
		PRESET_SHARE = table.get("preset_share").getAsDouble();
		for (JsonElement colour : table.getAsJsonArray("palette")) {
			COLOURS.add(colour.getAsJsonObject().get("id").getAsString());
			RGB.add(Integer.parseInt(colour.getAsJsonObject().get("rgb").getAsString().substring(1), 16));
		}
		for (JsonElement element : table.getAsJsonArray("shapes")) {
			JsonObject shape = element.getAsJsonObject();
			SHAPES.add(new Shape(shape.get("id").getAsString(), shape.get("model").getAsString(), shape.get("tufts").getAsBoolean(),
					shape.get("width").getAsFloat(), shape.get("height").getAsFloat()));
		}
		table.getAsJsonArray("patterns").forEach(pattern -> PATTERNS.add(pattern.getAsString()));
		if (SHAPES.size() > 8 || COLOURS.size() > 16 || PATTERNS.size() > 7) {
			throw new IllegalStateException("birds.json: at most 8 shapes, 16 colours and 7 patterns fit in a variant");
		}
		for (JsonElement element : table.getAsJsonArray("presets")) {
			JsonObject preset = element.getAsJsonObject();
			int[] patterns = new int[2];
			int[] colours = new int[2];
			var layers = preset.getAsJsonArray("patterns");
			for (int i = 0; i < layers.size(); i++) {
				JsonObject layer = layers.get(i).getAsJsonObject();
				patterns[i] = index(PATTERNS, layer.get("pattern").getAsString(), preset) + 1;
				colours[i] = index(COLOURS, layer.get("colour").getAsString(), preset);
			}
			int shape = SHAPES.stream().map(Shape::id).toList().indexOf(preset.get("shape").getAsString());
			if (shape < 0) {
				throw new IllegalStateException("birds.json: " + preset.get("id") + " has an unknown shape");
			}
			int variant = BirdVariant.pack(shape, index(COLOURS, preset.get("base").getAsString(), preset), patterns[0], colours[0], patterns[1], colours[1]);
			Preset entry = new Preset(preset.get("id").getAsString(), variant);
			PRESETS.add(entry);
			BY_VARIANT.putIfAbsent(variant, entry);
		}
	}

	private static int index(List<String> list, String id, JsonObject preset) {
		int index = list.indexOf(id);
		if (index < 0) {
			throw new IllegalStateException("birds.json: " + preset.get("id") + " names unknown " + id);
		}
		return index;
	}

	/** The preset this variant is, or null for a random bird. */
	public static Preset preset(int variant) {
		return BY_VARIANT.get(variant);
	}

	private static JsonObject read() {
		try (InputStream in = BirdTable.class.getResourceAsStream("/eternalreturn/birds.json")) {
			if (in == null) {
				throw new IllegalStateException("eternalreturn/birds.json missing from the mod jar");
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			}
		} catch (IOException e) {
			throw new IllegalStateException("cannot read eternalreturn/birds.json", e);
		}
	}
}
