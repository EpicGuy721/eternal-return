package com.eternalreturn.mobs.entity;

import com.eternalreturn.items.Speargun;
import com.eternalreturn.mobs.Jockeys;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.goal.BowAttackGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.PrioritizedGoal;
import net.minecraft.entity.ai.goal.SwimAroundGoal;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.ai.pathing.SwimNavigation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.passive.DolphinEntity;
import net.minecraft.entity.CrossbowUser;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * The Sunken: a skeleton of the sea, grown over with coral (fire, horn or bubble: its variant). It carries a speargun
 * (com.eternalreturn.items.Speargun, dropped as a skeleton drops its bow), draws and shoots it as a pillager does a
 * crossbow, and reels in what its harpoon hooks. After something in the water it swims as a drowned does; otherwise
 * it sinks and walks the bottom. Skeletons that stay under water long enough become Sunkens (SkeletonSinkingMixin);
 * a Sunken never becomes a stray. Some spawn riding a dolphin (Jockeys.tryDolphinMount), which then goes where the
 * Sunken wants, as a spider goes where its skeleton wants, and never runs out of air while it carries one.
 */
public class SunkenEntity extends SkeletonEntity implements CrossbowUser {
	public static final String[] VARIANTS = {"fire", "horn", "bubble"};
	private static final TrackedData<Integer> VARIANT = DataTracker.registerData(SunkenEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> CHARGING = DataTracker.registerData(SunkenEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private final SwimNavigation waterNavigation;
	private final MobNavigation landNavigation;
	/** Made when first needed: the skeleton's constructor already asks for it. */
	@Nullable
	private SpeargunAttackGoal speargunGoal;

	public SunkenEntity(EntityType<? extends SunkenEntity> type, World world) {
		super(type, world);
		this.moveControl = new SunkenMoveControl(this);
		this.setPathfindingPenalty(PathNodeType.WATER, 0.0F);
		this.waterNavigation = new SwimNavigation(this, world);
		this.landNavigation = new MobNavigation(this, world);
	}

	/** In water (and the block below it), dark, deep enough and not on peaceful; or from a spawner. */
	public static boolean canSpawn(EntityType<SunkenEntity> type, ServerWorldAccess world, SpawnReason reason, BlockPos pos, Random random) {
		if (SpawnReason.isAnySpawner(reason)) {
			return world.getDifficulty() != Difficulty.PEACEFUL;
		}
		return world.getFluidState(pos).isIn(FluidTags.WATER) && world.getFluidState(pos.down()).isIn(FluidTags.WATER)
				&& world.getDifficulty() != Difficulty.PEACEFUL && pos.getY() < world.getSeaLevel() - 5 && HostileEntity.isSpawnDark(world, pos, random)
				&& random.nextInt(3) == 0;
	}

	@Override
	protected void initGoals() {
		super.initGoals();
		this.goalSelector.add(6, new SwimAroundGoal(this, 1.0, 40));
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(VARIANT, 0);
		builder.add(CHARGING, false);
	}

	public int getVariant() {
		return MathHelper.clamp(this.dataTracker.get(VARIANT), 0, VARIANTS.length - 1);
	}

	public void setVariant(int variant) {
		this.dataTracker.set(VARIANT, MathHelper.clamp(variant, 0, VARIANTS.length - 1));
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putString("Variant", VARIANTS[this.getVariant()]);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		String variant = nbt.getString("Variant");
		for (int i = 0; i < VARIANTS.length; i++) {
			if (VARIANTS[i].equals(variant)) {
				this.setVariant(i);
			}
		}
	}

	@Override
	protected void initEquipment(Random random, LocalDifficulty difficulty) {
		this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Speargun.SPEARGUN));
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, @Nullable EntityData data) {
		data = super.initialize(world, difficulty, reason, data);
		this.setVariant(world.getRandom().nextInt(VARIANTS.length));
		Jockeys.tryDolphinMount(this, world, difficulty, reason);
		return data;
	}

	/** A Sunken stays one. */
	@Override
	protected void convertToStray() {
	}

	@Override
	public boolean canUseRangedWeapon(RangedWeaponItem weapon) {
		return weapon == Speargun.SPEARGUN || super.canUseRangedWeapon(weapon);
	}

