package com.eternalreturn.villager;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.event.GameEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Villagers open fence gates as they do wooden doors (villagers.openFenceGates): they path through a closed gate (a
 * villager's pathfinding takes one for a door: VillagerGatePathingMixin), open it as they reach it (swung away from
 * them, as a player opens one), and close it behind them once they're through and no one's in its way.
 */
public final class FenceGates {
	/** The gates each villager has opened and not yet closed. */
	private static final Map<VillagerEntity, Set<BlockPos>> OPENED = new WeakHashMap<>();

	private FenceGates() {
	}

	public static boolean enabled() {
		return EternalReturnConfig.get().villagers.openFenceGates;
	}

	public static boolean isClosedGate(BlockState state) {
		return state.getBlock() instanceof FenceGateBlock && !state.get(FenceGateBlock.OPEN);
	}

	/** Each tick (server). */
	public static void tick(VillagerEntity villager) {
		if (!(villager.getWorld() instanceof ServerWorld world) || !enabled()) {
			return;
		}
		Path path = villager.getNavigation().getCurrentPath();
		if (path != null && !path.isFinished()) {
			for (int i = path.getCurrentNodeIndex(); i < Math.min(path.getLength(), path.getCurrentNodeIndex() + 2); i++) {
				BlockPos pos = path.getNodePos(i);
				BlockState state = world.getBlockState(pos);
				if (isClosedGate(state) && pos.isWithinDistance(villager.getPos(), 2.5)) {
					open(world, villager, pos, state);
				}
			}
		}
		Set<BlockPos> opened = OPENED.get(villager);
		if (opened == null || opened.isEmpty() || villager.age % 5 != 0) {
			return;
		}
		opened.removeIf(pos -> {
			BlockState state = world.getBlockState(pos);
			if (!(state.getBlock() instanceof FenceGateBlock) || !state.get(FenceGateBlock.OPEN)) {
				return true;
			}
			if (villager.getBoundingBox().intersects(new Box(pos).expand(0.25)) || stillAhead(villager, pos)
					|| !world.getEntitiesByClass(LivingEntity.class, new Box(pos), LivingEntity::isAlive).isEmpty()) {
				return false;
			}
			world.setBlockState(pos, state.with(FenceGateBlock.OPEN, false), Block.NOTIFY_LISTENERS | Block.REDRAW_ON_MAIN_THREAD);
			world.playSound(null, pos, SoundEvents.BLOCK_FENCE_GATE_CLOSE, SoundCategory.BLOCKS, 1.0F, world.random.nextFloat() * 0.1F + 0.9F);
			world.emitGameEvent(villager, GameEvent.BLOCK_CLOSE, pos);
			return true;
		});
	}

	/** Whether the gate is still on the path ahead of it. */
	private static boolean stillAhead(VillagerEntity villager, BlockPos gate) {
		Path path = villager.getNavigation().getCurrentPath();
		if (path == null || path.isFinished()) {
			return false;
		}
		for (int i = path.getCurrentNodeIndex(); i < path.getLength(); i++) {
			if (path.getNodePos(i).equals(gate)) {
				return true;
			}
		}
		return false;
	}

	private static void open(ServerWorld world, VillagerEntity villager, BlockPos pos, BlockState state) {
		Direction facing = villager.getHorizontalFacing();
		if (state.get(FenceGateBlock.FACING) == facing.getOpposite()) {
			state = state.with(FenceGateBlock.FACING, facing);
		}
		world.setBlockState(pos, com.eternalreturn.blocks.SnowCover.gateMoved(world, pos, state.with(FenceGateBlock.OPEN, true), null),
				Block.NOTIFY_LISTENERS | Block.REDRAW_ON_MAIN_THREAD);
		world.playSound(null, pos, SoundEvents.BLOCK_FENCE_GATE_OPEN, SoundCategory.BLOCKS, 1.0F, world.random.nextFloat() * 0.1F + 0.9F);
		world.emitGameEvent(villager, GameEvent.BLOCK_OPEN, pos);
		OPENED.computeIfAbsent(villager, v -> new HashSet<>()).add(pos.toImmutable());
	}
}
