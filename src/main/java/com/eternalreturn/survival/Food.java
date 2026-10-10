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
 * <li>eating time by kind of food: snacks (item tag eternalreturn:snack_foods) 0.8 seconds, meals
 * (eternalreturn:meal_foods) 2.4 seconds, anything else vanilla's 1.6; potions, milk, honey and ominous
 * bottles take 1 second to drink;</li>
 * <li>getting hurt stops eating or drinking (the item isn't used up);</li>
 * <li>raw food (eternalreturn:raw_foods: raw meat and fish, this mod's fish included) poisons 70% of the
 * time, for 5 seconds.</li>
 * </ul>
 */
public final class Food {
	public static final TagKey<Item> SNACK_FOODS = TagKey.of(RegistryKeys.ITEM, EternalReturn.id("snack_foods"));
	public static final TagKey<Item> MEAL_FOODS = TagKey.of(RegistryKeys.ITEM, EternalReturn.id("meal_foods"));
	public static final TagKey<Item> RAW_FOODS = TagKey.of(RegistryKeys.ITEM, EternalReturn.id("raw_foods"));
	public static final int SNACK_TICKS = 16;
	public static final int MEAL_TICKS = 48;
	public static final int DRINK_TICKS = 20;

	private Food() {
	}

	/** The eating time for a food in one of the groups, or -1 to keep its own. */
	public static int eatTicks(ItemStack stack) {
		if (stack.isIn(SNACK_FOODS)) {
			return SNACK_TICKS;
		}
		if (stack.isIn(MEAL_FOODS)) {
			return MEAL_TICKS;
		}
		return -1;
	}

	/** After damage lands: an eater or drinker stops. */
	public static void interrupt(LivingEntity entity) {
		if (EternalReturnConfig.get().survival.hitsInterruptEating && entity.isUsingItem()) {
			UseAction action = entity.getActiveItem().getUseAction();
			if (action == UseAction.EAT || action == UseAction.DRINK) {
				entity.stopUsingItem();
			}
		}
	}

	/** As food is eaten: raw food may poison. */
	public static void ate(LivingEntity eater, ItemStack stack) {
		if (!eater.getWorld().isClient && stack.isIn(RAW_FOODS) && eater.getRandom().nextDouble() < EternalReturnConfig.get().survival.rawFoodPoisonChance) {
			eater.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 100, 0));
		}
	}
}
