package com.eternalreturn.items;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * Potions:
 * <ul>
 * <li>Haste can be brewed: an awkward potion and a diamond (redstone makes it last longer, glowstone makes it Haste
 * II), splash and lingering as usual;</li>
 * <li>potions of Blindness and of Bad Luck exist (illusioners drop them; not brewable);</li>
 * <li>drinkable, splash and lingering potions stack up to 16 (survival.potionStackSize);</li>
 * <li>potions of the same kind can be mixed in a crafting table (PotionMixingRecipe), up to 8 effects in one.</li>
 * </ul>
 */
public final class Potions2 {
	public static final RegistryEntry<Potion> HASTE = register("haste", new Potion("haste", new StatusEffectInstance(StatusEffects.HASTE, 3600)));
	public static final RegistryEntry<Potion> LONG_HASTE = register("long_haste", new Potion("haste", new StatusEffectInstance(StatusEffects.HASTE, 9600)));
	public static final RegistryEntry<Potion> STRONG_HASTE = register("strong_haste", new Potion("haste", new StatusEffectInstance(StatusEffects.HASTE, 1800, 1)));
	public static final RegistryEntry<Potion> BLINDNESS = register("blindness", new Potion("blindness", new StatusEffectInstance(StatusEffects.BLINDNESS, 900)));
	public static final RegistryEntry<Potion> FREEZING = register("freezing",
			new Potion("freezing", new StatusEffectInstance(com.eternalreturn.survival.Freezing.EFFECT, 600)));
	public static final RegistryEntry<Potion> LONG_FREEZING = register("long_freezing",
			new Potion("freezing", new StatusEffectInstance(com.eternalreturn.survival.Freezing.EFFECT, 1800)));
	public static final RegistryEntry<Potion> BAD_LUCK = register("bad_luck", new Potion("bad_luck", new StatusEffectInstance(StatusEffects.UNLUCK, 6000)));
	public static final RecipeSerializer<PotionMixingRecipe> MIXING = Registry.register(Registries.RECIPE_SERIALIZER, EternalReturn.id("potion_mixing"),
			new SpecialRecipeSerializer<>(PotionMixingRecipe::new));

	private Potions2() {
	}

	private static RegistryEntry<Potion> register(String name, Potion potion) {
		return Registry.registerReference(Registries.POTION, EternalReturn.id(name), potion);
	}

	public static void register() {
		FabricBrewingRecipeRegistryBuilder.BUILD.register(builder -> {
			builder.registerPotionRecipe(Potions.AWKWARD, Items.DIAMOND, HASTE);
			builder.registerPotionRecipe(HASTE, Items.REDSTONE, LONG_HASTE);
			builder.registerPotionRecipe(HASTE, Items.GLOWSTONE_DUST, STRONG_HASTE);
			builder.registerPotionRecipe(Potions.AWKWARD, com.eternalreturn.items.IceBall.ITEM, FREEZING);
			builder.registerPotionRecipe(FREEZING, Items.REDSTONE, LONG_FREEZING);
		});
		DefaultItemComponentEvents.MODIFY.register(context -> {
			int size = EternalReturnConfig.get().survival.potionStackSize;
			for (var item : new net.minecraft.item.Item[]{Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION}) {
				context.modify(item, builder -> builder.add(DataComponentTypes.MAX_STACK_SIZE, size));
			}
		});
	}
}
