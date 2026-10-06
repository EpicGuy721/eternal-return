package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.worldgen.caves.CaveWorlds;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.gen.carver.CarverContext;
import net.minecraft.world.gen.chunk.ChunkNoiseSampler;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.surfacebuilder.MaterialRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers which generator a carving context belongs to, and whether that is an Eternal Return world. */
@Mixin(CarverContext.class)
public abstract class CarverContextMixin implements CaveWorlds.Context {
	@Unique
	private NoiseChunkGenerator eternalreturn$generator;
	@Unique
	private boolean eternalreturn$eternalReturn;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void eternalreturn$rememberWorld(NoiseChunkGenerator generator, DynamicRegistryManager registryManager, HeightLimitView heightLimitView,
			ChunkNoiseSampler chunkNoiseSampler, NoiseConfig noiseConfig, MaterialRules.MaterialRule materialRule, CallbackInfo ci) {
		this.eternalreturn$generator = generator;
		this.eternalreturn$eternalReturn = generator != null && CaveWorlds.usesEternalReturnSettings(generator, registryManager);
	}

	@Override
	public NoiseChunkGenerator eternalreturn$generator() {
		return this.eternalreturn$generator;
	}

	@Override
	public boolean eternalreturn$isEternalReturn() {
		return this.eternalreturn$eternalReturn;
	}
}
