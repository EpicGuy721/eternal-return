package com.eternalreturn.mobs.entity;

import com.eternalreturn.mobs.ai.Lounging;
import com.eternalreturn.mobs.ai.VisionCone;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The grimlock: a grey, eyeless zombie of the deep caves (below y 0, in the dark), shirtless in tattered trousers.
 * Blind (entity tag eternalreturn:blind), it finds players as the warden does: it smells one close by (its smell
 * range, keener than a zombie's) and hears noises within HEARING blocks (what a sculk sensor hears, not a sneaking
 * player's steps), and walks to where it heard one. It burns in the sun like any zombie, and shuns light: it backs out
 * of anywhere lit (block light LIGHT or more, or daylight), won't chase a player standing in the light, and wanders
 * toward the dark. A zombie's drops; no gear, no turning into a drowned.
 */
public class GrimlockEntity extends ZombieEntity {
	/** Blocks within which it hears a noise. */
	public static final double HEARING = 16.0;
	/** Block light at which a place is too bright for it. */
	public static final int LIGHT = 7;
	/** Ticks it remembers a noise (where to go), and its target's noise (to keep after it out of smelling range). */
	public static final int MEMORY = 100;
	private static final Map<ServerWorld, Set<GrimlockEntity>> LISTENING = new WeakHashMap<>();
	@Nullable
	private Vec3d noise;
	private int heardAt = -1000;
	private int heardTargetAt = -1000;

	public GrimlockEntity(EntityType<? extends ZombieEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createGrimlockAttributes() {
		return ZombieEntity.createZombieAttributes().add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
	}

	/** Dark, and deep: below y 0. */
	public static boolean canSpawn(EntityType<GrimlockEntity> type, ServerWorldAccess world, SpawnReason reason, BlockPos pos, Random random) {
		return (pos.getY() < 0 || reason != SpawnReason.NATURAL) && HostileEntity.canSpawnInDark(type, world, reason, pos, random);
	}

	/** Too bright for a grimlock: lamp or torch light, or daylight. */
	public static boolean lit(World world, BlockPos pos) {
		return world.getLightLevel(LightType.BLOCK, pos) >= LIGHT || world.isDay() && world.getLightLevel(LightType.SKY, pos) > 10;
	}

	@Override
	protected void initCustomGoals() {
		this.goalSelector.add(1, new ShunLightGoal(this));
		this.goalSelector.add(2, new DarkAttackGoal(this));
		this.goalSelector.add(5, new InvestigateNoiseGoal(this));
		this.goalSelector.add(7, new WanderAroundFarGoal(this, 1.0));
		this.targetSelector.add(1, new RevengeGoal(this));
		this.targetSelector.add(2, new SmellTargetGoal<>(this, PlayerEntity.class));
		this.targetSelector.add(3, new SmellTargetGoal<>(this, IronGolemEntity.class));
	}

	/** Within its smelling range (no sight needed: it has none). */
	boolean smells(LivingEntity entity) {
		double range = VisionCone.smellRange(this.getType());
		return this.squaredDistanceTo(entity) <= range * range;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld() instanceof ServerWorld world && this.isAlive()) {
			LISTENING.computeIfAbsent(world, w -> Collections.newSetFromMap(new WeakHashMap<>())).add(this);
		}
	}

	/** A noise in the world (a game event): grimlocks in earshot note where, and whether it was their target. */
	public static void hear(ServerWorld world, RegistryEntry<GameEvent> event, Vec3d at, GameEvent.Emitter emitter) {
		Set<GrimlockEntity> listening = LISTENING.get(world);
		if (listening == null || listening.isEmpty() || !Lounging.isNoise(event, emitter)) {
			return;
		}
		for (GrimlockEntity grimlock : List.copyOf(listening)) {
			if (grimlock.isRemoved() || !grimlock.isAlive()) {
				listening.remove(grimlock);
				continue;
			}
			if (grimlock.squaredDistanceTo(at) <= HEARING * HEARING) {
				grimlock.noise = at;
				grimlock.heardAt = grimlock.age;
				if (emitter.sourceEntity() != null && emitter.sourceEntity() == grimlock.getTarget()) {
					grimlock.heardTargetAt = grimlock.age;
				}
			}
		}
	}

	/** Wandering, it heads for the dark. */
	@Override
	public float getPathfindingFavor(BlockPos pos, WorldView world) {
		return world instanceof World level && lit(level, pos) ? -10.0F : super.getPathfindingFavor(pos, world);
	}

	@Override
	protected boolean canConvertInWater() {
		return false;
	}

	@Override
	protected void initEquipment(Random random, LocalDifficulty difficulty) {
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, @Nullable EntityData data) {
		data = super.initialize(world, difficulty, reason, data);
		this.setCanPickUpLoot(false);
		return data;
	}

	/** A zombie's drops. */
	@Override
	public RegistryKey<LootTable> getLootTableId() {
		return EntityType.ZOMBIE.getLootTableId();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_ZOMBIE_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_ZOMBIE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_ZOMBIE_DEATH;
	}

	/** Lower than a zombie's. */
	@Override
	public float getSoundPitch() {
		return super.getSoundPitch() * 0.75F;
	}

	/** Spots a target by smell only; keeps after it while it can smell it, or hears it lately. */
	static class SmellTargetGoal<T extends LivingEntity> extends ActiveTargetGoal<T> {
		private final GrimlockEntity grimlock;

		SmellTargetGoal(GrimlockEntity grimlock, Class<T> targetClass) {
			super(grimlock, targetClass, 10, false, false, grimlock::smells);
			this.grimlock = grimlock;
		}

		@Override
		public boolean shouldContinue() {
			LivingEntity target = this.grimlock.getTarget();
			return super.shouldContinue() && target != null
					&& (this.grimlock.smells(target) || this.grimlock.age - this.grimlock.heardTargetAt < MEMORY || target == this.grimlock.getAttacker());
		}
	}

	/** A zombie's attack, but never after a target standing in the light. */
	static class DarkAttackGoal extends ZombieAttackGoal {
		private final GrimlockEntity grimlock;

		DarkAttackGoal(GrimlockEntity grimlock) {
			super(grimlock, 1.0, false);
			this.grimlock = grimlock;
		}

		private boolean inTheDark() {
			LivingEntity target = this.grimlock.getTarget();
			return target != null && !lit(this.grimlock.getWorld(), target.getBlockPos());
		}

		@Override
		public boolean canStart() {
			return this.inTheDark() && super.canStart();
		}

		@Override
		public boolean shouldContinue() {
			return this.inTheDark() && super.shouldContinue();
		}
	}

	/** Standing in the light: it backs off to somewhere dark nearby. */
	static class ShunLightGoal extends Goal {
		private final GrimlockEntity grimlock;

		ShunLightGoal(GrimlockEntity grimlock) {
			this.grimlock = grimlock;
			this.setControls(EnumSet.of(Goal.Control.MOVE));
		}

		@Override
		public boolean canStart() {
			if (!lit(this.grimlock.getWorld(), this.grimlock.getBlockPos())) {
				return false;
			}
			for (int i = 0; i < 12; i++) {
				Vec3d spot = NoPenaltyTargeting.find(this.grimlock, 10, 4);
				if (spot != null && !lit(this.grimlock.getWorld(), BlockPos.ofFloored(spot))) {
					return this.grimlock.getNavigation().startMovingTo(spot.x, spot.y, spot.z, 1.3);
				}
			}
			return false;
		}

		@Override
		public boolean shouldContinue() {
			return !this.grimlock.getNavigation().isIdle();
		}
	}

	/** It heard something lately, and has no target: it goes to see, for up to 15 seconds (unless the noise was somewhere lit). */
	static class InvestigateNoiseGoal extends Goal {
		private final GrimlockEntity grimlock;
		private int headingFor = -1;
		private int ticks;

		InvestigateNoiseGoal(GrimlockEntity grimlock) {
			this.grimlock = grimlock;
			this.setControls(EnumSet.of(Goal.Control.MOVE));
		}

		/** A noise to go to: not in the light, and nothing to hunt meanwhile. */
		private boolean worthIt() {
			return this.grimlock.noise != null && this.grimlock.getTarget() == null && !lit(this.grimlock.getWorld(), BlockPos.ofFloored(this.grimlock.noise));
		}

		@Override
		public boolean canStart() {
			return this.worthIt() && this.grimlock.age - this.grimlock.heardAt < MEMORY && this.grimlock.squaredDistanceTo(this.grimlock.noise) > 4.0;
		}

		@Override
		public void start() {
			this.ticks = 0;
			this.go();
		}

		private void go() {
			this.headingFor = this.grimlock.heardAt;
			Vec3d at = this.grimlock.noise;
			this.grimlock.getNavigation().startMovingTo(at.x, at.y, at.z, 1.0);
		}

		@Override
		public boolean shouldContinue() {
			return this.worthIt() && ++this.ticks < 300 && !this.grimlock.getNavigation().isIdle();
		}

		@Override
		public void tick() {
			if (this.grimlock.heardAt != this.headingFor) {
				this.go();
			}
		}
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		super.remove(reason);
		if (this.getWorld() instanceof ServerWorld world && LISTENING.containsKey(world)) {
			LISTENING.get(world).remove(this);
		}
	}
}
