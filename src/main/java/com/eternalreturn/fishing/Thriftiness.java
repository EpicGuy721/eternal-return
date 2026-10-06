package com.eternalreturn.fishing;

import com.eternalreturn.EternalReturn;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.effect.EnchantmentValueEffect;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * Thriftiness (data/eternalreturn/enchantment/thriftiness.json): a fishing rod enchantment that
 * gives each catch a chance to leave the bait unused. The chance comes from the enchantment's
 * eternalreturn:bait_saving_chance effect, so a datapack can retune the curve.
 */
public final class Thriftiness {
	public static final RegistryKey<Enchantment> KEY = RegistryKey.of(RegistryKeys.ENCHANTMENT, EternalReturn.id("thriftiness"));

	public static final ComponentType<EnchantmentValueEffect> BAIT_SAVING_CHANCE = Registry.register(
			Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE,
			EternalReturn.id("bait_saving_chance"),
			ComponentType.<EnchantmentValueEffect>builder().codec(EnchantmentValueEffect.CODEC).build());

	private Thriftiness() {
	}

	public static void register() {
	}

	/** Chance (0 to 1) that a catch with this rod keeps its bait. */
	public static float baitSavingChance(ItemStack rod, Random random) {
		float chance = 0.0F;
		ItemEnchantmentsComponent enchantments = rod.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
		for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : enchantments.getEnchantmentEntries()) {
			EnchantmentValueEffect effect = entry.getKey().value().effects().get(BAIT_SAVING_CHANCE);
			if (effect != null) {
				chance = effect.apply(entry.getIntValue(), random, chance);
			}
		}
		return MathHelper.clamp(chance, 0.0F, 1.0F);
	}
}
