package com.eternalreturn.worldgen.caves.types;

import com.eternalreturn.config.EternalReturnConfig.CaveTypeSettings;
import com.eternalreturn.worldgen.caves.CaveBuilder;
import com.eternalreturn.worldgen.caves.CaveType;
import com.eternalreturn.worldgen.caves.Tunnel;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * Big irregular caverns: a flattened, stretched main chamber with lobes of different sizes and heights
 * around it (which makes uneven walls, alcoves and a lumpy ceiling), a mostly flat floor, and two to
 * five winding tunnels leading out from its sides. Radius is the main chamber's horizontal radius.
 */
public final class LargeCave implements CaveType {
	@Override
	public String id() {
		return "large";
	}

	@Override
	public CaveTypeSettings defaults() {
		return new CaveTypeSettings(1.0, -48, 15, 10.0, 25.0);
	}

	@Override
	public double forcedWeight() {
		return 8.0;
	}

	@Override
	public void generate(CaveBuilder builder, Random random, double x, double y, double z, CaveTypeSettings settings) {
		double radius = CaveBuilder.radius(random, settings);
		double vertical = radius * (0.45 + random.nextDouble() * 0.2);
		double stretchX = 0.75 + random.nextDouble() * 0.5;
		double stretchZ = 0.75 + random.nextDouble() * 0.5;
		double centreY = builder.clampY(y, vertical);
		builder.ellipsoid(x, centreY, z, radius * stretchX, vertical, radius * stretchZ, -0.55);

		int lobes = 5 + random.nextInt(6);
		for (int i = 0; i < lobes; i++) {
			float angle = random.nextFloat() * CaveBuilder.TAU;
			double distance = radius * (0.35 + random.nextDouble() * 0.5);
			double lobeRadius = radius * (0.3 + random.nextDouble() * 0.35);
			double lobeVertical = lobeRadius * (0.5 + random.nextDouble() * 0.3);
			double lobeX = x + MathHelper.cos(angle) * distance * stretchX;
			double lobeZ = z + MathHelper.sin(angle) * distance * stretchZ;
			double lobeY = builder.clampY(centreY + (random.nextDouble() - 0.4) * vertical * 0.8, lobeVertical);
			builder.ellipsoid(lobeX, lobeY, lobeZ, lobeRadius, lobeVertical, lobeRadius * (0.8 + random.nextDouble() * 0.4), -0.5);
		}

		int exits = 2 + random.nextInt(4);
		for (int i = 0; i < exits; i++) {
			float angle = random.nextFloat() * CaveBuilder.TAU;
			double exitX = x + MathHelper.cos(angle) * radius * stretchX * 0.8;
			double exitZ = z + MathHelper.sin(angle) * radius * stretchZ * 0.8;
			double exitY = centreY - vertical * 0.3 + random.nextDouble() * vertical * 0.5;
			Tunnel tunnel = new Tunnel(exitX, exitY, exitZ, angle + (random.nextFloat() - 0.5F) * 0.6F, (random.nextFloat() - 0.5F) * 0.4F);
			SpaghettiCave.tunnel(builder, random, tunnel, 1.5 + random.nextDouble() * 1.3, 40 + random.nextInt(50), false);
		}
	}
}
