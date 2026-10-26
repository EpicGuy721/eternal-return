package com.eternalreturn.mobs.entity;

import com.eternalreturn.items.LaserBlaster;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.UniversalAngerGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.TimeHelper;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Sandown: a tall clown of the End (rare there), 40 health, neutral: he turns on whoever hurts him or shoots at him
 * (for 20 to 39 seconds, as an enderman's anger lasts). He holds a laser blaster (com.eternalreturn.items.LaserBlaster,
 * dropped one time in 100) and shoots whoever he's angry with when he can see them within RANGE blocks, every
 * SHOT_COOLDOWN ticks. Out of his reach (out of sight or range) for REACH_PATIENCE ticks, they're teleported in front of
 * him, where he can see them (at most every PULL_COOLDOWN ticks). He teleports, as an enderman does: away from a player
 * who comes within TOO_CLOSE blocks, away from a projectile coming at him or a block falling on him (and one that
 * reaches him doesn't hurt him: he's gone), and now and then when idle; but at most once every TELEPORT_COOLDOWN ticks,
 * so for a while after a teleport he can be caught and hit. Mostly silent: a robotic noise now and then.
 */
public class SandownEntity extends HostileEntity implements Angerable, RangedAttackMob {
	public static final float RANGE = 32.0F;
	public static final int SHOT_COOLDOWN = 40;
	public static final int REACH_PATIENCE = 40;
	public static final int PULL_COOLDOWN = 200;
	public static final double TOO_CLOSE = 3.5;
	/** Five seconds between his teleports. */
	public static final int TELEPORT_COOLDOWN = 100;
	/** Robotic noises, picked from now and then. */
	private static final SoundEvent[] NOISES = {SoundEvents.BLOCK_CRAFTER_CRAFT, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundEvents.BLOCK_COPPER_BULB_TURN_ON,
			SoundEvents.BLOCK_CRAFTER_FAIL};
	/** How far in front of him a target is put, in blocks. */
	private static final int[] PULL_DISTANCES = {6, 5, 7, 4, 8};
	private static final UniformIntProvider ANGER_TIME = TimeHelper.betweenSeconds(20, 39);
	private static final TrackedData<Boolean> AIMING = DataTracker.registerData(SandownEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private int angerTime;
	@Nullable
	private UUID angryAt;
	private int teleportCooldown;
	private int pullCooldown;

	public SandownEntity(EntityType<? extends SandownEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 10;
	}

	public static DefaultAttributeContainer.Builder createSandownAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.5)
				.add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.0);
	}

	/** Dark (the End always is), and half the time he's picked: rare. */
	public static boolean canSpawn(EntityType<SandownEntity> type, ServerWorldAccess world, SpawnReason reason, BlockPos pos, Random random) {
		return (reason != SpawnReason.NATURAL || random.nextBoolean()) && HostileEntity.canSpawnInDark(type, world, reason, pos, random);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(1, new SwimGoal(this));
		this.goalSelector.add(2, new AttackGoal(this));
		this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.8));
		this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 16.0F));
		this.goalSelector.add(8, new LookAroundGoal(this));
		this.targetSelector.add(1, new RevengeGoal(this));
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false, this::shouldAngerAt));
		this.targetSelector.add(3, new UniversalAngerGoal<>(this, false));
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(AIMING, false);
	}

	/** Pointing his blaster at a target in his reach (the model's raised arm). */
	public boolean isAiming() {
		return this.dataTracker.get(AIMING);
	}

	void setAiming(boolean aiming) {
		this.dataTracker.set(AIMING, aiming);
	}

	@Override
	protected void initEquipment(Random random, LocalDifficulty difficulty) {
		this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(LaserBlaster.BLASTER));
		this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.01F);
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, @Nullable EntityData data) {
		data = super.initialize(world, difficulty, reason, data);
		this.initEquipment(world.getRandom(), difficulty);
		return data;
	}

	// Anger, as a wolf's or an enderman's.

	@Override
	public int getAngerTime() {
		return this.angerTime;
	}

	@Override
	public void setAngerTime(int angerTime) {
		this.angerTime = angerTime;
	}

	@Override
	public void chooseRandomAngerTime() {
		this.setAngerTime(ANGER_TIME.get(this.random));
	}

	@Nullable
	@Override
	public UUID getAngryAt() {
		return this.angryAt;
	}

	@Override
	public void setAngryAt(@Nullable UUID angryAt) {
		this.angryAt = angryAt;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		this.writeAngerToNbt(nbt);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.readAngerFromNbt(this.getWorld(), nbt);
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		if (this.getWorld() instanceof ServerWorld server) {
			this.tickAngerLogic(server, true);
		}
		if (this.teleportCooldown > 0) {
			this.teleportCooldown--;
		}
		if (this.pullCooldown > 0) {
			this.pullCooldown--;
		}
		if (this.getTarget() == null && this.random.nextInt(800) == 0) {
			this.teleportAway(this.getPos(), 8.0, 24.0);
		}
		this.dodge();
	}

	// Teleporting away.

	/** A projectile or falling block about to hit him sends him off; so does a player (or his target) too close. */
	private void dodge() {
		if (this.teleportCooldown > 0) {
			return;
		}
		Box box = this.getBoundingBox();
		List<ProjectileEntity> incoming = this.getWorld().getEntitiesByClass(ProjectileEntity.class, box.expand(10.0),
				projectile -> projectile.getOwner() != this && this.comingAtMe(projectile));
		if (!incoming.isEmpty()) {
			ProjectileEntity projectile = incoming.get(0);
			this.angerAt(projectile.getOwner());
			this.teleportAway(projectile.getPos(), 8.0, 16.0);
			return;
		}
		Box overhead = box.expand(1.0, 0.0, 1.0).stretch(0.0, 12.0, 0.0);
		List<FallingBlockEntity> falling = this.getWorld().getEntitiesByClass(FallingBlockEntity.class, overhead,
				block -> block.getY() > this.getY() + 1.0 && block.getVelocity().y <= 0.0);
		if (!falling.isEmpty()) {
			this.teleportAway(falling.get(0).getPos(), 6.0, 14.0);
			return;
		}
		Entity near = this.tooClose();
		if (near != null) {
			this.teleportAway(near.getPos(), 8.0, 16.0);
		}
	}

	/** On a line that meets him (a little room to spare) within the next 10 ticks. */
	private boolean comingAtMe(ProjectileEntity projectile) {
		Vec3d velocity = projectile.getVelocity();
		if (velocity.lengthSquared() < 0.01) {
			return false;
		}
		Vec3d from = projectile.getPos();
		return this.getBoundingBox().expand(0.75).raycast(from, from.add(velocity.multiply(10.0))).isPresent();
	}

	@Nullable
	private Entity tooClose() {
		Box reach = this.getBoundingBox().expand(TOO_CLOSE, 1.0, TOO_CLOSE);
		List<PlayerEntity> players = this.getWorld().getEntitiesByClass(PlayerEntity.class, reach,
				player -> player.isAlive() && !player.isSpectator() && !player.isCreative());
		if (!players.isEmpty()) {
			return players.get(0);
		}
		LivingEntity target = this.getTarget();
		return target != null && target.isAlive() && target.getBoundingBox().intersects(reach) ? target : null;
	}

	private void angerAt(@Nullable Entity owner) {
		if (owner instanceof LivingEntity living && living != this && !(owner instanceof PlayerEntity player && (player.isCreative() || player.isSpectator()))) {
			this.setAttacker(living);
		}
	}

	/**
	 * Off to somewhere between min and max blocks from here, away from the threat (to either side of straight away), on
	 * solid ground; where he can see his target, if he can find such a place. Not again until TELEPORT_COOLDOWN is up.
	 */
	public boolean teleportAway(Vec3d threat, double min, double max) {
		if (this.getWorld().isClient() || !this.isAlive() || this.teleportCooldown > 0) {
			return false;
		}
		double away = Math.atan2(this.getZ() - threat.z, this.getX() - threat.x);
		if (threat.squaredDistanceTo(this.getPos()) < 0.01) {
			away = this.random.nextDouble() * Math.PI * 2.0;
		}
		LivingEntity target = this.getTarget();
		for (int attempt = 0; attempt < 48; attempt++) {
			double angle = away + (this.random.nextDouble() - 0.5) * Math.PI * 1.1;
			double distance = min + this.random.nextDouble() * (max - min);
			double x = this.getX() + Math.cos(angle) * distance;
			double z = this.getZ() + Math.sin(angle) * distance;
			double y = this.getY() + this.random.nextInt(17) - 8;
			// The first two thirds of the tries want a view of his target.
			boolean wantView = target != null && attempt < 32;
			if (this.teleportTo(x, y, z, wantView ? target : null)) {
				this.teleportCooldown = TELEPORT_COOLDOWN;
				return true;
			}
		}
		return false;
	}

	/** As an enderman teleports: down to solid ground (not water) below the spot, if he fits there. */
	private boolean teleportTo(double x, double y, double z, @Nullable LivingEntity mustSee) {
		World world = this.getWorld();
		BlockPos.Mutable pos = BlockPos.ofFloored(x, y, z).mutableCopy();
		while (pos.getY() > world.getBottomY() && !world.getBlockState(pos).blocksMovement()) {
			pos.move(Direction.DOWN);
		}
		BlockState ground = world.getBlockState(pos);
		if (!ground.blocksMovement() || ground.getFluidState().isIn(FluidTags.WATER)) {
			return false;
		}
		if (mustSee != null && !this.canSeeFrom(new Vec3d(x, pos.getY() + 1.0 + this.getStandingEyeHeight(), z), mustSee)) {
			return false;
		}
		Vec3d from = this.getPos();
		if (!this.teleport(x, pos.getY() + 1.0, z, true)) {
			return false;
		}
		world.emitGameEvent(GameEvent.TELEPORT, from, GameEvent.Emitter.of(this));
		if (!this.isSilent()) {
			world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, this.getSoundCategory(), 1.0F, 0.8F);
			this.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.8F);
		}
		this.getNavigation().stop();
		return true;
	}

	private boolean canSeeFrom(Vec3d eye, Entity target) {
		Vec3d to = new Vec3d(target.getX(), target.getEyeY(), target.getZ());
		return this.getWorld().raycast(new RaycastContext(eye, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this))
				.getType() == HitResult.Type.MISS;
	}

	/** A projectile or a falling block that reaches him anyway finds him gone. */
	@Override
	public boolean damage(DamageSource source, float amount) {
		if (this.isInvulnerableTo(source) || this.getWorld().isClient()) {
			return super.damage(source, amount);
		}
		boolean projectile = source.isIn(DamageTypeTags.IS_PROJECTILE);
		boolean falling = source.isOf(DamageTypes.FALLING_ANVIL) || source.isOf(DamageTypes.FALLING_BLOCK) || source.isOf(DamageTypes.FALLING_STALACTITE);
		if (projectile || falling) {
			Entity from = source.getSource();
			if (this.teleportAway(from != null ? from.getPos() : this.getPos(), 8.0, 16.0)) {
				if (projectile) {
					this.angerAt(source.getAttacker());
				}
				return false;
			}
		}
		return super.damage(source, amount);
	}

	// Reaching his target.

	/** He can see it, within RANGE. */
	public boolean inReach(LivingEntity target) {
		return this.squaredDistanceTo(target) <= RANGE * RANGE && this.getVisibilityCache().canSee(target);
	}

	/**
	 * The target teleported in front of him (as he faces, PULL_DISTANCES blocks off), onto the ground where it fits and
	 * he can see it, facing him.
	 */
	public boolean pull(LivingEntity target) {
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return false;
		}
		float yaw = this.getHeadYaw() * MathHelper.RADIANS_PER_DEGREE;
		Vec3d forward = new Vec3d(-MathHelper.sin(yaw), 0.0, MathHelper.cos(yaw));
		Vec3d side = new Vec3d(-forward.z, 0.0, forward.x);
		Vec3d eye = this.getEyePos();
		for (int distance : PULL_DISTANCES) {
			for (double offset : new double[] {0.0, -1.5, 1.5}) {
				Vec3d spot = this.getPos().add(forward.multiply(distance)).add(side.multiply(offset));
				BlockPos.Mutable pos = BlockPos.ofFloored(spot.x, this.getY() + 3.0, spot.z).mutableCopy();
				for (int down = 0; down < 8; down++, pos.move(Direction.DOWN)) {
					BlockPos below = pos.down();
					if (!world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP)) {
						continue;
					}
					Vec3d at = new Vec3d(spot.x, pos.getY(), spot.z);
					Box box = target.getDimensions(target.getPose()).getBoxAt(at);
					if (!world.isSpaceEmpty(target, box) || world.containsFluid(box)) {
						continue;
					}
					Vec3d middle = at.add(0.0, target.getHeight() * 0.5, 0.0);
					if (world.raycast(new RaycastContext(eye, middle, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this))
							.getType() != HitResult.Type.MISS) {
						continue;
					}
					Vec3d from = target.getPos();
					float facing = (float) (MathHelper.atan2(this.getZ() - at.z, this.getX() - at.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
					target.stopRiding();
					if (!target.teleport(world, at.x, at.y, at.z, Set.of(), facing, 0.0F)) {
						continue;
					}
					target.fallDistance = 0.0F;
					target.setVelocity(Vec3d.ZERO);
					world.emitGameEvent(GameEvent.TELEPORT, from, GameEvent.Emitter.of(target));
					world.spawnParticles(ParticleTypes.PORTAL, from.x, from.y + 1.0, from.z, 40, 0.4, 0.8, 0.4, 0.2);
					world.spawnParticles(ParticleTypes.PORTAL, at.x, at.y + 1.0, at.z, 40, 0.4, 0.8, 0.4, 0.2);
					world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, this.getSoundCategory(), 1.0F, 0.6F);
					world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, this.getSoundCategory(), 1.0F, 0.6F);
					this.pullCooldown = PULL_COOLDOWN;
					return true;
				}
			}
		}
		return false;
	}

	/** A bolt from his blaster (at his right hand, held out) at the target's chest, a little less steady on easier difficulties. */
	@Override
	public void shootAt(LivingEntity target, float pullProgress) {
		if (!this.getMainHandStack().isOf(LaserBlaster.BLASTER)) {
			return;
		}
		float yaw = this.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
		Vec3d forward = new Vec3d(-MathHelper.sin(yaw), 0.0, MathHelper.cos(yaw));
		Vec3d right = new Vec3d(-forward.z, 0.0, forward.x);
		Vec3d muzzle = this.getPos().add(0.0, 2.95, 0.0).add(forward.multiply(1.8)).add(right.multiply(0.72));
		Vec3d direction = new Vec3d(target.getX(), target.getBodyY(0.6), target.getZ()).subtract(muzzle);
		LaserBlaster.fire(this, muzzle, direction, 3 - this.getWorld().getDifficulty().getId());
	}

	@Override
	public boolean canPickUpLoot() {
		return false;
	}

	// Mostly silent: a whir, click or clunk of machinery about once a minute or more; a clank when hurt, a power-down when
	// he dies.

	@Nullable
	@Override
	protected SoundEvent getAmbientSound() {
		return NOISES[this.random.nextInt(NOISES.length)];
	}

	@Override
	public int getMinAmbientSoundDelay() {
		return 1200;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.BLOCK_BEACON_DEACTIVATE;
	}

	@Override
	public float getSoundPitch() {
		return 0.7F + this.random.nextFloat() * 0.5F;
	}

	/** Shoots whoever he's angry with in his reach; pulls them in front of him when they keep out of it. */
	static class AttackGoal extends Goal {
		private final SandownEntity sandown;
		private int cooldown;
		private int outOfReach;

		AttackGoal(SandownEntity sandown) {
			this.sandown = sandown;
			this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			LivingEntity target = this.sandown.getTarget();
			return target != null && target.isAlive() && this.sandown.canTarget(target);
		}

		@Override
		public boolean shouldContinue() {
			return this.canStart();
		}

		@Override
		public void start() {
			this.cooldown = 20;
			this.outOfReach = 0;
		}

		@Override
		public void stop() {
			this.sandown.setAiming(false);
			this.sandown.getNavigation().stop();
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = this.sandown.getTarget();
			if (target == null) {
				return;
			}
			this.sandown.getLookControl().lookAt(target, 30.0F, 30.0F);
			if (this.sandown.inReach(target)) {
				this.outOfReach = 0;
				this.sandown.getNavigation().stop();
				this.sandown.setAiming(true);
				if (--this.cooldown <= 0) {
					this.sandown.shootAt(target, 1.0F);
					this.cooldown = SHOT_COOLDOWN;
				}
				return;
			}
			this.sandown.setAiming(false);
			this.cooldown = Math.max(this.cooldown, 10);
			if (++this.outOfReach >= REACH_PATIENCE && this.sandown.pullCooldown == 0 && this.sandown.pull(target)) {
				this.outOfReach = 0;
				// A moment to see what happened before he shoots.
				this.cooldown = 15;
			} else if (this.sandown.getNavigation().isIdle()) {
				this.sandown.getNavigation().startMovingTo(target, 1.0);
			}
		}
	}
}
