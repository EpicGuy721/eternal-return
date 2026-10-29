package com.eternalreturn.birds;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.StemBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumSet;

/**
 * Birds as crop pests (mobs.birdsEatCrops): wild songbirds and corvids land on farmland and peck at what's growing
 * there, eating a seed just sown (the crop gone) or setting a young crop (under half grown) back a stage; grown crops
 * they leave alone (PeckCrops). A scarecrow, an armor stand wearing a carved pumpkin (item tag
 * eternalreturn:scarecrow_heads), keeps them off crops within mobs.scarecrowRadius. A bird at the crops is chased off
 * by villagers (a chore of theirs: VillagerChores) and by tamed wolves and cats that aren't sitting (ShooBirds); it
 * flies off with its flock and leaves crops alone for a minute.
 */
public final class CropPests {
	public static final TagKey<Item> SCARECROW_HEADS = TagKey.of(RegistryKeys.ITEM, EternalReturn.id("scarecrow_heads"));
	/** How far a pecking bird is seen by those who chase birds off. */
	public static final double CHASE_RANGE = 16.0;

	private CropPests() {
	}

	private static boolean enabled() {
		return EternalReturnConfig.get().mobs.birdsEatCrops;
	}

	/** Songbirds and corvids, wild. */
	public static boolean isPest(BirdEntity bird) {
		String shape = bird.getShape().id();
		return !bird.isTamed() && ("songbird".equals(shape) || "corvid".equals(shape));
	}

	/** A seed or young crop on farmland: under half grown. */
	public static boolean peckable(World world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		if (!world.getBlockState(pos.down()).isOf(Blocks.FARMLAND)) {
			return false;
		}
		if (state.getBlock() instanceof CropBlock crop) {
			return crop.getAge(state) < (crop.getMaxAge() + 1) / 2;
		}
		return state.getBlock() instanceof StemBlock && state.get(StemBlock.AGE) < 4;
	}

	/** Within a scarecrow's reach. */
	public static boolean guarded(World world, BlockPos pos) {
		double radius = EternalReturnConfig.get().mobs.scarecrowRadius;
		Vec3d center = Vec3d.ofCenter(pos);
		return !world.getEntitiesByClass(ArmorStandEntity.class, new Box(pos).expand(radius),
				stand -> stand.getEquippedStack(EquipmentSlot.HEAD).isIn(SCARECROW_HEADS) && stand.squaredDistanceTo(center) <= radius * radius).isEmpty();
	}

	/** The nearest bird pecking at crops within range, or null. */
	@Nullable
	public static BirdEntity nearestPecking(Entity from, double range) {
		return from.getWorld().getEntitiesByClass(BirdEntity.class, from.getBoundingBox().expand(range), bird -> bird.isAlive() && bird.isPecking())
				.stream().min(Comparator.comparingDouble(from::squaredDistanceTo)).orElse(null);
	}

	/** Chased off: it flies off (with its flock) away from who chased it, and leaves crops alone a minute. */
	public static void scareOff(BirdEntity bird, Entity from) {
		bird.peckingAt = null;
		bird.noPeckUntil = bird.age + 1200;
		BirdGoals.fleeFrom(bird, from.getPos());
	}

	/** Eats a seed (the crop gone) or sets a young crop back a stage. */
	static void eat(BirdEntity bird, BlockPos pos) {
		World world = bird.getWorld();
		BlockState state = world.getBlockState(pos);
		int age = state.getBlock() instanceof CropBlock crop ? crop.getAge(state) : state.get(StemBlock.AGE);
		bird.playSound(SoundEvents.ENTITY_PARROT_EAT, 0.8F, 1.2F);
		if (age == 0) {
			world.breakBlock(pos, false, bird);
		} else {
			world.setBlockState(pos, state.getBlock() instanceof CropBlock crop ? crop.withAge(age - 1) : state.with(StemBlock.AGE, age - 1));
		}
	}

	/** A bird a seed or young crop near: lands on it and pecks at it for six seconds, eating it (or setting it back). */
	static class PeckCrops extends Goal {
		private final BirdEntity bird;
		private BlockPos crop;
		private int ticks;
		private int pecks;

		PeckCrops(BirdEntity bird) {
			this.bird = bird;
			this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
		}

		@Override
		public boolean canStart() {
			World world = this.bird.getWorld();
			if (!enabled() || !isPest(this.bird) || this.bird.isFlocking() || this.bird.age < this.bird.noPeckUntil
					|| !world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING) || this.bird.getRandom().nextInt(20) != 0) {
				return false;
			}
			this.crop = this.find();
			if (this.crop == null) {
				this.bird.noPeckUntil = this.bird.age + 200;
			}
			return this.crop != null;
		}

