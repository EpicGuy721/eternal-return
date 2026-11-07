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

/** Structure timing for the worldgen benchmark (WorldgenProfile); does nothing unless switched on. */
@Mixin(StructureStart.class)
public abstract class StructureTimingMixin {
	@WrapMethod(method = "place")
	private void eternalreturn$time(StructureWorldAccess world, StructureAccessor accessor, ChunkGenerator generator, Random random, BlockBox box, ChunkPos pos,
			Operation<Void> original) {
		if (!WorldgenProfile.ON) {
			original.call(world, accessor, generator, random, box, pos);
			return;
		}
		boolean outer = WorldgenProfile.enter();
		long start = System.nanoTime();
		try {
			original.call(world, accessor, generator, random, box, pos);
		} finally {
			long nanos = System.nanoTime() - start;
			String key = "";
			if (outer) {
				key = "structure " + world.getRegistryManager().get(RegistryKeys.STRUCTURE).getId(((StructureStart) (Object) this).getStructure());
			}
			WorldgenProfile.exit(outer, key, nanos);
		}
	}
}
