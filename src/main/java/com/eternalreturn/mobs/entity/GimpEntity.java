package com.eternalreturn.mobs.entity;

import com.eternalreturn.mobs.NewMobs;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.SpiderNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The gimp: a man in a black latex suit from the waist up, a spider below. Rare, deep in the caves (below y 0), and
 * never alone: each spawns with its atropal (AtropalEntity), which floats about it. It climbs walls as a spider does
 * (and its model turns to lie against the wall), and fights with its fists: 20 health, a zombie's damage. While its
 * atropal lives it hunts players; once the atropal is dead it is neutral, fighting only what attacks it. Drops: loot
 * table eternalreturn:entities/gimp (leather and chains).
 */
public class GimpEntity extends HostileEntity {
	private static final TrackedData<Boolean> CLIMBING = DataTracker.registerData(GimpEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** Seconds its atropal may be missing (its spot loaded, the atropal not there) before the gimp counts it as gone. */
	private static final int MISSING_LIMIT = 10;
	@Nullable
	private UUID atropal;
	/** Where its atropal was last seen, to tell "gone" from "in a part of the world that isn't loaded". */
	@Nullable
	private BlockPos atropalSeen;
	private int atropalMissing;
	private boolean freed;
	/** Client side: how far its model has turned to lie against a wall, 0 to 1, now and the tick before. */
	private float climb;
	private float prevClimb;

	public GimpEntity(EntityType<? extends GimpEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 5;
	}

	public static DefaultAttributeContainer.Builder createGimpAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
	}

	/** Dark, and deep: below y 0. */
	public static boolean canSpawn(EntityType<GimpEntity> type, ServerWorldAccess world, SpawnReason reason, BlockPos pos, Random random) {
		return (pos.getY() < 0 || reason != SpawnReason.NATURAL) && HostileEntity.canSpawnInDark(type, world, reason, pos, random);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(1, new SwimGoal(this));
		this.goalSelector.add(4, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
		this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
		this.goalSelector.add(6, new LookAroundGoal(this));
		this.targetSelector.add(1, new RevengeGoal(this));
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false, entity -> !this.freed));
		this.targetSelector.add(3, new ActiveTargetGoal<>(this, IronGolemEntity.class, 10, true, false, entity -> !this.freed));
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(CLIMBING, false);
	}

	@Override
	protected EntityNavigation createNavigation(World world) {
		return new SpiderNavigation(this, world);
	}

	@Override
	public void tick() {
		super.tick();
		World world = this.getWorld();
		if (!world.isClient) {
			this.dataTracker.set(CLIMBING, this.horizontalCollision);
		} else {
			this.prevClimb = this.climb;
			float goal = this.isClimbing() && !this.isOnGround() ? 1.0F : 0.0F;
			this.climb += MathHelper.clamp(goal - this.climb, -0.2F, 0.2F);
		}
	}

	/** Climbing a wall, as a spider does. */
	@Override
	public boolean isClimbing() {
		return this.dataTracker.get(CLIMBING);
	}

	/** Client side: how far its model has turned to lie against a wall (0 upright, 1 flat on it). */
	public float getClimb(float tickDelta) {
		return MathHelper.lerp(tickDelta, this.prevClimb, this.climb);
	}

	/** As a spider: cobwebs don't slow it. */
	@Override
	public void slowMovement(BlockState state, Vec3d multiplier) {
		if (!state.isOf(Blocks.COBWEB)) {
			super.slowMovement(state, multiplier);
		}
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		if (this.age % 20 == 0) {
			this.checkAtropal();
		}
	}

	/** Its atropal: still there, or dead (killed, or gone from where it was while that spot is loaded), which frees the gimp. */
	private void checkAtropal() {
		if (this.atropal == null || this.freed || !(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		if (world.getEntity(this.atropal) instanceof AtropalEntity atropal) {
			if (atropal.isAlive()) {
				this.atropalSeen = atropal.getBlockPos();
				this.atropalMissing = 0;
			} else {
				this.free();
			}
		} else if (this.atropalSeen == null || world.shouldTickEntity(this.atropalSeen)) {
			if (++this.atropalMissing >= MISSING_LIMIT) {
				this.free();
			}
		}
	}

	/** Its atropal is dead: from now on it leaves players be unless they attack it. */
	public void free() {
		this.freed = true;
		this.atropal = null;
		this.atropalSeen = null;
		LivingEntity target = this.getTarget();
		if (target != null && target != this.getAttacker()) {
			this.setTarget(null);
		}
	}

	public boolean isFreed() {
		return this.freed;
	}

	void bind(AtropalEntity atropal) {
		this.atropal = atropal.getUuid();
		this.atropalSeen = atropal.getBlockPos();
		this.freed = false;
	}

	@Nullable
	public AtropalEntity getAtropal() {
		return this.atropal != null && this.getWorld() instanceof ServerWorld world && world.getEntity(this.atropal) instanceof AtropalEntity atropal
				? atropal : null;
	}

	/** Spawned (naturally, by an egg or a command): its atropal comes with it, floating just above. */
	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, @Nullable EntityData data) {
		data = super.initialize(world, difficulty, reason, data);
		if (this.atropal == null && !this.freed) {
			AtropalEntity atropal = NewMobs.ATROPAL.create(world.toServerWorld());
			if (atropal != null) {
				atropal.refreshPositionAndAngles(this.getX(), this.getY(), this.getZ(), this.getYaw(), 0.0F);
				for (double up = 2.5; up >= 0.0; up -= 0.5) {
					atropal.setPosition(this.getX(), this.getY() + up, this.getZ());
					if (world.isSpaceEmpty(atropal)) {
						break;
					}
				}
				atropal.initialize(world, difficulty, reason, null);
				atropal.bind(this);
				this.bind(atropal);
				world.spawnEntityAndPassengers(atropal);
			}
		}
		return data;
	}

	/** Despawning (or vanishing on Peaceful) takes its atropal with it. */
	@Override
	public void remove(Entity.RemovalReason reason) {
		if (reason == Entity.RemovalReason.DISCARDED) {
			AtropalEntity atropal = this.getAtropal();
			if (atropal != null) {
				atropal.discard();
			}
		}
		super.remove(reason);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		if (this.atropal != null) {
			nbt.putUuid("Atropal", this.atropal);
		}
		nbt.putBoolean("Freed", this.freed);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.atropal = nbt.containsUuid("Atropal") ? nbt.getUuid("Atropal") : null;
		this.freed = nbt.getBoolean("Freed");
	}

	@Override
	public int getLimitPerChunk() {
		return 1;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_SPIDER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_SPIDER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_SPIDER_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.ENTITY_SPIDER_STEP, 0.15F, 0.8F);
	}

	/** Lower than a spider's. */
	@Override
	public float getSoundPitch() {
		return super.getSoundPitch() * 0.75F;
	}
}
