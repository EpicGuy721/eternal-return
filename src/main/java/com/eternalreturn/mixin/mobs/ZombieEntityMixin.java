package com.eternalreturn.mixin.mobs;

import com.eternalreturn.mobs.SpawnGear;
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
	}

	@Inject(method = "initEquipment", at = @At("TAIL"))
	private void eternalreturn$rollRodAndPearl(Random random, LocalDifficulty localDifficulty, CallbackInfo ci) {
		SpawnGear.rollZombieExtras(this, random);
	}
}
