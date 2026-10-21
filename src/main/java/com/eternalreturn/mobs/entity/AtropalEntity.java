package com.eternalreturn.mobs.entity;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Flutterer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ProjectileDeflection;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * The atropal: a small, dead, deformed fetus that floats about its gimp (GimpEntity), keeps away from players, and
 * beams them from its head: it locks on to a player it can see within BEAM_RANGE blocks, and after BEAM_WARMUP ticks
 * the beam hits (BEAM_DAMAGE magic damage, one more on Hard). Hiding behind blocks breaks the beam. It can't attack
 * with its body, and its psionics turn every projectile aside, so it has to be caught. 10 health. When it dies its
 * gimp is freed (neutral). Drops: loot table eternalreturn:entities/atropal (rotten flesh).
 */
public class AtropalEntity extends HostileEntity implements Flutterer {
	public static final double BEAM_RANGE = 16.0;
	/** Ticks from the beam locking on to it hitting. */
	public static final int BEAM_WARMUP = 50;
	public static final float BEAM_DAMAGE = 4.0F;
	/** Ticks between one beam hitting and the next locking on. */
	public static final int BEAM_COOLDOWN = 40;
	/** It flies off from a player (or its target) closer than this. */
	public static final double FLEE_DISTANCE = 7.0;
	/** It keeps within this of its gimp, circling at about ORBIT_RADIUS. */
	public static final double LEASH = 10.0;
	public static final double ORBIT_RADIUS = 3.5;
	private static final TrackedData<Integer> BEAM_TARGET = DataTracker.registerData(AtropalEntity.class, TrackedDataHandlerRegistry.INTEGER);
	@Nullable
	private UUID gimp;
	@Nullable
	private LivingEntity cachedBeamTarget;
	/** Client side: how long the beam has been charging. */
	private int beamTicks;

	public AtropalEntity(EntityType<? extends AtropalEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 5;
		this.moveControl = new FlightMoveControl(this, 20, true);
		this.setNoGravity(true);
	}

