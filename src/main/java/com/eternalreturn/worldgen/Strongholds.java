package com.eternalreturn.worldgen;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.structure.StrongholdGenerator;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePiecesCollector;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.List;

/**
 * Strongholds in Eternal Return worlds (other worlds never change):
 * <ul>
 * <li>worldgen.surfaceStrongholdChance (a third) of them are built at ground level instead of deep underground,
 * where the ground is above sea level: the bottom of the stronghold sits 3 blocks into the ground below its
 * middle. Such a stronghold gets the ground raised to meet it and cleared above, the way villages do, instead of
 * being buried (StructureWeightSamplerMixin), so it stands in the open where it rises above the hills.</li>
 * <li>Illagers live in them (worldgen.strongholdIllagers): half the rooms have one or two, now and then a
 * corridor has one, a mix of pillagers, vindicators, witches and evokers; the portal room always has an
 * illusioner. They are placed with the stronghold and never despawn.</li>
 * </ul>
 */
public final class Strongholds {
	/**
	 * Underground strongholds are shifted to end at least 10 blocks below sea level, so they start far below y 30;
	 * ground-level ones start 3 blocks under ground that is above sea level. A start's box has 12 blocks added all
	 * round for the terrain shaping, so a ground-level one's box starts about 50 or higher, an underground one's
	 * below 20.
	 */
	private static final int SURFACE_MIN_Y = 40;
	private static final List<EntityType<? extends MobEntity>> ILLAGERS = List.of(EntityType.PILLAGER, EntityType.PILLAGER, EntityType.PILLAGER,
			EntityType.VINDICATOR, EntityType.VINDICATOR, EntityType.VINDICATOR, EntityType.WITCH, EntityType.WITCH, EntityType.EVOKER);

	private Strongholds() {
	}

	/** Instead of vanilla's shift underground: maybe up to the surface. Returns how far the pieces moved. */
	public static int place(StructurePiecesCollector collector, Structure.Context context, int seaLevel, int minY, int topPenalty) {
		double chance = EternalReturnConfig.get().worldgen.surfaceStrongholdChance;
		if (EternalReturnServer.active() && chance > 0.0 && context.random().nextDouble() < chance) {
			BlockBox box = collector.getBoundingBox();
			BlockPos center = box.getCenter();
			int ground = context.chunkGenerator().getHeightInGround(center.getX(), center.getZ(), Heightmap.Type.WORLD_SURFACE_WG, context.world(),
					context.noiseConfig());
			if (ground > seaLevel + 1) {
				int shift = ground - 3 - box.getMinY();
				collector.shift(shift);
				return shift;
			}
		}
		return collector.shiftInto(seaLevel, minY, context.random(), topPenalty);
	}

	public static boolean isSurface(StructureStart start) {
		return EternalReturnServer.active() && start.getStructure().getType() == StructureType.STRONGHOLD && start.getBoundingBox().getMinY() >= SURFACE_MIN_Y;
	}

	/** After a stronghold piece is built in a chunk: its illagers, once, from the chunk holding the piece's middle. */
	public static void afterPiece(StructureStart start, StructurePiece piece, StructureWorldAccess world, BlockBox chunkBox) {
		if (!EternalReturnServer.active() || !EternalReturnConfig.get().worldgen.strongholdIllagers
				|| start.getStructure().getType() != StructureType.STRONGHOLD) {
			return;
		}
		BlockBox box = piece.getBoundingBox();
		BlockPos center = box.getCenter();
		if (!chunkBox.contains(center)) {
			return;
		}
		Random random = Random.create(world.getSeed() ^ center.asLong() * 31L);
		if (piece instanceof StrongholdGenerator.PortalRoom) {
			spawn(world, EntityType.ILLUSIONER, box, chunkBox, random);
			if (random.nextBoolean()) {
				spawn(world, ILLAGERS.get(random.nextInt(ILLAGERS.size())), box, chunkBox, random);
			}
			return;
		}
		boolean room = piece instanceof StrongholdGenerator.SquareRoom || piece instanceof StrongholdGenerator.Library
				|| piece instanceof StrongholdGenerator.PrisonHall || piece instanceof StrongholdGenerator.FiveWayCrossing;
		int count = room ? (random.nextBoolean() ? 1 + random.nextInt(2) : 0) : (random.nextInt(10) == 0 ? 1 : 0);
		for (int i = 0; i < count; i++) {
			spawn(world, ILLAGERS.get(random.nextInt(ILLAGERS.size())), box, chunkBox, random);
		}
	}

	/** On the floor somewhere inside the piece (in this chunk), with two blocks of air above. */
	private static void spawn(StructureWorldAccess world, EntityType<? extends MobEntity> type, BlockBox piece, BlockBox chunkBox, Random random) {
		int minX = Math.max(piece.getMinX() + 1, chunkBox.getMinX());
		int maxX = Math.min(piece.getMaxX() - 1, chunkBox.getMaxX());
		int minZ = Math.max(piece.getMinZ() + 1, chunkBox.getMinZ());
		int maxZ = Math.min(piece.getMaxZ() - 1, chunkBox.getMaxZ());
		if (minX > maxX || minZ > maxZ) {
			return;
		}
		for (int attempt = 0; attempt < 24; attempt++) {
			int x = minX + random.nextInt(maxX - minX + 1);
			int z = minZ + random.nextInt(maxZ - minZ + 1);
			for (int y = piece.getMinY() + 1; y < piece.getMaxY() - 1; y++) {
				BlockPos pos = new BlockPos(x, y, z);
				if (world.isAir(pos) && world.isAir(pos.up()) && world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
					MobEntity mob = type.create(world.toServerWorld());
					if (mob == null) {
						return;
					}
					mob.refreshPositionAndAngles(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
					mob.initialize(world, world.getLocalDifficulty(pos), SpawnReason.STRUCTURE, null);
					mob.setPersistent();
					world.spawnEntityAndPassengers(mob);
					return;
				}
			}
		}
	}
}
