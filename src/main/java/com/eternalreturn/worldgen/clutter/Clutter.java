package com.eternalreturn.worldgen.clutter;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.worldgen.WorldgenFeatures;
import com.eternalreturn.worldgen.stone.BiomeStoneProcessor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeaturePlacementContext;
import net.minecraft.world.gen.placementmodifier.PlacementModifier;
import net.minecraft.world.gen.placementmodifier.PlacementModifierType;

import java.util.Map;
import java.util.stream.Stream;

/**
 * Clutter: small objects on the ground, each biome its own (phase 1 of the worldgen expansion, docs/worldgen.md).
 * So far boulders: data/eternalreturn/worldgen/placed_feature/boulder.json sets how many per chunk in each biome
 * (eternalreturn:biome_density), the templates are data/eternalreturn/structure/boulder. Eternal Return worlds only.
 */
public final class Clutter {
	public static final Feature<BoulderFeature.Config> BOULDER = Registry.register(Registries.FEATURE, EternalReturn.id("boulder"), new BoulderFeature());
	/** Giant mushrooms (phase 6, batch D): the giant mushroom island and the roofed fungi forest. */
	public static final Feature<GiantMushroomFeature.Config> GIANT_MUSHROOM = Registry.register(Registries.FEATURE, EternalReturn.id("giant_mushroom"),
			new GiantMushroomFeature());

	/**
	 * Spots on the surface of the chunk, as many on average as the biome's density (placement eternalreturn:biome_density):
	 * max_per_chunk tries at random columns, each kept with chance density / max_per_chunk, the density of the biome on
	 * that column's surface. "density" maps a biome id, or a biome tag as "#namespace:path", to boulders per chunk; an
	 * id wins over a tag, tags are tried in the order written, and a biome in none takes "default". A biome variant
	 * without its own id here takes its "boulders" from the variant table, else its base's id (BiomeVariants). Ids and tags of mods
	 * that aren't installed simply never match.
	 */
	public static final class BiomeDensity extends PlacementModifier {
		public static final MapCodec<BiomeDensity> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Codec.intRange(1, 64).fieldOf("max_per_chunk").forGetter(placement -> placement.maxPerChunk),
				Codec.floatRange(0, 64).optionalFieldOf("default", 0f).forGetter(placement -> placement.fallback),
				Codec.unboundedMap(Codec.STRING, Codec.floatRange(0, 64)).fieldOf("density").forGetter(placement -> placement.density))
				.apply(instance, BiomeDensity::new));
		private final int maxPerChunk;
		private final float fallback;
		private final Map<String, Float> density;

		public BiomeDensity(int maxPerChunk, float fallback, Map<String, Float> density) {
			this.maxPerChunk = maxPerChunk;
			this.fallback = fallback;
			this.density = density;
		}

		public float densityOf(RegistryEntry<Biome> biome) {
			Float exact = biome.getKey().map(key -> this.density.get(key.getValue().toString())).orElse(null);
			if (exact != null) {
				return exact;
			}
			// A biome variant: its own boulders (tools/worldgen/variants.json), else its base's by id.
			com.eternalreturn.worldgen.variants.BiomeVariants.Variant variant = com.eternalreturn.worldgen.variants.BiomeVariants.get(biome);
			if (variant != null) {
				if (variant.boulders() != null) {
					return variant.boulders();
				}
				Float base = this.density.get(variant.base().toString());
				if (base != null) {
					return base;
				}
			}
			for (Map.Entry<String, Float> entry : this.density.entrySet()) {
				if (entry.getKey().startsWith("#")) {
					Identifier tag = Identifier.tryParse(entry.getKey().substring(1));
					if (tag != null && biome.isIn(TagKey.of(RegistryKeys.BIOME, tag))) {
						return entry.getValue();
					}
				}
			}
			return this.fallback;
		}

		@Override
		public Stream<BlockPos> getPositions(FeaturePlacementContext context, Random random, BlockPos pos) {
			Stream.Builder<BlockPos> spots = Stream.builder();
			for (int i = 0; i < this.maxPerChunk; i++) {
				int x = pos.getX() + random.nextInt(16);
				int z = pos.getZ() + random.nextInt(16);
				BlockPos spot = new BlockPos(x, context.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z), z);
				// Drawn before the biome is looked up, so every try uses the same random numbers whatever the biome.
				float roll = random.nextFloat() * this.maxPerChunk;
				if (roll < this.densityOf(context.getWorld().getBiome(spot))) {
					spots.add(spot);
				}
			}
			return spots.build();
		}

		@Override
		public PlacementModifierType<?> getType() {
			return BIOME_DENSITY;
		}
	}

	public static final PlacementModifierType<BiomeDensity> BIOME_DENSITY = Registry.register(Registries.PLACEMENT_MODIFIER_TYPE,
			EternalReturn.id("biome_density"), () -> BiomeDensity.CODEC);

	private Clutter() {
	}

	/**
	 * Boulders go into every overworld biome's list, last of the underground decoration, so no other feature's place
	 * in the decoration order moves: after the ores and the dirt and gravel blobs, which would otherwise eat into
	 * them, and before the trees and plants, which then grow round them. The density placement leaves out the biomes that get none.
	 */
	public static void register() {
		// Registers the processor that turns the boulders' stone into the biome's.
		java.util.Objects.requireNonNull(BiomeStoneProcessor.TYPE);
		BiomeModifications.addFeature(WorldgenFeatures.overworld().and(context -> EternalReturnConfig.get().worldgen.boulders),
				GenerationStep.Feature.UNDERGROUND_DECORATION, RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalReturn.id("boulder")));
	}
}
