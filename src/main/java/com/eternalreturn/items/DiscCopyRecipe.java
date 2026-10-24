package com.eternalreturn.items;

import com.eternalreturn.EternalReturn;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

/**
 * Copying a music disc in a crafting table: a disc (anything a jukebox plays) and an echo shard make a copy of the
 * disc, and the disc stays in the grid.
 */
public class DiscCopyRecipe extends SpecialCraftingRecipe {
	public static final RecipeSerializer<DiscCopyRecipe> SERIALIZER = Registry.register(Registries.RECIPE_SERIALIZER, EternalReturn.id("music_disc_copy"),
			new SpecialRecipeSerializer<>(DiscCopyRecipe::new));

	public DiscCopyRecipe(CraftingRecipeCategory category) {
		super(category);
	}

	public static void register() {
	}

	/** The slot of the disc, or -1 if the grid holds anything but one disc and one echo shard. */
	private static int disc(CraftingRecipeInput input) {
		int disc = -1;
		int shards = 0;
		for (int i = 0; i < input.getSize(); i++) {
			ItemStack stack = input.getStackInSlot(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.isOf(Items.ECHO_SHARD)) {
				shards++;
			} else if (stack.contains(DataComponentTypes.JUKEBOX_PLAYABLE) && disc == -1) {
				disc = i;
			} else {
				return -1;
			}
		}
		return shards == 1 ? disc : -1;
	}

	@Override
	public boolean matches(CraftingRecipeInput input, World world) {
		return disc(input) != -1;
	}

	@Override
	public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
		int disc = disc(input);
		return disc == -1 ? ItemStack.EMPTY : input.getStackInSlot(disc).copyWithCount(1);
	}

	/** The disc stays. */
	@Override
	public DefaultedList<ItemStack> getRemainder(CraftingRecipeInput input) {
		DefaultedList<ItemStack> left = DefaultedList.ofSize(input.getSize(), ItemStack.EMPTY);
		int disc = disc(input);
		if (disc != -1) {
			left.set(disc, input.getStackInSlot(disc).copyWithCount(1));
		}
		return left;
	}

	@Override
	public boolean fits(int width, int height) {
		return width * height >= 2;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SERIALIZER;
	}
}
