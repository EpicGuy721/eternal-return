package com.eternalreturn.blocks;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Geysers (the owner's rule): a magma block with lava directly under it and water directly over it now and then erupts.
 * On one of its random ticks (it gets them for this), one time in two, it starts: 3 seconds of rising bubbles and a
 * rumble, then a 3-second plume of spray and steam from the water's surface about 10 blocks up, throwing whatever is in
 * the water over the magma, or standing on it, up with it (harmless itself; the fall is not). Then at least a minute's
 * rest. Hot springs whose vent sits on lava have one; so does any such spot built or found, in any world (water can't
 * lie in the Nether, so none there).
 */
public final class Geysers {
	public static final int WARNING = 60;
	public static final int ERUPTION = 60;
	public static final int REST = 1200;
	/** Per world: eruptions under way (when each began) and when each spot last finished. */
	private static final Map<World, Map<BlockPos, Long>> ACTIVE = new WeakHashMap<>();
	private static final Map<World, Map<BlockPos, Long>> RESTING = new WeakHashMap<>();

	private Geysers() {
	}

	/** Whether a magma block here can erupt: lava right under it, water right over it. */
	public static boolean canErupt(World world, BlockPos pos) {
		return world.getFluidState(pos.down()).isIn(FluidTags.LAVA) && world.getFluidState(pos.up()).isIn(FluidTags.WATER);
	}

	/** A magma block's random tick: one time in two, if it can, it starts an eruption. */
	public static void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		if (random.nextInt(2) == 0 && canErupt(world, pos)) {
			start(world, pos);
		}
	}

	/** Starts an eruption here unless one is under way or the spot is still resting. Whether it started. */
	public static boolean start(ServerWorld world, BlockPos pos) {
		Map<BlockPos, Long> active = ACTIVE.computeIfAbsent(world, w -> new HashMap<>());
		Long rested = RESTING.computeIfAbsent(world, w -> new HashMap<>()).get(pos);
		if (active.containsKey(pos) || rested != null && world.getTime() - rested < REST) {
			return false;
		}
		active.put(pos.toImmutable(), world.getTime());
		return true;
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(Geysers::tick);
	}

	public static void tick(ServerWorld world) {
		Map<BlockPos, Long> active = ACTIVE.get(world);
		if (active == null || active.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<BlockPos, Long>> it = active.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<BlockPos, Long> entry = it.next();
			BlockPos pos = entry.getKey();
			long t = world.getTime() - entry.getValue();
			if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4) || !canErupt(world, pos) || t >= WARNING + ERUPTION) {
				it.remove();
				RESTING.computeIfAbsent(world, w -> new HashMap<>()).put(pos, world.getTime());
				continue;
			}
			erupting(world, pos, (int) t);
		}
	}

	/** The water's top over the magma (the plume's foot): up to 12 blocks of water. */
	private static int surface(World world, BlockPos pos) {
		BlockPos.Mutable at = pos.mutableCopy().move(0, 1, 0);
		int n = 0;
		while (n < 12 && world.getFluidState(at.up()).isIn(FluidTags.WATER)) {
			at.move(0, 1, 0);
			n++;
		}
		return at.getY();
	}

	private static void erupting(ServerWorld world, BlockPos pos, int t) {
		double x = pos.getX() + 0.5;
		double z = pos.getZ() + 0.5;
		double top = surface(world, pos) + 1.0;
		if (t < WARNING) {
			// The warning: bubbles rising and a rumble.
			world.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, x, pos.getY() + 1.2, z, 6, 0.3, 0.6, 0.3, 0.1);
			world.spawnParticles(ParticleTypes.BUBBLE_POP, x, top, z, 3, 0.5, 0, 0.5, 0.05);
			if (t % 20 == 0) {
				world.playSound(null, x, top, z, SoundEvents.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, SoundCategory.BLOCKS, 1.5F, 0.6F);
			}
			return;
		}
		if (t == WARNING) {
			world.playSound(null, x, top, z, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 2.0F, 0.5F);
			world.playSound(null, x, top, z, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.BLOCKS, 2.0F, 0.6F);
		}
		if (t % 2 == 0) {
			// The plume: spray and steam up to about 10 blocks.
			for (int h = 0; h < 10; h += 2) {
				world.spawnParticles(ParticleTypes.SPLASH, x, top + h, z, 8, 0.35, 0.6, 0.35, 0.2);
				world.spawnParticles(ParticleTypes.CLOUD, x, top + h + 1, z, 2, 0.3, 0.6, 0.3, 0.02);
			}
			world.spawnParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, top + 8, z, 1, 0.3, 0.3, 0.3, 0.01);
		}
		// Whatever is in the water over the magma (or just above it) is thrown up.
		Box column = new Box(x - 0.9, pos.getY() + 1.0, z - 0.9, x + 0.9, top + 3.0, z + 0.9);
		for (Entity entity : world.getOtherEntities(null, column, entity -> !entity.isSpectator() && !entity.hasNoGravity())) {
			entity.setVelocity(entity.getVelocity().x * 0.5, 1.4, entity.getVelocity().z * 0.5);
			entity.velocityModified = true;
			entity.fallDistance = 0;
		}
	}
}
