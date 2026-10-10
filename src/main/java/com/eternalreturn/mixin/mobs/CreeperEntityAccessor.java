package com.eternalreturn.mixin.mobs;

import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.mob.CreeperEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The creeper's "charged" flag, which the philosopher's stone flips (PhilosophersStone). */
@Mixin(CreeperEntity.class)
public interface CreeperEntityAccessor {
	@Accessor("CHARGED")
	static TrackedData<Boolean> eternalreturn$charged() {
		throw new AssertionError();
	}
}
