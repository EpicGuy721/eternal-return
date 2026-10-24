package com.eternalreturn.weather;

import com.eternalreturn.EternalReturn;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

import java.util.List;

/**
 * The storms near a player and the wind (LocalWeather), sent to their game every second; active is false where the
 * weather is vanilla's (local weather off, or another dimension).
 */
public record WeatherPayload(boolean active, float windAngle, float windSpeed, List<Storm> storms) implements CustomPayload {
	public static final CustomPayload.Id<WeatherPayload> ID = new CustomPayload.Id<>(EternalReturn.id("weather"));
	public static final PacketCodec<RegistryByteBuf, WeatherPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOL, WeatherPayload::active,
			PacketCodecs.FLOAT, WeatherPayload::windAngle,
			PacketCodecs.FLOAT, WeatherPayload::windSpeed,
			Storm.CODEC.collect(PacketCodecs.toList()), WeatherPayload::storms,
			WeatherPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
