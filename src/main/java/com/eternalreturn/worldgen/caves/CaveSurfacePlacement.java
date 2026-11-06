package com.eternalreturn.worldgen.caves;

import com.eternalreturn.EternalReturn;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.feature.FeaturePlacementContext;
import net.minecraft.world.gen.placementmodifier.PlacementModifier;
import net.minecraft.world.gen.placementmodifier.PlacementModifierType;

import java.util.stream.Stream;

/**
 * Finds a cave's floor or ceiling (eternalreturn:cave_surface; the mushroom and magma caves' features). With a "range",
 * from the spot it looks down ("floor": the first open block resting on a solid one, which it gives) or up ("ceiling": the
 * first solid block over an open one, which it gives) for up to that many blocks. With range 0 it reads the spot's whole
 * column, from the spot's height down to the bottom, and gives one of the floors (or ceilings) there whose biome has this
 * feature, at random: a cave biome fills only patches of the underground, and a random height only lands in its air now
 * and then.
 */
public class CaveSurfacePlacement extends PlacementModifier {
	public static final MapCodec<CaveSurfacePlacement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.BOOL.fieldOf("ceiling").forGetter(p -> p.ceiling),
			Codec.intRange(0, 64).fieldOf("range").forGetter(p -> p.range)).apply(instance, CaveSurfacePlacement::new));
	public static final PlacementModifierType<CaveSurfacePlacement> TYPE = Registry.register(Registries.PLACEMENT_MODIFIER_TYPE,
			EternalReturn.id("cave_surface"), () -> CODEC);

	private final boolean ceiling;
	private final int range;

	public CaveSurfacePlacement(boolean ceiling, int range) {
		this.ceiling = ceiling;
		this.range = range;
	}

	public static void register() {
		// Loads the type.
	}

	private static boolean open(BlockState state) {
		return state.isAir();
	}

	private boolean surface(FeaturePlacementContext context, BlockPos at) {
		BlockState here = context.getBlockState(at);
		if (!open(here)) {
			return false;
		}
		BlockPos other = this.ceiling ? at.up() : at.down();
		BlockState there = context.getBlockState(other);
		return there.getFluidState().isEmpty() && there.isOpaqueFullCube(context.getWorld(), other);
	}

	private static boolean allowed(FeaturePlacementContext context, BlockPos pos) {
		return context.getPlacedFeature().map(feature -> context.getChunkGenerator().getGenerationSettings(context.getWorld().getBiome(pos))
				.isFeatureAllowed(feature)).orElse(true);
	}

	@Override
	public Stream<BlockPos> getPositions(FeaturePlacementContext context, Random random, BlockPos pos) {
		if (this.range == 0) {
			java.util.List<BlockPos> found = new java.util.ArrayList<>();
			BlockPos.Mutable at = pos.mutableCopy();
			for (int y = pos.getY(); y > context.getBottomY() + 1; y--) {
				at.setY(y);
				if (this.surface(context, at)) {
					BlockPos spot = this.ceiling ? at.up() : at.toImmutable();
					if (allowed(context, spot)) {
						found.add(spot);
					}
				}
			}
			return found.isEmpty() ? Stream.empty() : Stream.of(found.get(random.nextInt(found.size())));
		}
		BlockPos.Mutable at = pos.mutableCopy();
		int bottom = context.getBottomY();
		for (int i = 0; i < this.range && at.getY() > bottom + 1 && at.getY() < context.getBottomY() + context.getHeight() - 2; i++) {
			BlockState here = context.getBlockState(at);
			if (this.ceiling) {
				BlockState above = context.getBlockState(at.up());
				if (open(here) && !above.isAir() && above.getFluidState().isEmpty() && above.isOpaqueFullCube(context.getWorld(), at.up())) {
					return Stream.of(at.up().toImmutable());
				}
				at.move(0, 1, 0);
			} else {
				BlockState below = context.getBlockState(at.down());
				if (open(here) && below.isOpaqueFullCube(context.getWorld(), at.down())) {
					return Stream.of(at.toImmutable());
				}
				at.move(0, -1, 0);
			}
		}
		return Stream.empty();
	}

	@Override
	public PlacementModifierType<?> getType() {
		return TYPE;
	}
}
