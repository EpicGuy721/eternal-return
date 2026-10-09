package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.worldgen.caves.CaveWorlds;
import com.eternalreturn.worldgen.structures.MineshaftWoods;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.structure.MineshaftStructure;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Predicate;

/**
 * In Eternal Return worlds, ordinary mineshafts may also start in the biomes of the tag
 * eternalreturn:has_structure/mineshaft (see MineshaftWoods). Nothing changes in other worlds.
 */
@Mixin(Structure.class)
public abstract class StructureMixin {
	@ModifyVariable(method = "createStructureStart", at = @At("HEAD"), argsOnly = true)
	private Predicate<RegistryEntry<Biome>> eternalreturn$moreMineshaftBiomes(Predicate<RegistryEntry<Biome>> biomes,
			@Local(argsOnly = true) DynamicRegistryManager registries, @Local(argsOnly = true) ChunkGenerator generator) {
		if ((Object) this instanceof MineshaftStructure mineshaft
				&& ((MineshaftStructureAccessor) mineshaft).eternalreturn$type() == MineshaftStructure.Type.NORMAL
				&& generator instanceof NoiseChunkGenerator noise && CaveWorlds.usesEternalReturnSettings(noise, registries)) {
			return biomes.or(biome -> biome.isIn(MineshaftWoods.EXTRA_MINESHAFT_BIOMES));
		}
		return biomes;
	}
}
