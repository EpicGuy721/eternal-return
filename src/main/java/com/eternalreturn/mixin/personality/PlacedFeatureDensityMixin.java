package com.eternalreturn.mixin.personality;

import com.eternalreturn.worldgen.personality.PersonalityFeatures;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A biome's trees, grass patches and boulders placed with their patch's density (PersonalityFeatures). */
@Mixin(PlacedFeature.class)
public abstract class PlacedFeatureDensityMixin {
	@Inject(method = "generate(Lnet/minecraft/world/StructureWorldAccess;Lnet/minecraft/world/gen/chunk/ChunkGenerator;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/BlockPos;)Z",
			at = @At("HEAD"), cancellable = true)
	private void eternalreturn$density(StructureWorldAccess world, ChunkGenerator generator, Random random, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		Boolean placed = PersonalityFeatures.generate((PlacedFeature) (Object) this, world, generator, random, pos);
		if (placed != null) {
			cir.setReturnValue(placed);
		}
	}
}
