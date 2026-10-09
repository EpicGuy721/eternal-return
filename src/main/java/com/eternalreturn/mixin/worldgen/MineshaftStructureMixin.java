package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.worldgen.structures.MineshaftWoods;
import net.minecraft.structure.StructurePiecesCollector;
import net.minecraft.world.gen.structure.MineshaftStructure;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Picks an Eternal Return mineshaft's wood while its pieces are laid out (see MineshaftWoods). */
@Mixin(MineshaftStructure.class)
public abstract class MineshaftStructureMixin {
	@Inject(method = "addPieces", at = @At("HEAD"))
	private void eternalreturn$pickWood(StructurePiecesCollector collector, Structure.Context context, CallbackInfoReturnable<Integer> cir) {
		MineshaftWoods.enter(context);
	}

	@Inject(method = "addPieces", at = @At("RETURN"))
	private void eternalreturn$forgetWood(StructurePiecesCollector collector, Structure.Context context, CallbackInfoReturnable<Integer> cir) {
		MineshaftWoods.exit();
	}
}
