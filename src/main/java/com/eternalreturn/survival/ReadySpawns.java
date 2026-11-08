package com.eternalreturn.survival;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.SpawnLocating;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * The next random spawn, ready before anyone needs it (RandomSpawns). A random spot is almost always somewhere no one
 * has been, so a death without a bed used to wait 30 to 45 seconds while the spot was found and the land around it
 * generated. Now, while players are in the world, the next spot is picked the same way in the background and the
 * chunks around it (up to 5 chunks out) are generated ahead of time, kept loaded without ticking. The next death (or
 * new player) without a bed goes there at once, and a new spot is prepared. If none is ready yet, the spot is found as
 * before.
 */
public final class ReadySpawns {
	private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("eternalreturn_next_spawn", Comparator.comparingLong(ChunkPos::toLong));
	/** Chunks generated around the spot, out from its chunk. */
	public static final int RADIUS = 5;
	/** How long the chunks stay held after the spot is used, until the player's own view holds them. */
	private static final int RELEASE_AFTER = 200;

	private record Held(ServerWorld world, ChunkPos centre, long releaseAt) {
	}

	@Nullable
	private static ServerWorld world;
	@Nullable
	private static ChunkPos centre;
	@Nullable
	private static BlockPos spot;
	private static final List<Held> RELEASING = new ArrayList<>();

	private ReadySpawns() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(ReadySpawns::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> forget());
	}

	private static void tick(ServerWorld tickWorld) {
		if (tickWorld.getTime() % 20 != 0) {
			return;
		}
		for (Iterator<Held> it = RELEASING.iterator(); it.hasNext(); ) {
			Held held = it.next();
			if (held.world() == tickWorld && tickWorld.getTime() >= held.releaseAt()) {
				hold(held.world(), held.centre(), false);
				it.remove();
			}
		}
		if (!RandomSpawns.applies(tickWorld) || tickWorld.getPlayers().isEmpty()) {
			return;
		}
		if (world != null && world != tickWorld) {
			forget();
		}
		if (centre == null) {
			BlockPos candidate = RandomSpawns.candidate(tickWorld, tickWorld.getRandom());
			if (candidate != null) {
				world = tickWorld;
				centre = new ChunkPos(candidate);
				spot = null;
				hold(tickWorld, centre, true);
			}
		} else if (spot == null && tickWorld.getChunkManager().isChunkLoaded(centre.x, centre.z)) {
			BlockPos found = SpawnLocating.findServerSpawnPoint(tickWorld, centre);
			if (found != null && tickWorld.getWorldBorder().contains(found)) {
				spot = found;
			} else {
				// No ground to stand on in that chunk: let it go and try elsewhere.
				hold(tickWorld, centre, false);
				centre = null;
			}
		}
	}

	/** Holds (or lets go of) every chunk within RADIUS of the centre as a full chunk that doesn't tick. */
	private static void hold(ServerWorld in, ChunkPos at, boolean add) {
		ServerChunkManager manager = in.getChunkManager();
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				ChunkPos pos = new ChunkPos(at.x + dx, at.z + dz);
				if (add) {
					manager.addTicket(TICKET, pos, 0, pos);
				} else {
					manager.removeTicket(TICKET, pos, 0, pos);
				}
			}
		}
	}

	/** The prepared spot, if it is ready (and a new one will be prepared), or null. */
	@Nullable
	public static BlockPos take(ServerWorld in) {
		if (in != world || spot == null || centre == null || !in.getChunkManager().isChunkLoaded(centre.x, centre.z)) {
			return null;
		}
		BlockPos taken = spot;
		RELEASING.add(new Held(in, centre, in.getTime() + RELEASE_AFTER));
		centre = null;
		spot = null;
		return taken;
	}

	/** For tests: the spot ready to be taken, or null. */
	@Nullable
	public static BlockPos ready() {
		return spot;
	}

	/** Lets go of everything (server stopping, another world, tests). */
	public static void forget() {
		if (world != null && centre != null) {
			hold(world, centre, false);
		}
		for (Held held : RELEASING) {
			hold(held.world(), held.centre(), false);
		}
		RELEASING.clear();
		world = null;
		centre = null;
		spot = null;
	}
}
