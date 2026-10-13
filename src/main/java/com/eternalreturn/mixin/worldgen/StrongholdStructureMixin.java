package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.worldgen.Strongholds;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.structure.StructurePiecesCollector;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.structure.StrongholdStructure;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Some strongholds go at ground level in Eternal Return worlds (Strongholds). */
@Mixin(StrongholdStructure.class)
public abstract class StrongholdStructureMixin {
	@WrapOperation(method = "addPieces", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/structure/StructurePiecesCollector;shiftInto(IILnet/minecraft/util/math/random/Random;I)I"))
	private static int eternalreturn$maybeSurface(StructurePiecesCollector collector, int seaLevel, int minY, Random random, int topPenalty,
			Operation<Integer> original, @Local(argsOnly = true) Structure.Context context) {
		return Strongholds.place(collector, context, seaLevel, minY, topPenalty);
	}
}