	/** With a speargun, the speargun's goal instead of the skeleton's bow or melee goal. */
	@Override
	public void updateAttackType() {
		if (this.getWorld() == null || this.getWorld().isClient) {
			return;
		}
		if (this.speargunGoal == null) {
			this.speargunGoal = new SpeargunAttackGoal(this, 1.0, 16.0F);
		}
		this.goalSelector.remove(this.speargunGoal);
		if (!this.isHolding(Speargun.SPEARGUN)) {
			super.updateAttackType();
			return;
		}
		for (PrioritizedGoal goal : List.copyOf(this.goalSelector.getGoals())) {
			if (goal.getGoal() instanceof MeleeAttackGoal || goal.getGoal() instanceof BowAttackGoal) {
				this.goalSelector.remove(goal.getGoal());
			}
		}
		this.goalSelector.add(4, this.speargunGoal);
	}

	// The speargun, as a pillager's crossbow.

	public boolean isCharging() {
		return this.dataTracker.get(CHARGING);
	}

	@Override
	public void setCharging(boolean charging) {
		this.dataTracker.set(CHARGING, charging);
	}

	@Override
	public void postShoot() {
		this.despawnCounter = 0;
	}

	/**
	 * The loaded speargun's shot at the target, as a pillager's crossbow shot (CrossbowUser.shoot only finds a hand
	 * holding the crossbow item itself).
	 */
	@Override
	public void shootAt(LivingEntity target, float pullProgress) {
		ItemStack stack = this.getMainHandStack();
		if (stack.getItem() instanceof CrossbowItem crossbow) {
			crossbow.shootAll(this.getWorld(), this, Hand.MAIN_HAND, stack, 1.6F, 14 - this.getWorld().getDifficulty().getId() * 4, target);
		}
		this.postShoot();
	}

	@Override
	public void tick() {
		super.tick();
		// A dolphin carrying a Sunken goes where the Sunken wants, so it can't go up for air: it doesn't need to.
		if (!this.getWorld().isClient && this.getVehicle() instanceof DolphinEntity dolphin) {
			dolphin.setAir(dolphin.getMaxAir());
		}
	}

	// Swimming, as a drowned swims.

	boolean isTargetingUnderwater() {
		LivingEntity target = this.getTarget();
		return target != null && target.isTouchingWater();
	}

	@Override
	public void travel(Vec3d movementInput) {
		if (this.isLogicalSideForUpdatingMovement() && this.isTouchingWater() && this.isTargetingUnderwater()) {
			this.updateVelocity(0.01F, movementInput);
			this.move(MovementType.SELF, this.getVelocity());
			this.setVelocity(this.getVelocity().multiply(0.9));
		} else {
			super.travel(movementInput);
		}
	}

	@Override
	public void updateSwimming() {
		if (!this.getWorld().isClient) {
			if (this.canMoveVoluntarily() && this.isTouchingWater() && this.isTargetingUnderwater()) {
				this.navigation = this.waterNavigation;
				this.setSwimming(true);
			} else {
				this.navigation = this.landNavigation;
				this.setSwimming(false);
			}
		}
	}

	@Override
	public boolean isPushedByFluids() {
		return !this.isSwimming();
	}

	static class SunkenMoveControl extends MoveControl {
		private final SunkenEntity sunken;

		SunkenMoveControl(SunkenEntity sunken) {
			super(sunken);
			this.sunken = sunken;
		}

