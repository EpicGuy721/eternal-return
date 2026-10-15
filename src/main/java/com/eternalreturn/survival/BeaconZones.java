package com.eternalreturn.survival;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Working beacons (a clear beam, and paid for: BeaconPower) and the area they cover: the same area their
 * effects reach, from 32 blocks each way for one paid with iron to 512 for netherite (with the beacon rework
 * off, 20 to 50 for a 1 to 4 tier pyramid), from that far below the beacon up to the sky. Inside it no monster spawns naturally (nor phantoms or patrols), and animals
 * can still spawn; outside, animals no longer spawn once the land has been generated
 * (survival.beaconsBlockMonsters, survival.animalsOnlySpawnNearBeacons). Beacons register themselves as
 * they tick and leave when they are removed or unloaded.
 */
public final class BeaconZones {
	/** Per world: beacon position -> the range it covers (only working beacons). */
	private static final Map<World, Map<BlockPos, Integer>> BEACONS = new WeakHashMap<>();

	private BeaconZones() {
	}

	/** From a beacon's tick: range 0 or a blocked beam means it covers nothing. */
	public static synchronized void update(World world, BlockPos pos, int range, boolean beam) {
		if (range > 0 && beam) {
			BEACONS.computeIfAbsent(world, w -> new HashMap<>()).put(pos.toImmutable(), range);
		} else {
			remove(world, pos);
		}
	}

	public static synchronized void remove(World world, BlockPos pos) {
		Map<BlockPos, Integer> beacons = BEACONS.get(world);
		if (beacons != null) {
			beacons.remove(pos);
		}
	}

	/** Whether a working beacon covers this position. */
	public static synchronized boolean covers(World world, BlockPos pos) {
		Map<BlockPos, Integer> beacons = BEACONS.get(world);
		if (beacons == null) {
			return false;
		}
		for (Map.Entry<BlockPos, Integer> beacon : beacons.entrySet()) {
			int range = beacon.getValue();
			BlockPos at = beacon.getKey();
			if (Math.abs(pos.getX() - at.getX()) <= range && Math.abs(pos.getZ() - at.getZ()) <= range && pos.getY() >= at.getY() - range) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether a natural spawn of this type may happen here: monsters not inside a beacon's area, animals
	 * (the creature spawn group) only inside one. Returns true for everything else.
	 */
	public static boolean allowsNaturalSpawn(World world, EntityType<?> type, BlockPos pos) {
		EternalReturnConfig.SurvivalTweaks cfg = EternalReturnConfig.get().survival;
		SpawnGroup group = type.getSpawnGroup();
		if (group == SpawnGroup.MONSTER && cfg.beaconsBlockMonsters) {
			return !covers(world, pos);
		}
		if (group == SpawnGroup.CREATURE && cfg.animalsOnlySpawnNearBeacons) {
			return covers(world, pos);
		}
		return true;
	}

	/** Phantoms and patrols: no monster comes for a player inside a beacon's area. */
	public static boolean blocksMonstersAt(World world, BlockPos pos) {
		return EternalReturnConfig.get().survival.beaconsBlockMonsters && covers(world, pos);
	}

	/** The range a working beacon at pos registered, or 0. */
	public static synchronized int range(World world, BlockPos pos) {
		Map<BlockPos, Integer> beacons = BEACONS.get(world);
		return beacons == null ? 0 : beacons.getOrDefault(pos, 0);
	}

	/** For tests: forgets every beacon in this world (they register again on their next tick). */
	public static synchronized void clear(World world) {
		BEACONS.remove(world);
	}
}
