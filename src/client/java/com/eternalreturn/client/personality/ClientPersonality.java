package com.eternalreturn.client.personality;

import com.eternalreturn.worldgen.personality.Personality;
import com.eternalreturn.worldgen.personality.PersonalitySync;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.ColorResolver;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The patches' grass and leaf tint (Personality.tint) in the player's game, from the salt the server sent
 * (PersonalitySync): BiomeColorsTintMixin wraps vanilla's grass and foliage colour resolvers with tint here.
 */
public final class ClientPersonality {
	private static volatile long salt;
	private static final Map<Biome, Optional<Identifier>> IDS = new ConcurrentHashMap<>();

	private ClientPersonality() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PersonalitySync.Payload.ID, (payload, context) -> {
			long before = salt;
			salt = payload.salt();
			IDS.clear();
			if (before != salt && context.client().worldRenderer != null && context.client().world != null) {
				context.client().worldRenderer.reload();
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			salt = 0;
			IDS.clear();
		});
	}

	/** A resolver giving the original's colour tinted for its patch. */
	public static ColorResolver tinted(ColorResolver original) {
		return (biome, x, z) -> tint(original.getColor(biome, x, z), biome, x, z);
	}

	private static int tint(int rgb, Biome biome, double x, double z) {
		long current = salt;
		if (current == 0) {
			return rgb;
		}
		Optional<Identifier> id = IDS.computeIfAbsent(biome, ClientPersonality::idOf);
		return id.map(value -> Personality.tint(current, value, rgb, x, z)).orElse(rgb);
	}

	private static Optional<Identifier> idOf(Biome biome) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(client.world.getRegistryManager().get(RegistryKeys.BIOME).getId(biome));
	}
}
