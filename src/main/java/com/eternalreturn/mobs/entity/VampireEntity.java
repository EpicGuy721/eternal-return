package com.eternalreturn.mobs.entity;

import com.eternalreturn.mobs.Vampires;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.IllagerEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * The vampire, an illager of the autumnal forest's nights (com.eternalreturn.mobs.Vampires): very fast, it hunts
 * players, villagers and iron golems. In a fight it calls up bats (VampireBatEntity) that go for its target and
 * poison players they bite. A villager it or its bats bite turns into a vampire 30 seconds later; it leaves a bitten
 * villager alone. Badly hurt with a player close, it turns into a bat and flies off, and turns back once it is safe.
 * It burns in daylight and drops nothing, not even experience.
 */
public class VampireEntity extends IllagerEntity {
	/** Age before which it can't take bat form again (just after turning back from one). */
	private int noBatUntil;
	private int nextBats;

	public VampireEntity(EntityType<? extends VampireEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 0;
	}

	public static DefaultAttributeContainer.Builder createVampireAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.42)
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 24.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
	}

	/** Natural spawns: in the dark under the open sky, so at night (the autumnal forest's spawn list has them). */
	public static boolean canSpawn(EntityType<VampireEntity> type, ServerWorldAccess world, SpawnReason reason, BlockPos pos, Random random) {
		return HostileEntity.canSpawnInDark(type, world, reason, pos, random) && (reason != SpawnReason.NATURAL || world.isSkyVisible(pos));
	}

	@Override
	protected void initGoals() {
		super.initGoals();
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(1, new BatFormGoal());
		this.goalSelector.add(2, new SummonBatsGoal());
		this.goalSelector.add(3, new MeleeAttackGoal(this, 1.0, false));
		this.goalSelector.add(8, new WanderAroundFarGoal(this, 0.6));
		this.goalSelector.add(9, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
		this.goalSelector.add(10, new LookAroundGoal(this));
		this.targetSelector.add(1, new RevengeGoal(this, RaiderEntity.class).setGroupRevenge());
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
		this.targetSelector.add(3, new ActiveTargetGoal<>(this, MerchantEntity.class, 10, true, false, Vampires::biteable));
		this.targetSelector.add(3, new ActiveTargetGoal<>(this, IronGolemEntity.class, true));
	}

	@Override
	public void tickMovement() {
		if (this.isAlive() && this.isAffectedByDaylight()) {
			this.setOnFireFor(8.0F);
		}
		super.tickMovement();
	}

	/** A bite: a villager it lands on is turned, and it lets that one be. */
	@Override
	public boolean tryAttack(Entity target) {
		boolean hit = super.tryAttack(target);
		if (hit && target instanceof VillagerEntity villager && Vampires.infect(villager)) {
			this.setTarget(null);
		}
		return hit;
	}

	@Override
	public State getState() {
		return this.isAttacking() ? State.ATTACKING : State.CROSSED;
	}

	@Override
	public void addBonusForWave(ServerWorld world, int wave, boolean unused) {
	}

	@Override
	public SoundEvent getCelebratingSound() {
		return SoundEvents.ENTITY_ILLUSIONER_AMBIENT;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_ILLUSIONER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.entity.damage.DamageSource source) {
		return SoundEvents.ENTITY_ILLUSIONER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_ILLUSIONER_DEATH;
	}

	public void delayBatForm(int ticks) {
		this.noBatUntil = this.age + ticks;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("eternalreturn_no_bat", Math.max(0, this.noBatUntil - this.age));
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.noBatUntil = this.age + nbt.getInt("eternalreturn_no_bat");
	}

	/** With a target within 16 blocks, now and then (every 10 to 15 seconds) two or three bats, at most 4 at a time. */
	class SummonBatsGoal extends Goal {
		SummonBatsGoal() {
			this.setControls(EnumSet.noneOf(Control.class));
		}

		@Override
		public boolean canStart() {
			LivingEntity target = VampireEntity.this.getTarget();
			return target != null && target.isAlive() && VampireEntity.this.age >= VampireEntity.this.nextBats
					&& VampireEntity.this.squaredDistanceTo(target) < 256.0 && Vampires.batsOf(VampireEntity.this) < Vampires.MAX_BATS;
		}

		@Override
		public void start() {
			Vampires.summonBats(VampireEntity.this, VampireEntity.this.getTarget());
			VampireEntity.this.nextBats = VampireEntity.this.age + 200 + VampireEntity.this.random.nextInt(100);
		}

		@Override
		public boolean shouldContinue() {
			return false;
		}
	}

	/** Below a third of its health with a player within 8 blocks: off as a bat. */
	class BatFormGoal extends Goal {
		BatFormGoal() {
			this.setControls(EnumSet.noneOf(Control.class));
		}

		@Override
		public boolean canStart() {
			VampireEntity self = VampireEntity.this;
			return self.age >= self.noBatUntil && self.getHealth() < self.getMaxHealth() / 3.0F && Vampires.playerNear(self, 8.0) != null;
		}

		@Override
		public void start() {
			Vampires.toBat(VampireEntity.this);
		}

		@Override
		public boolean shouldContinue() {
			return false;
		}
	}
}
