package com.eternalreturn.mobs;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.provider.EnchantmentProviders;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;

/** Spawn equipment changes for zombies and skeletons. */
public final class SpawnGear {
	/** Vanilla MobEntity.initEquipment armor roll: 0.15 * clamped local difficulty. */
	private static final float VANILLA_ARMOR_CHANCE = 0.15F;

	private SpawnGear() {
	}

	private static EternalReturnConfig.MobTweaks cfg() {
		return EternalReturnConfig.get().mobs;
	}

	private static float chance(double base, double perDifficulty, LocalDifficulty difficulty) {
		return MathHelper.clamp((float) (base + perDifficulty * difficulty.getClampedLocalDifficulty()), 0.0F, 1.0F);
	}

	/**
	 * Runs after vanilla's armor roll. If that roll gave nothing, roll again with whatever chance
	 * brings the total up to the configured one, then pick pieces exactly the way vanilla does.
	 */
	public static void rollExtraArmor(MobEntity mob, Random random, LocalDifficulty difficulty) {
		if (!mob.getType().isIn(MobTags.ARMORED_SPAWNS) || hasAnyArmor(mob)) {
			return;
		}
		float vanilla = VANILLA_ARMOR_CHANCE * difficulty.getClampedLocalDifficulty();
		float target = chance(cfg().armorChanceBase, cfg().armorChancePerDifficulty, difficulty);
		if (target <= vanilla || random.nextFloat() >= (target - vanilla) / (1.0F - vanilla)) {
			return;
		}

		int tier = random.nextInt(2);
		for (int i = 0; i < 3; i++) {
			if (random.nextFloat() < 0.095F) {
				tier++;
			}
		}
		float stopChance = mob.getWorld().getDifficulty() == Difficulty.HARD ? 0.1F : 0.25F;
		boolean first = true;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
				continue;
			}
			if (!first && random.nextFloat() < stopChance) {
				break;
			}
			first = false;
			if (mob.getEquippedStack(slot).isEmpty()) {
				Item item = MobEntity.getEquipmentForSlot(slot, tier);
				if (item != null) {
					mob.equipStack(slot, new ItemStack(item));
				}
			}
		}
	}

	/** Replaces vanilla's per-piece armor enchant roll for armored_spawns mobs. Returns false to leave it to vanilla. */
	public static boolean enchantArmorPiece(MobEntity mob, ServerWorldAccess world, Random random, EquipmentSlot slot, LocalDifficulty difficulty) {
		if (!mob.getType().isIn(MobTags.ARMORED_SPAWNS)) {
			return false;
		}
		ItemStack stack = mob.getEquippedStack(slot);
		float vanilla = 0.5F * difficulty.getClampedLocalDifficulty();
		float chance = Math.max(vanilla, chance(cfg().armorEnchantChanceBase, cfg().armorEnchantChancePerDifficulty, difficulty));
		if (!stack.isEmpty() && random.nextFloat() < chance) {
			EnchantmentHelper.applyEnchantmentProvider(stack, world.getRegistryManager(), EnchantmentProviders.MOB_SPAWN_EQUIPMENT, difficulty, random);
			mob.equipStack(slot, stack);
		}
		return true;
	}

	public static void rollZombieExtras(MobEntity zombie, Random random) {
		if (zombie.getMainHandStack().isEmpty() && random.nextFloat() < cfg().zombieFishingRodChance) {
			zombie.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
		}
		if (zombie.getOffHandStack().isEmpty() && random.nextFloat() < cfg().zombieEnderPearlChance) {
			zombie.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.ENDER_PEARL));
		}
	}

	/** The sword goes in the off hand; SkeletonSidearm moves it to the main hand when the target closes in. */
	public static void rollSkeletonSword(MobEntity skeleton, Random random) {
		if (skeleton.getOffHandStack().isEmpty() && skeleton.getMainHandStack().isOf(Items.BOW)
				&& random.nextFloat() < cfg().skeletonSwordChance) {
			skeleton.equipStack(EquipmentSlot.OFFHAND, new ItemStack(random.nextFloat() < 0.25F ? Items.IRON_SWORD : Items.STONE_SWORD));
		}
	}

	private static boolean hasAnyArmor(MobEntity mob) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && !mob.getEquippedStack(slot).isEmpty()) {
				return true;
			}
		}
		return false;
	}
}
