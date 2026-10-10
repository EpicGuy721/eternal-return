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
import net.minecraft.util.math.random.Random;

/**
 * Slayer (data/eternalreturn/enchantment/slayer.json): a fishing rod enchantment that adds 5% per level
 * to the chance that a bite is a monster (HostileCatches), through its eternalreturn:hostile_catch_chance
 * effect, so a datapack can retune it.
 */
public final class Slayer {
	public static final RegistryKey<Enchantment> KEY = RegistryKey.of(RegistryKeys.ENCHANTMENT, EternalReturn.id("slayer"));

	public static final ComponentType<EnchantmentValueEffect> HOSTILE_CATCH_CHANCE = Registry.register(
			Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE,
			EternalReturn.id("hostile_catch_chance"),
			ComponentType.<EnchantmentValueEffect>builder().codec(EnchantmentValueEffect.CODEC).build());

	private Slayer() {
	}

	public static void register() {
	}

	/** The chance a bite is a monster with this rod, starting from the configured base chance. */
	public static double hostileCatchChance(ItemStack rod, double base, Random random) {
		float chance = (float) base;
		ItemEnchantmentsComponent enchantments = rod.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
		for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : enchantments.getEnchantmentEntries()) {
			EnchantmentValueEffect effect = entry.getKey().value().effects().get(HOSTILE_CATCH_CHANCE);
			if (effect != null) {
				chance = effect.apply(entry.getIntValue(), random, chance);
			}
		}
		return Math.max(0.0, Math.min(1.0, chance));
	}
}
