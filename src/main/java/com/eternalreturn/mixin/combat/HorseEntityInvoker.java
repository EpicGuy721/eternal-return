package com.eternalreturn.mixin.combat;

import net.minecraft.entity.passive.HorseColor;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.passive.HorseMarking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A horse's coat colour and markings together, which the philosopher's stone changes (PhilosophersStone). */
@Mixin(HorseEntity.class)
public interface HorseEntityInvoker {
	@Invoker("setHorseVariant")
	void eternalreturn$setHorseVariant(HorseColor color, HorseMarking marking);
}
