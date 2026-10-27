package com.eternalreturn.survival;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.mobs.entity.BearEntity;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.IceBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.PolarBearEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Ice (plain ice, not packed or blue) cracks under weight (survival.iceCracks), showing it as blocks show being broken:
 * a player (heavier for heavier armor: ArmorWeight), and the heavy mobs, polar bears and bears, iron golems and
 * horses in armor, each with whatever rides it. The heavier, the faster (rate), twice as fast sprinting, half as
 * fast sneaking; landing on it cracks it at once, more the further the fall. Ice nobody's stood on for HEAL_DELAY ticks
 * heals; cracked through, it breaks, to water if there's something under it (as ice broken by hand does).
 */
public final class IceCracking {
	/** Cracking a tick under a load of 1 (an unarmored player, about 33 seconds standing still). */
	private static final float RATE = 0.0015F;
	private static final int HEAL_DELAY = 40;
	private static final float HEAL_RATE = 0.005F;
	private static final Map<World, Long2ObjectMap<Crack>> CRACKS = new WeakHashMap<>();

	private static final class Crack {
		float progress;
		long lastLoaded;
		int shownStage = -1;
	}

	private IceCracking() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(IceCracking::tick);
	}

	private static boolean enabled() {
		return EternalReturnConfig.get().survival.iceCracks;
	}

	/**
	 * How heavy this is on the ice, with its riders: 0 for what doesn't crack it (most mobs; players in creative).
	 * A player weighs 1 and ten times the share of speed their armor takes (full diamond: 4).
	 */
	public static float load(Entity entity) {
		float load = ownLoad(entity);
		if (load <= 0.0F) {
			return 0.0F;
		}
		for (Entity rider : entity.getPassengerList()) {
			load += Math.max(1.0F, load(rider));
		}
		return load;
	}

	private static float ownLoad(Entity entity) {
		if (entity instanceof PlayerEntity player) {
			return player.isCreative() || player.isSpectator() ? 0.0F : 1.0F + 10.0F * (float) ArmorWeight.total(player);
		}
		if (entity instanceof PolarBearEntity || entity instanceof BearEntity) {
			return 3.0F;
		}
		if (entity instanceof IronGolemEntity) {
			return 5.0F;
		}
		if (entity instanceof AbstractHorseEntity horse && horse.isWearingBodyArmor()) {
			return 3.0F;
		}
		return 0.0F;
	}

	/** Cracking a tick under this entity: faster than its load grows; twice sprinting, half sneaking. */
	public static float rate(Entity entity) {
		float load = load(entity);
		if (load <= 0.0F) {
			return 0.0F;
		}
		float rate = RATE * (float) Math.pow(load, 1.5);
		Entity driver = entity.getControllingPassenger() != null ? entity.getControllingPassenger() : entity;
		if (driver.isSprinting() || entity.isSprinting()) {
			rate *= 2.0F;
		}
		if (driver.isSneaking()) {
			rate *= 0.5F;
		}
		return rate;
	}

	/** Landing on ice (IceLandingMixin): a crack at once, more for heavier and further. */
	public static void land(World world, BlockPos pos, Entity entity, float fallDistance) {
		if (!(world instanceof ServerWorld server) || !enabled() || !world.getBlockState(pos).isOf(Blocks.ICE)) {
			return;
		}
		float load = load(entity);
		if (load > 0.0F && fallDistance > 0.0F) {
			crack(server, pos, 0.05F * fallDistance * load);
		}
	}

	/** How far this ice is cracked, 0 to 1 (for tests). */
	public static float progress(World world, BlockPos pos) {
		Long2ObjectMap<Crack> cracks = CRACKS.get(world);
		Crack crack = cracks != null ? cracks.get(pos.asLong()) : null;
		return crack != null ? crack.progress : 0.0F;
	}

	private static void tick(ServerWorld world) {
		if (!enabled()) {
			return;
		}
		for (Entity entity : world.iterateEntities()) {
			if (!entity.isOnGround() || entity.hasVehicle() || ownLoad(entity) <= 0.0F) {
				continue;
			}
			BlockPos pos = entity.getSteppingPos();
			if (!world.getBlockState(pos).isOf(Blocks.ICE)) {
				continue;
			}
			float rate = rate(entity);
			if (rate > 0.0F) {
				crack(world, pos, rate);
			}
		}
		Long2ObjectMap<Crack> cracks = CRACKS.get(world);
		if (cracks == null || cracks.isEmpty()) {
			return;
		}
		long now = world.getTime();
		var iterator = cracks.long2ObjectEntrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			BlockPos pos = BlockPos.fromLong(entry.getLongKey());
			Crack crack = entry.getValue();
			if (!world.getBlockState(pos).isOf(Blocks.ICE)) {
				world.setBlockBreakingInfo(breakerId(pos), pos, -1);
				iterator.remove();
				continue;
			}
			if (now - crack.lastLoaded > HEAL_DELAY) {
				crack.progress -= HEAL_RATE;
			}
			if (crack.progress <= 0.0F) {
				world.setBlockBreakingInfo(breakerId(pos), pos, -1);
				iterator.remove();
			} else {
				show(world, pos, crack);
			}
		}
	}

	private static void crack(ServerWorld world, BlockPos pos, float amount) {
		Crack crack = CRACKS.computeIfAbsent(world, w -> new Long2ObjectOpenHashMap<>()).computeIfAbsent(pos.asLong(), key -> new Crack());
		crack.progress += amount;
		crack.lastLoaded = world.getTime();
		if (crack.progress >= 1.0F) {
			shatter(world, pos);
			CRACKS.get(world).remove(pos.asLong());
			return;
		}
		show(world, pos, crack);
	}

	private static void show(ServerWorld world, BlockPos pos, Crack crack) {
		int stage = Math.min(9, (int) (crack.progress * 10.0F));
		if (stage != crack.shownStage) {
			crack.shownStage = stage;
			world.setBlockBreakingInfo(breakerId(pos), pos, stage);
		}
	}

	/** As ice broken by hand: water if there's something under it to hold it, else gone. */
	private static void shatter(ServerWorld world, BlockPos pos) {
		BlockState ice = world.getBlockState(pos);
		world.setBlockBreakingInfo(breakerId(pos), pos, -1);
		world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(ice));
		BlockState below = world.getBlockState(pos.down());
		if (!world.getDimension().ultrawarm() && (below.blocksMovement() || below.isLiquid())) {
			world.setBlockState(pos, IceBlock.getMeltedState());
		} else {
			world.removeBlock(pos, false);
		}
	}

	/** Who's "breaking" a block, for the crack shown: a negative number of its own, not any entity's. */
	private static int breakerId(BlockPos pos) {
		return -1 - (int) (Long.hashCode(pos.asLong()) & 0x3FFFFFFF);
	}
}
