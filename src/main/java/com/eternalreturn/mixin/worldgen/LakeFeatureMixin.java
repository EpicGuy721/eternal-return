package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.worldgen.ores.OrePlacement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.LakeFeature;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lakes keep host stone where they would line themselves with plain stone (see OrePlacement.keepsHost). */
@SuppressWarnings("deprecation")
@Mixin(LakeFeature.class)
public abstract class LakeFeatureMixin {
	@Inject(method = "generate", at = @At("HEAD"))
	private void eternalreturn$enterLake(FeatureContext<LakeFeature.Config> context, CallbackInfoReturnable<Boolean> cir) {
		OrePlacement.enter(context);
	}

	@Inject(method = "generate", at = @At("RETURN"))
	private void eternalreturn$exitLake(FeatureContext<LakeFeature.Config> context, CallbackInfoReturnable<Boolean> cir) {
		OrePlacement.exit();
	}

	@WrapOperation(method = "generate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/StructureWorldAccess;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
	private boolean eternalreturn$keepHostStone(StructureWorldAccess world, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
		if (OrePlacement.keepsHost(state, world.getBlockState(pos))) {
			return false;
		}
		return original.call(world, pos, state, flags);
	}
}
