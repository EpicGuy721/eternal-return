package com.eternalreturn.client;

import com.eternalreturn.EternalReturn;
import me.mrhikmen.colorlight.client.core.light.engine.ColorLightEngine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.LightType;

/**
 * How far daylight takes the colour out of ColorLight's light, for the whole view at once: 1 outdoors at noon, 0 at
 * night, in a cave or in a dimension without sky. ColorLight (an optional client mod) decides per block, from how much
 * of a block's brightness its light gives: by day a block one sky level short of full daylight gets the whole colour
 * and the open block next to it none, in hard-edged patches (with Nostalgic Tweaks' old lighting a cave by day and
 * shade by day even look the same to it). One value for the view can't make patches: it follows the time of day and how
 * much sky the camera gets, eased over about half a second, so walking into a cave or nightfall brings the colour in
 * gently. ColorLightHeaderMixin hands it to ColorLight's shader, ColorLightDaylightTintMixin uses it there.
 */
public final class ColorLightDaylight {
	/** Seconds for the value to get most of the way to where it is heading. */
	private static final double EASE_SECONDS = 0.5;
	private static float value;
	private static long last;
	private static boolean broken;

	private ColorLightDaylight() {
	}

	public static float value() {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = client.world;
		long now = System.nanoTime();
		float target = 0.0F;
		if (world != null && !broken) {
			try {
				float day = ColorLightEngine.computeTimeOfDayFactor(world);
				float sky = world.getLightLevel(LightType.SKY, client.gameRenderer.getCamera().getBlockPos()) / 15.0F;
				target = day * sky;
			} catch (LinkageError e) {
				broken = true;
				EternalReturn.LOGGER.warn("ColorLight has changed: its colours by day are left as ColorLight draws them", e);
			}
		}
		double seconds = last == 0 ? EASE_SECONDS * 10 : (now - last) / 1.0e9;
		last = now;
		value += (float) ((target - value) * (1.0 - Math.exp(-seconds / EASE_SECONDS)));
		return value;
	}
}
