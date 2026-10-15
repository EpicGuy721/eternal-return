package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.Growing;
import net.minecraft.block.BambooBlock;
import net.minecraft.block.BambooShootBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.CocoaBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.SugarCaneBlock;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rain makes these grow faster (Growing). */
@Mixin({CropBlock.class, SugarCaneBlock.class, BambooBlock.class, BambooShootBlock.class, CocoaBlock.class, SweetBerryBushBlock.class})
public abstract class RainGrowthMixin {
	@Inject(method = "randomTick", at = @At("TAIL"))
	private void eternalreturn$rain(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
		Growing.rainTick(state, world, pos, random);
	}
}
