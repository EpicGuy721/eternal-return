package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.worldgen.Dungeons;
import com.eternalreturn.worldgen.Strongholds;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureSetKeys;
import net.minecraft.structure.StructureStart;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Eternal Return worlds: a third of strongholds at ground level, illagers and an illusioner in them, dungeons and wild
 * nether wart in the Nether, and overworld dungeons in cobblestone, stone brick and brick.
 */
public class StrongholdAndNetherTests implements FabricGameTest {
	private static final List<EntityType<?>> ILLAGERS = List.of(EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.WITCH, EntityType.EVOKER);

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = GameTestWorld.ETERNAL_RETURN_EXTRAS_BATCH, tickLimit = 2_000_000)
	public void strongholdsOnTheSurfaceAndTheirIllagers(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		var set = world.getRegistryManager().get(RegistryKeys.STRUCTURE_SET).get(StructureSetKeys.STRONGHOLDS);
		List<ChunkPos> positions = world.getChunkManager().getStructurePlacementCalculator().getPlacementPositions((ConcentricRingsStructurePlacement) set.placement());
		Structure stronghold = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(StructureKeys.STRONGHOLD);
		int surface = 0;
		int onLand = 0;
		int checked = 0;
		StructureStart nearestSurface = null;
		List<String> bad = new ArrayList<>();
		for (ChunkPos chunk : positions.subList(0, Math.min(36, positions.size()))) {
			StructureStart start = stronghold.createStructureStart(world.getRegistryManager(), generator, generator.getBiomeSource(), noise,
					world.getStructureTemplateManager(), world.getSeed(), chunk, 0, world, biome -> true);
			if (!start.hasChildren()) {
				continue;
			}
			checked++;
			// The pieces themselves: a start's own box has 12 blocks added all round for the terrain shaping.
			BlockBox box = BlockBox.encompass(start.getChildren().stream().map(net.minecraft.structure.StructurePiece::getBoundingBox).toList()).orElseThrow();
			BlockPos center = box.getCenter();
			int ground = generator.getHeightInGround(center.getX(), center.getZ(), Heightmap.Type.WORLD_SURFACE_WG, world, noise);
			if (ground > generator.getSeaLevel() + 1) {
				onLand++;
			}
			if (Strongholds.isSurface(start)) {
				surface++;
				if (Math.abs(box.getMinY() - (ground - 3)) > 1) {
					bad.add(chunk + " bottom " + box.getMinY() + ", ground " + ground);
				}
				if (nearestSurface == null || chunk.getSquaredDistance(ChunkPos.ORIGIN) < nearestSurface.getPos().getSquaredDistance(ChunkPos.ORIGIN)) {
					nearestSurface = start;
				}
			} else if (box.getMaxY() >= generator.getSeaLevel() - 5) {
				bad.add(chunk + " underground but from y " + box.getMinY());
			}
		}
		EternalReturn.LOGGER.info("[strongholds] {} of {} at ground level ({} of them under land)", surface, checked, onLand);
		ctx.assertTrue(bad.isEmpty(), "misplaced strongholds: " + bad);
		// A third of the ones under land go up (the shares wobble: the pick is rerolled whenever vanilla rebuilds a stronghold without a portal room).
		ctx.assertTrue(checked >= 20 && surface >= 1 && surface <= onLand * 0.6, surface + " of " + checked + " strongholds at ground level, " + onLand + " under land");

		// The nearest one at ground level, built for real into generated terrain (test worlds have structures off).
		BlockBox box = nearestSurface.getBoundingBox();
		for (int cx = (box.getMinX() >> 4) - 1; cx <= (box.getMaxX() >> 4) + 1; cx++) {
			for (int cz = (box.getMinZ() >> 4) - 1; cz <= (box.getMaxZ() >> 4) + 1; cz++) {
				world.getChunk(cx, cz, ChunkStatus.FULL, true);
			}
		}
		Random random = Random.create(world.getSeed());
		for (int cx = box.getMinX() >> 4; cx <= box.getMaxX() >> 4; cx++) {
			for (int cz = box.getMinZ() >> 4; cz <= box.getMaxZ() >> 4; cz++) {
				BlockBox chunkBox = new BlockBox(cx * 16, world.getBottomY(), cz * 16, cx * 16 + 15, world.getTopY() - 1, cz * 16 + 15);
				nearestSurface.place(world, world.getStructureAccessor(), generator, random, chunkBox, new ChunkPos(cx, cz));
			}
		}
		Box inside = new Box(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX() + 1, box.getMaxY() + 1, box.getMaxZ() + 1);
		List<MobEntity> mobs = world.getEntitiesByClass(MobEntity.class, inside, mob -> true);
		long illusioners = mobs.stream().filter(mob -> mob.getType() == EntityType.ILLUSIONER).count();
		long illagers = mobs.stream().filter(mob -> ILLAGERS.contains(mob.getType())).count();
		long rooms = nearestSurface.getChildren().stream().filter(piece -> piece instanceof net.minecraft.structure.StrongholdGenerator.SquareRoom
				|| piece instanceof net.minecraft.structure.StrongholdGenerator.Library || piece instanceof net.minecraft.structure.StrongholdGenerator.PrisonHall
				|| piece instanceof net.minecraft.structure.StrongholdGenerator.FiveWayCrossing).count();
		EternalReturn.LOGGER.info("[strongholds] built the one at {} ({} pieces, {} rooms): {} illusioners, {} other illagers", box.getCenter(),
				nearestSurface.getChildren().size(), rooms, illusioners, illagers);
		ctx.assertTrue(illusioners == 1, illusioners + " illusioners (one in the portal room)");
		// About half the rooms have one or two, and the odd corridor one; small strongholds have few rooms.
		ctx.assertTrue(illagers >= 1, "no illagers in a stronghold with " + rooms + " rooms");
		ctx.assertTrue(mobs.stream().allMatch(MobEntity::isPersistent), "stronghold illagers stay");
		mobs.forEach(MobEntity::discard);
		ctx.complete();
	}

	/** Spawners and nether wart in a square of Nether chunks: Nether dungeons of nether bricks, with Nether mobs. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = GameTestWorld.ETERNAL_RETURN_EXTRAS_BATCH, tickLimit = 2_000_000)
	public void netherDungeonsAndWart(TestContext ctx) {
		ServerWorld nether = ctx.getWorld().getServer().getWorld(World.NETHER);
		Map<String, Integer> spawners = new TreeMap<>();
		Map<String, Integer> floors = new TreeMap<>();
		int warts = 0;
		int chunks = 0;
		for (int cx = 60; cx < 72; cx++) {
			for (int cz = 60; cz < 72; cz++) {
				WorldChunk chunk = nether.getChunk(cx, cz);
				chunks++;
				for (BlockEntity entity : chunk.getBlockEntities().values()) {
					if (entity instanceof MobSpawnerBlockEntity spawner) {
						spawners.merge(mobOf(nether, spawner), 1, Integer::sum);
						floors.merge(Registries.BLOCK.getId(nether.getBlockState(spawner.getPos().down()).getBlock()).getPath(), 1, Integer::sum);
					}
				}
				BlockPos.Mutable pos = new BlockPos.Mutable();
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						for (int y = 0; y < 128; y++) {
							if (chunk.getBlockState(pos.set(x, y, z)).isOf(Blocks.NETHER_WART)) {
								warts++;
							}
						}
					}
				}
			}
		}
		EternalReturn.LOGGER.info("[nether] {} chunks: spawners {} on floors {}; {} nether warts", chunks, spawners, floors, warts);
		int dungeons = spawners.values().stream().mapToInt(Integer::intValue).sum();
		ctx.assertTrue(dungeons > 0, "no Nether dungeons in " + chunks + " chunks");
		List<String> netherMobs = Dungeons.netherSpawnerMobs().stream().map(type -> Registries.ENTITY_TYPE.getId(type).toString()).toList();
		ctx.assertTrue(netherMobs.containsAll(spawners.keySet()), "Nether spawners raise " + spawners.keySet());
		ctx.assertTrue(floors.keySet().stream().allMatch(block -> block.contains("nether_bricks")), "Nether dungeon floors: " + floors);
		ctx.assertTrue(warts > 0, "no wild nether wart in " + chunks + " chunks");
		ctx.complete();
	}

	/** Dungeons in a square of overworld chunks: cobblestone, stone brick and brick ones all turn up. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = GameTestWorld.ETERNAL_RETURN_EXTRAS_BATCH, tickLimit = 2_000_000)
	public void overworldDungeonStyles(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		Map<String, Integer> styles = new TreeMap<>();
		int chunks = 0;
		for (int cx = 140; cx < 156; cx++) {
			for (int cz = -150; cz < -134; cz++) {
				WorldChunk chunk = world.getChunk(cx, cz);
				chunks++;
				for (BlockEntity entity : chunk.getBlockEntities().values()) {
					if (entity instanceof MobSpawnerBlockEntity spawner && !mobOf(world, spawner).equals("minecraft:cave_spider")) {
						Block floor = world.getBlockState(spawner.getPos().down()).getBlock();
						String style = floor == Blocks.MOSSY_COBBLESTONE || floor == Blocks.COBBLESTONE ? "cobblestone"
								: floor == Blocks.MOSSY_STONE_BRICKS || floor == Blocks.STONE_BRICKS ? "stone_bricks"
								: floor == Blocks.BRICKS ? "bricks" : Registries.BLOCK.getId(floor).getPath();
						styles.merge(style, 1, Integer::sum);
					}
				}
			}
		}
		EternalReturn.LOGGER.info("[dungeons] {} chunks: {} dungeons by style {}", chunks, styles.values().stream().mapToInt(Integer::intValue).sum(), styles);
		for (String style : List.of("cobblestone", "stone_bricks", "bricks")) {
			ctx.assertTrue(styles.getOrDefault(style, 0) > 0, "no " + style + " dungeon in " + chunks + " chunks: " + styles);
		}
		ctx.complete();
	}

	private static String mobOf(ServerWorld world, MobSpawnerBlockEntity spawner) {
		return spawner.createNbt(world.getRegistryManager()).getCompound("SpawnData").getCompound("entity").getString("id");
	}
}
