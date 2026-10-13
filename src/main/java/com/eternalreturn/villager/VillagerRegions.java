package com.eternalreturn.villager;

import net.fabricmc.fabric.api.object.builder.v1.villager.VillagerTypeHelper;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.village.VillagerType;

import java.util.Map;

/**
 * Villagers born, cured or spawned in Moderner Beta's biomes get the region vanilla gives its own biome of the
 * same kind (vanilla only knows its own biomes, so they were all plains villagers): ice plains and tundras are
 * snow, taigas and extreme hills taiga, swamplands swamp, deserts desert, savannas savanna, rainforests jungle.
 * The region sets a villager's clothes and which fish a fisherman buys (FishermanTrades). Harmless without
 * Moderner Beta: the biomes are simply never seen.
 */
public final class VillagerRegions {
	private static final Map<String, VillagerType> MODERNER_BETA = Map.ofEntries(
			Map.entry("early_release_ice_plains", VillagerType.SNOW), Map.entry("late_beta_ice_plains", VillagerType.SNOW),
			Map.entry("beta_tundra", VillagerType.SNOW), Map.entry("pe_tundra", VillagerType.SNOW),
			Map.entry("beta_ice_desert", VillagerType.SNOW), Map.entry("pe_ice_desert", VillagerType.SNOW),
			Map.entry("alpha_winter", VillagerType.SNOW), Map.entry("beta_frozen_ocean", VillagerType.SNOW), Map.entry("pe_frozen_ocean", VillagerType.SNOW),
			Map.entry("early_release_taiga", VillagerType.TAIGA), Map.entry("late_beta_taiga", VillagerType.TAIGA),
			Map.entry("beta_taiga", VillagerType.TAIGA), Map.entry("beta_oak_taiga", VillagerType.TAIGA), Map.entry("pe_taiga", VillagerType.TAIGA),
			Map.entry("early_release_extreme_hills", VillagerType.TAIGA), Map.entry("late_beta_extreme_hills", VillagerType.TAIGA),
			Map.entry("early_release_swampland", VillagerType.SWAMP), Map.entry("late_beta_swampland", VillagerType.SWAMP),
			Map.entry("beta_swampland", VillagerType.SWAMP), Map.entry("pe_swampland", VillagerType.SWAMP),
			Map.entry("beta_desert", VillagerType.DESERT), Map.entry("pe_desert", VillagerType.DESERT),
			Map.entry("beta_savanna", VillagerType.SAVANNA), Map.entry("pe_savanna", VillagerType.SAVANNA),
			Map.entry("beta_rainforest", VillagerType.JUNGLE), Map.entry("pe_rainforest", VillagerType.JUNGLE));

	private VillagerRegions() {
	}

	public static void register() {
		MODERNER_BETA.forEach((biome, type) ->
				VillagerTypeHelper.addVillagerTypeToBiome(RegistryKey.of(RegistryKeys.BIOME, Identifier.of("moderner_beta", biome)), type));
	}
}
