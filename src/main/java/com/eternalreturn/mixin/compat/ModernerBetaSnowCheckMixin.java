package com.eternalreturn.mixin.compat;

import com.eternalreturn.blocks.Snowlogging;
import com.eternalreturn.compat.ModernerBetaHooks;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Moderner Beta's snow check, during its snow-and-freeze step (ModernerBetaSnowMixin), at the point where it has found the
 * spot cold, low enough and dark enough for snow and looks at what is there: snow it will lay on a whole top marks that
 * top (Snowlogging.snowLiesOn); a plant there, or a slab, stairs, wall, fence or gate under it, gets a layer of snow
 * logged in it (Snowlogging.snowfallCold), as vanilla's step gives them (FreezeTopLayerMixin).
 */
@Mixin(targets = "mod.bluestaggo.modernerbeta.level.biome.ClimateHelper", remap = false)
public abstract class ModernerBetaSnowCheckMixin {
	@Inject(method = "shouldSnow", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;isAir()Z", remap = true))
	private static void eternalreturn$snowInBlocks(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) WorldView view, @Local(argsOnly = true) BlockPos pos) {
		StructureWorldAccess world = ModernerBetaHooks.SNOWING.get();
		if (world == null || world != view) {
			return;
		}
		BlockPos space = pos.toImmutable();
		if (world.getBlockState(space).isAir() && Blocks.SNOW.getDefaultState().canPlaceAt(world, space)) {
			Snowlogging.snowLiesOn(world, space, Block.NOTIFY_LISTENERS);
		} else {
			Snowlogging.snowfallCold(world, space, 1, Block.NOTIFY_LISTENERS);
		}
		ModernerBetaHooks.SNOW_CHECKS.incrementAndGet();
	}
}
