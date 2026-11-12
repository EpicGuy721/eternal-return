package com.eternalreturn.worldgen.personality;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.worldgen.EternalReturnServer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The world's personality salt (Personality.salt, from the seed but not it) sent to each player's game as they join, for
 * the patches' grass and leaf tint drawn there; 0 outside Eternal Return worlds or with personality rolls off.
 */
public final class PersonalitySync {
	public record Payload(long salt) implements CustomPayload {
		public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(EternalReturn.id("personality"));
		public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodecs.VAR_LONG.xmap(Payload::new, Payload::salt).cast();

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** The accents (AccentFeature). */
	public static final net.minecraft.world.gen.feature.Feature<net.minecraft.world.gen.feature.DefaultFeatureConfig> ACCENT = net.minecraft.registry.Registry.register(
			net.minecraft.registry.Registries.FEATURE, EternalReturn.id("accent"), new AccentFeature());

	private PersonalitySync() {
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(Payload.ID, Payload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(server, handler.player));
		PatchCommand.register();
		AccentFeature.register();
		// Accents: last of the plants, so no other feature's place in the decoration order moves (other worlds unchanged:
		// the placed feature carries eternalreturn:eternal_return_only).
		net.fabricmc.fabric.api.biome.v1.BiomeModifications.addFeature(
				com.eternalreturn.worldgen.WorldgenFeatures.overworld().and(context -> EternalReturnConfig.get().worldgen.personalityRolls),
				net.minecraft.world.gen.GenerationStep.Feature.VEGETAL_DECORATION,
				net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.PLACED_FEATURE, EternalReturn.id("accent")));
	}

	/** The salt this server's world tints with (0: none). */
	public static long salt(MinecraftServer server) {
		return EternalReturnServer.active() && EternalReturnConfig.get().worldgen.personalityRolls ? Personality.salt(server.getOverworld().getSeed()) : 0;
	}

	private static void send(MinecraftServer server, ServerPlayerEntity player) {
		if (ServerPlayNetworking.canSend(player, Payload.ID)) {
			ServerPlayNetworking.send(player, new Payload(salt(server)));
		}
	}
}