	public static DefaultAttributeContainer.Builder createAtropalAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 10.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
				.add(EntityAttributes.GENERIC_FLYING_SPEED, 0.5)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(1, new FleeGoal(this));
		this.goalSelector.add(2, new BeamGoal(this));
		this.goalSelector.add(3, new LookAtTargetGoal(this));
		this.goalSelector.add(4, new HoverGoal(this));
		ActiveTargetGoal<PlayerEntity> players = new ActiveTargetGoal<>(this, PlayerEntity.class, true);
		players.setMaxTimeWithoutVisibility(100);
		this.targetSelector.add(1, players);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(BEAM_TARGET, 0);
	}

	@Override
	protected EntityNavigation createNavigation(World world) {
		BirdNavigation navigation = new BirdNavigation(this, world);
		navigation.setCanPathThroughDoors(false);
		navigation.setCanSwim(true);
		navigation.setCanEnterOpenDoors(true);
		return navigation;
	}

	@Override
	public boolean isInAir() {
		return !this.isOnGround();
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
	}

	/** It never touches anything: no pushing, no attacks with its body. */
	@Override
	public boolean tryAttack(Entity target) {
		return false;
	}

	/** Psionics: every projectile turns aside before it lands, with a flash and a ripple of sound. */
	@Override
	public ProjectileDeflection getProjectileDeflection(ProjectileEntity projectile) {
		return (deflected, hitEntity, random) -> {
			ProjectileDeflection.SIMPLE.deflect(deflected, hitEntity, random);
			if (this.getWorld() instanceof ServerWorld world) {
				world.spawnParticles(ParticleTypes.ENCHANT, this.getX(), this.getBodyY(0.6), this.getZ(), 20, 0.4, 0.4, 0.4, 0.6);
				world.spawnParticles(ParticleTypes.REVERSE_PORTAL, deflected.getX(), deflected.getY(), deflected.getZ(), 6, 0.1, 0.1, 0.1, 0.05);
				world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_BREEZE_DEFLECT, this.getSoundCategory(), 1.0F, 1.6F);
			}
		};
	}

	// Its gimp.

	void bind(GimpEntity gimp) {
		this.gimp = gimp.getUuid();
	}

	@Nullable
	public GimpEntity getGimp() {
		return this.gimp != null && this.getWorld() instanceof ServerWorld world && world.getEntity(this.gimp) instanceof GimpEntity gimp && gimp.isAlive()
				? gimp : null;
	}

	/** Its death frees its gimp. */
	@Override
	public void onDeath(DamageSource source) {
		super.onDeath(source);
		GimpEntity gimp = this.getGimp();
		if (gimp != null) {
			gimp.free();
		}
	}

	/** While its gimp is about, the gimp decides when they despawn (and they go together). */
	@Override
	public void checkDespawn() {
		if (this.getGimp() != null && this.getWorld().getDifficulty() != Difficulty.PEACEFUL) {
			return;
		}
		super.checkDespawn();
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		if (this.gimp != null) {
			nbt.putUuid("Gimp", this.gimp);
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.gimp = nbt.containsUuid("Gimp") ? nbt.getUuid("Gimp") : null;
	}

	// The beam (drawn as a guardian's, by AtropalRenderer).

	public boolean hasBeamTarget() {
		return this.dataTracker.get(BEAM_TARGET) != 0;
	}

	void setBeamTarget(int entityId) {
		this.dataTracker.set(BEAM_TARGET, entityId);
	}

	@Nullable
	public LivingEntity getBeamTarget() {
		if (!this.hasBeamTarget()) {
			return null;
		}
		if (!this.getWorld().isClient) {
			return this.getTarget();
		}
		if (this.cachedBeamTarget == null && this.getWorld().getEntityById(this.dataTracker.get(BEAM_TARGET)) instanceof LivingEntity living) {
			this.cachedBeamTarget = living;
		}
		return this.cachedBeamTarget;
	}

	/** 0 when the beam locks on, 1 when it hits. */
	public float getBeamProgress(float tickDelta) {
		return Math.min(1.0F, (this.beamTicks + tickDelta) / BEAM_WARMUP);
	}

	public int getBeamTicks() {
		return this.beamTicks;
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (BEAM_TARGET.equals(data)) {
			this.beamTicks = 0;
			this.cachedBeamTarget = null;
		}
	}

	@Override
	public void tickMovement() {
		if (this.getWorld().isClient && this.hasBeamTarget() && this.beamTicks < BEAM_WARMUP) {
			this.beamTicks++;
		}
		super.tickMovement();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_VEX_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_VEX_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_VEX_DEATH;
	}

	/** Lower than a vex's. */
	@Override
	public float getSoundPitch() {
		return super.getSoundPitch() * 0.6F;
	}

	/** A spot in the air near target: up to tries random picks within spread blocks across and rise up or down, the first clear one. */
	@Nullable
	private Vec3d clearSpot(Vec3d target, double spread, double rise, int tries) {
		for (int i = 0; i < tries; i++) {
			double angle = this.random.nextDouble() * Math.PI * 2.0;
			double distance = this.random.nextDouble() * spread;
			Vec3d spot = target.add(Math.cos(angle) * distance, (this.random.nextDouble() * 2.0 - 1.0) * rise, Math.sin(angle) * distance);
			if (this.getWorld().isSpaceEmpty(this, this.getDimensions(this.getPose()).getBoxAt(spot))) {
				return spot;
			}
		}
		return null;
	}

	/** Players (and its target) closer than FLEE_DISTANCE: it flies off from them, staying near its gimp if it has one. */
	static class FleeGoal extends Goal {
		private final AtropalEntity atropal;
		@Nullable
		private LivingEntity threat;

		FleeGoal(AtropalEntity atropal) {
			this.atropal = atropal;
			this.setControls(EnumSet.of(Goal.Control.MOVE));
		}

		@Nullable
		private LivingEntity threat() {
			LivingEntity near = this.atropal.getWorld().getClosestPlayer(this.atropal.getX(), this.atropal.getY(), this.atropal.getZ(), FLEE_DISTANCE,
					EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR);
			LivingEntity target = this.atropal.getTarget();
			if (target != null && target.isAlive() && this.atropal.squaredDistanceTo(target) < FLEE_DISTANCE * FLEE_DISTANCE
					&& (near == null || this.atropal.squaredDistanceTo(target) < this.atropal.squaredDistanceTo(near))) {
				near = target;
			}
			return near;
		}

		@Override
		public boolean canStart() {
			this.threat = this.threat();
			return this.threat != null;
		}

		@Override
		public boolean shouldContinue() {
			return this.threat != null && this.threat.isAlive() && this.atropal.squaredDistanceTo(this.threat) < (FLEE_DISTANCE + 3.0) * (FLEE_DISTANCE + 3.0);
		}

		@Override
		public void start() {
			this.flee();
		}

		@Override
		public void tick() {
			if (this.atropal.getNavigation().isIdle() || this.atropal.age % 20 == 0) {
				this.flee();
			}
		}

		/** The best of a few clear spots: furthest from the threat, and, with a gimp, within LEASH of it. */
		private void flee() {
			Vec3d from = this.threat.getPos();
			Vec3d away = this.atropal.getPos().subtract(from).multiply(1.0, 0.0, 1.0);
			away = away.lengthSquared() < 1.0E-4 ? new Vec3d(1.0, 0.0, 0.0) : away.normalize();
			GimpEntity gimp = this.atropal.getGimp();
			Vec3d best = null;
			double bestScore = Double.NEGATIVE_INFINITY;
			for (int i = 0; i < 8; i++) {
				Vec3d spot = this.atropal.clearSpot(this.atropal.getPos().add(away.multiply(6.0)).add(0.0, 1.5, 0.0), 4.0, 2.0, 1);
				if (spot == null) {
					continue;
				}
				double score = spot.squaredDistanceTo(from);
				if (gimp != null && spot.squaredDistanceTo(gimp.getPos()) > LEASH * LEASH) {
					score -= 400.0;
				}
				if (score > bestScore) {
					bestScore = score;
					best = spot;
				}
			}
			if (best != null) {
				this.atropal.getNavigation().startMovingTo(best.x, best.y, best.z, 1.5);
			}
		}
	}

	/** Not fleeing: it circles its gimp a few blocks up and out, or drifts about where it is if it has none. */
	static class HoverGoal extends Goal {
		private final AtropalEntity atropal;
		private double angle;

		HoverGoal(AtropalEntity atropal) {
			this.atropal = atropal;
			this.angle = atropal.getRandom().nextDouble() * Math.PI * 2.0;
			this.setControls(EnumSet.of(Goal.Control.MOVE));
		}

		@Override
		public boolean canStart() {
			return this.atropal.getNavigation().isIdle() && this.atropal.getRandom().nextInt(10) == 0;
		}

		@Override
		public boolean shouldContinue() {
			return !this.atropal.getNavigation().isIdle();
		}

		@Override
		public void start() {
			GimpEntity gimp = this.atropal.getGimp();
			Vec3d spot;
			if (gimp != null) {
				this.angle += 0.6 + this.atropal.getRandom().nextDouble() * 0.6;
				Vec3d around = gimp.getPos().add(Math.cos(this.angle) * ORBIT_RADIUS, 2.5, Math.sin(this.angle) * ORBIT_RADIUS);
				spot = this.atropal.clearSpot(around, 1.0, 1.0, 6);
				if (spot == null) {
					spot = this.atropal.clearSpot(gimp.getPos().add(0.0, 2.0, 0.0), 2.0, 1.0, 6);
				}
			} else {
				spot = this.atropal.clearSpot(this.atropal.getPos(), 6.0, 2.0, 6);
			}
			if (spot != null) {
				this.atropal.getNavigation().startMovingTo(spot.x, spot.y, spot.z, gimp != null ? 1.0 : 0.6);
			}
		}
	}

	/** Faces its target (or where it flies). */
	static class LookAtTargetGoal extends Goal {
		private final AtropalEntity atropal;

		LookAtTargetGoal(AtropalEntity atropal) {
			this.atropal = atropal;
			this.setControls(EnumSet.of(Goal.Control.LOOK));
		}

		@Override
		public boolean canStart() {
			return this.atropal.getTarget() != null;
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = this.atropal.getTarget();
			if (target != null) {
				this.atropal.getLookControl().lookAt(target, 30.0F, 30.0F);
			}
		}
	}

	/** The beam: locks on to its target in sight and range, hits after BEAM_WARMUP ticks, rests BEAM_COOLDOWN; breaks if the target hides. */
	static class BeamGoal extends Goal {
		private final AtropalEntity atropal;
		private int ticks;

		BeamGoal(AtropalEntity atropal) {
			this.atropal = atropal;
		}

		@Override
		public boolean canStart() {
			LivingEntity target = this.atropal.getTarget();
			return target != null && target.isAlive();
		}

		@Override
		public void start() {
			this.ticks = -10;
		}

		@Override
		public void stop() {
			this.atropal.setBeamTarget(0);
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = this.atropal.getTarget();
			if (target == null) {
				return;
			}
			boolean sighted = this.atropal.squaredDistanceTo(target) <= BEAM_RANGE * BEAM_RANGE && this.atropal.canSee(target);
			if (!sighted) {
				if (this.atropal.hasBeamTarget()) {
					this.atropal.setBeamTarget(0);
				}
				this.ticks = Math.min(this.ticks, -10);
				this.ticks++;
				return;
			}
			this.ticks++;
			if (this.ticks == 0) {
				this.atropal.setBeamTarget(target.getId());
				if (!this.atropal.isSilent()) {
					this.atropal.playSound(SoundEvents.ENTITY_GUARDIAN_ATTACK, 1.0F, 1.7F);
				}
			} else if (this.ticks >= BEAM_WARMUP) {
				float damage = BEAM_DAMAGE + (this.atropal.getWorld().getDifficulty() == Difficulty.HARD ? 1.0F : 0.0F);
				target.damage(this.atropal.getDamageSources().indirectMagic(this.atropal, this.atropal), damage);
				this.atropal.setBeamTarget(0);
				this.ticks = -BEAM_COOLDOWN;
			}
			if (this.ticks > 0) {
				this.atropal.getLookControl().lookAt(target, 90.0F, 90.0F);
			}
		}
	}

	/** Its body yaw follows its head (it has no legs to turn on). */
	@Override
	public void tick() {
		super.tick();
		this.bodyYaw = MathHelper.stepUnwrappedAngleTowards(this.bodyYaw, this.headYaw, 10.0F);
	}
}
