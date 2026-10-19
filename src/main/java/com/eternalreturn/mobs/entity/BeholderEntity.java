package com.eternalreturn.mobs.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.FlyingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldEvents;

import java.util.EnumSet;

/**
 * The beholder: a rare floating eye of the Nether, as big as a ghast and six times as tough (60 health). It drifts as a
 * ghast does. A player it can see within LASER_RANGE blocks gets a guardian's beam from its great eye: it locks on, and
 * after WARMUP ticks the beam hits (magic damage 1, 3 on Hard, plus its attack damage, 6). A player further off, or one
 * out of sight (hiding behind blocks breaks the beam), gets a ghast's fireball from its mouth instead. It keeps hunting
 * a player it can't see for 10 seconds. Drops: loot table eternalreturn:entities/beholder.
 */
public class BeholderEntity extends FlyingEntity implements Monster {
	/** Blocks within which it lasers a target it can see. */
	public static final double LASER_RANGE = 24.0;
	/** Ticks from the beam locking on to it hitting (a guardian's). */
	public static final int WARMUP = 80;
	private static final TrackedData<Integer> BEAM_TARGET = DataTracker.registerData(BeholderEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> SHOOTING = DataTracker.registerData(BeholderEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private LivingEntity cachedBeamTarget;
	/** Client side: how long the beam has been charging, for its colour. */
	private int beamTicks;

	public BeholderEntity(EntityType<? extends BeholderEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 20;
		this.moveControl = new BeholderMoveControl(this);
	}

	public static DefaultAttributeContainer.Builder createBeholderAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 60.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 100.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0);
	}

