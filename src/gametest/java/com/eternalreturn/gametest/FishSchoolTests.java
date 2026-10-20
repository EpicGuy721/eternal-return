package com.eternalreturn.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CodEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Fish schools (FishSchoolGoal): cod in open water hold together, swim the same way, keep apart and stay in the water. */
public class FishSchoolTests implements FabricGameTest {
	private static final String ARENA = "eternalreturn_test:arena";

	@GameTest(templateName = ARENA, batchId = "fish_schools", skyAccess = true, tickLimit = 300)
	public void codSchool(TestContext ctx) {
		for (int x = 0; x < 16; x++) {
			for (int y = 1; y <= 12; y++) {
				for (int z = 0; z < 40; z++) {
					ctx.setBlockState(x, y, z, Blocks.WATER.getDefaultState());
				}
			}
		}
		List<CodEntity> school = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			school.add(ctx.spawnEntity(EntityType.COD, new BlockPos(6 + i % 4, 5 + i / 4, 18 + i % 3)));
		}
		float[] sharpest = {0.0F};
		float[] last = new float[school.size()];
		ctx.addInstantFinalTask(ctx::killAllEntities);
		ctx.runAtEveryTick(() -> {
			for (int i = 0; i < school.size(); i++) {
				float yaw = school.get(i).bodyYaw;
				if (ctx.getTick() > 20) {
					sharpest[0] = Math.max(sharpest[0], Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(yaw - last[i])));
				}
				last[i] = yaw;
			}
		});
		ctx.runAtTick(160, () -> {
			ctx.assertTrue(sharpest[0] <= 15.0F, "they turn smoothly (degrees a tick at most): " + sharpest[0]);
			Vec3d centre = school.stream().map(CodEntity::getPos).reduce(Vec3d.ZERO, Vec3d::add).multiply(1.0 / school.size());
			double spread = school.stream().mapToDouble(fish -> fish.getPos().distanceTo(centre)).max().orElse(0);
			Vec3d heading = school.stream().map(fish -> fish.getVelocity().lengthSquared() > 1.0E-8 ? fish.getVelocity().normalize() : Vec3d.ZERO)
					.reduce(Vec3d.ZERO, Vec3d::add).multiply(1.0 / school.size());
			double closest = Double.MAX_VALUE;
			for (int a = 0; a < school.size(); a++) {
				for (int b = a + 1; b < school.size(); b++) {
					closest = Math.min(closest, school.get(a).distanceTo(school.get(b)));
				}
			}
			ctx.assertTrue(school.stream().allMatch(fish -> fish.isSubmergedIn(FluidTags.WATER)), "all still under water");
			ctx.assertTrue(spread < 7.0, "the school holds together: " + spread);
			ctx.assertTrue(heading.length() > 0.4, "it swims the same way: " + heading.length());
			ctx.assertTrue(closest > 0.15, "no two fish on top of each other: " + closest);
			ctx.complete();
		});
	}

	/** A survival player swims up to a school: it moves off, away from the player, as a school. */
	@GameTest(templateName = ARENA, batchId = "fish_schools", skyAccess = true, tickLimit = 300)
	public void schoolAvoidsPlayers(TestContext ctx) {
		for (int x = 0; x < 16; x++) {
			for (int y = 1; y <= 12; y++) {
				for (int z = 0; z < 40; z++) {
					ctx.setBlockState(x, y, z, Blocks.WATER.getDefaultState());
				}
			}
		}
		List<CodEntity> school = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			school.add(ctx.spawnEntity(EntityType.COD, new BlockPos(6 + i % 4, 5 + i / 4, 18 + i % 3)));
		}
		net.minecraft.server.network.ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(7, 5, 15));
		ctx.addInstantFinalTask(() -> {
			FishingTests.disconnect(player);
			ctx.killAllEntities();
		});
		Vec3d[] start = new Vec3d[1];
		ctx.runAtTick(25, () -> start[0] = school.stream().map(CodEntity::getPos).reduce(Vec3d.ZERO, Vec3d::add).multiply(1.0 / school.size()));
		ctx.runAtTick(120, () -> {
			Vec3d centre = school.stream().map(CodEntity::getPos).reduce(Vec3d.ZERO, Vec3d::add).multiply(1.0 / school.size());
			ctx.assertTrue(centre.distanceTo(player.getPos()) > start[0].distanceTo(player.getPos()) + 2.0,
					"the school moved off: " + start[0].distanceTo(player.getPos()) + " -> " + centre.distanceTo(player.getPos()));
			ctx.complete();
		});
	}
}
