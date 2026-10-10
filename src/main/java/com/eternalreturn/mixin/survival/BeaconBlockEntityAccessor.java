package com.eternalreturn.mixin.survival;

import net.minecraft.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The beacon's pyramid level, for the size of its area (BeaconZones). */
@Mixin(BeaconBlockEntity.class)
public interface BeaconBlockEntityAccessor {
	@Accessor("level")
	int eternalreturn$level();
}
