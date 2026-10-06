package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.worldgen.caves.CaveWorlds;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.carver.Carver;
import net.minecraft.world.gen.carver.CarverContext;
import net.minecraft.world.gen.carver.CarvingMask;
import net.minecraft.world.gen.carver.ConfiguredCarver;
import net.minecraft.world.gen.chunk.AquiferSampler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

/**
 * Every carver runs through ConfiguredCarver.carve, under vanilla's generator and Moderner Beta's
 * alike (Moderner Beta swaps in its Beta-style carvers before this call). In Eternal Return worlds
 * this skips every carver that isn't the cave engine's (see CaveWorlds.skipsCarver). Skipping after
 * shouldCarve leaves the other carvers' random seeds untouched, since each is seeded separately.
 */
@Mixin(ConfiguredCarver.class)
public abstract class ConfiguredCarverMixin {
	@Shadow
	public abstract Carver<?> carver();

	@Inject(method = "carve", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$onlyCaveEngineInEternalReturn(CarverContext context, Chunk chunk, Function<BlockPos, RegistryEntry<Biome>> posToBiome,
			Random random, AquiferSampler aquiferSampler, ChunkPos pos, CarvingMask mask, CallbackInfoReturnable<Boolean> cir) {
		if (CaveWorlds.skipsCarver(context, this.carver())) {
			cir.setReturnValue(false);
		}
	}
}
