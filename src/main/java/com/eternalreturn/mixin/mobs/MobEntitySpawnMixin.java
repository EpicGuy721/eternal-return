package com.eternalreturn.mixin.mobs;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.mobs.BabyVariants;
import com.eternalreturn.mobs.SpawnGear;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
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

@Mixin(MobEntity.class)
public abstract class MobEntitySpawnMixin extends LivingEntity {
	@Unique
	private static final int BOAT_ESCAPE_DELAY_TICKS = 20;
	@Unique
	private static final int BOAT_HIT_INTERVAL_TICKS = 10;
	/** Each hit adds amount * 10 wobble and boats break above 40, so 3 breaks one in two quick hits. */
	@Unique
	private static final float BOAT_HIT_DAMAGE = 3.0F;

	@Unique
	private int eternalreturn$ticksInBoat;

	protected MobEntitySpawnMixin(EntityType<? extends LivingEntity> entityType, World world) {
		super(entityType, world);
	}

	@Inject(method = "initialize", at = @At("HEAD"))
	private void eternalreturn$rollBaby(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
			EntityData entityData, CallbackInfoReturnable<EntityData> cir) {
		BabyVariants.rollOnSpawn((MobEntity) (Object) this, world.getRandom());
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void eternalreturn$saveBaby(NbtCompound nbt, CallbackInfo ci) {
		if (BabyVariants.supportsBaby(this) && this.isBaby()) {
			nbt.putBoolean("IsBaby", true);
		}
	}

	/** Same key as baby zombies, so /summon skeleton ~ ~ ~ {IsBaby:1b} works too. */
	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void eternalreturn$loadBaby(NbtCompound nbt, CallbackInfo ci) {
		if (BabyVariants.supportsBaby(this) && nbt.contains("IsBaby", NbtElement.BYTE_TYPE)) {
			((MobEntity) (Object) this).setBaby(nbt.getBoolean("IsBaby"));
		}
	}

	@Inject(method = "initEquipment", at = @At("TAIL"))
	private void eternalreturn$moreArmor(Random random, LocalDifficulty localDifficulty, CallbackInfo ci) {
		SpawnGear.rollExtraArmor((MobEntity) (Object) this, random, localDifficulty);
	}

	@Inject(method = "enchantEquipment(Lnet/minecraft/world/ServerWorldAccess;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/entity/EquipmentSlot;Lnet/minecraft/world/LocalDifficulty;)V",
			at = @At("HEAD"), cancellable = true)
	private void eternalreturn$moreArmorEnchants(ServerWorldAccess world, Random random, EquipmentSlot slot, LocalDifficulty localDifficulty, CallbackInfo ci) {
		if (SpawnGear.enchantArmorPiece((MobEntity) (Object) this, world, random, slot, localDifficulty)) {
			ci.cancel();
		}
	}

	/** Monsters stuck in a boat beat on it until it breaks and drops, which dismounts them. */
	@Inject(method = "tickNewAi", at = @At("TAIL"))
	private void eternalreturn$breakOutOfBoat(CallbackInfo ci) {
		if (!(this instanceof Monster) || !(this.getVehicle() instanceof BoatEntity boat)
				|| !EternalReturnConfig.get().mobs.monstersBreakOutOfBoats) {
			this.eternalreturn$ticksInBoat = 0;
			return;
		}
		int ticks = ++this.eternalreturn$ticksInBoat;
		if (ticks < BOAT_ESCAPE_DELAY_TICKS || (ticks - BOAT_ESCAPE_DELAY_TICKS) % BOAT_HIT_INTERVAL_TICKS != 0) {
			return;
		}
		this.swingHand(Hand.MAIN_HAND);
		boat.damage(this.getDamageSources().mobAttack(this), BOAT_HIT_DAMAGE);
		this.getWorld().playSound(null, boat.getX(), boat.getY(), boat.getZ(),
				boat.isRemoved() ? SoundEvents.BLOCK_WOOD_BREAK : SoundEvents.BLOCK_WOOD_HIT, SoundCategory.HOSTILE, 1.0F, 1.0F);
	}
}
