package com.eternalreturn.worldgen.caves.types;

import com.eternalreturn.config.EternalReturnConfig.CaveTypeSettings;
import com.eternalreturn.worldgen.caves.CaveBuilder;
import com.eternalreturn.worldgen.caves.CaveType;
import com.eternalreturn.worldgen.caves.Tunnel;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * Tunnels whose width pulses along their length: the radius swings 35 to 55 percent above and below
 * its base every 5 to 9 blocks, leaving rings of rock between wide bulges. The path curves gently so
 * the rings line up. Radius is the base radius before the pulse.
 */
public final class RibbedCave implements CaveType {
	@Override
	public String id() {
		return "ribbed";
	}

	@Override
	public CaveTypeSettings defaults() {
		return new CaveTypeSettings(5.0, -50, 50, 2.0, 3.5);
	}

	@Override
	public double forcedWeight() {
		return 60.0;
	}

	@Override
	public Vec3d generate(CaveBuilder builder, Random random, double x, double y, double z, CaveTypeSettings settings) {
		double base = CaveBuilder.radius(random, settings);
		double pulse = 0.35 + random.nextDouble() * 0.2;
		double period = 5 + random.nextInt(5);
		double phase = random.nextDouble() * Math.PI * 2;
		int length = 70 + random.nextInt(60);
		Tunnel tunnel = new Tunnel(x, y, z, random.nextFloat() * CaveBuilder.TAU, (random.nextFloat() - 0.5F) * 0.3F);
		double step = 0.5;
		for (int i = 0; i * step < length; i++) {
			double along = i * step;
			double radius = base * (1.0 + pulse * Math.sin(Math.PI * 2 * along / period + phase)) * CaveBuilder.endTaper((int) along, length);
			tunnel.step(step);
			if (i % 2 == 0) {
				tunnel.wander(random, 0.85F, 0.05F, 1.5F, 2.5F);
			}
			tunnel.keepWithin(builder, radius);
			if (!builder.inReach(tunnel.x, tunnel.z, radius + 1)) {
				break;
			}
			builder.ellipsoid(tunnel.x, tunnel.y, tunnel.z, radius, radius * 0.85, radius, -0.75);
		}
		return new Vec3d(x, y, z);
	}
}
