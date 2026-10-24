package com.eternalreturn.mixin.weather;

import com.eternalreturn.weather.LocalWeather;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.WeatherCommand;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.intprovider.IntProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * With storms (LocalWeather), /weather rain and /weather thunder make a storm where the command is run (at the world
 * spawn from another dimension or the console), and /weather clear clears every storm and keeps the sky clear for
 * its time.
 */
@Mixin(WeatherCommand.class)
public abstract class WeatherCommandMixin {
	@Unique
	private static int eternalreturn$duration(ServerCommandSource source, int duration, IntProvider provider) {
		return duration == -1 ? provider.get(source.getServer().getOverworld().getRandom()) : duration;
	}

	@Unique
	private static Vec3d eternalreturn$where(ServerCommandSource source, ServerWorld world) {
		return source.getWorld() == world ? source.getPosition() : Vec3d.ofBottomCenter(world.getSpawnPos());
	}

	@Unique
	private static boolean eternalreturn$storm(ServerCommandSource source, int duration, boolean thunder, IntProvider provider) {
		ServerWorld world = source.getServer().getOverworld();
		if (LocalWeather.field(world) == null) {
			return false;
		}
		LocalWeather.get(world).storm(world, eternalreturn$where(source, world), thunder, eternalreturn$duration(source, duration, provider));
		source.sendFeedback(() -> Text.translatable(thunder ? "commands.weather.set.thunder" : "commands.weather.set.rain"), true);
		return true;
	}

	@Inject(method = "executeRain", at = @At("HEAD"), cancellable = true)
	private static void eternalreturn$rainHere(ServerCommandSource source, int duration, CallbackInfoReturnable<Integer> cir) {
		if (eternalreturn$storm(source, duration, false, ServerWorld.RAIN_WEATHER_DURATION_PROVIDER)) {
			cir.setReturnValue(duration);
		}
	}

	@Inject(method = "executeThunder", at = @At("HEAD"), cancellable = true)
	private static void eternalreturn$thunderHere(ServerCommandSource source, int duration, CallbackInfoReturnable<Integer> cir) {
		if (eternalreturn$storm(source, duration, true, ServerWorld.THUNDER_WEATHER_DURATION_PROVIDER)) {
			cir.setReturnValue(duration);
		}
	}

	@Inject(method = "executeClear", at = @At("HEAD"), cancellable = true)
	private static void eternalreturn$clearAll(ServerCommandSource source, int duration, CallbackInfoReturnable<Integer> cir) {
		ServerWorld world = source.getServer().getOverworld();
		if (LocalWeather.field(world) == null) {
			return;
		}
		LocalWeather.get(world).clear(world, eternalreturn$duration(source, duration, ServerWorld.CLEAR_WEATHER_DURATION_PROVIDER));
		source.sendFeedback(() -> Text.translatable("commands.weather.set.clear"), true);
		cir.setReturnValue(duration);
	}
}
