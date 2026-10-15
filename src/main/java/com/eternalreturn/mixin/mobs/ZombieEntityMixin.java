package com.eternalreturn.mixin.mobs;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.mobs.SpawnGear;
import com.eternalreturn.mobs.ZombieHabits;
import com.eternalreturn.mobs.ai.MineTowardTargetGoal;
import com.eternalreturn.mobs.ai.ZombieEatMeatGoal;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.eternalreturn.mobs.ai.ReelInTargetGoal;
import com.eternalreturn.mobs.ai.ThrowPearlGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ZombieEntity.class)
public abstract class ZombieEntityMixin extends HostileEntity {
	protected ZombieEntityMixin(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
	}

	/**
	 * initGoals rather than initCustomGoals, so subclasses that replace their custom goals (drowned,
	 * zombified piglins) still get these. Drowned that spawn holding a fishing rod will use it.
	 */
	@Inject(method = "initGoals", at = @At("TAIL"))
	private void eternalreturn$addItemGoals(CallbackInfo ci) {
		this.goalSelector.add(3, new ReelInTargetGoal(this));
		this.goalSelector.add(3, new ThrowPearlGoal(this));
		ZombieEntity self = (ZombieEntity) (Object) this;
		this.goalSelector.add(2, new MineTowardTargetGoal(self));
		this.goalSelector.add(4, new ZombieEatMeatGoal(self));
		this.goalSelector.add(5, new com.eternalreturn.mobs.ai.BreakLightsGoal(self));
		this.goalSelector.add(3, new com.eternalreturn.mobs.ai.ZombieCrossbowGoal(self));
		this.goalSelector.add(3, new com.eternalreturn.mobs.ai.ZombiePotionGoal(self));
		// Adults hunt farm animals, after players and villagers (ZombieHabits), but not one a mob is riding (a baby zombie's cow, a chicken jockey's chicken).
		this.targetSelector.add(5, new ActiveTargetGoal<>(this, AnimalEntity.class, 10, true, false,
				animal -> !self.isBaby() && animal.getType().isIn(ZombieHabits.ZOMBIE_PREY) && EternalReturnConfig.get().mobs.zombiesHuntAnimals
						&& !com.eternalreturn.mobs.LargeMobs.isLarge(self)
						&& animal.getPassengerList().stream().noneMatch(rider -> rider instanceof net.minecraft.entity.mob.MobEntity)));
	}

	@Inject(method = "initialize", at = @At("RETURN"))
	private void eternalreturn$animalJockey(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, EntityData data,
			CallbackInfoReturnable<EntityData> cir) {
		ZombieHabits.tryAnimalJockey((ZombieEntity) (Object) this, world, difficulty, reason);
		com.eternalreturn.mobs.HeadGear.roll((ZombieEntity) (Object) this, world.getRandom());
		com.eternalreturn.mobs.LargeMobs.roll((ZombieEntity) (Object) this, world.getRandom());
		com.eternalreturn.mobs.ZombieVillagerJobs.onSpawn((ZombieEntity) (Object) this, world, difficulty, reason);
	}

	@Inject(method = "initEquipment", at = @At("TAIL"))
	private void eternalreturn$rollRodAndPearl(Random random, LocalDifficulty localDifficulty, CallbackInfo ci) {
		SpawnGear.rollZombieExtras(this, random);
		ZombieHabits.rollDiggingTool((ZombieEntity) (Object) this, random);
	}
}
