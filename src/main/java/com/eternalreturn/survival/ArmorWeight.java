package com.eternalreturn.survival;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.Map;

/**
 * Armor weight (survival.armorWeight): each armor material has a weight, the share of walking speed a
 * full set of it takes away, split over the pieces (helmet 15%, chestplate 40%, leggings 30%, boots 15%
 * of the set's weight). A full diamond set takes 30%, as much as Slowness II; netherite and iron 15%
 * (Slowness I); gold 20%; chainmail 8%; leather and turtle 3%. Weightless on a piece makes it weigh
 * nothing. Applied to players as one speed modifier, updated when their armor changes.
 */
public final class ArmorWeight {
	public static final Identifier MODIFIER_ID = EternalReturn.id("armor_weight");
	public static final Identifier WEIGHTLESS = EternalReturn.id("weightless");
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private ArmorWeight() {
	}

	/** The share of a full set's weight each piece carries. */
	static double share(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD, FEET -> 0.15;
			case CHEST -> 0.40;
			case LEGS -> 0.30;
			default -> 0.0;
		};
	}

	/** How much walking speed this piece takes away (0.06 = 6%), or 0 for anything that isn't armor or is Weightless. */
	public static double weight(ItemStack stack) {
		if (!(stack.getItem() instanceof ArmorItem armor) || isWeightless(stack)) {
			return 0.0;
		}
		Map<String, Double> sets = EternalReturnConfig.get().survival.armorWeight;
		String material = armor.getMaterial().getKey().map(key -> key.getValue().toString()).orElse("");
		return sets.getOrDefault(material, 0.0) * share(armor.getSlotType());
	}

	public static boolean isWeightless(ItemStack stack) {
		ItemEnchantmentsComponent enchantments = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
		return enchantments.getEnchantments().stream().anyMatch(entry -> entry.matchesId(WEIGHTLESS));
	}

	public static double total(PlayerEntity player) {
		double total = 0.0;
		for (EquipmentSlot slot : SLOTS) {
			total += weight(player.getEquippedStack(slot));
		}
		return Math.min(total, 0.9);
	}

	/** Server side, each tick: brings the speed modifier in line with the armor worn. */
	public static void update(PlayerEntity player) {
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		double weight = total(player);
		EntityAttributeModifier current = speed.getModifier(MODIFIER_ID);
		if (current != null && current.value() == -weight) {
			return;
		}
		if (current != null) {
			speed.removeModifier(MODIFIER_ID);
		}
		if (weight > 0.0) {
			speed.addTemporaryModifier(new EntityAttributeModifier(MODIFIER_ID, -weight, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}
