package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.worldgen.ores.OrePlacement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.OreFeatureConfig;
import net.minecraft.world.gen.feature.ScatteredOreFeature;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The scattered-ore feature places variants too (see OrePlacement); its target test goes through OreFeature.shouldPlace. */
@Mixin(ScatteredOreFeature.class)
public abstract class ScatteredOreFeatureMixin {
	@Inject(method = "generate", at = @At("HEAD"))
	private void eternalreturn$enterOre(FeatureContext<OreFeatureConfig> context, CallbackInfoReturnable<Boolean> cir) {
		OrePlacement.enter(context);
	}

	@Inject(method = "generate", at = @At("RETURN"))
	private void eternalreturn$exitOre(FeatureContext<OreFeatureConfig> context, CallbackInfoReturnable<Boolean> cir) {
		OrePlacement.exit();
	}

	@WrapOperation(method = "generate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/StructureWorldAccess;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
	private boolean eternalreturn$placeVariant(StructureWorldAccess world, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
		return original.call(world, pos, OrePlacement.place(state, world.getBlockState(pos)), flags);
	}
}
