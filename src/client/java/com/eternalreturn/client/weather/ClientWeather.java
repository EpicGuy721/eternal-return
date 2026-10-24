package com.eternalreturn.client.weather;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.weather.LocalWeather;
import com.eternalreturn.weather.StormField;
import com.eternalreturn.weather.WeatherPayload;
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
 * out), how much of a blizzard blows, and how far the clouds have drifted. Inactive (vanilla's weather) until the server
 * says otherwise.
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

	private ClientWeather() {
	}

	public static void register() {
		LocalWeather.clientField = world -> active && world == MinecraftClient.getInstance().world ? FIELD : null;
		ClientPlayNetworking.registerGlobalReceiver(WeatherPayload.ID, (payload, context) -> {
			active = payload.active();
			FIELD.windAngle = payload.windAngle();
			FIELD.windSpeed = payload.windSpeed();
			FIELD.storms.clear();
			FIELD.storms.addAll(payload.storms());
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			active = false;
			FIELD.storms.clear();
			blizzard = 0.0F;
		});
		ClientTickEvents.END_CLIENT_TICK.register(ClientWeather::tick);
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
		prevCloudX = cloudX;
		prevCloudZ = cloudZ;
		cloudX += FIELD.windX();
		cloudZ += FIELD.windZ();
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
