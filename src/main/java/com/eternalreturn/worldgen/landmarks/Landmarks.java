package com.eternalreturn.worldgen.landmarks;

import com.eternalreturn.EternalReturn;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.world.gen.structure.StructureType;

/**
 * Terrain landmarks (worldgen expansion phase 4, docs/worldgen.md): rock arches, rock spires, tropical pillars,
 * craters with ore round the rim, lava flows with charred ground, volcanoes, glaciers and ice walls. Each is a structure
 * (data/eternalreturn/worldgen/structure/*.json: which landmark, its size range and biomes; structure_set/*.json: how
 * rare), so it can cross chunks, its rarity is data and /locate structure finds it. They start only in Eternal Return
 * worlds (LandmarkStructure), are kept there by worldgen.onlyEssentialStructures (EternalReturnServer), and are built of
 * the stone of the biome they start in.
 */
public final class Landmarks {
	/** The kinds; each one's shape is in LandmarkShapes. */
	public enum Kind {
		ARCH, SPIRE, TROPICAL_PILLARS, CRATER, LAVA_FLOW, VOLCANO, GLACIER, ICE_WALL,
		/** The mesa styles' rock forms (phase 6): flat-topped buttes, wide green-topped tables, fields of hoodoos. */
		BUTTE, TABLE, HOODOOS,
		/** The warm seas' landmarks (phase 6, batch D): chains of small islands, coral reef shelves under the surface. */
		ISLETS, REEF,
		/** Hot springs (worldgen expansion 2, phase 3): terraced pools with a vent, some with a geyser (HotSprings). */
		HOT_SPRING;

		public String id() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}

		public static Kind of(String id) {
			return valueOf(id.toUpperCase(java.util.Locale.ROOT));
		}
	}

	public static final StructureType<LandmarkStructure> TYPE = Registry.register(Registries.STRUCTURE_TYPE, EternalReturn.id("landmark"),
			() -> LandmarkStructure.CODEC);
	public static final StructurePieceType PIECE = Registry.register(Registries.STRUCTURE_PIECE, EternalReturn.id("landmark"),
			(StructurePieceType.Simple) LandmarkPiece::new);

	private Landmarks() {
	}

	public static void register() {
		// The fields above register the structure and piece types when the class loads.
		HotSprings.register();
	}
}
