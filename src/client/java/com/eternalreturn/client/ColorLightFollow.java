package com.eternalreturn.client;

import com.eternalreturn.EternalReturn;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.mrhikmen.colorlight.client.config.BlockSettings;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * With ColorLight (an optional client mod), related light sources share one colour: every fire light (fire, campfires,
 * lanterns, lit furnaces, candles...) the torch's, every soul light the soul torch's, glowstone's lights glowstone's
 * (the table eternalreturn/colorlight_follow.json). ColorLight builds its table of block colours whenever its settings
 * load or change (ColorLightFollowMixin); each follower there is given its source's colour then, keeping its own light
 * level, so recolouring the torch in ColorLight's menu recolours them all. A follower ColorLight has no light for (or
 * has switched off) stays so; with the source switched off, followers keep their own colours.
 */
public final class ColorLightFollow {
	private static final String TABLE = "/eternalreturn/colorlight_follow.json";
	private static Map<Identifier, Identifier> follows;

	private ColorLightFollow() {
	}

	/** ColorLight's new block colours with each follower recoloured as its source, ready for ColorLight to use. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Map apply(Map colours) {
		try {
			Map<Block, BlockSettings> map = (Map<Block, BlockSettings>) colours;
			int shared = 0;
			for (Map.Entry<Identifier, Identifier> entry : follows().entrySet()) {
				if (!Registries.BLOCK.containsId(entry.getKey()) || !Registries.BLOCK.containsId(entry.getValue())) {
					continue;
				}
				BlockSettings source = map.get(Registries.BLOCK.get(entry.getValue()));
				Block follower = Registries.BLOCK.get(entry.getKey());
				BlockSettings own = map.get(follower);
				if (source == null || own == null) {
					continue;
				}
				// A copy: an entry edited in ColorLight's menu is its settings' own object, saved to its file.
				BlockSettings recoloured = own.copy();
				recoloured.r = source.r;
				recoloured.g = source.g;
				recoloured.b = source.b;
				map.put(follower, recoloured);
				shared++;
			}
			EternalReturn.LOGGER.info("ColorLight: {} lights take their source's colour (torch, soul torch, glowstone)", shared);
		} catch (RuntimeException | LinkageError e) {
			EternalReturn.LOGGER.warn("ColorLight has changed: related lights keep their own colours", e);
		}
		return colours;
	}

	/** Follower -> source. */
	private static synchronized Map<Identifier, Identifier> follows() {
		if (follows == null) {
			Map<Identifier, Identifier> table = new LinkedHashMap<>();
			try (InputStream in = ColorLightFollow.class.getResourceAsStream(TABLE);
				 Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				for (Map.Entry<String, JsonElement> source : json.entrySet()) {
					if (source.getKey().startsWith("_")) {
						continue;
					}
					for (JsonElement follower : source.getValue().getAsJsonArray()) {
						table.put(Identifier.of(follower.getAsString()), Identifier.of(source.getKey()));
					}
				}
			} catch (Exception e) {
				EternalReturn.LOGGER.warn("Couldn't read {}: related lights keep their own ColorLight colours", TABLE, e);
			}
			follows = table;
		}
		return follows;
	}
}
