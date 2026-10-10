package com.eternalreturn.mobs;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.duck.JockeyMount;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;

import java.util.List;

/**
 * Zombie habits and the newer jockeys:
 * <ul>
 * <li>zombies that spawn deep underground (below y 0) sometimes hold a pickaxe or shovel and dig toward
 * a target they can't reach (MineTowardTargetGoal; mobs.miningZombieChance);</li>
 * <li>adult zombies hunt farm animals (entity tag eternalreturn:zombie_prey) and, when hurt, eat raw meat
 * lying about (ZombieEatMeatGoal);</li>
 * <li>baby zombies sometimes ride a cow, pig or sheep (mobs.babyZombieAnimalJockeyChance) and steer it,
 * as they do chickens;</li>
 * <li>phantoms sometimes carry a skeleton (mobs.phantomSkeletonChance), and slimes too
 * (mobs.slimeSkeletonChance); the mount moves on its own while the skeleton shoots.</li>
 * </ul>
 */
public final class ZombieHabits {
	public static final TagKey<EntityType<?>> ZOMBIE_PREY = TagKey.of(RegistryKeys.ENTITY_TYPE, EternalReturn.id("zombie_prey"));
	private static final List<EntityType<? extends AnimalEntity>> BABY_MOUNTS = List.of(EntityType.COW, EntityType.PIG, EntityType.SHEEP);

	private ZombieHabits() {
	}

	/** From a zombie's initEquipment: deep underground, sometimes a pickaxe or shovel instead of an empty hand. */
	public static void rollDiggingTool(ZombieEntity zombie, Random random) {
		if (zombie.getY() >= 0.0 || !zombie.getMainHandStack().isEmpty() || random.nextDouble() >= EternalReturnConfig.get().mobs.miningZombieChance) {
			return;
		}
		ItemStack tool = switch (random.nextInt(4)) {
			case 0 -> new ItemStack(Items.IRON_PICKAXE);
			case 1 -> new ItemStack(Items.STONE_SHOVEL);
			case 2 -> new ItemStack(Items.IRON_SHOVEL);
			default -> new ItemStack(Items.STONE_PICKAXE);
		};
		zombie.equipStack(EquipmentSlot.MAINHAND, tool);
	}

	/** From a baby zombie's initialize: now and then it comes riding a cow, pig or sheep. */
	public static void tryAnimalJockey(ZombieEntity zombie, ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason) {
		if (!zombie.isBaby() || zombie.hasVehicle() || reason == SpawnReason.CONVERSION || reason == SpawnReason.JOCKEY
				|| world.getRandom().nextDouble() >= EternalReturnConfig.get().mobs.babyZombieAnimalJockeyChance) {
			return;
		}
		EntityType<? extends AnimalEntity> type = BABY_MOUNTS.get(world.getRandom().nextInt(BABY_MOUNTS.size()));
		AnimalEntity mount = type.create(zombie.getWorld());
		if (mount == null) {
			return;
		}
		mount.refreshPositionAndAngles(zombie.getX(), zombie.getY(), zombie.getZ(), zombie.getYaw(), 0.0F);
		if (!world.isSpaceEmpty(mount, mount.getBoundingBox().stretch(0.0, zombie.getHeight(), 0.0))) {
			return;
		}
		mount.initialize(world, difficulty, SpawnReason.JOCKEY, null);
		mount.setBreedingAge(0);
		((JockeyMount) mount).eternalreturn$setJockeyMount(true);
		if (zombie.startRiding(mount)) {
			world.spawnEntity(mount);
		}
	}

	/** From a phantom's or slime's initialize: a skeleton on its back, now and then. */
	public static void trySkeletonRider(MobEntity mount, ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, double chance) {
		if (mount.hasPassengers() || reason == SpawnReason.JOCKEY || world.getRandom().nextDouble() >= chance) {
			return;
		}
		SkeletonEntity skeleton = EntityType.SKELETON.create(mount.getWorld());
		if (skeleton == null) {
			return;
		}
		skeleton.refreshPositionAndAngles(mount.getX(), mount.getY(), mount.getZ(), mount.getYaw(), 0.0F);
		skeleton.initialize(world, difficulty, SpawnReason.JOCKEY, null);
		skeleton.startRiding(mount, true);
	}
}
