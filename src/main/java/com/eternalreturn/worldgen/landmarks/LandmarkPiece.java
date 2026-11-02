package com.eternalreturn.worldgen.landmarks;

import net.minecraft.block.Block;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/**
 * A landmark, built a chunk at a time: everything it places comes from its kind, centre, size, seed and stone (and a
 * lava flow's course), so each chunk builds its own part and the parts meet whatever order chunks generate in.
 */
public final class LandmarkPiece extends StructurePiece {
	public final Landmarks.Kind kind;
	public final BlockPos centre;
	public final int size;
	final long seed;
	public final Block stone;
	final int[] path;

	public LandmarkPiece(Landmarks.Kind kind, BlockPos centre, int size, long seed, Block stone, int[] path) {
		super(Landmarks.PIECE, 0, box(kind, centre, size, path));
		this.kind = kind;
		this.centre = centre;
		this.size = size;
		this.seed = seed;
		this.stone = stone;
		this.path = path;
	}

	public LandmarkPiece(NbtCompound nbt) {
		super(Landmarks.PIECE, nbt);
		this.kind = Landmarks.Kind.of(nbt.getString("landmark"));
		this.centre = new BlockPos(nbt.getInt("cx"), nbt.getInt("cy"), nbt.getInt("cz"));
		this.size = nbt.getInt("size");
		this.seed = nbt.getLong("seed");
		this.stone = Registries.BLOCK.get(Identifier.of(nbt.getString("stone")));
		this.path = nbt.getIntArray("path");
	}

	@Override
	protected void writeNbt(StructureContext context, NbtCompound nbt) {
		nbt.putString("landmark", this.kind.id());
		nbt.putInt("cx", this.centre.getX());
		nbt.putInt("cy", this.centre.getY());
		nbt.putInt("cz", this.centre.getZ());
		nbt.putInt("size", this.size);
		nbt.putLong("seed", this.seed);
		nbt.putString("stone", Registries.BLOCK.getId(this.stone).toString());
		nbt.putIntArray("path", this.path);
	}

	/** How far each kind reaches round its centre (generous: it only decides which chunks build a part). */
	private static BlockBox box(Landmarks.Kind kind, BlockPos c, int size, int[] path) {
		int half;
		int down = 40;
		int up;
		switch (kind) {
			case ARCH -> {
				half = size / 2 + 8;
				up = size + 10;
			}
			case SPIRE -> {
				half = 10;
				up = size + 6;
			}
			case TROPICAL_PILLARS -> {
				half = 24;
				up = size + 8;
			}
			case CRATER -> {
				half = (int) (size * 1.5) + 4;
				up = 12;
				down = size + 12;
			}
			case VOLCANO -> {
				half = (int) (size * 1.2) + 4;
				up = 95;
			}
			case GLACIER -> {
				half = size / 2 + 40;
				up = 45;
			}
			case ICE_WALL -> {
				half = size / 2 + 12;
				up = 45;
			}
			default -> {
				int minX = c.getX();
				int maxX = c.getX();
				int minZ = c.getZ();
				int maxZ = c.getZ();
				int minY = c.getY();
				int maxY = c.getY();
				for (int i = 0; i + 2 < path.length; i += 3) {
					minX = Math.min(minX, path[i]);
					maxX = Math.max(maxX, path[i]);
					minY = Math.min(minY, path[i + 1]);
					maxY = Math.max(maxY, path[i + 1]);
					minZ = Math.min(minZ, path[i + 2]);
					maxZ = Math.max(maxZ, path[i + 2]);
				}
				// The course has only the vent's height; the lava follows the ground, which may fall or rise a long way.
				return new BlockBox(minX - 10, minY - 48, minZ - 10, maxX + 10, maxY + 32, maxZ + 10);
			}
		}
		return new BlockBox(c.getX() - half, c.getY() - down, c.getZ() - half, c.getX() + half, c.getY() + up, c.getZ() + half);
	}

	@Override
	public void generate(StructureWorldAccess world, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator, Random random,
			BlockBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
		new LandmarkShapes(this, world, chunkBox, chunkGenerator).build();
	}
}
