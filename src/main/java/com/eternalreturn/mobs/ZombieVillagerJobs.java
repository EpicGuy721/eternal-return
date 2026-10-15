package com.eternalreturn.mobs;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.duck.JockeyMount;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;

/**
 * Zombies by trade, and on horseback:
 * <ul>
 * <li>a zombie fletcher comes with a crossbow and shoots it (ZombieCrossbowGoal);</li>
 * <li>a zombie cleric throws potions of Harming at players, and at hurt undead (whom Harming heals) (ZombiePotionGoal);</li>
 * <li>a zombie fisherman comes with a fishing rod and reels players in, like the fishing-rod zombies (ReelInTargetGoal);</li>
 * <li>mobs.zombieHorsemanChance (2%) of adult zombies that spawn naturally ride a zombie horse, which goes where its
 * rider goes and despawns with it.</li>
 * </ul>
 */
public final class ZombieVillagerJobs {
	private ZombieVillagerJobs() {
	}

	/** As a zombie spawns, after its own gear. */
	public static void onSpawn(ZombieEntity zombie, ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason) {
		if (zombie instanceof ZombieVillagerEntity villager && villager.getMainHandStack().isEmpty()) {
			var profession = villager.getVillagerData().getProfession();
			if (profession == VillagerProfession.FLETCHER) {
				villager.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
			} else if (profession == VillagerProfession.FISHERMAN) {
				villager.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
			}
		}
		if (zombie.getType() == EntityType.ZOMBIE && !zombie.isBaby() && !zombie.hasVehicle() && reason == SpawnReason.NATURAL
				&& world.getRandom().nextDouble() < EternalReturnConfig.get().mobs.zombieHorsemanChance) {
			AbstractHorseEntity horse = EntityType.ZOMBIE_HORSE.create(zombie.getWorld());
			if (horse == null) {
				return;
			}
			horse.refreshPositionAndAngles(zombie.getX(), zombie.getY(), zombie.getZ(), zombie.getYaw(), 0.0F);
			if (!world.isSpaceEmpty(horse, horse.getBoundingBox().stretch(0.0, zombie.getHeight(), 0.0))) {
				return;
			}
			horse.initialize(world, difficulty, SpawnReason.JOCKEY, null);
			horse.setBreedingAge(0);
			horse.setTame(true);
			((JockeyMount) horse).eternalreturn$setJockeyMount(true);
			if (zombie.startRiding(horse, true)) {
				world.spawnEntity(horse);
			}
		}
	}

	public static boolean isCleric(ZombieEntity zombie) {
		return zombie instanceof ZombieVillagerEntity villager && villager.getVillagerData().getProfession() == VillagerProfession.CLERIC;
	}
}
