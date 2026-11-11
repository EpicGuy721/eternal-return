package com.eternalreturn.mobs;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.SpawnSettings;

/**
 * Guardians spawn in every ocean biome (eternalreturn:ocean_waters: vanilla's oceans and Moderner Beta's own) as
 * monsters, rarely, and elder guardians only round the seamounts (eternalreturn:elder_guardian_seas, Eternal Return
 * worlds' seamount sub-biome; since 2026-11-10, before in every ocean), very rarely (weights in mobs.guardianSpawnWeight and mobs.elderGuardianSpawnWeight, read when the world
 * loads), and only in deep water (mobs.guardianMinDepth blocks of it or more overhead: SpawnRestrictionMixin).
 * Vanilla's own rule still applies: under open sky only one try in 20 passes. Natural elder
 * guardians have further limits (SpawnRestrictionMixin) and despawn like other monsters (MobEntitySpawnMixin).
 */
public final class OceanSpawns {
	/** The ocean biomes, the same ones fishing goes by: minecraft:is_ocean plus Moderner Beta's oceans. */
	public static final TagKey<Biome> OCEAN_WATERS = TagKey.of(RegistryKeys.BIOME, EternalReturn.id("ocean_waters"));
	/** Where elder guardians spawn: the seamounts. */
	public static final TagKey<Biome> ELDER_GUARDIAN_SEAS = TagKey.of(RegistryKeys.BIOME, EternalReturn.id("elder_guardian_seas"));

	private OceanSpawns() {
	}

	public static void register() {
		BiomeModifications.create(EternalReturn.id("ocean_guardians")).add(ModificationPhase.ADDITIONS, BiomeSelectors.tag(OCEAN_WATERS), context -> {
			EternalReturnConfig.MobTweaks cfg = EternalReturnConfig.get().mobs;
			if (cfg.guardianSpawnWeight > 0) {
				context.getSpawnSettings().addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(EntityType.GUARDIAN, cfg.guardianSpawnWeight, 1, 2));
			}
		});
		// Elder guardians only round the seamounts (eternalreturn:elder_guardian_seas; the owner's call, 2026-11-10), three times
		// as often there as they once were in any ocean.
		BiomeModifications.create(EternalReturn.id("seamount_elder_guardians")).add(ModificationPhase.ADDITIONS, BiomeSelectors.tag(ELDER_GUARDIAN_SEAS), context -> {
			EternalReturnConfig.MobTweaks cfg = EternalReturnConfig.get().mobs;
			if (cfg.elderGuardianSpawnWeight > 0) {
				context.getSpawnSettings().addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(EntityType.ELDER_GUARDIAN, cfg.elderGuardianSpawnWeight * 3, 1, 1));
			}
		});
	}
}
