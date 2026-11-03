package com.eternalreturn.client.climate;

import com.eternalreturn.worldgen.climate.FogTints;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The fog tints the server sent (FogTints), drawn by FogTintMixin: a tinted biome's fog colour is its day colour, dimmed
 * toward dusk as vanilla dims fog, fading into its night colour as the sun goes down.
 */
public final class ClientFog {
	private static volatile Map<Identifier, int[]> tints = Map.of();

	private ClientFog() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(FogTints.Payload.ID, (payload, context) -> {
			Map<Identifier, int[]> received = new HashMap<>();
			for (FogTints.Tint tint : payload.tints()) {
				received.put(tint.biome(), new int[]{tint.day(), tint.night()});
			}
			tints = Map.copyOf(received);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> tints = Map.of());
	}

	public static boolean active(ClientWorld world) {
		return !tints.isEmpty() && world.getRegistryKey() == World.OVERWORLD;
	}

	/**
	 * The fog colour of a biome at this daylight (0 night to 1 day, vanilla's), already dimmed for the time of day, or
	 * null where the biome has no tint (vanilla's colour then).
	 */
	public static Vec3d color(ClientWorld world, RegistryEntry<Biome> biome, float daylight) {
		Optional<RegistryKey<Biome>> key = biome.getKey();
		int[] tint = key.isPresent() ? tints.get(key.get().getValue()) : null;
		if (tint == null) {
			return null;
		}
		Vec3d day = world.getDimensionEffects().adjustFogColor(Vec3d.unpackRgb(tint[0]), daylight);
		Vec3d night = Vec3d.unpackRgb(tint[1]);
		return new Vec3d(MathHelper.lerp(daylight, night.x, day.x), MathHelper.lerp(daylight, night.y, day.y), MathHelper.lerp(daylight, night.z, day.z));
	}
}
