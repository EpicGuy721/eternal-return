package com.eternalreturn.blocks;

import com.eternalreturn.EternalReturn;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.EntityEffectParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The gas a puffed purple or blue mushroom lets out (GasMushroomBlock): a cloud that fills the open air round the
 * mushroom, spreading out from it over its first second to about CELLS blocks, round corners and through gaps but not
 * through anything solid, shown as a lingering potion's swirling particles in the mushroom's colour all through the
 * space it fills. It follows the space as it changes (a door opened or a block placed) and fades over its last
 * 3 seconds; it lasts LIFETIME ticks. Every living thing in it gets the gas's effect: the purple's Poison I while it
 * stands in it and 4 seconds after, the blue's 30 seconds of Night Vision.
 */
public final class MushroomGas {
	public enum Kind {
		POISON(0x8F45C8, StatusEffects.POISON, 80),
		NIGHT_VISION(0x4A86E8, StatusEffects.NIGHT_VISION, 600);

		public final int color;
		public final RegistryEntry<StatusEffect> effect;
		public final int duration;

		Kind(int color, RegistryEntry<StatusEffect> effect, int duration) {
			this.color = color;
			this.effect = effect;
			this.duration = duration;
		}
	}

	public static final int CELLS = 28;
	public static final int LIFETIME = 200;
	public static final int FADE = 60;

	public static final EntityType<Cloud> CLOUD = Registry.register(Registries.ENTITY_TYPE, EternalReturn.id("mushroom_gas"),
			EntityType.Builder.<Cloud>create(Cloud::new, SpawnGroup.MISC).dimensions(0.5F, 0.5F).makeFireImmune().maxTrackingRange(10)
					.trackingTickInterval(Integer.MAX_VALUE).build());

	private MushroomGas() {
	}

	public static void register() {
		// Loads the entity type.
	}

	/** Lets the gas out at this spot. */
	public static Cloud release(ServerWorld world, BlockPos pos, Kind kind) {
		Cloud cloud = new Cloud(CLOUD, world);
		cloud.setKind(kind);
		cloud.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
		world.spawnEntity(cloud);
		world.playSound(null, pos, SoundEvents.BLOCK_FUNGUS_BREAK, SoundCategory.BLOCKS, 1.0F, 0.6F);
		world.playSound(null, pos, SoundEvents.ENTITY_SPLASH_POTION_BREAK, SoundCategory.BLOCKS, 0.4F, 0.5F);
		return cloud;
	}

	/** Whether gas can be in this block: nothing to collide with, and no liquid. */
	public static boolean open(World world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return state.getCollisionShape(world, pos).isEmpty() && state.getFluidState().isEmpty();
	}

	public static class Cloud extends Entity {
		private static final TrackedData<Integer> KIND = DataTracker.registerData(Cloud.class, TrackedDataHandlerRegistry.INTEGER);
		private List<BlockPos> cells = List.of();
		private int lastSize = -1;

		public Cloud(EntityType<? extends Cloud> type, World world) {
			super(type, world);
			this.noClip = true;
		}

		@Override
		protected void initDataTracker(DataTracker.Builder builder) {
			builder.add(KIND, 0);
		}

		public Kind kind() {
			Kind[] kinds = Kind.values();
			return kinds[Math.floorMod(this.dataTracker.get(KIND), kinds.length)];
		}

		public void setKind(Kind kind) {
			this.dataTracker.set(KIND, kind.ordinal());
		}

		/** The blocks the gas fills now (worked out every few ticks). */
		public List<BlockPos> cells() {
			return this.cells;
		}

		/** How many blocks it may fill at its age: from one to CELLS over its first 14 ticks. */
		private int size() {
			return Math.min(CELLS, 1 + this.age * 2);
		}

		/**
		 * Where the gas spreads from: the spot it was let out at, or, when that has been filled since (a piston crushing a
		 * puffed block against obsidian moves the next block of the row into its place), the nearest open block beside it.
		 */
		private BlockPos openStart() {
			BlockPos spot = this.getBlockPos();
			if (open(this.getWorld(), spot)) {
				return spot;
			}
			BlockPos best = null;
			for (BlockPos near : BlockPos.iterate(spot.add(-1, -1, -1), spot.add(1, 1, 1))) {
				if (open(this.getWorld(), near) && (best == null || near.getManhattanDistance(spot) < best.getManhattanDistance(spot))) {
					best = near.toImmutable();
				}
			}
			return best;
		}

		/** Flood fill through open blocks from the mushroom's spot, nearest first, up to n blocks. */
		private void spread(int n) {
			BlockPos start = this.openStart();
			List<BlockPos> out = new ArrayList<>();
			if (start == null) {
				this.cells = out;
				return;
			}
			Set<BlockPos> seen = new HashSet<>();
			ArrayDeque<BlockPos> queue = new ArrayDeque<>();
			queue.add(start);
			seen.add(start);
			while (!queue.isEmpty() && out.size() < n) {
				BlockPos at = queue.poll();
				out.add(at);
				for (Direction direction : Direction.values()) {
					BlockPos next = at.offset(direction);
					if (Math.abs(next.getY() - this.getBlockY()) <= 3 && seen.add(next) && open(this.getWorld(), next)) {
						queue.add(next);
					}
				}
			}
			this.cells = out;
		}

		@Override
		public void tick() {
			super.tick();
			int size = this.size();
			if (size != this.lastSize || this.age % 10 == 0) {
				this.spread(size);
				this.lastSize = size;
			}
			World world = this.getWorld();
			if (world.isClient) {
				float strength = this.age > LIFETIME - FADE ? (LIFETIME - this.age) / (float) FADE : 1.0F;
				Kind kind = this.kind();
				for (BlockPos cell : this.cells) {
					if (this.random.nextFloat() < 0.45F * strength) {
						world.addParticle(EntityEffectParticleEffect.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | kind.color),
								cell.getX() + this.random.nextDouble(), cell.getY() + this.random.nextDouble(), cell.getZ() + this.random.nextDouble(),
								(this.random.nextDouble() - 0.5) * 0.02, 0.01, (this.random.nextDouble() - 0.5) * 0.02);
					}
				}
				return;
			}
			if (this.age >= LIFETIME) {
				this.discard();
				return;
			}
			if (this.age % 5 == 0 && this.age < LIFETIME - 20 && !this.cells.isEmpty()) {
				this.affect();
			}
		}

		/** Gives the effect to every living thing touching a block of the gas. */
		public void affect() {
			Box all = null;
			for (BlockPos cell : this.cells) {
				all = all == null ? new Box(cell) : all.union(new Box(cell));
			}
			Kind kind = this.kind();
			for (LivingEntity living : this.getWorld().getNonSpectatingEntities(LivingEntity.class, all)) {
				Box box = living.getBoundingBox();
				for (BlockPos cell : this.cells) {
					if (box.intersects(new Box(cell))) {
						living.addStatusEffect(new StatusEffectInstance(kind.effect, kind.duration, 0), this);
						break;
					}
				}
			}
		}

		@Override
		public boolean damage(DamageSource source, float amount) {
			return false;
		}

		@Override
		protected void readCustomDataFromNbt(NbtCompound nbt) {
			this.dataTracker.set(KIND, nbt.getInt("Kind"));
			this.age = nbt.getInt("Age");
		}

		@Override
		protected void writeCustomDataToNbt(NbtCompound nbt) {
			nbt.putInt("Kind", this.dataTracker.get(KIND));
			nbt.putInt("Age", this.age);
		}
	}
}
