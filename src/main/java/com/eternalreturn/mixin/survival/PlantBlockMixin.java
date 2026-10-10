package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.Farming;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.BlockState;
import net.minecraft.block.FlowerbedBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Pink petals stand on still water (Farming). */
@Mixin(PlantBlock.class)
public abstract class PlantBlockMixin {
	@ModifyReturnValue(method = "canPlantOnTop", at = @At("RETURN"))
	private boolean eternalreturn$petalsOnWater(boolean can, BlockState floor, BlockView world, BlockPos pos) {
		return can || (Object) this instanceof FlowerbedBlock && world instanceof WorldView view && Farming.waterHoldsPetals(view, pos, floor);
	}
}
