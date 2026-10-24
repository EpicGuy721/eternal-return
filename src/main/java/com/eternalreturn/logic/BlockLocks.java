package com.eternalreturn.logic;

import com.eternalreturn.EternalReturn;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Blocks set by hand with a wrench or a hammer (BlockTools) keep their state: they don't change by themselves (random and
 * scheduled ticks) or because of their neighbours (shape and redstone updates), though players can still use them
 * (BlockLockMixin). The lock goes when the block is broken or replaced by another (BlockLockClearMixin). Locked spots
 * are kept per chunk, saved with it and known to the players' games, so they don't guess wrong.
 */
public final class BlockLocks {
	public static final AttachmentType<Set<Long>> LOCKS = AttachmentRegistry.<Set<Long>>builder()
			.persistent(Codec.LONG.listOf().xmap(list -> Set.copyOf(list), set -> List.copyOf(set)))
			.syncWith(PacketCodecs.VAR_LONG.collect(PacketCodecs.toList()).xmap(list -> Set.copyOf(list), set -> new ArrayList<>(set)),
					AttachmentSyncPredicate.all())
			.buildAndRegister(EternalReturn.id("block_locks"));

	private BlockLocks() {
	}

	public static void register() {
	}

	/** The loaded chunk of a world (not of a world being generated), or null. */
	private static WorldChunk chunk(WorldAccess world, BlockPos pos) {
		if (!(world instanceof World w)) {
			return null;
		}
		Chunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
		return chunk instanceof WorldChunk worldChunk ? worldChunk : null;
	}

	public static boolean isLocked(WorldAccess world, BlockPos pos) {
		WorldChunk chunk = chunk(world, pos);
		if (chunk == null) {
			return false;
		}
		Set<Long> locks = chunk.getAttached(LOCKS);
		return locks != null && locks.contains(pos.asLong());
	}

	public static void lock(World world, BlockPos pos) {
		WorldChunk chunk = chunk(world, pos);
		if (chunk == null || world.isClient) {
			return;
		}
		Set<Long> locks = new HashSet<>(chunk.getAttachedOrElse(LOCKS, Set.of()));
		if (locks.add(pos.asLong())) {
			chunk.setAttached(LOCKS, Set.copyOf(locks));
			chunk.setNeedsSaving(true);
		}
	}

	public static void unlock(WorldChunk chunk, BlockPos pos) {
		Set<Long> locks = chunk.getAttached(LOCKS);
		if (locks == null || !locks.contains(pos.asLong()) || chunk.getWorld().isClient) {
			return;
		}
		Set<Long> left = new HashSet<>(locks);
		left.remove(pos.asLong());
		chunk.setAttached(LOCKS, left.isEmpty() ? null : Set.copyOf(left));
		chunk.setNeedsSaving(true);
	}
}
