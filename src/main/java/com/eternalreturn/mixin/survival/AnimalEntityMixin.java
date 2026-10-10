package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.AnimalHunger;
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

/** Animal hunger (AnimalHunger): when it last ate, ticking, saving, and feeding by hand. */
@Mixin(AnimalEntity.class)
public abstract class AnimalEntityMixin extends PassiveEntity implements AnimalHunger.Holder {
	@Unique
	private static final String FED_KEY = "EternalReturnLastFed";

	@Unique
	private long eternalreturn$lastFed = Long.MIN_VALUE;

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

	@Inject(method = "mobTick", at = @At("HEAD"))
	private void eternalreturn$hunger(CallbackInfo ci) {
		AnimalHunger.tick((AnimalEntity) (Object) this);
	}

	/** Every bite of breeding food a player gives counts as a meal. */
	@Inject(method = "eat", at = @At("HEAD"))
	private void eternalreturn$fedByHand(PlayerEntity player, Hand hand, ItemStack stack, CallbackInfo ci) {
		AnimalHunger.fed((AnimalEntity) (Object) this);
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void eternalreturn$saveFed(NbtCompound nbt, CallbackInfo ci) {
		if (this.eternalreturn$lastFed != Long.MIN_VALUE) {
			nbt.putLong(FED_KEY, this.eternalreturn$lastFed);
		}
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void eternalreturn$loadFed(NbtCompound nbt, CallbackInfo ci) {
		this.eternalreturn$lastFed = nbt.contains(FED_KEY, NbtElement.LONG_TYPE) ? nbt.getLong(FED_KEY) : Long.MIN_VALUE;
	}
}
