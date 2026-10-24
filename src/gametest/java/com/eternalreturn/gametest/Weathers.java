package com.eternalreturn.gametest;

import com.eternalreturn.weather.LocalWeather;
import com.eternalreturn.weather.Storm;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Weather for tests: rain over a spot, at full strength at once. With local weather (LocalWeather) that is a storm 128
 * blocks across over the spot, taken away again by dry(); without it, vanilla's rain everywhere.
 */
final class Weathers {
	private Weathers() {
	}

	@Nullable
	static Storm rain(ServerWorld world, Vec3d at, boolean thunder) {
		if (LocalWeather.field(world) == null) {
			world.setWeather(0, 6000, true, thunder);
			// Vanilla's rain fades in over seconds; start it at full.
			world.setRainGradient(1.0F);
			world.setThunderGradient(thunder ? 1.0F : 0.0F);
			return null;
		}
		Storm storm = LocalWeather.get(world).add(at.x, at.z, 64.0F, thunder, Storm.GROW, Storm.GROW * 100, 1.0F, world.random);
		storm.climate = 1.0F;
		storm.age(0);
		return storm;
	}

	static void dry(ServerWorld world, @Nullable Storm storm) {
		if (LocalWeather.field(world) == null) {
			world.setWeather(6000, 0, false, false);
			world.setRainGradient(0.0F);
			world.setThunderGradient(0.0F);
			return;
		}
		if (storm != null) {
			LocalWeather.get(world).field.storms.remove(storm);
		}
	}
}
