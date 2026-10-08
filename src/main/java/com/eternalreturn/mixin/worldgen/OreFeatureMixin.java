package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.worldgen.ores.OrePlacement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.structure.rule.RuleTest;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.gen.feature.OreFeature;
import net.minecraft.world.gen.feature.OreFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ore variants: host stones count as stone for ore targets, and an ore placed into one becomes its variant (see OrePlacement). */
@Mixin(OreFeature.class)
public abstract class OreFeatureMixin {
	@Inject(method = "generate", at = @At("HEAD"))
	private void eternalreturn$enterOre(FeatureContext<OreFeatureConfig> context, CallbackInfoReturnable<Boolean> cir) {
		OrePlacement.enter(context);
	}

	@Inject(method = "generate", at = @At("RETURN"))
	private void eternalreturn$exitOre(FeatureContext<OreFeatureConfig> context, CallbackInfoReturnable<Boolean> cir) {
		OrePlacement.exit();
	}

	@WrapOperation(method = "shouldPlace", at = @At(value = "INVOKE", target = "Lnet/minecraft/structure/rule/RuleTest;test(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/random/Random;)Z"))
	private static boolean eternalreturn$hostStoneAsStone(RuleTest rule, BlockState state, Random random, Operation<Boolean> original) {
		return original.call(rule, OrePlacement.asTarget(state), random);
	}

	@WrapOperation(method = "generateVeinPart", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/ChunkSection;setBlockState(IIILnet/minecraft/block/BlockState;Z)Lnet/minecraft/block/BlockState;"))
	private BlockState eternalreturn$placeVariant(ChunkSection section, int x, int y, int z, BlockState state, boolean lock, Operation<BlockState> original) {
		return original.call(section, x, y, z, OrePlacement.place(state, section.getBlockState(x, y, z)), lock);
	}
}
