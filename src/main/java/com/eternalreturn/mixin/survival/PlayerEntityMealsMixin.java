package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.Meals;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** Healing from food (Meals): who may eat, and the healing still to come, saved with the player. */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMealsMixin implements Meals.Holder {
	@Unique
	private final List<float[]> eternalreturn$meals = new ArrayList<>();

	@Override
	public List<float[]> eternalreturn$meals() {
		return this.eternalreturn$meals;
	}

	/** Hunger is gone, so the food bar is always full: ordinary food may be eaten when hurt instead. */
	@Inject(method = "canConsume", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$eatWhenHurt(boolean ignoreHunger, CallbackInfoReturnable<Boolean> cir) {
		if (Meals.enabled()) {
			cir.setReturnValue(Meals.canEat((PlayerEntity) (Object) this, ignoreHunger));
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void eternalreturn$heal(CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (!self.getWorld().isClient) {
			Meals.tick(self);
			com.eternalreturn.logic.Autoload.tick(self);
		}
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void eternalreturn$saveMeals(NbtCompound nbt, CallbackInfo ci) {
		Meals.write((PlayerEntity) (Object) this, nbt);
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void eternalreturn$loadMeals(NbtCompound nbt, CallbackInfo ci) {
		Meals.read((PlayerEntity) (Object) this, nbt);
	}
}
