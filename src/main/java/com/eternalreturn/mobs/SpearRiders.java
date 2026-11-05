package com.eternalreturn.mobs;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.items.Spears;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

/**
 * Mobs spawn with a spear only when they come riding a mount that can charge with it (eternalreturn:spear_mounts: the
 * horses, donkeys, mules and camels), such as the zombie horsemen. Such a rider that spawned with the weapon a spear
 * replaces takes the spear instead, at the old odds: a third of the zombies (husks, zombie villagers) with an iron
 * sword or shovel get an iron spear, a third of the zombified piglins and half the piglins with a golden sword a golden
 * spear. Checked both when the mob is first fitted out (SpearRiderMixin, at the end of its initialize: a rider mounted
 * before it, as on spiders) and when it mounts while still unspawned (a horseman's horse comes after).
 */
public final class SpearRiders {
	public static final TagKey<EntityType<?>> SPEAR_MOUNTS = TagKey.of(RegistryKeys.ENTITY_TYPE, EternalReturn.id("spear_mounts"));
	/** Riders already given their one roll (both checks can find one mounted and armed). */
	private static final java.util.Set<MobEntity> ROLLED = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

	private SpearRiders() {
	}

	public static void arm(MobEntity rider) {
		if (rider.getWorld().isClient || rider.age > 0) {
			return;
		}
		Entity mount = rider.getVehicle();
		if (mount == null || !mount.getType().isIn(SPEAR_MOUNTS) || rider.getMainHandStack().isEmpty() || !ROLLED.add(rider)) {
			return;
		}
		ItemStack weapon = rider.getMainHandStack();
		ItemStack spear = null;
		if (rider instanceof ZombifiedPiglinEntity) {
			spear = weapon.isOf(Items.GOLDEN_SWORD) && rider.getRandom().nextInt(3) == 0 ? new ItemStack(Spears.GOLDEN) : null;
		} else if (rider instanceof ZombieEntity) {
			spear = (weapon.isOf(Items.IRON_SWORD) || weapon.isOf(Items.IRON_SHOVEL)) && rider.getRandom().nextInt(3) == 0 ? new ItemStack(Spears.IRON) : null;
		} else if (rider instanceof PiglinEntity) {
			spear = weapon.isOf(Items.GOLDEN_SWORD) && rider.getRandom().nextBoolean() ? new ItemStack(Spears.GOLDEN) : null;
		}
		if (spear != null) {
			rider.equipStack(EquipmentSlot.MAINHAND, spear);
		}
	}
}
