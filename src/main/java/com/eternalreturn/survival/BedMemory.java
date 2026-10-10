package com.eternalreturn.survival;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Beds only set the respawn point (the night never skips, SleepManagerMixin), and each player remembers
 * the last few beds that set it (survival.rememberedBeds, 3 by default), newest first. When the player
 * respawns and the spawn point is a bed that is gone or blocked, the newest remembered bed that still
 * works becomes the spawn point instead, then the one before, and so on. Respawn anchors and spawn
 * points set by command are left alone.
 */
public final class BedMemory {
	public record Bed(RegistryKey<World> dimension, BlockPos pos, float angle) {
		boolean sameBed(Bed other) {
			return this.dimension.equals(other.dimension) && this.pos.equals(other.pos);
		}
	}

	/** What a player keeps (ServerPlayerEntityMixin). */
	public interface Holder {
		List<Bed> eternalreturn$beds();
	}

	private BedMemory() {
	}

	/** A bed just set the player's spawn point: it goes to the front of the list. */
	public static void remember(ServerPlayerEntity player, BlockPos pos, float angle) {
		List<Bed> beds = ((Holder) player).eternalreturn$beds();
		Bed bed = new Bed(player.getWorld().getRegistryKey(), pos.toImmutable(), angle);
		beds.removeIf(bed::sameBed);
		beds.add(0, bed);
		int keep = Math.max(1, EternalReturnConfig.get().survival.rememberedBeds);
		while (beds.size() > keep) {
			beds.remove(beds.size() - 1);
		}
	}

	/**
	 * Just before the game works out where the player respawns: if the spawn point is a bed that no
	 * longer works, makes the newest remembered bed that does the spawn point. Returns that bed, or null.
	 */
	@Nullable
	public static Bed fallBack(ServerPlayerEntity player) {
		if (player.isSpawnForced() || player.getSpawnPointPosition() == null || player.getServer() == null) {
			return null;
		}
		ServerWorld current = player.getServer().getWorld(player.getSpawnPointDimension());
		if (current != null) {
			BlockState spawn = current.getBlockState(player.getSpawnPointPosition());
			if (spawn.getBlock() instanceof RespawnAnchorBlock || works(current, player.getSpawnPointPosition(), player.getSpawnAngle())) {
				// A respawn anchor, or a bed that still works: nothing to do.
				return null;
			}
		}
		for (Bed bed : ((Holder) player).eternalreturn$beds()) {
			ServerWorld world = player.getServer().getWorld(bed.dimension());
			if (world != null && works(world, bed.pos(), bed.angle())) {
				player.setSpawnPoint(bed.dimension(), bed.pos(), bed.angle(), false, false);
				return bed;
			}
		}
		return null;
	}

	/** A bed is still there and there is room to stand up next to it. */
	static boolean works(ServerWorld world, BlockPos pos, float angle) {
		BlockState state = world.getBlockState(pos);
		return state.getBlock() instanceof BedBlock && BedBlock.isBedWorking(world)
				&& BedBlock.findWakeUpPosition(EntityType.PLAYER, world, pos, state.get(BedBlock.FACING), angle).isPresent();
	}

	public static NbtList write(List<Bed> beds) {
		NbtList list = new NbtList();
		for (Bed bed : beds) {
			NbtCompound entry = new NbtCompound();
			entry.putString("Dimension", bed.dimension().getValue().toString());
			entry.putInt("X", bed.pos().getX());
			entry.putInt("Y", bed.pos().getY());
			entry.putInt("Z", bed.pos().getZ());
			entry.putFloat("Angle", bed.angle());
			list.add(entry);
		}
		return list;
	}

	public static void read(NbtList list, List<Bed> beds) {
		beds.clear();
		for (int i = 0; i < list.size(); i++) {
			NbtCompound entry = list.getCompound(i);
			Identifier dimension = Identifier.tryParse(entry.getString("Dimension"));
			if (dimension != null) {
				beds.add(new Bed(RegistryKey.of(RegistryKeys.WORLD, dimension), new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")), entry.getFloat("Angle")));
			}
		}
	}

	public static final String NBT_KEY = "EternalReturnBeds";
	public static final int NBT_TYPE = NbtElement.LIST_TYPE;
}
