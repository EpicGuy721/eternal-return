package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.Farming;
import net.minecraft.entity.passive.PassiveEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A stunted baby (Farming.poisonousPotato) never reaches adulthood: its age stops just short of it. */
@Mixin(PassiveEntity.class)
public abstract class PassiveEntityMixin {
	@ModifyVariable(method = "setBreedingAge", at = @At("HEAD"), argsOnly = true)
	private int eternalreturn$stunted(int age) {
		return this instanceof Farming.Holder holder && holder.eternalreturn$stunted() && age >= 0 ? -1 : age;
	}
}
