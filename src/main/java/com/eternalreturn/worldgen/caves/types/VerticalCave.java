package com.eternalreturn.worldgen.caves.types;

import com.eternalreturn.config.EternalReturnConfig.CaveTypeSettings;
import com.eternalreturn.worldgen.caves.CaveBuilder;
import com.eternalreturn.worldgen.caves.CaveType;
import com.eternalreturn.worldgen.caves.Tunnel;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * Steep connections between levels. Three in five are shafts: a near-vertical pipe dropping 20 to 60
 * blocks with a slight drift and a wall that bulges and narrows. The rest are steep tunnels sloping
 * down at 50 to 75 degrees. Short side tunnels leave the top and the bottom so the shaft joins the
 * caves around it. The start y is the top. Radius is the shaft's radius.
 */
public final class VerticalCave implements CaveType {
	@Override
	public String id() {
		return "vertical";
	}

	@Override
	public CaveTypeSettings defaults() {
		return new CaveTypeSettings(8.0, -20, 60, 1.5, 3.5);
	}

	@Override
	public double forcedWeight() {
		return 60.0;
	}

	@Override
	public void generate(CaveBuilder builder, Random random, double x, double y, double z, CaveTypeSettings settings) {
		double radius = CaveBuilder.radius(random, settings);
		int depth = 20 + random.nextInt(41);
		double[] bottom = random.nextInt(5) < 3 ? shaft(builder, random, x, y, z, radius, depth) : steep(builder, random, x, y, z, radius, depth);
		stubs(builder, random, x, y, z, radius);
		stubs(builder, random, bottom[0], bottom[1], bottom[2], radius);
	}

	private static double[] shaft(CaveBuilder builder, Random random, double x, double y, double z, double radius, int depth) {
		double driftX = (random.nextDouble() - 0.5) * 0.3;
		double driftZ = (random.nextDouble() - 0.5) * 0.3;
		double phase = random.nextDouble() * Math.PI * 2;
		double wobbleRate = 0.25 + random.nextDouble() * 0.3;
		double shaftX = x;
		double shaftY = y;
		double shaftZ = z;
		for (int i = 0; i < depth; i++) {
			shaftY = y - i;
			if (shaftY < builder.minY() + 4) {
				break;
			}
			shaftX += driftX + (random.nextDouble() - 0.5) * 0.2;
			shaftZ += driftZ + (random.nextDouble() - 0.5) * 0.2;
			double r = radius * (1.0 + 0.2 * Math.sin(i * wobbleRate + phase));
			if (!builder.inReach(shaftX, shaftZ, r + 1)) {
				break;
			}
			builder.ellipsoid(shaftX, shaftY, shaftZ, r, 1.6, r, -1.0);
		}
		return new double[]{shaftX, shaftY, shaftZ};
	}

	private static double[] steep(CaveBuilder builder, Random random, double x, double y, double z, double radius, int depth) {
		float slope = -(0.9F + random.nextFloat() * 0.4F);
		Tunnel tunnel = new Tunnel(x, y, z, random.nextFloat() * CaveBuilder.TAU, slope);
		double r = Math.max(1.4, radius * 0.8);
		int length = (int) (depth / Math.abs(MathHelper.sin(slope)));
		for (int i = 0; i < length; i++) {
			tunnel.step(1.0);
			tunnel.pitch = tunnel.pitch * 0.85F + slope * 0.15F + (random.nextFloat() - random.nextFloat()) * 0.1F;
			tunnel.yaw += (random.nextFloat() - random.nextFloat()) * 0.25F;
			if (tunnel.y < builder.minY() + 4 || !builder.inReach(tunnel.x, tunnel.z, r + 1)) {
				break;
			}
			builder.ellipsoid(tunnel.x, tunnel.y, tunnel.z, r, r, r, -1.0);
		}
		return new double[]{tunnel.x, tunnel.y, tunnel.z};
	}

	/** One or two short, nearly level tunnels leaving a point. */
	private static void stubs(CaveBuilder builder, Random random, double x, double y, double z, double radius) {
		int count = 1 + random.nextInt(2);
		for (int i = 0; i < count; i++) {
			Tunnel tunnel = new Tunnel(x, y, z, random.nextFloat() * CaveBuilder.TAU, (random.nextFloat() - 0.5F) * 0.2F);
			SpaghettiCave.tunnel(builder, random, tunnel, Math.max(1.3, radius * 0.7), 12 + random.nextInt(17), false);
		}
	}
}
