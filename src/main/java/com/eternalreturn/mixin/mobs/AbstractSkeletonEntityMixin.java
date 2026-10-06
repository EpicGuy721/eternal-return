package com.eternalreturn.mixin.mobs;

import com.eternalreturn.mobs.BabyVariants;
import com.eternalreturn.mobs.Jockeys;
import com.eternalreturn.mobs.SkeletonSidearm;
import com.eternalreturn.mobs.SpawnGear;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSkeletonEntity.class)
public abstract class AbstractSkeletonEntityMixin extends HostileEntity {
	/** Registered from AbstractSkeletonEntity's own static init, so it gets its id before any subclass registers theirs. */
	@Unique
	private static final TrackedData<Boolean> ETERNALRETURN_BABY = DataTracker.registerData(AbstractSkeletonEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	@Unique
	private int eternalreturn$nextSidearmSwapAge;

	protected AbstractSkeletonEntityMixin(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ETERNALRETURN_BABY, false);
	}

	@Override
	public boolean isBaby() {
		return this.getDataTracker().get(ETERNALRETURN_BABY);
	}

	@Override
	public void setBaby(boolean baby) {
		this.getDataTracker().set(ETERNALRETURN_BABY, baby);
		BabyVariants.applySpeed(this, baby);
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		if (ETERNALRETURN_BABY.equals(data)) {
			this.calculateDimensions();
		}
		super.onTrackedDataSet(data);
	}

	/** At the head of tickMovement, i.e. before this tick's goals run rather than in the middle of them. */
	@Inject(method = "tickMovement", at = @At("HEAD"))
	private void eternalreturn$swapSidearm(CallbackInfo ci) {
		if (!this.getWorld().isClient && !this.isAiDisabled() && this.age >= this.eternalreturn$nextSidearmSwapAge
				&& SkeletonSidearm.tick(this)) {
			this.eternalreturn$nextSidearmSwapAge = this.age + SkeletonSidearm.SWAP_COOLDOWN_TICKS;
		}
	}

	@Inject(method = "initEquipment", at = @At("TAIL"))
	private void eternalreturn$rollSword(Random random, LocalDifficulty localDifficulty, CallbackInfo ci) {
		SpawnGear.rollSkeletonSword(this, random);
	}

	@Inject(method = "initialize", at = @At("RETURN"))
	private void eternalreturn$polarBearMount(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
			EntityData entityData, CallbackInfoReturnable<EntityData> cir) {
		if (this.getType() == EntityType.STRAY) {
			Jockeys.tryPolarBearMount(this, world, difficulty, spawnReason);
		}
	}

	/**
	 * Vanilla picks the bow goal if either hand has a bow. A skeleton that has drawn its sword keeps the
	 * bow in its off hand, so let a sword in the main hand win.
	 */
	@WrapOperation(method = "updateAttackType", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/projectile/ProjectileUtil;getHandPossiblyHolding(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/Item;)Lnet/minecraft/util/Hand;"))
	private Hand eternalreturn$swordInMainHandWins(LivingEntity entity, Item item, Operation<Hand> original) {
		return entity.getMainHandStack().isIn(ItemTags.SWORDS) ? Hand.MAIN_HAND : original.call(entity, item);
	}
}
