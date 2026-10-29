package com.eternalreturn.client;

import com.eternalreturn.blocks.Snowlogging;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The snow logged in blocks (Snowlogging) as the player's game knows it: per chunk, as the server sends it with each
 * chunk and as it changes, forgotten with the chunk. It's read where the blocks are drawn (SnowModels, on the threads
 * that build the chunks' meshes too) and by the game's rules in the player's game (aiming, standing on it), and the
 * blocks are drawn again when it changes.
 */
public final class ClientSnow {
	private static final Map<Long, Map<Long, Integer>> CHUNKS = new ConcurrentHashMap<>();

	private ClientSnow() {
	}

	public static void register() {
		Snowlogging.clientLayers = ClientSnow::layers;
		Snowlogging.clientSet = ClientSnow::set;
		ClientPlayNetworking.registerGlobalReceiver(Snowlogging.Payload.ID, (payload, context) -> {
			for (Snowlogging.Entry entry : payload.entries()) {
				set(BlockPos.fromLong(entry.pos()), entry.layers());
			}
		});
		ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> CHUNKS.remove(chunk.getPos().toLong()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CHUNKS.clear());
	}

	private static long chunk(BlockPos pos) {
		return ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
	}

	public static int layers(BlockPos pos) {
		Map<Long, Integer> chunk = CHUNKS.get(chunk(pos));
		return chunk == null ? 0 : chunk.getOrDefault(pos.asLong(), 0);
	}

	private static void set(BlockPos pos, int layers) {
		Map<Long, Integer> chunk = CHUNKS.computeIfAbsent(chunk(pos), key -> new ConcurrentHashMap<>());
		Integer old = layers > 0 ? chunk.put(pos.asLong(), layers) : chunk.remove(pos.asLong());
		if ((old == null ? 0 : old) == layers) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.worldRenderer != null) {
			client.worldRenderer.scheduleBlockRenders(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
		}
	}
}
