package com.eternalreturn.mixin.survival;

import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The beacon's level and chosen effect (BeaconZones, BeaconPower). */
@Mixin(BeaconBlockEntity.class)
public interface BeaconBlockEntityAccessor {
	@Accessor("level")
	int eternalreturn$level();

	@Accessor("primary")
	RegistryEntry<StatusEffect> eternalreturn$primary();
}
