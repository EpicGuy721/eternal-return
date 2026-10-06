package com.eternalreturn.fishing;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;

public final class FishingLoot {
	public static final LootConditionType BAIT_CONDITION = Registry.register(
			Registries.LOOT_CONDITION_TYPE, EternalReturn.id("bait"), new LootConditionType(BaitLootCondition.CODEC));
	public static final LootConditionType DEEP_WATER_CONDITION = Registry.register(
			Registries.LOOT_CONDITION_TYPE, EternalReturn.id("deep_water"), new LootConditionType(DeepWaterLootCondition.CODEC));

	/** Used instead of minecraft:gameplay/fishing when the hook is in lava. */
	public static final RegistryKey<LootTable> LAVA_FISHING = RegistryKey.of(RegistryKeys.LOOT_TABLE, EternalReturn.id("gameplay/lava_fishing"));

	private FishingLoot() {
	}

	public static void register() {
	}

	/** Lava fishing works in ultrawarm dimensions (the Nether), or anywhere if the config allows it. */
	public static boolean lavaFishingAllowed(World world) {
		return world.getDimension().ultrawarm() || EternalReturnConfig.get().fishing.lavaFishingOutsideNether;
	}

	/** Water particles would vanish in lava; swap them for ones that read as lava. */
	public static ParticleEffect lavaParticle(ParticleEffect particle) {
		if (particle == ParticleTypes.BUBBLE) {
			return ParticleTypes.LAVA;
		}
		if (particle == ParticleTypes.FISHING) {
			return ParticleTypes.FLAME;
		}
		if (particle == ParticleTypes.SPLASH) {
			return ParticleTypes.SMOKE;
		}
		return particle;
	}
}
