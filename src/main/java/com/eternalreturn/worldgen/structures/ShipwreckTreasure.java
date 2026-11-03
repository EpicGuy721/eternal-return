package com.eternalreturn.worldgen.structures;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.worldgen.caves.CaveWorlds;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.loot.LootTables;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.Optional;

/**
 * Shipwrecks in Eternal Return worlds (worldgen.onlyEssentialStructures, EternalReturnServer): they come back at 1% of
 * vanilla's rate (structure set eternalreturn:shipwrecks: vanilla's spacing and salt, frequency 0.01), and every one
 * has a buried treasure chest beside it (eternalreturn:shipwreck_treasure, this structure: the same placement, so it
 * starts in the very chunks the shipwrecks do, in any shipwreck biome). The chest (vanilla's buried treasure loot) is
 * buried one block down in the sea floor or beach at the chunk's (9, 9), whatever stone is there, where the shipwreck's
 * treasure map (structure tag minecraft:on_treasure_maps) puts its X. Both sets are dropped in every other world.
 */
public final class ShipwreckTreasure extends Structure {
	public static final MapCodec<ShipwreckTreasure> CODEC = createCodec(ShipwreckTreasure::new);
	public static final StructureType<ShipwreckTreasure> TYPE = Registry.register(Registries.STRUCTURE_TYPE, EternalReturn.id("shipwreck_treasure"),
			() -> CODEC);
	public static final StructurePieceType PIECE = Registry.register(Registries.STRUCTURE_PIECE, EternalReturn.id("shipwreck_treasure"),
			(StructurePieceType.Simple) Piece::new);

	public ShipwreckTreasure(Structure.Config config) {
		super(config);
	}

	public static void register() {
		// The fields above register the structure and piece types when the class loads.
	}

	@Override
	public Optional<StructurePosition> getStructurePosition(Context context) {
		ChunkGenerator generator = context.chunkGenerator();
		if (!(generator instanceof NoiseChunkGenerator noise) || !CaveWorlds.usesEternalReturnSettings(noise, context.dynamicRegistryManager())) {
			return Optional.empty();
		}
		ChunkPos chunk = context.chunkPos();
		int x = chunk.getOffsetX(9);
		int z = chunk.getOffsetZ(9);
		// The biome before the height, which is slow to work out by the sea (Moderner Beta's ocean distance).
		if (!context.biomePredicate().test(context.biomeSource().getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(generator.getSeaLevel()),
				BiomeCoords.fromBlock(z), context.noiseConfig().getMultiNoiseSampler()))) {
			return Optional.empty();
		}
		int y = generator.getHeightInGround(x, z, Heightmap.Type.OCEAN_FLOOR_WG, context.world(), context.noiseConfig());
		BlockPos pos = new BlockPos(x, y, z);
		return Optional.of(new StructurePosition(pos, collector -> collector.addPiece(new Piece(pos))));
	}

	@Override
	public StructureType<?> getType() {
		return TYPE;
	}

	/** The buried chest: one block down in the ground at its column, sand round it where there was water or air. */
	public static final class Piece extends StructurePiece {
		public Piece(BlockPos pos) {
			super(PIECE, 0, new BlockBox(pos));
		}

		public Piece(NbtCompound nbt) {
			super(PIECE, nbt);
		}

		@Override
		protected void writeNbt(StructureContext context, NbtCompound nbt) {
		}

		@Override
		public void generate(StructureWorldAccess world, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator, Random random,
				BlockBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
			int x = this.boundingBox.getMinX();
			int z = this.boundingBox.getMinZ();
			// The top of the ground (the first block from the top that isn't air or water), and the chest below it.
			int top = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z) - 1;
			BlockPos chest = new BlockPos(x, top - 1, z);
			if (chest.getY() <= world.getBottomY() + 1) {
				return;
			}
			for (Direction direction : Direction.values()) {
				BlockPos side = chest.offset(direction);
				BlockState state = world.getBlockState(side);
				if (state.isAir() || !state.getFluidState().isEmpty()) {
					world.setBlockState(side, direction == Direction.DOWN ? Blocks.SANDSTONE.getDefaultState() : Blocks.SAND.getDefaultState(),
							3);
				}
			}
			this.boundingBox = new BlockBox(chest);
			this.addChest(world, chunkBox, random, chest, LootTables.BURIED_TREASURE_CHEST, null);
		}
	}
}
