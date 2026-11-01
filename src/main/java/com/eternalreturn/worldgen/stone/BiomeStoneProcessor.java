package com.eternalreturn.worldgen.stone;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.processor.StructureProcessor;
import net.minecraft.structure.processor.StructureProcessorType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A template's plain stone becomes the stone of the ground it stands on (processor eternalreturn:biome_stone): the
 * first stone or host stone (block tag eternalreturn:biome_host_stones) straight down from the placement's pivot,
 * so one placement is all one stone and always matches the rock beneath, even across a biome border. Where no stone
 * lies within 32 blocks below, the biome's: each host stone's biomes are the biome tag
 * eternalreturn:biome_stone/&lt;block path&gt;. Both tags are written by build_preset.py from the biome stone table.
 * With worldgen.biomeStone off the ground is plain stone, and so is the template. "stones" (a block tag, optional)
 * limits what it may become: a ground stone outside the tag gives plain stone (boulders: eternalreturn:boulder_stones).
 */
public final class BiomeStoneProcessor extends StructureProcessor {
	public static final BiomeStoneProcessor INSTANCE = new BiomeStoneProcessor(Optional.empty());
	public static final MapCodec<BiomeStoneProcessor> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			TagKey.unprefixedCodec(RegistryKeys.BLOCK).optionalFieldOf("stones").forGetter(processor -> processor.stones))
			.apply(instance, BiomeStoneProcessor::new));
	public static final StructureProcessorType<BiomeStoneProcessor> TYPE = Registry.register(Registries.STRUCTURE_PROCESSOR,
			EternalReturn.id("biome_stone"), () -> CODEC);

	private final Optional<TagKey<Block>> stones;

	public BiomeStoneProcessor(Optional<TagKey<Block>> stones) {
		this.stones = stones;
	}

	/** The stone of this biome: its host stone, or plain stone. */
	public static Block stoneOf(RegistryEntry<Biome> biome) {
		if (EternalReturnConfig.get().worldgen.biomeStone) {
			for (RegistryEntry<Block> host : Registries.BLOCK.iterateEntries(BiomeStoneSurface.HOST_STONES)) {
				String path = host.getKey().orElseThrow().getValue().getPath();
				if (biome.isIn(TagKey.of(RegistryKeys.BIOME, EternalReturn.id("biome_stone/" + path)))) {
					return host.value();
				}
			}
		}
		return Blocks.STONE;
	}

	/** The stone of the ground at this spot: the first stone or host stone straight down, else the biome's. */
	public static Block groundStone(WorldView world, BlockPos from) {
		BlockPos.Mutable pos = from.mutableCopy();
		for (int i = 0; i < 32 && pos.getY() > world.getBottomY(); i++, pos.move(0, -1, 0)) {
			BlockState state = world.getBlockState(pos);
			if (state.isOf(Blocks.STONE) || state.isIn(BiomeStoneSurface.HOST_STONES)) {
				return state.getBlock();
			}
		}
		return stoneOf(world.getBiome(from));
	}

	@Nullable
	@Override
	public StructureTemplate.StructureBlockInfo process(WorldView world, BlockPos pos, BlockPos pivot, StructureTemplate.StructureBlockInfo original,
			StructureTemplate.StructureBlockInfo current, StructurePlacementData data) {
		if (!current.state().isOf(Blocks.STONE)) {
			return current;
		}
		Block stone = groundStone(world, pivot);
		if (this.stones.isPresent() && !stone.getDefaultState().isIn(this.stones.get())) {
			stone = Blocks.STONE;
		}
		return stone == Blocks.STONE ? current : new StructureTemplate.StructureBlockInfo(current.pos(), stone.getDefaultState(), current.nbt());
	}

	@Override
	protected StructureProcessorType<?> getType() {
		return TYPE;
	}
}
