package com.eternalreturn.worldgen.climate;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.worldgen.EternalReturnServer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fog tints by biome (phase 5 of the worldgen expansion), Eternal Return worlds only: the table
 * data/eternalreturn/eternalreturn/fog_tints.json gives a biome (its id, or a biome tag written "#namespace:path") a
 * day fog colour and a night one. An id wins over a tag, tags are tried in the order written, and a biome in none
 * keeps its own fog. The server works out each biome's tint and sends the list to the players' games as they join
 * (and after /reload), which draw it (client ClientFog, FogTintMixin).
 */
public final class FogTints {
	/** A biome's tint, colours as 0xRRGGBB. */
	public record Tint(Identifier biome, int day, int night) {
		static final PacketCodec<RegistryByteBuf, Tint> CODEC = PacketCodec.tuple(Identifier.PACKET_CODEC, Tint::biome,
				PacketCodecs.INTEGER, Tint::day, PacketCodecs.INTEGER, Tint::night, Tint::new);
	}

	/** The tints for a player's game; empty: none (not an Eternal Return world, or tints off). */
	public record Payload(List<Tint> tints) implements CustomPayload {
		public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(EternalReturn.id("fog_tints"));
		public static final PacketCodec<RegistryByteBuf, Payload> CODEC = Tint.CODEC.collect(PacketCodecs.toList()).xmap(Payload::new, Payload::tints);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** The table as written: key (id or #tag) to day and night colour, in file order. */
	private static volatile Map<String, int[]> table = Map.of();

	private FogTints() {
	}

	static void register() {
		PayloadTypeRegistry.playS2C().register(Payload.ID, Payload.CODEC);
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return EternalReturn.id("fog_tints");
			}

			@Override
			public void reload(ResourceManager manager) {
				load(manager);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(server, handler.player));
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				send(server, player);
			}
		});
	}

	static void load(ResourceManager manager) {
		Map<String, int[]> loaded = new LinkedHashMap<>();
		for (Map.Entry<Identifier, List<Resource>> file : manager.findAllResources("eternalreturn",
				id -> id.getPath().equals("eternalreturn/fog_tints.json")).entrySet()) {
			for (Resource resource : file.getValue()) {
				try (Reader reader = resource.getReader()) {
					JsonObject tints = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("tints");
					for (Map.Entry<String, JsonElement> entry : tints.entrySet()) {
						JsonObject tint = entry.getValue().getAsJsonObject();
						loaded.put(entry.getKey(), new int[]{color(tint.get("day").getAsString()), color(tint.get("night").getAsString())});
					}
				} catch (Exception e) {
					EternalReturn.LOGGER.error("Couldn't read the fog tints {}", file.getKey(), e);
				}
			}
		}
		table = Map.copyOf(loaded);
	}

	private static int color(String hex) {
		return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16) & 0xFFFFFF;
	}

	/** Every biome's tint in this server's world (none outside Eternal Return worlds, or with fog tints off). */
	public static List<Tint> tints(MinecraftServer server) {
		List<Tint> tints = new ArrayList<>();
		if (!EternalReturnServer.active() || !EternalReturnConfig.get().worldgen.fogTints) {
			return tints;
		}
		Registry<Biome> biomes = server.getRegistryManager().get(RegistryKeys.BIOME);
		Map<String, int[]> written = table;
		for (RegistryEntry.Reference<Biome> biome : biomes.streamEntries().toList()) {
			int[] tint = tintOf(biome, written);
			if (tint != null) {
				tints.add(new Tint(biome.registryKey().getValue(), tint[0], tint[1]));
			}
		}
		return tints;
	}

	private static int[] tintOf(RegistryEntry<Biome> biome, Map<String, int[]> written) {
		int[] byId = biome.getKey().map(key -> written.get(key.getValue().toString())).orElse(null);
		if (byId != null) {
			return byId;
		}
		for (Map.Entry<String, int[]> entry : written.entrySet()) {
			if (entry.getKey().startsWith("#")) {
				Identifier tag = Identifier.tryParse(entry.getKey().substring(1));
				if (tag != null && biome.isIn(TagKey.of(RegistryKeys.BIOME, tag))) {
					return entry.getValue();
				}
			}
		}
		return null;
	}

	private static void send(MinecraftServer server, ServerPlayerEntity player) {
		if (ServerPlayNetworking.canSend(player, Payload.ID)) {
			ServerPlayNetworking.send(player, new Payload(tints(server)));
		}
	}
}
