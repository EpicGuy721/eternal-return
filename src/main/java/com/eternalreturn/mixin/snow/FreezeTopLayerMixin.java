package com.eternalreturn.mixin.snow;

import com.eternalreturn.blocks.Snowlogging;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.FreezeTopLayerFeature;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Snowy biomes' generation lays a layer of snow in and on slabs, stairs, walls, fences and gates (Snowlogging) where
 * it would lay a snow layer on a full block, and marks the whole tops it lays snow layers on, so snowy villages' and
 * igloos' roofs and fences are snowed on.
 */
@Mixin(FreezeTopLayerFeature.class)
public abstract class FreezeTopLayerMixin {
	@WrapOperation(method = "generate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/biome/Biome;canSetSnow(Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;)Z"))
	private boolean eternalreturn$snowOnBlocks(Biome biome, WorldView view, BlockPos pos, Operation<Boolean> original,
			@Local(argsOnly = true) FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		if (original.call(biome, view, pos)) {
			Snowlogging.snowLiesOn(world, pos.toImmutable(), Block.NOTIFY_LISTENERS);
			return true;
		}
		Snowlogging.snowfall(world, biome, pos.toImmutable(), 1, Block.NOTIFY_LISTENERS);
		return false;
	}
}
