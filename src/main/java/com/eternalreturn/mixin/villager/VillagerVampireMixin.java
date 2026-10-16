package com.eternalreturn.mixin.villager;

import com.eternalreturn.mobs.Vampires;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A villager bitten by a vampire counts down to turning into one (Vampires); saved as eternalreturn_turn_ticks. */
@Mixin(VillagerEntity.class)
public abstract class VillagerVampireMixin implements Vampires.Bitten {
	@Unique
	private int eternalreturn$turnTicks;

	@Override
	public int eternalreturn$turnTicks() {
		return this.eternalreturn$turnTicks;
	}

	@Override
	public void eternalreturn$setTurnTicks(int ticks) {
		this.eternalreturn$turnTicks = ticks;
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void eternalreturn$turn(CallbackInfo ci) {
		Vampires.tickBitten((VillagerEntity) (Object) this);
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void eternalreturn$saveTurn(NbtCompound nbt, CallbackInfo ci) {
		if (this.eternalreturn$turnTicks > 0) {
			nbt.putInt("eternalreturn_turn_ticks", this.eternalreturn$turnTicks);
		}
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void eternalreturn$loadTurn(NbtCompound nbt, CallbackInfo ci) {
		this.eternalreturn$turnTicks = nbt.getInt("eternalreturn_turn_ticks");
	}
}
