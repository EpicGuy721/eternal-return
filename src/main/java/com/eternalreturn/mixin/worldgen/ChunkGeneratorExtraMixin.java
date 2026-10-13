package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.worldgen.Dungeons;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nether dungeons and wild nether wart, after a Nether chunk's own features, in Eternal Return worlds (Dungeons). */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorExtraMixin {
	@Inject(method = "generateFeatures", at = @At("TAIL"))
	private void eternalreturn$netherExtras(StructureWorldAccess world, Chunk chunk, StructureAccessor structureAccessor, CallbackInfo ci) {
		Dungeons.netherChunk((ChunkGenerator) (Object) this, world, chunk);
	}
}
