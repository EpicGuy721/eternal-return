package com.eternalreturn.weather;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.math.MathHelper;

/**
 * One storm (LocalWeather): a patch of rain (or snow, by the biome) a few hundred blocks across, with a ragged edge,
 * that drifts with the wind. It builds up over its first minute, holds, and dies away over its last; over dry country it
 * weakens and dies sooner. A thunderstorm has lightning in it, and over snow it is a blizzard.
 */
public final class Storm {
	/** Ticks to build up, and to die away. */
	public static final int GROW = 1200;

	public final int id;
	public double x;
	public double z;
	public final float radius;
	public final boolean thunder;
	public final long seed;
	public int age;
	public final int life;
	/** How strong it is now, 0 to 1. */
	public float intensity;
	/** How strong it can get, 0 to 1. */
	public final float peak;
	/** How rainy the country under it is (0 dry to 1 wet), as last looked at. */
	public float climate = 1.0F;

	public Storm(int id, double x, double z, float radius, boolean thunder, long seed, int age, int life, float intensity, float peak) {
		this.id = id;
		this.x = x;
		this.z = z;
		this.radius = radius;
		this.thunder = thunder;
		this.seed = seed;
		this.age = age;
		this.life = life;
		this.intensity = intensity;
		this.peak = peak;
	}

	/** The edge's distance from the middle in this direction: the radius, pushed in and out a little. */
	public double edge(double angle) {
		double a = (this.seed & 0xFFFF) / 65536.0 * MathHelper.TAU;
		double b = (this.seed >>> 16 & 0xFFFF) / 65536.0 * MathHelper.TAU;
		double c = (this.seed >>> 32 & 0xFFFF) / 65536.0 * MathHelper.TAU;
		return this.radius * (1.0 + 0.14 * Math.sin(3.0 * angle + a) + 0.09 * Math.sin(5.0 * angle + b) + 0.06 * Math.sin(2.0 * angle + c));
	}

	/**
	 * How much of this storm covers a spot: its full intensity in its core (the inner six tenths), then easing off
	 * gradually through its edge to nothing band blocks beyond it, so rain thins out rather than stopping at a line.
	 */
	public float cover(double px, double pz, double band) {
		double dx = px - this.x;
		double dz = pz - this.z;
		double distanceSq = dx * dx + dz * dz;
		double reach = this.radius * 1.3 + band;
		if (distanceSq > reach * reach) {
			return 0.0F;
		}
		double distance = Math.sqrt(distanceSq);
		double edge = this.edge(Math.atan2(dz, dx));
		double core = edge * 0.6;
		if (distance <= core) {
			return this.intensity;
		}
		double outside = edge + band;
		if (distance >= outside) {
			return 0.0F;
		}
		double t = (distance - core) / (outside - core);
		return (float) (this.intensity * (1.0 - t * t * (3.0 - 2.0 * t)));
	}

	/** Where its strength is in its life (building, holding, dying away), times how much the country lets it rain. */
	public void age(int ticks) {
		this.age += ticks;
		float strength = this.peak * (0.3F + 0.7F * this.climate);
		float building = MathHelper.clamp(this.age / (float) GROW, 0.0F, 1.0F);
		float dying = MathHelper.clamp((this.life - this.age) / (float) GROW, 0.0F, 1.0F);
		this.intensity = strength * Math.min(building, dying);
	}

	public boolean over() {
		return this.age >= this.life;
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("id", this.id);
		nbt.putDouble("x", this.x);
		nbt.putDouble("z", this.z);
		nbt.putFloat("radius", this.radius);
		nbt.putBoolean("thunder", this.thunder);
		nbt.putLong("seed", this.seed);
		nbt.putInt("age", this.age);
		nbt.putInt("life", this.life);
		nbt.putFloat("intensity", this.intensity);
		nbt.putFloat("peak", this.peak);
		nbt.putFloat("climate", this.climate);
		return nbt;
	}

	public static Storm fromNbt(NbtCompound nbt) {
		Storm storm = new Storm(nbt.getInt("id"), nbt.getDouble("x"), nbt.getDouble("z"), nbt.getFloat("radius"), nbt.getBoolean("thunder"),
				nbt.getLong("seed"), nbt.getInt("age"), nbt.getInt("life"), nbt.getFloat("intensity"), nbt.getFloat("peak"));
		storm.climate = nbt.contains("climate") ? nbt.getFloat("climate") : 1.0F;
		return storm;
	}

	/** What a player's game needs: where it is, how big and strong, and its shape. */
	public static final PacketCodec<RegistryByteBuf, Storm> CODEC = PacketCodec.of((storm, buf) -> {
		buf.writeVarInt(storm.id);
		buf.writeDouble(storm.x);
		buf.writeDouble(storm.z);
		buf.writeFloat(storm.radius);
		buf.writeBoolean(storm.thunder);
		buf.writeLong(storm.seed);
		buf.writeFloat(storm.intensity);
	}, buf -> new Storm(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readBoolean(), buf.readLong(), Storm.GROW, Integer.MAX_VALUE,
			buf.readFloat(), 1.0F));
}
