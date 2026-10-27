package com.eternalreturn.mobs.ai;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PiglinBrain;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.GameEventTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Lounging: now and then an idle bipedal monster (entity tag eternalreturn:loungers: zombies, skeletons, piglins,
 * witches and the like; no illagers) sits down for a while (mobs.loungeMinTicks to mobs.loungeMaxTicks), head bowed
 * (the sitting pose; drawn by the client's LoungeRendering). Sitting, its AI is off (MobLoungeMixin skips it): it
 * spots and hunts no one, and costs little. A piglin doesn't sit down to it while trading (holding what it was given,
 * or with a player near holding gold or rubies), and gets up for that. It gets up when its time is up, when hurt, in
 * water or lava, or at a noise
 * within mobs.loungeHearing blocks (twice that for an explosion): anything a sculk sensor hears, except a sneaking
 * player's quiet steps and other mobs' doings. Woken by a noise it turns to face it.
 */
public final class Lounging {
	public static final TagKey<EntityType<?>> LOUNGERS = TagKey.of(RegistryKeys.ENTITY_TYPE, EternalReturn.id("loungers"));
	/** The loungers sitting in each world, to hear noises by. */
	private static final Map<ServerWorld, Set<MobEntity>> SITTING = new WeakHashMap<>();

	private Lounging() {
	}

	/** A mob's lounging timer (MobLoungeMixin). */
	public interface Lounger {
		int eternalreturn$loungeTicks();

		void eternalreturn$setLoungeTicks(int ticks);
	}

	public static boolean isLounging(Entity entity) {
		return entity.isInPose(EntityPose.SITTING) && entity.getType().isIn(LOUNGERS);
	}

	/** Whether it may sit down now: a lounger, idle (no target, not walking anywhere), settled on dry ground, untouched. */
	public static boolean canLounge(MobEntity mob) {
		return EternalReturnConfig.get().mobs.lounging && mob.getType().isIn(LOUNGERS) && mob.isAlive() && mob.isOnGround() && !mob.isTouchingWater()
				&& !mob.isInLava() && !mob.isOnFire() && !mob.hasVehicle() && !mob.hasPassengers() && !mob.isLeashed() && mob.getTarget() == null
				&& !mob.isAttacking() && mob.hurtTime == 0 && mob.isInPose(EntityPose.STANDING) && mob.getNavigation().isIdle() && !trading(mob);
	}

	/** A piglin minding a trade: admiring what it was given (something in its off hand), or a player near holding gold or rubies. */
	private static boolean trading(MobEntity mob) {
		if (!(mob instanceof PiglinEntity piglin)) {
			return false;
		}
		if (!piglin.getOffHandStack().isEmpty()) {
			return true;
		}
		PlayerEntity player = mob.getWorld().getClosestPlayer(mob, 8.0);
		return player != null && !player.isSpectator() && PiglinBrain.isGoldHoldingPlayer(player);
	}

	/** Each tick, before its AI (server). Returns true if it's sitting (so its AI is skipped). */
	public static boolean tick(MobEntity mob) {
		Lounger lounger = (Lounger) mob;
		if (isLounging(mob)) {
			int left = lounger.eternalreturn$loungeTicks() - 1;
			lounger.eternalreturn$setLoungeTicks(left);
			if (left <= 0 || !mob.isAlive() || mob.isTouchingWater() || mob.isInLava() || mob.hasVehicle() || (mob.age % 10 == 0 && trading(mob))) {
				stand(mob);
				return false;
			}
			if (mob.getWorld() instanceof ServerWorld world) {
				sitting(world).add(mob);
			}
			return true;
		}
		EternalReturnConfig.MobTweaks config = EternalReturnConfig.get().mobs;
		if (config.loungeChance > 0 && mob.getRandom().nextInt(config.loungeChance) == 0 && canLounge(mob)) {
			sit(mob, config.loungeMinTicks + mob.getRandom().nextInt(Math.max(1, config.loungeMaxTicks - config.loungeMinTicks + 1)));
			return true;
		}
		return false;
	}

	/** Sits it down for this many ticks. */
	public static void sit(MobEntity mob, int ticks) {
		mob.getNavigation().stop();
		mob.setTarget(null);
		mob.setAttacking(false);
		mob.setVelocity(0.0, mob.getVelocity().y, 0.0);
		mob.setPose(EntityPose.SITTING);
		((Lounger) mob).eternalreturn$setLoungeTicks(ticks);
		if (mob.getWorld() instanceof ServerWorld world) {
			sitting(world).add(mob);
		}
	}

	/** Gets it up. */
	public static void stand(MobEntity mob) {
		if (mob.isInPose(EntityPose.SITTING)) {
			mob.setPose(EntityPose.STANDING);
		}
		((Lounger) mob).eternalreturn$setLoungeTicks(0);
		if (mob.getWorld() instanceof ServerWorld world) {
			sitting(world).remove(mob);
		}
	}

	private static Set<MobEntity> sitting(ServerWorld world) {
		return SITTING.computeIfAbsent(world, w -> Collections.newSetFromMap(new WeakHashMap<>()));
	}

	/**
	 * Whether a game event is a noise monsters hear: anything a sculk sensor hears, except a sneaking player's quiet
	 * steps and other mobs' doings (Lounging, GrimlockEntity).
	 */
	public static boolean isNoise(RegistryEntry<GameEvent> event, GameEvent.Emitter emitter) {
		if (!event.isIn(GameEventTags.VIBRATIONS)) {
			return false;
		}
		Entity source = emitter.sourceEntity();
		if (source instanceof LivingEntity && !(source instanceof PlayerEntity)) {
			return false;
		}
		return source == null || !event.isIn(GameEventTags.IGNORE_VIBRATIONS_SNEAKING) || !source.bypassesSteppingEffects();
	}

	/** A noise in the world (a game event): loungers in earshot get up and turn to it. */
	public static void hear(ServerWorld world, RegistryEntry<GameEvent> event, Vec3d at, GameEvent.Emitter emitter) {
		Set<MobEntity> sitting = SITTING.get(world);
		if (sitting == null || sitting.isEmpty() || !isNoise(event, emitter)) {
			return;
		}
		double hearing = EternalReturnConfig.get().mobs.loungeHearing * (event.matchesKey(GameEvent.EXPLODE.registryKey()) ? 2.0 : 1.0);
		for (MobEntity mob : List.copyOf(sitting)) {
			if (mob.isRemoved() || !isLounging(mob)) {
				sitting.remove(mob);
				continue;
			}
			if (mob.squaredDistanceTo(at) <= hearing * hearing) {
				stand(mob);
				float yaw = (float) (MathHelper.atan2(at.z - mob.getZ(), at.x - mob.getX()) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
				mob.setYaw(yaw);
				mob.setBodyYaw(yaw);
				mob.setHeadYaw(yaw);
			}
		}
	}
}
