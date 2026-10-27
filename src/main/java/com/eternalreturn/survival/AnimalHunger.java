package com.eternalreturn.survival;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.block.Block;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import org.jetbrains.annotations.Nullable;

/**
 * Animals get hungry and can starve (survival.animalsGetHungry), and thirsty, and can die of thirst
 * (survival.animalsGetThirsty). The farm animals in the entity tags eternalreturn:grazers (cows, sheep, horses,
 * donkeys, mules, rabbits) and eternalreturn:peckers (chickens, pigs) remember when they last ate and drank, on the
 * day clock. Once half a day has passed they eat when they can: a grazer a plant at its feet
 * (eternalreturn:grazing_plants: grass, ferns, flowers) or a grass block under it, which turns to dirt, as sheep do
 * (with mobGriefing off they still eat, the block stays); a pecker pecks the ground under it for seeds (block tag
 * eternalreturn:pecking_ground: grass, dirt, farmland...), leaving it as it is. Being fed their breeding food by a
 * player counts too, whenever they are hungry, not only to breed, and so does a trough with their food (Troughs).
 * After 3 days without food they lose a point of health every 30 seconds until they eat or die. A day after they
 * last drank they're thirsty: they drink standing in or next to water, or out in the rain, or go to a trough of
 * water near (Troughs), or else to open water; after 3 days without, they lose health as starving ones do. An
 * animal starts fed and watered the first time it ticks with this mod.
 */
public final class AnimalHunger {
	public static final TagKey<EntityType<?>> GRAZERS = TagKey.of(RegistryKeys.ENTITY_TYPE, EternalReturn.id("grazers"));
	public static final TagKey<EntityType<?>> PECKERS = TagKey.of(RegistryKeys.ENTITY_TYPE, EternalReturn.id("peckers"));
	public static final TagKey<Block> GRAZING_PLANTS = TagKey.of(RegistryKeys.BLOCK, EternalReturn.id("grazing_plants"));
	public static final TagKey<Block> PECKING_GROUND = TagKey.of(RegistryKeys.BLOCK, EternalReturn.id("pecking_ground"));
	public static final long DAY = 24000L;

	/** What an animal keeps (AnimalEntityMixin). */
	public interface Holder {
		/** When it last ate, on the day clock; Long.MIN_VALUE before its first tick with this mod. */
		long eternalreturn$lastFed();

		void eternalreturn$setLastFed(long time);

		/** When it last drank, the same way. */
		long eternalreturn$lastWatered();

		void eternalreturn$setLastWatered(long time);
	}

	private AnimalHunger() {
	}

	private static boolean farmAnimal(AnimalEntity animal) {
		return animal.getType().isIn(GRAZERS) || animal.getType().isIn(PECKERS);
	}

	public static boolean hungers(AnimalEntity animal) {
		return EternalReturnConfig.get().survival.animalsGetHungry && farmAnimal(animal);
	}

	public static boolean thirsts(AnimalEntity animal) {
		return EternalReturnConfig.get().survival.animalsGetThirsty && farmAnimal(animal);
	}

	private static double daysSince(AnimalEntity animal, long time) {
		return time == Long.MIN_VALUE ? 0.0 : Math.max(0L, animal.getWorld().getTimeOfDay() - time) / (double) DAY;
	}

	public static double daysUnfed(AnimalEntity animal) {
		return daysSince(animal, ((Holder) animal).eternalreturn$lastFed());
	}

	public static double daysUnwatered(AnimalEntity animal) {
		return daysSince(animal, ((Holder) animal).eternalreturn$lastWatered());
	}

	/** Hungry enough to graze, or to take food from a player outside breeding. */
	public static boolean isHungry(AnimalEntity animal) {
		return hungers(animal) && daysUnfed(animal) >= EternalReturnConfig.get().survival.hungryAfterDays;
	}

	public static boolean isThirsty(AnimalEntity animal) {
		return thirsts(animal) && daysUnwatered(animal) >= EternalReturnConfig.get().survival.thirstyAfterDays;
	}

	public static void fed(AnimalEntity animal) {
		((Holder) animal).eternalreturn$setLastFed(animal.getWorld().getTimeOfDay());
	}

	public static void watered(AnimalEntity animal) {
		((Holder) animal).eternalreturn$setLastWatered(animal.getWorld().getTimeOfDay());
	}

