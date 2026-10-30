package com.eternalreturn.client;

import com.eternalreturn.EternalReturn;
import me.mrhikmen.colorlight.client.config.BlockSettings;
import me.mrhikmen.colorlight.client.core.light.engine.ColorLightEngine;
import me.mrhikmen.colorlight.client.core.light.engine.ColorLightEngineHolder;
import me.mrhikmen.colorlight.client.core.light.registry.ColorLightBlockRegistry;
import me.mrhikmen.colorlight.client.core.light.scan.ColorLightChunkScanner;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.AbstractCandleBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * With ColorLight (an optional client mod), weak light sources are drawn brighter: soul torches, soul lanterns, soul fire
 * and lit soul campfires light as a level 13 block (10 in vanilla), and lit candles 3 levels more per candle (1 to 4
 * candles: 6, 9, 12, 15; vanilla 3, 6, 9, 12; a lit candle cake as one candle). Only ColorLight's drawing changes: the
 * game's light level, and so where mobs spawn, stays vanilla's. ColorLight's level for a block caps it, raised for these
 * in this mod's ColorLight file (assets/colorlight/block/eternalreturn_brighter.json). ColorLight reads a block's light
 * when it scans a chunk (ColorLightScanMixin gives it this level) and when a block changes, where its light is put back
 * to this level here, on ColorLight's thread after its own work. Held and dropped ones light the same
 * (ColorLightHeldLevelMixin). Loaded only when ColorLight is (EternalReturnClient); if its insides change, this stops.
 */
public final class ColorLightBrighter {
	/** Changed blocks whose light is still to be raised, with how many more ticks to try (ColorLight may not have it yet). */
	private static final Map<BlockPos, Integer> PENDING = new HashMap<>();
	private static final int TRIES = 20;
	private static boolean broken;

	private ColorLightBrighter() {
	}

	/** The light level ColorLight draws a block at. */
	public static int level(BlockState state) {
		int light = state.getLuminance();
		if (light == 0) {
			return 0;
		}
		if (state.isOf(Blocks.SOUL_TORCH) || state.isOf(Blocks.SOUL_WALL_TORCH) || state.isOf(Blocks.SOUL_LANTERN) || state.isOf(Blocks.SOUL_FIRE)
				|| state.isOf(Blocks.SOUL_CAMPFIRE)) {
			return 13;
		}
		if (state.getBlock() instanceof CandleBlock) {
			return Math.min(15, 3 + 3 * state.get(CandleBlock.CANDLES));
		}
		if (state.getBlock() instanceof AbstractCandleBlock) {
			return 6;
		}
		return light;
	}

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
	}

	/** A block changed on the client (WorldChunk.setBlockState): its light is raised once ColorLight has placed it. */
	public static void changed(BlockPos pos, BlockState state) {
		if (!broken && level(state) > state.getLuminance()) {
			PENDING.put(pos.toImmutable(), TRIES);
		}
	}

	private static void tick() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (broken || PENDING.isEmpty() || client.world == null) {
			PENDING.clear();
			return;
		}
		for (Iterator<Map.Entry<BlockPos, Integer>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<BlockPos, Integer> entry = it.next();
			BlockPos pos = entry.getKey();
			BlockState state = client.world.getBlockState(pos);
			int light = level(state);
			BlockSettings settings = ColorLightBlockRegistry.get(state.getBlock());
			if (light <= state.getLuminance() || settings == null || entry.getValue() <= 0) {
				it.remove();
				continue;
			}
			entry.setValue(entry.getValue() - 1);
			try {
				ColorLightChunkScanner.runOnApplyThread(() -> {
					ColorLightEngine engine = ColorLightEngineHolder.get();
					if (engine != null && engine.hasSource(pos)) {
						engine.addSource(pos, settings.r, settings.g, settings.b, light);
						client.execute(() -> PENDING.remove(pos));
					}
				});
			} catch (LinkageError e) {
				broken = true;
				EternalReturn.LOGGER.warn("ColorLight has changed: soul lights and candles keep its light level for them", e);
			}
		}
	}
}