		@Override
		public void tick() {
			LivingEntity target = this.sunken.getTarget();
			if (this.sunken.isTargetingUnderwater() && this.sunken.isTouchingWater()) {
				if (target != null && target.getY() > this.sunken.getY()) {
					this.sunken.setVelocity(this.sunken.getVelocity().add(0.0, 0.002, 0.0));
				}
				if (this.state != MoveControl.State.MOVE_TO || this.sunken.getNavigation().isIdle()) {
					this.sunken.setMovementSpeed(0.0F);
					return;
				}
				double dx = this.targetX - this.sunken.getX();
				double dy = this.targetY - this.sunken.getY();
				double dz = this.targetZ - this.sunken.getZ();
				double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
				dy /= distance;
				float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
				this.sunken.setYaw(this.wrapDegrees(this.sunken.getYaw(), yaw, 90.0F));
				this.sunken.bodyYaw = this.sunken.getYaw();
				float speed = (float) (this.speed * this.sunken.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
				float eased = MathHelper.lerp(0.125F, this.sunken.getMovementSpeed(), speed);
				this.sunken.setMovementSpeed(eased);
				this.sunken.setVelocity(this.sunken.getVelocity().add(eased * dx * 0.005, eased * dy * 0.1, eased * dz * 0.005));
			} else {
				if (!this.sunken.isOnGround()) {
					this.sunken.setVelocity(this.sunken.getVelocity().add(0.0, -0.008, 0.0));
				}
				super.tick();
			}
		}
	}

	/** CrossbowAttackGoal's draw, hold and shoot, for the speargun. */
	static class SpeargunAttackGoal extends Goal {
		private enum Stage {
			UNCHARGED, CHARGING, CHARGED, READY_TO_ATTACK
		}

		private final SunkenEntity actor;
		private final double speed;
		private final float squaredRange;
		private Stage stage = Stage.UNCHARGED;
		private int seeingTargetTicker;
		private int chargedTicksLeft;
		private int cooldown;

		SpeargunAttackGoal(SunkenEntity actor, double speed, float range) {
			this.actor = actor;
			this.speed = speed;
			this.squaredRange = range * range;
			this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			LivingEntity target = this.actor.getTarget();
			return target != null && target.isAlive() && this.actor.isHolding(Speargun.SPEARGUN);
		}

		/** For as long as the target lives and it has the speargun (stopping forgets the target). */
		@Override
		public boolean shouldContinue() {
			return this.canStart();
		}

		private boolean isUncharged() {
			return this.stage == Stage.UNCHARGED;
		}

		@Override
		public void stop() {
			this.actor.setAttacking(false);
			this.actor.setTarget(null);
			this.seeingTargetTicker = 0;
			if (this.actor.isUsingItem()) {
				this.actor.clearActiveItem();
				this.actor.setCharging(false);
				this.actor.getActiveItem().set(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.DEFAULT);
			}
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = this.actor.getTarget();
			if (target == null) {
				return;
			}
			boolean sees = this.actor.getVisibilityCache().canSee(target);
			if (sees != this.seeingTargetTicker > 0) {
				this.seeingTargetTicker = 0;
			}
			this.seeingTargetTicker += sees ? 1 : -1;
			boolean approach = (this.actor.squaredDistanceTo(target) > this.squaredRange || this.seeingTargetTicker < 5) && this.chargedTicksLeft == 0;
			if (approach) {
				if (--this.cooldown <= 0) {
					this.actor.getNavigation().startMovingTo(target, this.isUncharged() ? this.speed : this.speed * 0.5);
					this.cooldown = 20 + this.actor.getRandom().nextInt(20);
				}
			} else {
				this.cooldown = 0;
				this.actor.getNavigation().stop();
			}
			this.actor.getLookControl().lookAt(target, 30.0F, 30.0F);
			switch (this.stage) {
				case UNCHARGED -> {
					if (!approach) {
						this.actor.setCurrentHand(Hand.MAIN_HAND);
						this.stage = Stage.CHARGING;
						this.actor.setCharging(true);
					}
				}
				case CHARGING -> {
					if (!this.actor.isUsingItem()) {
						this.stage = Stage.UNCHARGED;
					}
					if (this.actor.getItemUseTime() >= CrossbowItem.getPullTime(this.actor.getActiveItem(), this.actor)) {
						this.actor.stopUsingItem();
						this.stage = Stage.CHARGED;
						this.chargedTicksLeft = 20 + this.actor.getRandom().nextInt(20);
						this.actor.setCharging(false);
					}
				}
				case CHARGED -> {
					if (--this.chargedTicksLeft <= 0) {
						this.chargedTicksLeft = 0;
						this.stage = Stage.READY_TO_ATTACK;
					}
				}
				case READY_TO_ATTACK -> {
					if (sees) {
						this.actor.shootAt(target, 1.0F);
						this.stage = Stage.UNCHARGED;
					}
				}
			}
		}
	}
}
