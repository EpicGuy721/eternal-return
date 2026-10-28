package com.eternalreturn.survival;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.UseAction;

/**
 * Eating and drinking:
 * <ul>
 * <li>potions, milk and ominous bottles take 1 second to drink (each food's own eating time is in the food
 * table, Meals);</li>
 * <li>getting hurt stops eating or drinking (the item isn't used up);</li>
 * <li>raw food (eternalreturn:raw_foods: raw meat and fish, this mod's fish included) poisons 70% of the
 * time, for 5 seconds.</li>
 * </ul>
 */
public final class Food {
	public static final TagKey<Item> RAW_FOODS = TagKey.of(RegistryKeys.ITEM, EternalReturn.id("raw_foods"));
	public static final int DRINK_TICKS = 20;

	private Food() {
	}

	/** After damage lands: an eater stops (a drinker drinks on). */
	public static void interrupt(LivingEntity entity) {
		if (EternalReturnConfig.get().survival.hitsInterruptEating && entity.isUsingItem() && entity.getActiveItem().getUseAction() == UseAction.EAT) {
			entity.stopUsingItem();
		}
	}

	/** As food is eaten: raw food may poison. */
	public static void ate(LivingEntity eater, ItemStack stack) {
		if (!eater.getWorld().isClient && stack.isIn(RAW_FOODS) && eater.getRandom().nextDouble() < EternalReturnConfig.get().survival.rawFoodPoisonChance) {
			eater.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 100, 0));
		}
	}
}
