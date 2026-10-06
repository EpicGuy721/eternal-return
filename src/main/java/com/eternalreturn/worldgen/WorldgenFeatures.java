package com.eternalreturn.worldgen;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.worldgen.carver.DebugTunnelCarver;
import com.eternalreturn.worldgen.caves.CaveSystemCarver;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.carver.Carver;
import net.minecraft.world.gen.carver.CarverConfig;
import net.minecraft.world.gen.carver.ConfiguredCarver;

/**
 * Overworld generation for Eternal Return (vision and status in docs/worldgen.md).
 * Each feature registers here and is switched on or off from the "worldgen" config section.
 */
public final class WorldgenFeatures {
	public static final Carver<CarverConfig> DEBUG_TUNNEL_CARVER = Registry.register(
			Registries.CARVER, EternalReturn.id("debug_tunnel"), new DebugTunnelCarver(CarverConfig.CONFIG_CODEC.codec()));

	/** data/eternalreturn/worldgen/configured_carver/debug_tunnel.json */
	public static final RegistryKey<ConfiguredCarver<?>> DEBUG_TUNNEL = RegistryKey.of(RegistryKeys.CONFIGURED_CARVER, EternalReturn.id("debug_tunnel"));

	/** The cave engine (com.eternalreturn.worldgen.caves); carves only in Eternal Return worlds. */
	public static final Carver<CarverConfig> CAVE_CARVER = Registry.register(
			Registries.CARVER, EternalReturn.id("caves"), new CaveSystemCarver(CarverConfig.CONFIG_CODEC.codec()));

	/** data/eternalreturn/worldgen/configured_carver/caves.json: lava level and the eternalreturn:carvable tag. */
	public static final RegistryKey<ConfiguredCarver<?>> CAVES = RegistryKey.of(RegistryKeys.CONFIGURED_CARVER, EternalReturn.id("caves"));

	private WorldgenFeatures() {
	}

	/**
	 * Carvers are attached to overworld biomes through Fabric's biome modifications. The config is
	 * read when the world's biomes load, so a toggle takes effect on the next start. The cave carver
	 * is attached to every overworld biome and decides per world (and per config) whether to carve;
	 * it is added after the biomes' own carvers, so their random seeds don't move.
	 */
	public static void register() {
		BiomeModifications.addCarver(
				BiomeSelectors.foundInOverworld().and(context -> EternalReturnConfig.get().worldgen.debugTunnel),
				GenerationStep.Carver.AIR,
				DEBUG_TUNNEL);
		BiomeModifications.addCarver(BiomeSelectors.foundInOverworld(), GenerationStep.Carver.AIR, CAVES);
	}
}
