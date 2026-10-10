package com.eternalreturn.survival;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.block.Block;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.WorldEvents;

/**
 * Animals get hungry and can starve (survival.animalsGetHungry). The animals in the entity tag
 * eternalreturn:grazers (farm animals) remember when they last ate, on the day clock. Once half a day
 * has passed they graze when they can: a plant at their feet (eternalreturn:grazing_plants: grass,
 * ferns, flowers) or a grass block under them, which turns to dirt, as sheep do (with mobGriefing off they
 * still eat, the block stays). Being fed their breeding food by a player counts too, whenever they are
 * hungry, not only to breed. After 3 days without food they lose a point of health every 30 seconds
 * until they eat or die. An animal starts fed the first time it ticks with this mod.
 */
public final class AnimalHunger {
	public static final TagKey<EntityType<?>> GRAZERS = TagKey.of(RegistryKeys.ENTITY_TYPE, EternalReturn.id("grazers"));
	public static final TagKey<Block> GRAZING_PLANTS = TagKey.of(RegistryKeys.BLOCK, EternalReturn.id("grazing_plants"));
	public static final long DAY = 24000L;

	/** What an animal keeps (AnimalEntityMixin). */
	public interface Holder {
		/** When it last ate, on the day clock; Long.MIN_VALUE before its first tick with this mod. */
		long eternalreturn$lastFed();

		void eternalreturn$setLastFed(long time);
	}

	private AnimalHunger() {
	}

	public static boolean hungers(AnimalEntity animal) {
		return EternalReturnConfig.get().survival.animalsGetHungry && animal.getType().isIn(GRAZERS);
	}

	public static double daysUnfed(AnimalEntity animal) {
		long fed = ((Holder) animal).eternalreturn$lastFed();
		return fed == Long.MIN_VALUE ? 0.0 : Math.max(0L, animal.getWorld().getTimeOfDay() - fed) / (double) DAY;
	}

	/** Hungry enough to graze, or to take food from a player outside breeding. */
	public static boolean isHungry(AnimalEntity animal) {
		return hungers(animal) && daysUnfed(animal) >= EternalReturnConfig.get().survival.hungryAfterDays;
	}

	public static void fed(AnimalEntity animal) {
		((Holder) animal).eternalreturn$setLastFed(animal.getWorld().getTimeOfDay());
	}

	/** From the animal's mobTick (server side). */
	public static void tick(AnimalEntity animal) {
		Holder holder = (Holder) animal;
		if (holder.eternalreturn$lastFed() == Long.MIN_VALUE) {
			fed(animal);
		}
		if (!hungers(animal) || !(animal.getWorld() instanceof ServerWorld world) || animal.age % 20 != 0) {
			return;
		}
		EternalReturnConfig.SurvivalTweaks cfg = EternalReturnConfig.get().survival;
		if (isHungry(animal) && animal.getRandom().nextDouble() < cfg.grazeChance && graze(animal, world)) {
			return;
		}
		int interval = Math.max(1, cfg.starveDamageSeconds) * 20;
		if (daysUnfed(animal) >= cfg.daysBeforeStarving && animal.age % interval == 0) {
			animal.damage(world.getDamageSources().starve(), 1.0F);
		}
	}

	/** Eats the plant at its feet or the grass under it. Returns whether it ate. */
	static boolean graze(AnimalEntity animal, ServerWorld world) {
		BlockPos feet = animal.getBlockPos();
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
		BlockPos below = feet.down();
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
