package com.eternalreturn.mobs.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The bone spider, a rare spider of the Nether (com.eternalreturn.mobs.NewMobs): four times a spider's health, and its
 * bite withers (4 seconds on Easy, 7 on Normal, 15 on Hard). One in six comes with a wither
 * skeleton riding it.
 */
public class BoneSpiderEntity extends SpiderEntity {
	public BoneSpiderEntity(EntityType<? extends SpiderEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createBoneSpiderAttributes() {
		return SpiderEntity.createSpiderAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 64.0);
	}

	@Override
	public boolean tryAttack(Entity target) {
		if (!super.tryAttack(target)) {
			return false;
		}
		if (target instanceof LivingEntity living) {
			Difficulty difficulty = this.getWorld().getDifficulty();
			int seconds = difficulty == Difficulty.HARD ? 15 : difficulty == Difficulty.NORMAL ? 7 : difficulty == Difficulty.EASY ? 4 : 0;
			if (seconds > 0) {
				living.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, seconds * 20, 0), this);
			}
		}
		return true;
	}

	@Nullable
	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, @Nullable EntityData data) {
		EntityData result = super.initialize(world, difficulty, reason, data);
		if (!this.hasPassengers() && world.getRandom().nextInt(6) == 0) {
			WitherSkeletonEntity rider = EntityType.WITHER_SKELETON.create(this.getWorld());
			if (rider != null) {
				rider.refreshPositionAndAngles(this.getX(), this.getY(), this.getZ(), this.getYaw(), 0.0F);
				rider.initialize(world, difficulty, SpawnReason.JOCKEY, null);
				rider.startRiding(this);
			}
		}
		return result;
	}
}
