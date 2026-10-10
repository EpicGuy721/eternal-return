package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.worldgen.EternalReturnServer;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.StructureSet;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/** In Eternal Return worlds each dimension places only the essential structures (EternalReturnServer). */
@Mixin(StructurePlacementCalculator.class)
public abstract class StructurePlacementCalculatorMixin {
	@ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
	private static List<RegistryEntry<StructureSet>> eternalreturn$essentialOnly(List<RegistryEntry<StructureSet>> sets) {
		return EternalReturnServer.filter(sets);
	}
}
