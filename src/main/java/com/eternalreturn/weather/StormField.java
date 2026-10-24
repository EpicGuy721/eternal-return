package com.eternalreturn.weather;

import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * The storms over a world and the wind that carries them (LocalWeather), as the server keeps them and as each
 * player's game is told of them, so both agree where it rains.
 */
public class StormField {
	/** Rain fades out over this many blocks beyond a storm's edge. */
	public static final double RAIN_EDGE = 16.0;
	/** The sky darkens from this far beyond a storm's edge, so it can be seen coming. */
	public static final double DARK_EDGE = 160.0;
	/** It rains where a storm covers more than this. */
	public static final float RAINING = 0.2F;

	public final List<Storm> storms = new ArrayList<>();
	/** The way the wind blows to, in radians (0 east, a quarter turn south), and how fast, in blocks a tick. */
	public float windAngle;
	public float windSpeed = 0.08F;

	public double windX() {
		return MathHelper.cos(this.windAngle) * this.windSpeed;
	}

	public double windZ() {
		return MathHelper.sin(this.windAngle) * this.windSpeed;
	}

	/** Each storm moves with the wind. */
	public void drift(int ticks) {
		double dx = this.windX() * ticks;
		double dz = this.windZ() * ticks;
		for (Storm storm : this.storms) {
			storm.x += dx;
			storm.z += dz;
		}
	}

	private float cover(double x, double z, double band, boolean thunderOnly) {
		float cover = 0.0F;
		for (Storm storm : this.storms) {
			if (!thunderOnly || storm.thunder) {
				cover = Math.max(cover, storm.cover(x, z, band));
			}
		}
		return cover;
	}

	/** How hard it rains (or snows) here, 0 to 1, before the biome and the sky have their say. */
	public float rain(double x, double z) {
		return this.cover(x, z, RAIN_EDGE, false);
	}

	/** How much of a thunderstorm is here, 0 to 1. */
	public float thunder(double x, double z) {
		return this.cover(x, z, RAIN_EDGE, true);
	}

	/** How dark the sky is here: storms darken it from well beyond their edge. */
	public float darkness(double x, double z) {
		return this.cover(x, z, DARK_EDGE, false);
	}

	/** How dark the sky is with thunder here. */
	public float thunderDarkness(double x, double z) {
		return this.cover(x, z, DARK_EDGE, true);
	}

	public boolean raining(double x, double z) {
		return this.rain(x, z) > RAINING;
	}

	public boolean thundering(double x, double z) {
		return this.thunder(x, z) > 0.5F;
	}
}
