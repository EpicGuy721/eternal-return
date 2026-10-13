package com.eternalreturn.mobs;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.survival.BeaconZones;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

/**
 * Pillagers in boats: now and then (mobs.pillagerBoatChance, checked once a minute for each player in an ocean
 * biome; never on Peaceful or inside a beacon's area) a dark oak boat with two pillagers turns up on the water
 * 24 to 40 blocks away. The front pillager steers: toward whoever it or its partner is after, or the nearest
 * player within 48 blocks, slowing to a stop about 6 blocks off so they can shoot. They don't break out of their
 * boat like other monsters do, and a boat left empty (its pillagers dead or despawned) sinks away after 5
 * seconds.
 */
public final class PillagerBoats {
	public static final String TAG = "eternalreturn_pillager_boat";
	private static final TagKey<Biome> OCEANS = TagKey.of(RegistryKeys.BIOME, EternalReturn.id("ocean_waters"));
	private static final int CHECK_TICKS = 1200;

	private PillagerBoats() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			if (world.getTime() % CHECK_TICKS != 0 || world.getDifficulty() == Difficulty.PEACEFUL) {
				return;
			}
			double chance = EternalReturnConfig.get().mobs.pillagerBoatChance;
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (!player.isSpectator() && world.random.nextDouble() < chance && world.getBiome(player.getBlockPos()).isIn(OCEANS)) {
					trySpawn(world, player);
				}
			}
		});
	}

	public static boolean isPillagerBoat(BoatEntity boat) {
		return boat.getCommandTags().contains(TAG);
	}

	/** A boat of pillagers on open water near the player, unless another is already about. */
	@Nullable
	public static BoatEntity trySpawn(ServerWorld world, PlayerEntity player) {
		if (!world.getEntitiesByClass(BoatEntity.class, player.getBoundingBox().expand(96.0), PillagerBoats::isPillagerBoat).isEmpty()) {
			return null;
		}
		for (int attempt = 0; attempt < 10; attempt++) {
			double angle = world.random.nextDouble() * Math.PI * 2.0;
			double distance = 24.0 + world.random.nextDouble() * 16.0;
			int x = MathHelper.floor(player.getX() + Math.cos(angle) * distance);
			int z = MathHelper.floor(player.getZ() + Math.sin(angle) * distance);
			int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
			BlockPos water = new BlockPos(x, top - 1, z);
			if (world.getFluidState(water).isIn(FluidTags.WATER) && world.isAir(water.up()) && world.getBiome(water).isIn(OCEANS)
					&& !BeaconZones.blocksMonstersAt(world, water)) {
				return spawnAt(world, water.up());
			}
		}
		return null;
	}

	public static BoatEntity spawnAt(ServerWorld world, BlockPos at) {
		BoatEntity boat = new BoatEntity(world, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		boat.setVariant(BoatEntity.Type.DARK_OAK);
		boat.setYaw(world.random.nextFloat() * 360.0F);
		boat.addCommandTag(TAG);
		for (int i = 0; i < 2; i++) {
			PillagerEntity pillager = EntityType.PILLAGER.create(world);
			if (pillager == null) {
				continue;
			}
			pillager.refreshPositionAndAngles(boat.getX(), boat.getY(), boat.getZ(), boat.getYaw(), 0.0F);
			pillager.initialize(world, world.getLocalDifficulty(at), SpawnReason.EVENT, null);
			pillager.startRiding(boat, true);
		}
		// Boat and crew go into the world together, as jockeys do, so players never hear of the crew before the boat.
		world.spawnEntityAndPassengers(boat);
		return boat;
	}

	/** Each tick, server side: the front pillager works the paddles. */
	public static void steer(BoatEntity boat) {
		LivingEntity driver = boat.getControllingPassenger();
		LivingEntity target = null;
		for (var passenger : boat.getPassengerList()) {
			if (passenger instanceof MobEntity mob && mob.getTarget() != null && mob.getTarget().isAlive()) {
				target = mob.getTarget();
				break;
			}
		}
		if (target == null && driver != null) {
			PlayerEntity player = boat.getWorld().getClosestPlayer(boat, 48.0);
			if (player != null && !player.isCreative() && !player.isSpectator()) {
				target = player;
			}
		}
		if (!(driver instanceof MobEntity) || target == null) {
			boat.setInputs(false, false, false, false);
			return;
		}
		double dx = target.getX() - boat.getX();
		double dz = target.getZ() - boat.getZ();
		float wanted = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float turn = MathHelper.wrapDegrees(wanted - boat.getYaw());
		double distance = Math.sqrt(dx * dx + dz * dz);
		boolean left = turn < -10.0F;
		boolean right = turn > 10.0F;
		boolean forward = distance > 6.0 && Math.abs(turn) < 70.0F;
		boat.setInputs(left, right, forward, false);
	}
}
