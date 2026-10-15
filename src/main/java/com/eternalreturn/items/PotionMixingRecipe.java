package com.eternalreturn.items;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mixing potions in a crafting table: two or more potions of the same kind (drinkable, splash or lingering), each
 * with an effect, make one "Mixed Potion" with all their effects, each keeping its own time and level (the stronger,
 * then the longer, where two share an effect), up to 8 effects. The other bottles come back empty.
 */
public class PotionMixingRecipe extends SpecialCraftingRecipe {
	public static final int MAX_EFFECTS = 8;

	public PotionMixingRecipe(CraftingRecipeCategory category) {
		super(category);
	}

	private static boolean isPotion(Item item) {
		return item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION;
	}

	/** The effects of all the potions, one per kind of effect, or null if these don't mix. */
	private static List<StatusEffectInstance> mix(CraftingRecipeInput input) {
		Item kind = null;
		int potions = 0;
		Map<Object, StatusEffectInstance> effects = new LinkedHashMap<>();
		for (int i = 0; i < input.getSize(); i++) {
			ItemStack stack = input.getStackInSlot(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (!isPotion(stack.getItem()) || (kind != null && stack.getItem() != kind)) {
				return null;
			}
			kind = stack.getItem();
			potions++;
			boolean any = false;
			for (StatusEffectInstance effect : stack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT).getEffects()) {
				any = true;
				StatusEffectInstance had = effects.get(effect.getEffectType());
				if (had == null || effect.getAmplifier() > had.getAmplifier()
						|| (effect.getAmplifier() == had.getAmplifier() && (effect.isInfinite() || effect.getDuration() > had.getDuration()))) {
					effects.put(effect.getEffectType(), new StatusEffectInstance(effect));
				}
			}
			if (!any) {
				return null;
			}
		}
		if (potions < 2 || effects.size() > MAX_EFFECTS) {
			return null;
		}
		return new ArrayList<>(effects.values());
	}

	@Override
	public boolean matches(CraftingRecipeInput input, World world) {
		return mix(input) != null;
	}

	@Override
	public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
		List<StatusEffectInstance> effects = mix(input);
		if (effects == null) {
			return ItemStack.EMPTY;
		}
		Item kind = null;
		for (int i = 0; i < input.getSize() && kind == null; i++) {
			if (!input.getStackInSlot(i).isEmpty()) {
				kind = input.getStackInSlot(i).getItem();
			}
		}
		ItemStack result = new ItemStack(kind);
		result.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Optional.empty(), Optional.empty(), effects));
		String name = kind == Items.SPLASH_POTION ? "mixed_splash_potion" : kind == Items.LINGERING_POTION ? "mixed_lingering_potion" : "mixed_potion";
		result.set(DataComponentTypes.ITEM_NAME, Text.translatable("item.eternalreturn." + name));
		return result;
	}

	/** Every potion but one leaves its bottle behind. */
	@Override
	public DefaultedList<ItemStack> getRemainder(CraftingRecipeInput input) {
		DefaultedList<ItemStack> left = DefaultedList.ofSize(input.getSize(), ItemStack.EMPTY);
		boolean kept = false;
		for (int i = 0; i < input.getSize(); i++) {
			if (!input.getStackInSlot(i).isEmpty()) {
				if (kept) {
					left.set(i, new ItemStack(Items.GLASS_BOTTLE));
				}
				kept = true;
			}
		}
		return left;
	}

	@Override
	public boolean fits(int width, int height) {
		return width * height >= 2;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return Potions2.MIXING;
	}
}
