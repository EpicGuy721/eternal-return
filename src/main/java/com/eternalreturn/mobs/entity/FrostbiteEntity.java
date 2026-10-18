package com.eternalreturn.mobs.entity;

import com.eternalreturn.items.IceBall;
import com.eternalreturn.survival.Freezing;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * The frostbite, a zombie of the cold (com.eternalreturn.mobs.NewMobs): a little tougher (24 health), its hits give
 * Freezing for 10 seconds, and one in six carries ice balls in its off hand and throws them from 4 to 16 blocks.
 * It spawns in snowy biomes in place of most zombies, and a zombie left in powder snow turns into one.
 */
public class FrostbiteEntity extends ZombieEntity {
	public FrostbiteEntity(EntityType<? extends ZombieEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createFrostbiteAttributes() {
		return ZombieEntity.createZombieAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 24.0);
	}

	@Override
	protected void initCustomGoals() {
		super.initCustomGoals();
		this.goalSelector.add(2, new IceBallGoal());
	}

	@Override
	protected void initEquipment(Random random, LocalDifficulty difficulty) {
		super.initEquipment(random, difficulty);
		if (random.nextInt(6) == 0) {
			this.equipStack(EquipmentSlot.OFFHAND, new ItemStack(IceBall.ITEM, 16));
		}
	}

	@Override
	public boolean tryAttack(Entity target) {
		boolean hit = super.tryAttack(target);
		if (hit && target instanceof LivingEntity living) {
			living.addStatusEffect(new StatusEffectInstance(Freezing.EFFECT, 200), this);
		}
		return hit;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_STRAY_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.entity.damage.DamageSource source) {
		return SoundEvents.ENTITY_STRAY_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_STRAY_DEATH;
	}

	/** With ice balls in its off hand and its target 4 to 16 blocks off and in sight: one every 2 to 3 seconds. */
	class IceBallGoal extends Goal {
		private int cooldown = 20;

		IceBallGoal() {
			this.setControls(EnumSet.noneOf(Control.class));
		}

		@Override
		public boolean canStart() {
			LivingEntity target = FrostbiteEntity.this.getTarget();
			if (target == null || !target.isAlive() || !FrostbiteEntity.this.getOffHandStack().isOf(IceBall.ITEM)) {
				return false;
			}
			double distance = FrostbiteEntity.this.squaredDistanceTo(target);
			return distance >= 16.0 && distance <= 256.0 && FrostbiteEntity.this.getVisibilityCache().canSee(target);
		}

		@Override
		public boolean shouldContinue() {
			return this.canStart();
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (--this.cooldown > 0) {
				return;
			}
			this.cooldown = 40 + FrostbiteEntity.this.random.nextInt(20);
			IceBall.throwAt(FrostbiteEntity.this, FrostbiteEntity.this.getTarget());
			FrostbiteEntity.this.swingHand(net.minecraft.util.Hand.OFF_HAND);
		}
	}
}
