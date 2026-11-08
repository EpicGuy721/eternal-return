package com.eternalreturn.survival;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.worldgen.EternalReturnServer;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

/**
 * Random spawns (survival.randomSpawnRadius, Eternal Return worlds): a player's first spawn in the world, and every
 * respawn after a death with no bed (or respawn anchor) to go back to, is a random spot on dry land anywhere within
 * 5,000 blocks of 0, 0, not the world spawn point (RandomSpawnMixin, RandomFirstSpawnMixin). Oceans and rivers are
 * skipped by their biome before any ground is generated; if no dry spot turns up in TRIES places, vanilla's spawn
 * by the world spawn point is used. Leaving the End (alive) still goes to the world spawn point, as in vanilla. The next
 * spot is prepared in the background, land and all (ReadySpawns), so a respawn doesn't wait for it.
 */
public final class RandomSpawns {
	private static final int TRIES = 24;
	/** Tests only: applies in any world (the test worlds aren't Eternal Return worlds). */
	public static boolean testEverywhere;
	/** In game test runs it's off unless a test turns it on: the test players would each be sent thousands of blocks off. */
	private static final boolean GAMETEST = System.getProperty("fabric-api.gametest") != null;

	private RandomSpawns() {
	}

	/** Whether players spawn at random in this world. */
	public static boolean applies(ServerWorld world) {
		return EternalReturnConfig.get().survival.randomSpawnRadius > 0 && world.getRegistryKey() == World.OVERWORLD
				&& (testEverywhere || !GAMETEST && EternalReturnServer.active());
	}

	/** A random spot to stand on, on land within the radius of 0, 0, or null if none turned up. */
	@Nullable
	public static BlockPos find(ServerWorld world, Random random) {
		for (int i = 0; i < TRIES; i++) {
			BlockPos candidate = candidate(world, random, 1);
			if (candidate == null) {
				continue;
			}
			BlockPos spot = standingSpot(world, new ChunkPos(candidate));
			if (spot != null) {
				return spot;
			}
		}
		return null;
	}

	/**
	 * A place to stand in this chunk (it is generated if it isn't yet), or null if it has none: the top of the first
	 * column, in vanilla's order, whose highest solid block has a full top, isn't under water or lava, and has two free
	 * dry blocks above it. Read from the blocks themselves: vanilla's spawn finder trusted the chunk's height map and
	 * didn't check the room above, so a height map off by a little put players inside the ground or under water.
	 */
	@Nullable
	public static BlockPos standingSpot(ServerWorld world, ChunkPos chunkPos) {
		WorldChunk chunk = world.getChunk(chunkPos.x, chunkPos.z);
		int top = Math.min(world.getTopY() - 1, chunk.getHighestNonEmptySectionYOffset() + 15);
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int x = chunkPos.getStartX(); x <= chunkPos.getEndX(); x++) {
			for (int z = chunkPos.getStartZ(); z <= chunkPos.getEndZ(); z++) {
				for (int y = top; y > world.getBottomY(); y--) {
					BlockState state = chunk.getBlockState(pos.set(x, y, z));
					if (passable(world, pos, state)) {
						continue;
					}
					BlockPos feet = pos.up().toImmutable();
					if (state.getFluidState().isEmpty() && Block.isFaceFullSquare(state.getCollisionShape(world, pos), Direction.UP)
							&& passable(world, feet, chunk.getBlockState(feet)) && passable(world, feet.up(), chunk.getBlockState(feet.up()))
							&& world.getWorldBorder().contains(feet)) {
						return feet;
					}
					// The top of this column is water, lava, a slab, a fence...: try the next column.
					break;
				}
			}
		}
		return null;
	}

	/** Whether a player can stand in this block: nothing to collide with, and no water or lava. */
	private static boolean passable(ServerWorld world, BlockPos pos, BlockState state) {
		return state.getFluidState().isEmpty() && state.getCollisionShape(world, pos).isEmpty();
	}

	/** Whether a spot found earlier is still one to stand on (nothing has been built or poured there since). */
	public static boolean stillStanding(ServerWorld world, BlockPos feet) {
		BlockState below = world.getBlockState(feet.down());
		return below.getFluidState().isEmpty() && Block.isFaceFullSquare(below.getCollisionShape(world, feet.down()), Direction.UP)
				&& passable(world, feet, world.getBlockState(feet)) && passable(world, feet.up(), world.getBlockState(feet.up()));
	}

	/**
	 * A random place within the radius whose biome isn't ocean or river, found without generating anything (ReadySpawns
	 * then generates the land there), or null if none turned up in TRIES places.
	 */
	@Nullable
	public static BlockPos candidate(ServerWorld world, Random random) {
		return candidate(world, random, TRIES);
	}

	@Nullable
	private static BlockPos candidate(ServerWorld world, Random random, int tries) {
		int radius = EternalReturnConfig.get().survival.randomSpawnRadius;
		for (int i = 0; i < tries; i++) {
			// Even over the disc: the distance from 0, 0 goes as the square root.
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = radius * Math.sqrt(random.nextDouble());
			int x = MathHelper.floor(Math.cos(angle) * distance);
			int z = MathHelper.floor(Math.sin(angle) * distance);
			if (!world.getWorldBorder().contains(x, z)) {
				continue;
			}
			RegistryEntry<Biome> biome = world.getBiome(new BlockPos(x, world.getSeaLevel(), z));
			if (biome.isIn(BiomeTags.IS_OCEAN) || biome.isIn(BiomeTags.IS_DEEP_OCEAN) || biome.isIn(BiomeTags.IS_RIVER)) {
				continue;
			}
			return new BlockPos(x, world.getSeaLevel(), z);
		}
		return null;
	}

	/** The spot prepared ahead of time (ReadySpawns), or one found now. */
	@Nullable
	private static BlockPos spawnSpot(ServerWorld world) {
		BlockPos ready = ReadySpawns.take(world);
		return ready != null ? ready : find(world, world.getRandom());
	}

	/** A dead player's respawn: a random spot if they have no bed to go back to (vanilla's target otherwise). */
	public static TeleportTarget respawn(ServerPlayerEntity player, boolean alive, TeleportTarget target) {
		ServerWorld overworld = player.getServer() == null ? null : player.getServer().getOverworld();
		if (alive || overworld == null || target.world() != overworld || !applies(overworld)) {
			return target;
		}
		boolean bed = player.getSpawnPointPosition() != null && player.getServer().getWorld(player.getSpawnPointDimension()) != null
				&& !target.missingRespawnBlock();
		if (bed) {
			return target;
		}
		BlockPos spot = spawnSpot(overworld);
		if (spot == null) {
			return target;
		}
		return new TeleportTarget(overworld, Vec3d.ofBottomCenter(spot), Vec3d.ZERO, target.yaw(), target.pitch(), target.missingRespawnBlock(),
				target.postDimensionTransition());
	}

	/** A player new to the world: placed at a random spot (before they're sent where they are). */
	public static void firstSpawn(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		if (!applies(world)) {
			return;
		}
		BlockPos spot = spawnSpot(world);
		if (spot != null) {
			player.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYaw(), 0.0F);
		}
	}
}
