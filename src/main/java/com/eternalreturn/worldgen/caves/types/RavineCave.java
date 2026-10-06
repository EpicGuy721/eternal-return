package com.eternalreturn.worldgen.caves.types;

import com.eternalreturn.config.EternalReturnConfig.CaveTypeSettings;
import com.eternalreturn.worldgen.caves.CaveBuilder;
import com.eternalreturn.worldgen.caves.CaveType;
import com.eternalreturn.worldgen.caves.Tunnel;
import net.minecraft.util.math.random.Random;

/**
 * Release 1.6.4-style ravines: long, narrow, tall canyons with near-vertical, jagged walls (every
 * few blocks of height the width changes at random). One in four is a large ravine: wider, taller and
 * twice as long, grown both ways from its start. Shallow ones cut up through the surface.
 * Radius is the half-width at the widest point (large ravines use 1.5 times it).
 */
public final class RavineCave implements CaveType {
	@Override
	public String id() {
		return "ravine";
	}

	@Override
	public CaveTypeSettings defaults() {
		return new CaveTypeSettings(2.0, -30, 45, 2.5, 4.5);
	}

	@Override
	public double forcedWeight() {
		return 10.0;
	}

	@Override
	public void generate(CaveBuilder builder, Random random, double x, double y, double z, CaveTypeSettings settings) {
		boolean large = random.nextInt(4) == 0;
		double width = CaveBuilder.radius(random, settings) * (large ? 1.5 : 1.0);
		double heightScale = large ? 4.0 : 3.0;
		float yaw = random.nextFloat() * CaveBuilder.TAU;
		float pitch = (random.nextFloat() - 0.5F) * 0.25F;
		float[] walls = jaggedWalls(random, builder);
		if (large) {
			int half = 70 + random.nextInt(40);
			walk(builder, random, new Tunnel(x, y, z, yaw, pitch), half, width, heightScale, walls, true);
			walk(builder, random, new Tunnel(x, y, z, yaw + CaveBuilder.TAU / 2, -pitch), half, width, heightScale, walls, true);
		} else {
			walk(builder, random, new Tunnel(x, y, z, yaw, pitch), 80 + random.nextInt(40), width, heightScale, walls, false);
		}
	}

	/** Release 1.6.4: per y level, a width factor that changes every one to three blocks of height. */
	private static float[] jaggedWalls(Random random, CaveBuilder builder) {
		float[] walls = new float[builder.maxY() - builder.minY() + 8];
		float factor = 1.0F;
		for (int i = 0; i < walls.length; i++) {
			if (i == 0 || random.nextInt(3) == 0) {
				factor = 1.0F + random.nextFloat() * random.nextFloat();
			}
			walls[i] = factor * factor;
		}
		return walls;
	}

	/**
	 * fromMiddle: the walk starts at the ravine's widest point and narrows to its end (large ravines
	 * walk out both ways from the middle); otherwise it widens and narrows again along its length.
	 */
	private static void walk(CaveBuilder builder, Random random, Tunnel tunnel, int length, double width, double heightScale, float[] walls, boolean fromMiddle) {
		int wallBase = builder.minY() - 4;
		for (int i = 0; i < length; i++) {
			double along = fromMiddle ? Math.cos(Math.PI / 2 * i / length) : Math.sin(Math.PI * i / length);
			double horizontal = 1.5 + along * width;
			double vertical = horizontal * heightScale;
			horizontal *= random.nextFloat() * 0.25F + 0.75F;
			vertical *= random.nextFloat() * 0.25F + 0.75F;
			tunnel.step(1.0);
			tunnel.wander(random, 0.7F, 0.05F, 2.0F, 4.0F);
			tunnel.y = builder.clampY(tunnel.y, 2.0);
			if (!builder.inReach(tunnel.x, tunnel.z, horizontal + 1)) {
				return;
			}
			// Release 1.6.4 skipped a quarter of its steps, which roughens the walls further.
			if (random.nextInt(4) == 0) {
				continue;
			}
			builder.shape(tunnel.x, tunnel.y, tunnel.z, horizontal, vertical, horizontal, true, (dx, dy, dz, y) -> {
				int index = y - wallBase;
				float wall = index >= 0 && index < walls.length ? walls[index] : 1.0F;
				return (dx * dx + dz * dz) * wall + dy * dy / 6.0 < 1.0;
			});
		}
	}
}
