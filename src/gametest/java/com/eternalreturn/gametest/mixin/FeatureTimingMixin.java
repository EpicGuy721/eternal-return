package com.eternalreturn.gametest.mixin;

import com.eternalreturn.gametest.WorldgenProfile;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;

/** Feature timing for the worldgen benchmark (WorldgenProfile); does nothing unless switched on. */
@Mixin(PlacedFeature.class)
public abstract class FeatureTimingMixin {
	@WrapMethod(method = "generate(Lnet/minecraft/world/StructureWorldAccess;Lnet/minecraft/world/gen/chunk/ChunkGenerator;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/BlockPos;)Z")
	private boolean eternalreturn$time(StructureWorldAccess world, ChunkGenerator generator, Random random, BlockPos pos, Operation<Boolean> original) {
		if (!WorldgenProfile.ON) {
			return original.call(world, generator, random, pos);
		}
		boolean outer = WorldgenProfile.enter();
		long start = System.nanoTime();
		try {
			return original.call(world, generator, random, pos);
		} finally {
			long nanos = System.nanoTime() - start;
			String key = "";
			if (outer) {
				Identifier id = world.getRegistryManager().get(RegistryKeys.PLACED_FEATURE).getId((PlacedFeature) (Object) this);
				key = "feature " + id;
			}
			WorldgenProfile.exit(outer, key, nanos);
		}
	}
}
