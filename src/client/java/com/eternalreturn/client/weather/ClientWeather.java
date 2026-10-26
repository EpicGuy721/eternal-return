package com.eternalreturn.client.weather;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.weather.LocalWeather;
import com.eternalreturn.weather.Storm;
import com.eternalreturn.weather.StormField;
import com.eternalreturn.weather.WeatherPayload;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * The storms a player's game knows of (LocalWeather, sent each second, drifted with the wind between), and what they
 * make of the sky where the camera is: the rain and thunder darkness vanilla's sky, fog and clouds use (eased in and
 * out), how much of a blizzard blows, and how far the clouds have drifted. Each storm's strength and the wind the rain
 * and clouds show ease toward what each second's news says, so nothing jumps when it comes; a storm the server no
 * longer sends fades out. Inactive (vanilla's weather) until the server says otherwise.
 */
public final class ClientWeather {
	private static final StormField FIELD = new StormField();
	/** The clouds go round every 2,048 cloud cells of 12 blocks. */
	private static final double CLOUD_LOOP = 2048.0 * 12.0;
	private static boolean active;
	private static float blizzard;
	private static float prevBlizzard;
	private static double cloudX;
	private static double cloudZ;
	private static double prevCloudX;
	private static double prevCloudZ;
	/** How strong each storm is to be (by id), as the server last said; its strength eases there. */
	private static final Int2FloatOpenHashMap STRENGTHS = new Int2FloatOpenHashMap();
	/** The wind the rain, snow and clouds show, in blocks a tick: eased toward the server's. */
	private static double windX;
	private static double windZ;
	private static double prevWindX;
	private static double prevWindZ;
	/** How far the snow has fallen, in steps of a 512th of its texture (a tick's fall out of a blizzard). */
	private static double snowFall;
	private static double prevSnowFall;

	private ClientWeather() {
	}

	public static void register() {
		LocalWeather.clientField = world -> active && world == MinecraftClient.getInstance().world ? FIELD : null;
		ClientPlayNetworking.registerGlobalReceiver(WeatherPayload.ID, (payload, context) -> {
			active = payload.active();
			FIELD.windAngle = payload.windAngle();
			FIELD.windSpeed = payload.windSpeed();
			// A storm already known keeps the strength it has here and eases to the new one; one no longer sent fades out.
			STRENGTHS.clear();
			for (Storm storm : payload.storms()) {
				STRENGTHS.put(storm.id, storm.intensity);
				for (Storm known : FIELD.storms) {
					if (known.id == storm.id) {
						storm.intensity = known.intensity;
						break;
					}
				}
			}
			FIELD.storms.removeIf(known -> STRENGTHS.containsKey(known.id));
			FIELD.storms.addAll(payload.storms());
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			active = false;
			FIELD.storms.clear();
			STRENGTHS.clear();
			blizzard = 0.0F;
		});
		ClientTickEvents.END_CLIENT_TICK.register(ClientWeather::tick);
		SnowTextures.register();
	}

	/** Whether the weather here is storms (and the overworld). */
	public static boolean active(World world) {
		return active && world != null && world.getRegistryKey() == World.OVERWORLD;
	}

	public static StormField field() {
		return FIELD;
	}

	private static void tick(MinecraftClient client) {
		ClientWorld world = client.world;
		if (!active(world) || client.isPaused()) {
			return;
		}
		FIELD.drift(1);
		for (Storm storm : FIELD.storms) {
			float strength = STRENGTHS.getOrDefault(storm.id, 0.0F);
			storm.intensity += MathHelper.clamp(strength - storm.intensity, -0.01F, 0.01F);
		}
		FIELD.storms.removeIf(storm -> storm.intensity <= 0.0F && !STRENGTHS.containsKey(storm.id));
		prevWindX = windX;
		prevWindZ = windZ;
		windX += (FIELD.windX() - windX) * 0.05;
		windZ += (FIELD.windZ() - windZ) * 0.05;
		prevCloudX = cloudX;
		prevCloudZ = cloudZ;
		cloudX += windX;
		cloudZ += windZ;
		if (Math.abs(cloudX) > CLOUD_LOOP || Math.abs(cloudZ) > CLOUD_LOOP) {
			double shiftX = Math.floor(cloudX / CLOUD_LOOP) * CLOUD_LOOP;
			double shiftZ = Math.floor(cloudZ / CLOUD_LOOP) * CLOUD_LOOP;
			cloudX -= shiftX;
			prevCloudX -= shiftX;
			cloudZ -= shiftZ;
			prevCloudZ -= shiftZ;
		}
		Vec3d at = client.gameRenderer.getCamera().getPos();
		// The sky darkens as a storm comes near, and more with thunder; eased as vanilla eases its weather.
		float rain = world.getRainGradient(1.0F);
		float thunder = world.getThunderGradient(1.0F);
		world.setRainGradient(rain + MathHelper.clamp(FIELD.darkness(at.x, at.z) - rain, -0.01F, 0.01F));
		world.setThunderGradient(thunder + MathHelper.clamp(FIELD.thunderDarkness(at.x, at.z) - thunder, -0.01F, 0.01F));
		BlockPos pos = BlockPos.ofFloored(at);
		boolean snowing = world.getBiome(pos).value().getPrecipitation(pos) == Biome.Precipitation.SNOW;
		float target = snowing && EternalReturnConfig.get().weather.blizzards ? FIELD.thunder(at.x, at.z) : 0.0F;
		prevBlizzard = blizzard;
		blizzard += MathHelper.clamp(target - blizzard, -0.02F, 0.02F);
		// Snow falls faster in a blizzard (added up tick by tick, so a change of pace never jumps it).
		prevSnowFall = snowFall;
		snowFall += 1.0 + 4.0 * blizzard;
	}

	/** How far the snow has fallen, in 512ths of its texture. */
	public static double snowFall(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevSnowFall, snowFall);
	}

	/** The wind the rain and snow show, east and south, in blocks a tick. */
	public static double windX(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevWindX, windX);
	}

	public static double windZ(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevWindZ, windZ);
	}

	/** How much of a blizzard blows at the camera, 0 to 1. */
	public static float blizzard(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevBlizzard, blizzard);
	}

	/** How far the wind has carried the clouds, east and south, in blocks. */
	public static double cloudX(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevCloudX, cloudX);
	}

	public static double cloudZ(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevCloudZ, cloudZ);
	}
}
