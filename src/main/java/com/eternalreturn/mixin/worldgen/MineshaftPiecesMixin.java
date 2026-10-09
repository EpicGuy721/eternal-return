package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.worldgen.structures.MineshaftWoods;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.structure.MineshaftGenerator;
import net.minecraft.world.gen.structure.MineshaftStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Corridors and crossings build with their piece's wood (see MineshaftPartMixin). */
@Mixin({MineshaftGenerator.MineshaftCorridor.class, MineshaftGenerator.MineshaftCrossing.class})
public abstract class MineshaftPiecesMixin {
	@WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/structure/MineshaftStructure$Type;getPlanks()Lnet/minecraft/block/BlockState;"))
	private BlockState eternalreturn$planks(MineshaftStructure.Type type, Operation<BlockState> original) {
		MineshaftWoods.Wood wood = ((MineshaftWoods.Holder) this).eternalreturn$wood();
		return wood != null ? wood.planksState() : original.call(type);
	}

	@WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/structure/MineshaftStructure$Type;getLog()Lnet/minecraft/block/BlockState;"), require = 0)
	private BlockState eternalreturn$log(MineshaftStructure.Type type, Operation<BlockState> original) {
		MineshaftWoods.Wood wood = ((MineshaftWoods.Holder) this).eternalreturn$wood();
		return wood != null ? wood.logState() : original.call(type);
	}

	@WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/structure/MineshaftStructure$Type;getFence()Lnet/minecraft/block/BlockState;"), require = 0)
	private BlockState eternalreturn$fence(MineshaftStructure.Type type, Operation<BlockState> original) {
		MineshaftWoods.Wood wood = ((MineshaftWoods.Holder) this).eternalreturn$wood();
		return wood != null ? wood.fenceState() : original.call(type);
	}
}
