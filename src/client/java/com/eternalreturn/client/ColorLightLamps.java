package com.eternalreturn.client;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.blocks.Dyeing;
import me.mrhikmen.colorlight.client.core.light.engine.ColorLightEngine;
import me.mrhikmen.colorlight.client.core.light.engine.ColorLightEngineHolder;
import me.mrhikmen.colorlight.client.core.light.scan.ColorLightChunkScanner;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ColorLight (an optional client mod) gives every block of a kind one light colour, and a dyed lamp keeps its colour per
 * lamp (Dyeing), so its light is the lamp's warm white from ColorLight's colours file. With ColorLight installed, each
 * lit dyed lamp's light is set to the lamp's own colour, made as bright as it can be with the same hue (as ColorLight's
 * colours are: a dark dye's lamp still lights). Only light ColorLight already has at a lamp is recoloured, on its own
 * thread after its own work; it sets the light back to the block colour when it scans the chunk again, so lit lamps are
 * recoloured again every second. Loaded only when ColorLight is (EternalReturnClient); if its insides change, this stops.
 */
final class ColorLightLamps {
	private static final Set<BlockPos> LAMPS = new HashSet<>();
	/** The colour each lamp's light has been given, written on ColorLight's thread. */
	private static final Map<BlockPos, Integer> GIVEN = new ConcurrentHashMap<>();
	private static boolean broken;
	private static int ticks;

	private ColorLightLamps() {
	}

	static void register() {
		ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, world) -> {
			if (blockEntity.getCachedState().isOf(Dyeing.DYED_REDSTONE_LAMP)) {
				LAMPS.add(blockEntity.getPos().toImmutable());
			}
		});
		ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, world) -> {
			LAMPS.remove(blockEntity.getPos());
			GIVEN.remove(blockEntity.getPos());
		});
		ClientTickEvents.END_CLIENT_TICK.register(ColorLightLamps::tick);
	}

	private static void tick(MinecraftClient client) {
		ClientWorld world = client.world;
		if (broken || world == null) {
			return;
		}
		boolean again = ++ticks % 20 == 0;
		for (Iterator<BlockPos> it = LAMPS.iterator(); it.hasNext(); ) {
			BlockPos pos = it.next();
			if (!(world.getBlockEntity(pos) instanceof Dyeing.DyedBlockEntity lamp) || !lamp.getCachedState().isOf(Dyeing.DYED_REDSTONE_LAMP)) {
				it.remove();
				GIVEN.remove(pos);
				continue;
			}
			int light = world.getBlockState(pos).getLuminance();
			if (light == 0 || !(lamp.getRenderData() instanceof Integer dyed)) {
				GIVEN.remove(pos);
				continue;
			}
			int color = brightest(dyed);
			if (again || !Integer.valueOf(color).equals(GIVEN.get(pos))) {
				give(pos, color, light);
			}
		}
	}

	private static void give(BlockPos pos, int color, int light) {
		try {
			ColorLightChunkScanner.runOnApplyThread(() -> {
				ColorLightEngine engine = ColorLightEngineHolder.get();
				// Not yet there (its scan of the chunk hasn't finished) or gone: tried again next tick.
				if (engine != null && engine.hasSource(pos)) {
					engine.addSource(pos, color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, light);
					GIVEN.put(pos, color);
				}
			});
		} catch (LinkageError e) {
			broken = true;
			EternalReturn.LOGGER.warn("ColorLight has changed: dyed lamps keep its light colour for them", e);
		}
	}

	/** A colour as bright as it goes with the same hue: its brightest channel full (white for black). */
	static int brightest(int color) {
		int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF;
		int max = Math.max(r, Math.max(g, b));
		if (max == 0) {
			return 0xFFFFFF;
		}
		return Math.round(r * 255.0F / max) << 16 | Math.round(g * 255.0F / max) << 8 | Math.round(b * 255.0F / max);
	}
}
