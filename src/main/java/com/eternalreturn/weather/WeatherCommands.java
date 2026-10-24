package com.eternalreturn.weather;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;

/**
 * Commands for local weather (LocalWeather): /eternalreturn weather lists the storms near the player and the wind; and
 * /weather, rerouted by WeatherCommandMixin, makes or clears storms.
 */
public final class WeatherCommands {
	private static final String[] COMPASS = {"east", "south_east", "south", "south_west", "west", "north_west", "north", "north_east"};

	private WeatherCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(CommandManager.literal("eternalreturn")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.literal("weather").executes(context -> list(context.getSource())))));
	}

	/** A compass point for a direction given as east and south components. */
	private static Text compass(double east, double south) {
		int point = Math.floorMod(Math.round((float) (Math.atan2(south, east) / (Math.PI / 4.0))), 8);
		return Text.translatable("commands.eternalreturn.weather.compass." + COMPASS[point]);
	}

	private static int list(ServerCommandSource source) {
		ServerWorld world = source.getServer().getOverworld();
		if (!LocalWeather.enabled() || source.getWorld() != world) {
			source.sendError(Text.translatable("commands.eternalreturn.weather.off"));
			return 0;
		}
		LocalWeather weather = LocalWeather.get(world);
		Vec3d at = source.getPosition();
		StormField field = weather.field;
		source.sendFeedback(() -> Text.translatable("commands.eternalreturn.weather.wind", String.format("%.1f", field.windSpeed * 20.0F),
				compass(field.windX(), field.windZ())), false);
		List<Storm> near = field.storms.stream().filter(storm -> MathHelper.square(storm.x - at.x) + MathHelper.square(storm.z - at.z) < 4.0 * LocalWeather.NEAR
				* LocalWeather.NEAR).sorted(Comparator.comparingDouble(storm -> MathHelper.square(storm.x - at.x) + MathHelper.square(storm.z - at.z))).toList();
		if (near.isEmpty()) {
			source.sendFeedback(() -> Text.translatable("commands.eternalreturn.weather.none"), false);
		}
		for (Storm storm : near) {
			int distance = (int) Math.sqrt(MathHelper.square(storm.x - at.x) + MathHelper.square(storm.z - at.z));
			String kind = storm.thunder ? "thunderstorm" : "storm";
			Text where = storm.cover(at.x, at.z, StormField.RAIN_EDGE) > StormField.RAINING ? Text.translatable("commands.eternalreturn.weather.here")
					: Text.translatable("commands.eternalreturn.weather.away", distance, compass(storm.x - at.x, storm.z - at.z));
			source.sendFeedback(() -> Text.translatable("commands.eternalreturn.weather." + kind, (int) (storm.radius * 2), Math.round(storm.intensity * 100),
					where), false);
		}
		return near.size();
	}
}
