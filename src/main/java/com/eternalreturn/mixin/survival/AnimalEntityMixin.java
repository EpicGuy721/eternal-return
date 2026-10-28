package com.eternalreturn.mixin.survival;

import com.eternalreturn.duck.JockeyMount;
import com.eternalreturn.survival.AnimalHunger;
import com.eternalreturn.survival.Farming;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Animal hunger and thirst (AnimalHunger): when it last ate and drank, ticking, saving, feeding by hand, troughs; and jockey mounts despawning. */
@Mixin(AnimalEntity.class)
public abstract class AnimalEntityMixin extends PassiveEntity implements AnimalHunger.Holder, JockeyMount, Farming.Holder, com.eternalreturn.items.Milk.CowMilk.Holder {
	@Unique
	private static final String FED_KEY = "EternalReturnLastFed";

	@Unique
	private long eternalreturn$lastFed = Long.MIN_VALUE;
	@Unique
	private long eternalreturn$lastWatered = Long.MIN_VALUE;
	/** A cow's: when its milk is full again (Milk.CowMilk). */
	@Unique
	private long eternalreturn$milkFullAt;

	@Override
	public long eternalreturn$milkFullAt() {
		return this.eternalreturn$milkFullAt;
	}

	@Override
	public void eternalreturn$setMilkFullAt(long time) {
		this.eternalreturn$milkFullAt = time;
	}

	/** Spawned as a mount for a hostile rider (a polar bear under a stray, a cow under a baby zombie): despawns like the rider. */
	@Unique
	private boolean eternalreturn$jockeyMount;

	@Override
	public boolean eternalreturn$isJockeyMount() {
		return this.eternalreturn$jockeyMount;
	}

	@Override
	public void eternalreturn$setJockeyMount(boolean mount) {
		this.eternalreturn$jockeyMount = mount;
	}

	@Unique
	private boolean eternalreturn$goldenFed;
	@Unique
	private boolean eternalreturn$stunted;

	@Override
	public boolean eternalreturn$goldenFed() {
		return this.eternalreturn$goldenFed;
	}

	@Override
	public void eternalreturn$setGoldenFed(boolean fed) {
		this.eternalreturn$goldenFed = fed;
	}

	@Override
	public boolean eternalreturn$stunted() {
		return this.eternalreturn$stunted;
	}

	@Override
	public void eternalreturn$setStunted(boolean stunted) {
		this.eternalreturn$stunted = stunted;
		if (stunted && this.getBreedingAge() >= 0) {
			this.setBreedingAge(-1);
		}
	}

	/** Pig litters (Farming): the rest of the litter after the first piglet. */
	@Inject(method = "breed(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/passive/AnimalEntity;)V", at = @At("TAIL"))
	private void eternalreturn$litter(net.minecraft.server.world.ServerWorld world, AnimalEntity other, CallbackInfo ci) {
		Farming.litter(world, (AnimalEntity) (Object) this, other);
	}

	@Inject(method = "canImmediatelyDespawn", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$mountDespawns(double distanceSquared, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if (this.eternalreturn$jockeyMount) {
			cir.setReturnValue(true);
		}
	}

	protected AnimalEntityMixin(EntityType<? extends PassiveEntity> entityType, World world) {
		super(entityType, world);
	}

	@Override
	public long eternalreturn$lastFed() {
		return this.eternalreturn$lastFed;
	}

	@Override
	public void eternalreturn$setLastFed(long time) {
		this.eternalreturn$lastFed = time;
	}

	@Override
	public long eternalreturn$lastWatered() {
		return this.eternalreturn$lastWatered;
	}

	@Override
	public void eternalreturn$setLastWatered(long time) {
		this.eternalreturn$lastWatered = time;
	}

	/** Hungry or thirsty, it goes to a trough (or open water): Troughs.FeedGoal. */
	@Inject(method = "<init>", at = @At("TAIL"))
	private void eternalreturn$feedGoal(EntityType<? extends AnimalEntity> type, World world, CallbackInfo ci) {
		if (!world.isClient) {
			this.goalSelector.add(4, new com.eternalreturn.survival.Troughs.FeedGoal((AnimalEntity) (Object) this));
		}
	}

	@Inject(method = "mobTick", at = @At("HEAD"))
	private void eternalreturn$hunger(CallbackInfo ci) {
		AnimalHunger.tick((AnimalEntity) (Object) this);
	}

	/** Every bite of breeding food a player gives counts as a meal. */
	@Inject(method = "eat", at = @At("HEAD"))
	private void eternalreturn$fedByHand(PlayerEntity player, Hand hand, ItemStack stack, CallbackInfo ci) {
		AnimalHunger.fed((AnimalEntity) (Object) this);
		if (stack.isOf(net.minecraft.item.Items.GOLDEN_CARROT)) {
			this.eternalreturn$goldenFed = true;
		}
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void eternalreturn$saveFed(NbtCompound nbt, CallbackInfo ci) {
		if (this.eternalreturn$lastFed != Long.MIN_VALUE) {
			nbt.putLong(FED_KEY, this.eternalreturn$lastFed);
		}
		if (this.eternalreturn$lastWatered != Long.MIN_VALUE) {
			nbt.putLong("EternalReturnLastWatered", this.eternalreturn$lastWatered);
		}
		if (this.eternalreturn$milkFullAt != 0L) {
			nbt.putLong("EternalReturnMilkFullAt", this.eternalreturn$milkFullAt);
		}
		if (this.eternalreturn$jockeyMount) {
			nbt.putBoolean("EternalReturnJockeyMount", true);
		}
		if (this.eternalreturn$stunted) {
			nbt.putBoolean("EternalReturnStunted", true);
		}
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void eternalreturn$loadFed(NbtCompound nbt, CallbackInfo ci) {
		this.eternalreturn$lastFed = nbt.contains(FED_KEY, NbtElement.LONG_TYPE) ? nbt.getLong(FED_KEY) : Long.MIN_VALUE;
		this.eternalreturn$lastWatered = nbt.contains("EternalReturnLastWatered", NbtElement.LONG_TYPE) ? nbt.getLong("EternalReturnLastWatered") : Long.MIN_VALUE;
		this.eternalreturn$milkFullAt = nbt.getLong("EternalReturnMilkFullAt");
		this.eternalreturn$jockeyMount = nbt.getBoolean("EternalReturnJockeyMount");
		this.eternalreturn$stunted = nbt.getBoolean("EternalReturnStunted");
	}
}