	/** As a ghast spawns: not on Peaceful, and only one try in 20 goes ahead. */
	public static boolean canSpawn(EntityType<BeholderEntity> type, WorldAccess world, SpawnReason reason, BlockPos pos, Random random) {
		return world.getDifficulty() != Difficulty.PEACEFUL && random.nextInt(20) == 0 && canMobSpawn(type, world, reason, pos, random);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(5, new FlyRandomlyGoal(this));
		this.goalSelector.add(7, new LookAtTargetGoal(this));
		this.goalSelector.add(7, new AttackGoal(this));
		ActiveTargetGoal<PlayerEntity> players = new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false,
				entity -> Math.abs(entity.getY() - this.getY()) <= 16.0);
		players.setMaxTimeWithoutVisibility(200);
		this.targetSelector.add(1, players);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(BEAM_TARGET, 0);
		builder.add(SHOOTING, false);
	}

	/** Whether it lasers this target now (in sight and in range) rather than spitting a fireball. */
	public boolean lasers(LivingEntity target) {
		return this.squaredDistanceTo(target) <= LASER_RANGE * LASER_RANGE && this.canSee(target);
	}

	public boolean hasBeamTarget() {
		return this.dataTracker.get(BEAM_TARGET) != 0;
	}

	void setBeamTarget(int entityId) {
		this.dataTracker.set(BEAM_TARGET, entityId);
	}

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
		return (this.beamTicks + tickDelta) / WARMUP;
	}

	public int getBeamTicks() {
		return this.beamTicks;
	}

	public boolean isShooting() {
		return this.dataTracker.get(SHOOTING);
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
		if (this.getWorld().isClient && this.hasBeamTarget() && this.beamTicks < WARMUP) {
			this.beamTicks++;
		}
		super.tickMovement();
	}

	@Override
	protected boolean isDisallowedInPeaceful() {
		return true;
	}

	@Override
	public int getLimitPerChunk() {
		return 1;
	}

	@Override
	public SoundCategory getSoundCategory() {
		return SoundCategory.HOSTILE;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_GHAST_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_GHAST_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_GHAST_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 4.0F;
	}

	/** Deeper than a ghast. */
	@Override
	public float getSoundPitch() {
		return super.getSoundPitch() * 0.6F;
	}

	/** Lasers what it can see in range, spits fireballs at the rest (ghast timing: a warning, then the shot, then 2 seconds' rest). */
	static class AttackGoal extends Goal {
		private final BeholderEntity beholder;
		private int beamTicks;
		private int fireballCooldown;

		AttackGoal(BeholderEntity beholder) {
			this.beholder = beholder;
		}

		@Override
		public boolean canStart() {
			LivingEntity target = this.beholder.getTarget();
			return target != null && target.isAlive();
		}

		@Override
		public void start() {
			this.beamTicks = -10;
			this.fireballCooldown = 0;
		}

		@Override
		public void stop() {
			this.beholder.setBeamTarget(0);
			this.beholder.dataTracker.set(SHOOTING, false);
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = this.beholder.getTarget();
			if (target == null) {
				return;
			}
			if (this.beholder.lasers(target)) {
				this.fireballCooldown = 0;
				this.beamTicks++;
				if (this.beamTicks == 0) {
					this.beholder.setBeamTarget(target.getId());
					if (!this.beholder.isSilent()) {
						this.beholder.playSound(SoundEvents.ENTITY_GUARDIAN_ATTACK, 3.0F, 0.6F);
					}
				} else if (this.beamTicks >= WARMUP) {
					float magic = this.beholder.getWorld().getDifficulty() == Difficulty.HARD ? 3.0F : 1.0F;
					target.damage(this.beholder.getDamageSources().indirectMagic(this.beholder, this.beholder), magic);
					this.beholder.tryAttack(target);
					this.beholder.setBeamTarget(0);
					this.beamTicks = -30;
				}
			} else {
				// Out of range, or hidden: the beam breaks, and fireballs come instead.
				if (this.beholder.hasBeamTarget()) {
					this.beholder.setBeamTarget(0);
				}
				this.beamTicks = -10;
				if (target.squaredDistanceTo(this.beholder) < 64.0 * 64.0) {
					this.fireballCooldown++;
					World world = this.beholder.getWorld();
					if (this.fireballCooldown == 10 && !this.beholder.isSilent()) {
						world.syncWorldEvent(null, WorldEvents.GHAST_WARNS, this.beholder.getBlockPos(), 0);
					}
					if (this.fireballCooldown == 20) {
						this.shoot(target);
						this.fireballCooldown = -40;
					}
				} else if (this.fireballCooldown > 0) {
					this.fireballCooldown--;
				}
			}
			this.beholder.dataTracker.set(SHOOTING, this.fireballCooldown > 10);
		}

		/** A fireball from its mouth, low on its front face. */
		private void shoot(LivingEntity target) {
			World world = this.beholder.getWorld();
			Vec3d facing = this.beholder.getRotationVec(1.0F);
			double x = this.beholder.getX() + facing.x * 2.5;
			double y = this.beholder.getBodyY(0.3);
			double z = this.beholder.getZ() + facing.z * 2.5;
			Vec3d direction = new Vec3d(target.getX() - x, target.getBodyY(0.5) - y, target.getZ() - z);
			if (!this.beholder.isSilent()) {
				world.syncWorldEvent(null, WorldEvents.GHAST_SHOOTS, this.beholder.getBlockPos(), 0);
			}
			FireballEntity fireball = new FireballEntity(world, this.beholder, direction.normalize(), 1);
			fireball.setPosition(x, y, z);
			world.spawnEntity(fireball);
		}
	}

	/** A ghast's: picks a spot within 16 blocks whenever it has none (or it's far off). */
	static class FlyRandomlyGoal extends Goal {
		private final BeholderEntity beholder;

		FlyRandomlyGoal(BeholderEntity beholder) {
			this.beholder = beholder;
			this.setControls(EnumSet.of(Goal.Control.MOVE));
		}

		@Override
		public boolean canStart() {
			MoveControl control = this.beholder.getMoveControl();
			if (!control.isMoving()) {
				return true;
			}
			double dx = control.getTargetX() - this.beholder.getX();
			double dy = control.getTargetY() - this.beholder.getY();
			double dz = control.getTargetZ() - this.beholder.getZ();
			double distance = dx * dx + dy * dy + dz * dz;
			return distance < 1.0 || distance > 3600.0;
		}

		@Override
		public boolean shouldContinue() {
			return false;
		}

		@Override
		public void start() {
			Random random = this.beholder.getRandom();
			double x = this.beholder.getX() + (random.nextFloat() * 2.0F - 1.0F) * 16.0F;
			double y = this.beholder.getY() + (random.nextFloat() * 2.0F - 1.0F) * 16.0F;
			double z = this.beholder.getZ() + (random.nextFloat() * 2.0F - 1.0F) * 16.0F;
			this.beholder.getMoveControl().moveTo(x, y, z, 1.0);
		}
	}

	/** A ghast's: drifts toward the spot while the way there is clear. */
	static class BeholderMoveControl extends MoveControl {
		private final BeholderEntity beholder;
		private int collisionCheckCooldown;

		BeholderMoveControl(BeholderEntity beholder) {
			super(beholder);
			this.beholder = beholder;
		}

		@Override
		public void tick() {
			if (this.state == MoveControl.State.MOVE_TO && this.collisionCheckCooldown-- <= 0) {
				this.collisionCheckCooldown += this.beholder.getRandom().nextInt(5) + 2;
				Vec3d way = new Vec3d(this.targetX - this.beholder.getX(), this.targetY - this.beholder.getY(), this.targetZ - this.beholder.getZ());
				double distance = way.length();
				way = way.normalize();
				if (this.clear(way, MathHelper.ceil(distance))) {
					this.beholder.setVelocity(this.beholder.getVelocity().add(way.multiply(0.1)));
				} else {
					this.state = MoveControl.State.WAIT;
				}
			}
		}

		private boolean clear(Vec3d direction, int steps) {
			Box box = this.beholder.getBoundingBox();
			for (int i = 1; i < steps; i++) {
				box = box.offset(direction);
				if (!this.beholder.getWorld().isSpaceEmpty(this.beholder, box)) {
					return false;
				}
			}
			return true;
		}
	}

	/** A ghast's: faces where it drifts, or its target within 64 blocks. */
	static class LookAtTargetGoal extends Goal {
		private final BeholderEntity beholder;

		LookAtTargetGoal(BeholderEntity beholder) {
			this.beholder = beholder;
			this.setControls(EnumSet.of(Goal.Control.LOOK));
		}

		@Override
		public boolean canStart() {
			return true;
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			Entity target = this.beholder.getTarget();
			if (target == null) {
				Vec3d velocity = this.beholder.getVelocity();
				this.beholder.setYaw(-((float) MathHelper.atan2(velocity.x, velocity.z)) * MathHelper.DEGREES_PER_RADIAN);
			} else if (target.squaredDistanceTo(this.beholder) < 4096.0) {
				this.beholder.setYaw(-((float) MathHelper.atan2(target.getX() - this.beholder.getX(), target.getZ() - this.beholder.getZ()))
						* MathHelper.DEGREES_PER_RADIAN);
			}
			this.beholder.bodyYaw = this.beholder.getYaw();
		}
	}
}
