package com.eternalreturn.worldgen.landmarks;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hot springs (worldgen expansion 2, phase 3; landmark kind hot_spring): two to six pools stepping down from the first
 * like terraces, calcite rims streaked with tuff, clear water one to three deep, in cold land (and, rarer, the cherry
 * grove and the volcanic jungle). The first pool is the deepest, with a vent of magma at its bottom (standing on it
 * hurts); whoever is in a pool or within 4 blocks of one is warm (warms, for ColdExposure).
 * Steam rises from every pool all the time, seen from far off. Within about 8 blocks of the pools no water freezes and
 * no snow settles (vanilla's snow and ice, Moderner Beta's, Snowlogging's), so grass stays bare for animals to graze in
 * deep cold. About one spring in 3 has a geyser in its first pool: every 1 to 2 minutes a rumble and bubbles, then a
 * 3-second plume of spray and steam that throws whatever stands over the vent about 10 blocks up (harmless itself; the
 * fall is not). Everything comes from the spring's centre, size and seed, so each chunk builds its own part; springs
 * are listed in the world's saved data as they are built, for the snow, steam and geyser checks.
 */
public final class HotSprings {
	/** How far beyond its rims a spring keeps snow and ice off. */
	public static final int CLEAR = 8;
	/** Geyser timing (ticks): the warning before an eruption, and the eruption itself. */
	static final int WARNING = 60;
	static final int ERUPTION = 60;

	/** One pool: its centre, water level (the water's top block), radius and depth (water blocks over the floor). */
	public record Pool(int x, int level, int z, int radius, int depth) {
		boolean water(int px, int pz) {
			int dx = px - this.x;
			int dz = pz - this.z;
			return dx * dx + dz * dz <= (this.radius + 0.5) * (this.radius + 0.5);
		}

		boolean rim(int px, int pz) {
			int dx = px - this.x;
			int dz = pz - this.z;
			return dx * dx + dz * dz <= (this.radius + 1.6) * (this.radius + 1.6);
		}

		double distance(double px, double pz) {
			return Math.sqrt((px - this.x) * (px - this.x) + (pz - this.z) * (pz - this.z));
		}
	}

	/** A spring: its pools (the first the deepest, with the vent), and its geyser's timing if it has one. */
	public record Spring(BlockPos centre, int size, long seed, List<Pool> pools, boolean geyser, int period, int phase) {
		public Pool vent() {
			return this.pools.get(0);
		}

		/** Whether a spot is within CLEAR blocks of the springs' rims (across, not up). */
		public boolean clears(double x, double z) {
			for (Pool pool : this.pools) {
				if (pool.distance(x, z) <= pool.radius() + 1.6 + CLEAR) {
					return true;
				}
			}
			return false;
		}
	}

	/** For the tests (a flat world isn't an Eternal Return world): the springs' warmth and clear ground count anyway. */
	public static volatile boolean testing;

	private HotSprings() {
	}

	/** The plan of a spring at "centre" (the air over its ground), from its size (pools) and seed. */
	public static Spring plan(BlockPos centre, int size, long seed) {
		Random random = Random.create(seed);
		List<Pool> pools = new ArrayList<>();
		double heading = random.nextDouble() * Math.PI * 2;
		int radius = 3 + random.nextInt(2);
		Pool first = new Pool(centre.getX(), centre.getY() - 1, centre.getZ(), radius, 3);
		pools.add(first);
		double x = first.x();
		double z = first.z();
		Pool last = first;
		for (int i = 1; i < size; i++) {
			heading += (random.nextDouble() - 0.5) * 1.2;
			int r = 2 + random.nextInt(2);
			// Far enough apart for a rim between them, whatever the rounding.
			double step = last.radius() + r + 2.5;
			x += Math.cos(heading) * step;
			z += Math.sin(heading) * step;
			Pool pool = new Pool((int) Math.round(x), last.level() - 1 - random.nextInt(2), (int) Math.round(z), r, 1 + random.nextInt(2));
			pools.add(pool);
			last = pool;
		}
		boolean geyser = random.nextInt(3) == 0;
		int period = 1200 + random.nextInt(1200);
		return new Spring(centre, size, seed, List.copyOf(pools), geyser, period, random.nextInt(period));
	}

	/** How far a spring of this size can reach round its centre (generous: it decides which chunks build a part). */
	static int reach(int size) {
		return size * 7 + 6 + CLEAR + 2;
	}

	// ------------------------------------------------------------------ building

	/** Builds this chunk's part of the spring (box: the chunk's part of the landmark's box). */
	public static void build(LandmarkPiece piece, StructureWorldAccess world, BlockBox box) {
		Spring spring = plan(piece.centre, piece.size, piece.seed);
		Index.of(world.toServerWorld()).add(spring);
		Random random = Random.create(piece.seed ^ BlockPos.asLong(box.getMinX(), 0, box.getMinZ()));
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int x = box.getMinX(); x <= box.getMaxX(); x++) {
			for (int z = box.getMinZ(); z <= box.getMaxZ(); z++) {
				Pool water = null;
				Pool rim = null;
				for (Pool pool : spring.pools()) {
					if (pool.water(x, z) && (water == null || pool.level() > water.level())) {
						water = pool;
					}
					if (pool.rim(x, z) && (rim == null || pool.level() > rim.level())) {
						rim = pool;
					}
				}
				if (water != null && (rim == null || rim.level() <= water.level())) {
					int floor = water.level() - water.depth();
					clearAbove(world, pos, x, water.level(), z);
					for (int y = floor + 1; y <= water.level(); y++) {
						world.setBlockState(pos.set(x, y, z), Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
					boolean centre = x == water.x() && z == water.z();
					boolean vent = water == spring.vent() && centre;
					world.setBlockState(pos.set(x, floor, z), vent ? Blocks.MAGMA_BLOCK.getDefaultState() : rimBlock(random), Block.NOTIFY_LISTENERS);
					fillBelow(world, pos, x, floor - 1, z);
				} else if (rim != null) {
					clearAbove(world, pos, x, rim.level(), z);
					world.setBlockState(pos.set(x, rim.level(), z), rimBlock(random), Block.NOTIFY_LISTENERS);
					fillBelow(world, pos, x, rim.level() - 1, z);
				} else if (spring.clears(x + 0.5, z + 0.5)) {
					bareGround(world, pos, x, z, random);
				}
			}
		}
	}

	private static BlockState rimBlock(Random random) {
		return random.nextInt(5) == 0 ? Blocks.TUFF.getDefaultState() : Blocks.CALCITE.getDefaultState();
	}

	/** Air over a pool or rim (a hillside it's cut into), up to 12 blocks. */
	private static void clearAbove(StructureWorldAccess world, BlockPos.Mutable pos, int x, int level, int z) {
		for (int y = level + 1; y <= level + 12; y++) {
			if (!world.getBlockState(pos.set(x, y, z)).isAir()) {
				world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
		}
	}

	/** Calcite under a pool or rim down to the ground (a terrace wall where the land falls away), up to 14 blocks. */
	private static void fillBelow(StructureWorldAccess world, BlockPos.Mutable pos, int x, int top, int z) {
		for (int y = top; y > top - 14 && y > world.getBottomY(); y--) {
			BlockState state = world.getBlockState(pos.set(x, y, z));
			if (state.isOpaqueFullCube(world, pos) && !state.isIn(BlockTags.LEAVES)) {
				return;
			}
			world.setBlockState(pos, Blocks.CALCITE.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
	}

	/** The clear ground round the springs: snow and ice on top become grass, and some grass grows on it. */
	private static void bareGround(StructureWorldAccess world, BlockPos.Mutable pos, int x, int z, Random random) {
		int top = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z) - 1;
		BlockState state = world.getBlockState(pos.set(x, top, z));
		if (state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.POWDER_SNOW) || state.isOf(Blocks.PACKED_ICE) || state.isOf(Blocks.ICE)
				|| state.isOf(Blocks.BLUE_ICE)) {
			world.setBlockState(pos, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
			state = world.getBlockState(pos);
		}
		if (state.isOf(Blocks.GRASS_BLOCK) && world.isAir(pos.set(x, top + 1, z)) && random.nextInt(4) == 0) {
			world.setBlockState(pos, Blocks.SHORT_GRASS.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
	}

	// ------------------------------------------------------------------ the springs' list

	/** Whether this spot is warmed by a hot spring: in a pool, or within 4 blocks of one (across) and a little above or below its water. */
	public static boolean warms(World view, BlockPos pos) {
		if (!(com.eternalreturn.worldgen.EternalReturnServer.active() || testing) || !(view instanceof ServerWorld world) || world.getRegistryKey() != World.OVERWORLD
				|| !EternalReturnConfig.get().worldgen.hotSprings) {
			return false;
		}
		for (Spring spring : Index.of(world).within(pos.getX(), pos.getZ(), 8)) {
			for (Pool pool : spring.pools()) {
				if (pool.distance(pos.getX() + 0.5, pos.getZ() + 0.5) <= pool.radius() + 4 && pos.getY() >= pool.level() - pool.depth()
						&& pos.getY() <= pool.level() + 4) {
					return true;
				}
			}
		}
		return false;
	}

	/** Whether snow and ice stay off this spot: within CLEAR blocks of a hot spring's rims (overworld; false elsewhere). */
	public static boolean keepsClear(WorldView view, BlockPos pos) {
		if (!(com.eternalreturn.worldgen.EternalReturnServer.active() || testing)) {
			return false;
		}
		ServerWorld world = view instanceof ServerWorld server ? server : view instanceof StructureWorldAccess access ? access.toServerWorld() : null;
		if (world == null || world.getRegistryKey() != World.OVERWORLD || !EternalReturnConfig.get().worldgen.hotSprings) {
			return false;
		}
		return Index.of(world).near(pos.getX(), pos.getZ()) != null;
	}

	/** The springs of a world, by 128-block cell; kept in the world's saved data (centre, size, seed: the rest is the plan). */
	public static final class Index extends PersistentState {
		private static final PersistentState.Type<Index> TYPE = new PersistentState.Type<>(Index::new, Index::read, null);
		private final Map<Long, List<Spring>> cells = new ConcurrentHashMap<>();
		private final Map<Long, Spring> all = new ConcurrentHashMap<>();

		private static final Map<ServerWorld, Index> LOADED = new java.util.WeakHashMap<>();

		/** The world's springs (loaded once; reached from the world-generation threads too, hence the lock). */
		public static Index of(ServerWorld world) {
			synchronized (LOADED) {
				return LOADED.computeIfAbsent(world, w -> w.getPersistentStateManager().getOrCreate(TYPE, EternalReturn.MOD_ID + "_hot_springs"));
			}
		}

		private static Index read(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
			Index index = new Index();
			NbtList list = nbt.getList("springs", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < list.size(); i++) {
				NbtCompound entry = list.getCompound(i);
				index.put(plan(BlockPos.fromLong(entry.getLong("centre")), entry.getInt("size"), entry.getLong("seed")));
			}
			return index;
		}

		@Override
		public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
			NbtList list = new NbtList();
			for (Spring spring : this.all.values()) {
				NbtCompound entry = new NbtCompound();
				entry.putLong("centre", spring.centre().asLong());
				entry.putInt("size", spring.size());
				entry.putLong("seed", spring.seed());
				list.add(entry);
			}
			nbt.put("springs", list);
			return nbt;
		}

		void add(Spring spring) {
			if (!this.all.containsKey(spring.centre().asLong())) {
				this.put(spring);
				this.markDirty();
			}
		}

		private void put(Spring spring) {
			if (this.all.putIfAbsent(spring.centre().asLong(), spring) != null) {
				return;
			}
			int reach = reach(spring.size());
			for (int cx = (spring.centre().getX() - reach) >> 7; cx <= (spring.centre().getX() + reach) >> 7; cx++) {
				for (int cz = (spring.centre().getZ() - reach) >> 7; cz <= (spring.centre().getZ() + reach) >> 7; cz++) {
					this.cells.computeIfAbsent(cell(cx, cz), key -> java.util.Collections.synchronizedList(new ArrayList<>())).add(spring);
				}
			}
		}

		private static long cell(int cx, int cz) {
			return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
		}

		/** The spring whose clear ground covers this spot, if any. */
		@Nullable
		public Spring near(int x, int z) {
			List<Spring> list = this.cells.get(cell(x >> 7, z >> 7));
			if (list == null) {
				return null;
			}
			synchronized (list) {
				for (Spring spring : list) {
					if (spring.clears(x + 0.5, z + 0.5)) {
						return spring;
					}
				}
			}
			return null;
		}

		/** Springs with any part within "radius" blocks of a spot. */
		public List<Spring> within(int x, int z, int radius) {
			List<Spring> found = new ArrayList<>();
			for (int cx = (x - radius) >> 7; cx <= (x + radius) >> 7; cx++) {
				for (int cz = (z - radius) >> 7; cz <= (z + radius) >> 7; cz++) {
					List<Spring> list = this.cells.get(cell(cx, cz));
					if (list == null) {
						continue;
					}
					synchronized (list) {
						for (Spring spring : list) {
							if (!found.contains(spring) && spring.centre().getSquaredDistance(x, spring.centre().getY(), z) <= (double) (radius + 40) * (radius + 40)) {
								found.add(spring);
							}
						}
					}
				}
			}
			return found;
		}
	}

	// ------------------------------------------------------------------ steam and geysers

	public static void register() {
		// Load the list on the main thread before any chunk is made (the generation threads only add to it).
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> Index.of(server.getOverworld()));
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			if (world.getRegistryKey() != World.OVERWORLD || world.getTime() % 4 != 0 || !EternalReturnConfig.get().worldgen.hotSprings
					|| world.getPlayers().isEmpty()) {
				return;
			}
			Index index = Index.of(world);
			List<Spring> active = new ArrayList<>();
			for (ServerPlayerEntity player : world.getPlayers()) {
				for (Spring spring : index.within(player.getBlockX(), player.getBlockZ(), 160)) {
					if (!active.contains(spring)) {
						active.add(spring);
					}
					steam(world, player, spring);
				}
			}
			for (Spring spring : active) {
				if (spring.geyser() && world.isChunkLoaded(spring.vent().x() >> 4, spring.vent().z() >> 4)) {
					geyser(world, spring);
				}
			}
		});
	}

	/** Steam over every pool, sent to this player from as far as they can see it. */
	private static void steam(ServerWorld world, ServerPlayerEntity player, Spring spring) {
		Random random = world.getRandom();
		for (Pool pool : spring.pools()) {
			double angle = random.nextDouble() * Math.PI * 2;
			double r = random.nextDouble() * pool.radius();
			double x = pool.x() + 0.5 + Math.cos(angle) * r;
			double z = pool.z() + 0.5 + Math.sin(angle) * r;
			world.spawnParticles(player, ParticleTypes.CAMPFIRE_COSY_SMOKE, true, x, pool.level() + 1.0, z, 0, 0, 0.07, 0, 1);
			if (random.nextInt(3) == 0) {
				world.spawnParticles(player, ParticleTypes.CLOUD, true, x, pool.level() + 1.1, z, 0, 0, 0.03, 0, 1);
			}
		}
	}

	/** Where a geyser is in its cycle: ticks since its last eruption began. */
	public static int cycle(Spring spring, long time) {
		return (int) Math.floorMod(time + spring.phase(), (long) spring.period());
	}

	private static void geyser(ServerWorld world, Spring spring) {
		geyserAt(world, spring, cycle(spring, world.getTime()));
	}

	/** A geyser's doings at this point of its cycle (t: ticks since its last eruption began). Public for the tests. */
	public static void geyserAt(ServerWorld world, Spring spring, int t) {
		Pool vent = spring.vent();
		double x = vent.x() + 0.5;
		double z = vent.z() + 0.5;
		double top = vent.level() + 1.0;
		if (t >= spring.period() - WARNING) {
			// The warning: bubbles and a rumble.
			world.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, x, vent.level() - vent.depth() + 1.0, z, 6, 0.3, 0.5, 0.3, 0.1);
			world.spawnParticles(ParticleTypes.BUBBLE_POP, x, top, z, 4, 0.5, 0, 0.5, 0.05);
			if ((t / 4) % 5 == 0) {
				world.playSound(null, x, top, z, SoundEvents.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, SoundCategory.BLOCKS, 1.5F, 0.6F);
			}
			return;
		}
		if (t >= ERUPTION) {
			return;
		}
		if (t < 4) {
			world.playSound(null, x, top, z, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 2.0F, 0.5F);
			world.playSound(null, x, top, z, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.BLOCKS, 2.0F, 0.6F);
		}
		// The plume: spray and steam up to about 10 blocks.
		for (int h = 0; h < 10; h += 2) {
			world.spawnParticles(ParticleTypes.SPLASH, x, top + h, z, 12, 0.35, 0.6, 0.35, 0.2);
			world.spawnParticles(ParticleTypes.CLOUD, x, top + h + 1, z, 3, 0.3, 0.6, 0.3, 0.02);
		}
		world.spawnParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, top + 8, z, 2, 0.3, 0.3, 0.3, 0.01);
		// Whatever stands or swims over the vent is thrown up.
		Box column = new Box(x - 0.9, vent.level() - vent.depth(), z - 0.9, x + 0.9, top + 3.0, z + 0.9);
		for (Entity entity : world.getOtherEntities(null, column, entity -> !entity.isSpectator() && !entity.hasNoGravity())) {
			entity.setVelocity(entity.getVelocity().x * 0.5, 1.4, entity.getVelocity().z * 0.5);
			entity.velocityModified = true;
			entity.fallDistance = 0;
		}
	}
}
