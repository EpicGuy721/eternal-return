package com.eternalreturn.worldgen.caves;

import com.eternalreturn.config.EternalReturnConfig.CaveTypeSettings;
import com.eternalreturn.worldgen.caves.types.LargeCave;
import com.eternalreturn.worldgen.caves.types.RavineCave;
import com.eternalreturn.worldgen.caves.types.RibbedCave;
import com.eternalreturn.worldgen.caves.types.SpaghettiCave;
import com.eternalreturn.worldgen.caves.types.VerticalCave;
import com.eternalreturn.worldgen.caves.types.ZigZagCave;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every cave type, in the order they are carved. A new type is one class plus one entry here. */
public final class CaveTypes {
	public static final List<CaveType> ALL = List.of(
			new SpaghettiCave(),
			new RavineCave(),
			new LargeCave(),
			new VerticalCave(),
			new ZigZagCave(),
			new RibbedCave());

	private CaveTypes() {
	}

	@Nullable
	public static CaveType byId(String id) {
		for (CaveType type : ALL) {
			if (type.id().equals(id)) {
				return type;
			}
		}
		return null;
	}

	/** Fresh default settings for every type, keyed by id (written to new config files). */
	public static Map<String, CaveTypeSettings> defaultSettings() {
		Map<String, CaveTypeSettings> settings = new LinkedHashMap<>();
		for (CaveType type : ALL) {
			settings.put(type.id(), type.defaults());
		}
		return settings;
	}
}