		/** The nearest within 8 blocks across, 6 down and 3 up. */
		@Nullable
		private BlockPos find() {
			World world = this.bird.getWorld();
			BlockPos at = this.bird.getBlockPos();
			for (BlockPos pos : BlockPos.iterateOutwards(at.down(), 8, 3, 8)) {
				if (pos.getY() <= at.getY() + 3 && peckable(world, pos) && !guarded(world, pos)) {
					return pos.toImmutable();
				}
			}
			return null;
		}

		@Override
		public boolean shouldContinue() {
			World world = this.bird.getWorld();
			return this.ticks < 400 && !this.bird.isFlocking() && this.bird.age >= this.bird.noPeckUntil && peckable(world, this.crop)
					&& (this.ticks % 20 != 0 || !guarded(world, this.crop));
		}

		@Override
		public void start() {
			this.ticks = 0;
			this.pecks = 0;
			this.bird.getNavigation().startMovingTo(this.crop.getX() + 0.5, this.crop.getY(), this.crop.getZ() + 0.5, 1.0);
		}

		@Override
		public void stop() {
			this.bird.peckingAt = null;
			if (this.bird.age >= this.bird.noPeckUntil) {
				this.bird.noPeckUntil = this.bird.age + 600 + this.bird.getRandom().nextInt(600);
			}
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			this.ticks++;
			Vec3d center = Vec3d.ofBottomCenter(this.crop);
			double dx = center.x - this.bird.getX();
			double dz = center.z - this.bird.getZ();
			double off = dx * dx + dz * dz;
			if (off > 0.7 * 0.7 || Math.abs(center.y - this.bird.getY()) > 1.0 || !this.bird.isOnGround()) {
				this.bird.peckingAt = null;
				if (off > 0.7 * 0.7 && off < 2.0 * 2.0 && this.bird.getNavigation().isIdle()) {
					// A path ends within a block of the crop: the last step straight onto it (then it drops onto it).
					this.bird.getMoveControl().moveTo(center.x, center.y, center.z, 1.0);
				} else if (this.ticks % 20 == 0) {
					this.bird.getNavigation().startMovingTo(center.x, center.y, center.z, 1.0);
				}
				return;
			}
			this.bird.peckingAt = this.crop;
			this.bird.getNavigation().stop();
			this.bird.getLookControl().lookAt(center.x, center.y, center.z);
			if (this.ticks % 20 == 0 && this.bird.getWorld() instanceof ServerWorld world) {
				BlockState state = world.getBlockState(this.crop);
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), center.x, center.y + 0.1, center.z, 3, 0.1, 0.05, 0.1, 0.0);
				this.bird.playSound(SoundEvents.BLOCK_CROP_BREAK, 0.3F, 1.6F);
				if (++this.pecks >= 6) {
					eat(this.bird, this.crop);
					this.ticks = 400;
				}
			}
		}
	}

	/** A tamed pet (wolf, cat) not sitting runs at a bird pecking at crops near and chases it off. */
	public static class ShooBirds extends Goal {
		private final TameableEntity pet;
		private BirdEntity bird;
		private int ticks;

		public ShooBirds(TameableEntity pet) {
			this.pet = pet;
			this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
		}

		@Override
		public boolean canStart() {
			if (!enabled() || !this.pet.isTamed() || this.pet.isSitting() || this.pet.isLeashed() || this.pet.getRandom().nextInt(5) != 0) {
				return false;
			}
			this.bird = nearestPecking(this.pet, CHASE_RANGE);
			return this.bird != null;
		}

		@Override
		public boolean shouldContinue() {
			return this.ticks < 200 && this.bird.isAlive() && this.bird.isPecking() && !this.pet.isSitting();
		}

		@Override
		public void start() {
			this.ticks = 0;
			this.pet.getNavigation().startMovingTo(this.bird, 1.4);
		}

		@Override
		public void stop() {
			this.pet.getNavigation().stop();
			this.bird = null;
		}

		@Override
		public void tick() {
			this.ticks++;
			this.pet.getLookControl().lookAt(this.bird);
			if (this.pet.squaredDistanceTo(this.bird) < 2.5 * 2.5) {
				this.pet.playAmbientSound();
				scareOff(this.bird, this.pet);
			} else if (this.ticks % 10 == 0) {
				this.pet.getNavigation().startMovingTo(this.bird, 1.4);
			}
		}
	}
}
