package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.worldgen.Strongholds;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.structure.StructureStart;
import net.minecraft.world.gen.StructureTerrainAdaptation;
import net.minecraft.world.gen.StructureWeightSampler;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A stronghold at ground level (Strongholds) has the ground raised to meet it, like a village, instead of being buried. */
@Mixin(StructureWeightSampler.class)
public abstract class StructureWeightSamplerMixin {
	@WrapOperation(method = "method_42694", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/gen/structure/Structure;getTerrainAdaptation()Lnet/minecraft/world/gen/StructureTerrainAdaptation;"))
	private static StructureTerrainAdaptation eternalreturn$surfaceStronghold(Structure structure, Operation<StructureTerrainAdaptation> original,
			@Local(argsOnly = true) StructureStart start) {
		return Strongholds.isSurface(start) ? StructureTerrainAdaptation.BEARD_THIN : original.call(structure);
	}
}
