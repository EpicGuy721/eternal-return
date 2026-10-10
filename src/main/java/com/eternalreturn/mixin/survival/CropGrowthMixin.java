package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.CropBiomes;
import net.minecraft.block.BlockState;
import net.minecraft.block.CactusBlock;
import net.minecraft.block.CocoaBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.block.StemBlock;
import net.minecraft.block.SugarCaneBlock;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Crops outside their ideal biomes skip most growth steps (CropBiomes). */
@Mixin({CropBlock.class, StemBlock.class, SugarCaneBlock.class, CactusBlock.class, CocoaBlock.class, SweetBerryBushBlock.class, NetherWartBlock.class})
public abstract class CropGrowthMixin {
	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$idealBiome(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
		if (!CropBiomes.mayGrow(world, pos, state.getBlock(), random)) {
			ci.cancel();
		}
	}
}