	/** In water, next to it (at its feet or a block down), or out in the rain. */
	public static boolean canDrinkHere(AnimalEntity animal) {
		World world = animal.getWorld();
		BlockPos feet = animal.getBlockPos();
		if (animal.isTouchingWater() || world.hasRain(feet)) {
			return true;
		}
		int reach = (int) Math.ceil(animal.getWidth() / 2.0);
		for (Direction side : Direction.Type.HORIZONTAL) {
			BlockPos beside = feet.offset(side, reach);
			if (world.getFluidState(beside).isIn(FluidTags.WATER) || world.getFluidState(beside.down()).isIn(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	/** Dry ground to stand on beside open water within range (water with air over it), or null; a few columns tried. */
	@Nullable
	public static BlockPos findWaterside(AnimalEntity animal, int range) {
		World world = animal.getWorld();
		BlockPos at = animal.getBlockPos();
		for (int i = 0; i < 48; i++) {
			BlockPos column = at.add(animal.getRandom().nextInt(2 * range + 1) - range, 0, animal.getRandom().nextInt(2 * range + 1) - range);
			for (int dy = 3; dy >= -4; dy--) {
				BlockPos water = column.up(dy);
				if (!world.getFluidState(water).isIn(FluidTags.WATER) || !world.getBlockState(water.up()).isAir()) {
					continue;
				}
				for (Direction side : Direction.Type.HORIZONTAL) {
					for (BlockPos stand : new BlockPos[] {water.offset(side), water.offset(side).up()}) {
						if (world.getBlockState(stand).isAir() && world.getBlockState(stand.up()).isAir() && world.getFluidState(stand.down()).isEmpty()
								&& world.getBlockState(stand.down()).isSolidBlock(world, stand.down())) {
							return stand;
						}
					}
				}
			}
		}
		return null;
	}

	/** From the animal's mobTick (server side). */
	public static void tick(AnimalEntity animal) {
		Holder holder = (Holder) animal;
		if (holder.eternalreturn$lastFed() == Long.MIN_VALUE) {
			fed(animal);
		}
		if (holder.eternalreturn$lastWatered() == Long.MIN_VALUE) {
			watered(animal);
		}
		if (!(animal.getWorld() instanceof ServerWorld world) || animal.age % 20 != 0) {
			return;
		}
		EternalReturnConfig.SurvivalTweaks cfg = EternalReturnConfig.get().survival;
		int interval = Math.max(1, cfg.starveDamageSeconds) * 20;
		if (thirsts(animal)) {
			if (isThirsty(animal) && canDrinkHere(animal)) {
				watered(animal);
			} else if (daysUnwatered(animal) >= cfg.daysBeforeDehydrating && animal.age % interval == 0) {
				animal.damage(world.getDamageSources().dryOut(), 1.0F);
			}
		}
		if (!hungers(animal)) {
			return;
		}
		if (isHungry(animal) && animal.getRandom().nextDouble() < cfg.grazeChance && graze(animal, world)) {
			return;
		}
		if (daysUnfed(animal) >= cfg.daysBeforeStarving && animal.age % interval == 0) {
			animal.damage(world.getDamageSources().starve(), 1.0F);
		}
	}

	/** Eats the plant at its feet or the grass under it (a pecker: pecks the ground). Returns whether it ate. */
	static boolean graze(AnimalEntity animal, ServerWorld world) {
		BlockPos feet = animal.getBlockPos();
		BlockPos below = feet.down();
		if (animal.getType().isIn(PECKERS)) {
			BlockState ground = world.getBlockState(below);
			if (!ground.isIn(PECKING_GROUND)) {
				return false;
			}
			world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), animal.getX(), feet.getY() + 0.05, animal.getZ(), 4, 0.15, 0.0, 0.15, 0.0);
			animal.playSound(SoundEvents.BLOCK_ROOTED_DIRT_STEP, 0.4F, 1.4F);
			fed(animal);
			return true;
		}
		BlockState plant = world.getBlockState(feet);
		boolean griefing = world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING);
		if (plant.isIn(GRAZING_PLANTS)) {
			if (griefing) {
				world.breakBlock(feet, false);
			} else {
				world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, feet, Block.getRawIdFromState(plant));
			}
			fed(animal);
			return true;
		}
		if (world.getBlockState(below).isOf(Blocks.GRASS_BLOCK)) {
			world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, below, Block.getRawIdFromState(Blocks.GRASS_BLOCK.getDefaultState()));
			if (griefing) {
				world.setBlockState(below, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
			fed(animal);
			return true;
		}
		return false;
	}
}
