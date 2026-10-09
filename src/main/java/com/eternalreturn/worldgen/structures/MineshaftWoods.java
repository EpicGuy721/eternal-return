package com.eternalreturn.worldgen.structures;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.worldgen.caves.CaveWorlds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Mineshaft woods, in Eternal Return worlds only: a mineshaft's supports, planks and fences are the wood
 * of the biome it starts in, its signature tree (biome tags eternalreturn:mineshaft_wood/<wood>), and oak
 * in biomes with no tag (treeless ones too, badlands included). Vanilla only knows oak and dark oak (the
 * badlands "mesa" mineshaft); every piece of an Eternal Return mineshaft carries its wood instead
 * (MineshaftPartMixin), saved with the piece, and the pieces ask it for their blocks.
 * <p>
 * A new wood is one entry in WOODS plus its biome tag. Mineshafts also start in the biomes of the tag
 * eternalreturn:has_structure/mineshaft (Moderner Beta's biomes that vanilla's mineshaft tag misses), in
 * Eternal Return worlds only (StructureMixin).
 */
public final class MineshaftWoods {
	public record Wood(String name, Block log, Block planks, Block fence) {
		public BlockState logState() {
			return this.log.getDefaultState();
		}

		public BlockState planksState() {
			return this.planks.getDefaultState();
		}

		public BlockState fenceState() {
			return this.fence.getDefaultState();
		}

		TagKey<Biome> biomes() {
			return TagKey.of(RegistryKeys.BIOME, EternalReturn.id("mineshaft_wood/" + this.name));
		}
	}

	public static final Wood OAK = new Wood("oak", Blocks.OAK_LOG, Blocks.OAK_PLANKS, Blocks.OAK_FENCE);
	public static final List<Wood> WOODS = List.of(OAK,
			new Wood("birch", Blocks.BIRCH_LOG, Blocks.BIRCH_PLANKS, Blocks.BIRCH_FENCE),
			new Wood("spruce", Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_FENCE),
			new Wood("jungle", Blocks.JUNGLE_LOG, Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_FENCE),
			new Wood("acacia", Blocks.ACACIA_LOG, Blocks.ACACIA_PLANKS, Blocks.ACACIA_FENCE),
			new Wood("dark_oak", Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE),
			new Wood("cherry", Blocks.CHERRY_LOG, Blocks.CHERRY_PLANKS, Blocks.CHERRY_FENCE));
	/** Biomes where mineshafts start in Eternal Return worlds besides vanilla's minecraft:has_structure/mineshaft. */
	public static final TagKey<Biome> EXTRA_MINESHAFT_BIOMES = TagKey.of(RegistryKeys.BIOME, EternalReturn.id("has_structure/mineshaft"));

	/** The wood of the mineshaft whose pieces are being laid out on this thread, or null (vanilla's wood). */
	private static final ThreadLocal<Wood> CURRENT = new ThreadLocal<>();

	private MineshaftWoods() {
	}

	public static boolean isEternalReturn(Structure.Context context) {
		return context.chunkGenerator() instanceof NoiseChunkGenerator generator
				&& CaveWorlds.usesEternalReturnSettings(generator, context.dynamicRegistryManager());
	}

	/** A mineshaft's pieces are about to be laid out: pick its wood from the biome where it starts. */
	public static void enter(Structure.Context context) {
		if (!isEternalReturn(context)) {
			CURRENT.remove();
			return;
		}
		int x = context.chunkPos().getOffsetX(2);
		int z = context.chunkPos().getOffsetZ(2);
		RegistryEntry<Biome> biome = context.biomeSource().getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(64), BiomeCoords.fromBlock(z),
				context.noiseConfig().getMultiNoiseSampler());
		CURRENT.set(woodFor(biome));
	}

	public static void exit() {
		CURRENT.remove();
	}

	/** The wood for a new piece, or null outside an Eternal Return world. */
	@Nullable
	public static Wood current() {
		return CURRENT.get();
	}

	public static Wood woodFor(RegistryEntry<Biome> biome) {
		for (Wood wood : WOODS) {
			if (wood != OAK && biome.isIn(wood.biomes())) {
				return wood;
			}
		}
		return OAK;
	}

	@Nullable
	public static Wood byName(String name) {
		for (Wood wood : WOODS) {
			if (wood.name().equals(name)) {
				return wood;
			}
		}
		return null;
	}

	/** Implemented by every mineshaft piece (MineshaftPartMixin). */
	public interface Holder {
		@Nullable
		Wood eternalreturn$wood();
	}
}
