package com.eternalreturn.mobs;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.worldgen.EternalReturnServer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.SpawnSettings;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureKeys;

/**
 * Blazes without fortresses: Eternal Return worlds have no nether fortresses, so blazes spawn naturally in
 * soul sand valleys and basalt deltas there (keeping blaze rods, and so eyes of ender and the End, within
 * reach). Other worlds are unchanged: there a natural blaze spawn still needs a fortress around it.
 */
public final class Blazes {
	private Blazes() {
	}

	public static void register() {
		BiomeModifications.create(EternalReturn.id("free_blazes")).add(ModificationPhase.ADDITIONS,
				BiomeSelectors.includeByKey(BiomeKeys.SOUL_SAND_VALLEY, BiomeKeys.BASALT_DELTAS),
				context -> context.getSpawnSettings().addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(EntityType.BLAZE, 15, 1, 2)));
	}

	/** A natural blaze spawn: anywhere in an Eternal Return world, elsewhere only inside a fortress (as in vanilla). */
	public static boolean mayFreeSpawn(ServerWorld world, BlockPos pos) {
		if (EternalReturnServer.active()) {
			return true;
		}
		Structure fortress = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(StructureKeys.FORTRESS);
		return fortress != null && world.getStructureAccessor().getStructureContaining(pos, fortress).hasChildren();
	}
}
