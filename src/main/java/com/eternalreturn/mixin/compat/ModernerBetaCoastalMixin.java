package com.eternalreturn.mixin.compat;

import com.eternalreturn.worldgen.terrain.TerrainShape;
import com.eternalreturn.worldgen.variants.BiomeVariants;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Coastal sea sub-biomes (the mangrove lagoon: BiomeVariants.Variant.coastal, the owner's call 2026-11-10): only within
 * that many blocks of land, by the stepped sea floor's distance to the coast (TerrainShape.coastDistanceAt), a thin strip
 * along the shores; elsewhere in their cells, their parent sea. On Moderner Beta's fractal biome provider (biome coordinates and block
 * coordinates), which only Eternal Return worlds give these biomes.
 */
@Mixin(targets = "mod.bluestaggo.modernerbeta.level.biome.provider.BiomeProviderFractal", remap = false)
public abstract class ModernerBetaCoastalMixin {
	@Shadow
	private native RegistryEntry<Biome> getBiomeHolderFromId(Identifier id);

	@Inject(method = "getBiome", at = @At("RETURN"), cancellable = true, remap = false)
	private void eternalreturn$coastal(int x, int y, int z, CallbackInfoReturnable<RegistryEntry<Biome>> cir) {
		RegistryEntry<Biome> parent = this.eternalreturn$parent(cir.getReturnValue(), x, z);
		if (parent != null) {
			cir.setReturnValue(parent);
		}
	}

	@Inject(method = "getBiomeBlock", at = @At("RETURN"), cancellable = true, remap = false)
	private void eternalreturn$coastalBlock(int x, int y, int z, CallbackInfoReturnable<RegistryEntry<Biome>> cir) {
		RegistryEntry<Biome> parent = this.eternalreturn$parent(cir.getReturnValue(), x >> 2, z >> 2);
		if (parent != null) {
			cir.setReturnValue(parent);
		}
	}

	/** The parent sea for a coastal sub-biome out of its reach of land at this biome column, or null to keep the biome. */
	private RegistryEntry<Biome> eternalreturn$parent(RegistryEntry<Biome> biome, int quartX, int quartZ) {
		if (biome == null) {
			return null;
		}
		Identifier id = biome.getKey().map(key -> key.getValue()).orElse(null);
		BiomeVariants.Variant variant = id == null ? null : BiomeVariants.get(id);
		if (variant == null || variant.coastal() == null) {
			return null;
		}
		double distance = TerrainShape.coastDistanceAt(quartX, quartZ);
		if (distance < 0 || distance <= variant.coastal()) {
			return null;
		}
		try {
			return this.getBiomeHolderFromId(variant.base());
		} catch (RuntimeException e) {
			return null;
		}
	}
}
