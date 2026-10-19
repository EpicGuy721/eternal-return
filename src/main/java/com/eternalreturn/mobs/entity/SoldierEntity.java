package com.eternalreturn.mobs.entity;

import com.eternalreturn.items.Firearms;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.ProjectileAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

/**
 * The soldier: a gunman deep in the caves (below y 0). He carries a rifle (dropped one time in 10) and shoots bullets
 * (com.eternalreturn.items.Firearms) at players within RANGE blocks he can see, every RELOAD ticks: a long reload
 * between shots. 20 health. Always drops 8 to 16 bullets (loot table eternalreturn:entities/soldier).
 */
public class SoldierEntity extends HostileEntity implements RangedAttackMob {
	public static final float RANGE = 24.0F;
	/** Ticks between shots (reloading). */
	public static final int RELOAD = 70;
	/** Ticks since his last shot, for the reload's sounds; -1 before the first. */
	private int sinceShot = -1;
	private static final TrackedData<Boolean> AIMING = DataTracker.registerData(SoldierEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	public SoldierEntity(EntityType<? extends SoldierEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 8;
	}

	public static DefaultAttributeContainer.Builder createSoldierAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
	}

	/** Dark, and deep: below y 0. */
	public static boolean canSpawn(EntityType<SoldierEntity> type, ServerWorldAccess world, SpawnReason reason, BlockPos pos, Random random) {
		return (pos.getY() < 0 || reason != SpawnReason.NATURAL) && HostileEntity.canSpawnInDark(type, world, reason, pos, random);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(1, new SwimGoal(this));
		this.goalSelector.add(4, new ProjectileAttackGoal(this, 1.0, RELOAD, RANGE));
		this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
		this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 12.0F));
		this.goalSelector.add(6, new LookAroundGoal(this));
		this.targetSelector.add(1, new RevengeGoal(this));
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
		this.targetSelector.add(3, new ActiveTargetGoal<>(this, IronGolemEntity.class, true));
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(AIMING, false);
	}

	/** Raising his rifle at a target he can see within range (the model's aim pose; else he holds it at the ready). */
	public boolean isAiming() {
		return this.dataTracker.get(AIMING);
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		if (this.sinceShot >= 0 && this.sinceShot < RELOAD) {
			Firearms.reloadSounds(this, ++this.sinceShot, RELOAD);
		}
		LivingEntity target = this.getTarget();
		this.dataTracker.set(AIMING, target != null && target.isAlive() && this.squaredDistanceTo(target) <= RANGE * RANGE && this.getVisibilityCache().canSee(target));
	}

	@Override
	protected void initEquipment(Random random, LocalDifficulty difficulty) {
		this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Firearms.RIFLE));
		this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.1F);
	}

	@Override
	public net.minecraft.entity.EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason,
			net.minecraft.entity.EntityData data) {
		data = super.initialize(world, difficulty, reason, data);
		this.initEquipment(world.getRandom(), difficulty);
		return data;
	}

	/** A bullet at the target's chest, a little less steady on easier difficulties. */
	@Override
	public void shootAt(LivingEntity target, float pullProgress) {
		if (!this.getMainHandStack().isOf(Firearms.RIFLE)) {
			return;
		}
		Vec3d from = new Vec3d(this.getX(), this.getEyeY() - 0.15, this.getZ());
		Vec3d direction = new Vec3d(target.getX(), target.getBodyY(0.6), target.getZ()).subtract(from);
		Firearms.fire(this, direction, 4 - this.getWorld().getDifficulty().getId());
		this.sinceShot = 0;
	}
}
