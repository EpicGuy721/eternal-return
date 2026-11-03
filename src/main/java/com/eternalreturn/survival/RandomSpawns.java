package com.eternalreturn.survival;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.worldgen.EternalReturnServer;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.SpawnLocating;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

/**
 * Random spawns (survival.randomSpawnRadius, Eternal Return worlds): a player's first spawn in the world, and every
 * respawn after a death with no bed (or respawn anchor) to go back to, is a random spot on dry land anywhere within
 * 5,000 blocks of 0, 0, not the world spawn point (RandomSpawnMixin, RandomFirstSpawnMixin). Oceans and rivers are
 * skipped by their biome before any ground is generated; if no dry spot turns up in TRIES places, vanilla's spawn
 * by the world spawn point is used. Leaving the End (alive) still goes to the world spawn point, as in vanilla.
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
		int radius = EternalReturnConfig.get().survival.randomSpawnRadius;
		for (int i = 0; i < TRIES; i++) {
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
			BlockPos spot = SpawnLocating.findServerSpawnPoint(world, new ChunkPos(x >> 4, z >> 4));
			if (spot != null && world.getWorldBorder().contains(spot)) {
				return spot;
			}
		}
		return null;
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
		BlockPos spot = find(overworld, overworld.getRandom());
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
		BlockPos spot = find(world, world.getRandom());
		if (spot != null) {
			player.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYaw(), 0.0F);
		}
	}
}
