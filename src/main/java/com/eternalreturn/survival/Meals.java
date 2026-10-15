package com.eternalreturn.survival;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.fishing.FishingItems;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * No hunger (survival.removeHunger): the food bar stays full and is hidden, so sprinting always works, and health
 * never comes back on its own (except on Peaceful). Food heals instead, each kind its own way, from the food table
 * (src/main/resources/eternalreturn/foods.json): how many fit in a stack, how long it takes to eat, how much health
 * it heals and how fast (at once, or spread over a few seconds), and what effects it gives. Ordinary food can only
 * be eaten when hurt; food with a perk any time. Healing from several foods runs side by side. The Hunger effect
 * halves food healing; Saturation heals. The mod's fish keep their own settings and heal like raw salmon.
 */
public final class Meals {
	/** A food's healing: so much health over so many seconds (0: at once). */
	public record Heal(float health, float seconds) {
	}

	/** What a player keeps (PlayerEntityMealsMixin): the healing still to come from each food eaten. */
	public interface Holder {
		List<float[]> eternalreturn$meals();
	}

	private static final Map<Item, Heal> HEALS = new HashMap<>();
	private static Heal cake = new Heal(4.0F, 3.0F);
	private static final String NBT_KEY = "eternalreturn_meals";

	private Meals() {
	}

	public static boolean enabled() {
		return EternalReturnConfig.get().survival.removeHunger;
	}

	public static void register() {
		JsonObject table = read();
		JsonObject foods = table.getAsJsonObject("foods");
		JsonObject cakeEntry = table.getAsJsonObject("cake");
		cake = new Heal(cakeEntry.get("heal").getAsFloat(), cakeEntry.get("seconds").getAsFloat());
		Map<Item, JsonObject> entries = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : foods.entrySet()) {
			Item item = Registries.ITEM.get(Identifier.of(e.getKey()));
			if (item == Items.AIR) {
				throw new IllegalStateException("foods.json names unknown item " + e.getKey());
			}
			JsonObject entry = e.getValue().getAsJsonObject();
			entries.put(item, entry);
			HEALS.put(item, new Heal(entry.get("heal").getAsFloat(), entry.get("heal_seconds").getAsFloat()));
		}
		DefaultItemComponentEvents.MODIFY.register(context -> entries.forEach((item, entry) -> context.modify(item, builder -> {
			FoodComponent food = item.getComponents().get(DataComponentTypes.FOOD);
			if (food == null) {
				return;
			}
			List<FoodComponent.StatusEffectEntry> effects = food.effects();
			if (entry.get("effects").isJsonArray()) {
				effects = new ArrayList<>();
				for (JsonElement element : entry.getAsJsonArray("effects")) {
					JsonObject effect = element.getAsJsonObject();
					var type = Registries.STATUS_EFFECT.getEntry(Identifier.of(effect.get("effect").getAsString())).orElseThrow();
					effects.add(new FoodComponent.StatusEffectEntry(new StatusEffectInstance(type, Math.round(effect.get("seconds").getAsFloat() * 20.0F),
							effect.get("level").getAsInt() - 1), effect.get("chance").getAsFloat()));
				}
			}
			builder.add(DataComponentTypes.FOOD, new FoodComponent(food.nutrition(), food.saturation(), entry.get("always_edible").getAsBoolean(),
					entry.get("eat_seconds").getAsFloat(), food.usingConvertsTo(), List.copyOf(effects)));
			builder.add(DataComponentTypes.MAX_STACK_SIZE, entry.get("stack").getAsInt());
		})));
	}

	/** How a food heals, or null if it doesn't (the mod's fish heal like raw salmon). */
	@Nullable
	public static Heal heal(ItemStack stack) {
		if (stack.getItem() instanceof FishingItems.FishItem) {
			return HEALS.get(Items.SALMON);
		}
		return HEALS.get(stack.getItem());
	}

	public static Heal cake() {
		return cake;
	}

	/** Whether the player may start eating: when hurt, or anything that can always be eaten. */
	public static boolean canEat(PlayerEntity player, boolean alwaysEdible) {
		return alwaysEdible || player.getAbilities().invulnerable || player.getHealth() < player.getMaxHealth();
	}

	/** A food was eaten: its healing starts. */
	public static void ate(PlayerEntity player, ItemStack stack) {
		Heal heal = heal(stack);
		if (heal != null) {
			add(player, heal);
		}
	}

	public static void add(PlayerEntity player, Heal heal) {
		if (player.getWorld().isClient || heal.health() <= 0.0F) {
			return;
		}
		float health = player.hasStatusEffect(StatusEffects.HUNGER) ? heal.health() * 0.5F : heal.health();
		if (heal.seconds() <= 0.0F) {
			player.heal(health);
		} else {
			((Holder) player).eternalreturn$meals().add(new float[]{health, health / (heal.seconds() * 20.0F)});
		}
	}

	/** Each tick, server side: every food still healing heals its share. */
	public static void tick(PlayerEntity player) {
		List<float[]> meals = ((Holder) player).eternalreturn$meals();
		if (meals.isEmpty()) {
			return;
		}
		float total = 0.0F;
		for (var iterator = meals.iterator(); iterator.hasNext(); ) {
			float[] meal = iterator.next();
			float part = Math.min(meal[0], meal[1]);
			total += part;
			meal[0] -= part;
			if (meal[0] <= 0.0001F) {
				iterator.remove();
			}
		}
		if (player.isAlive()) {
			player.heal(total);
		}
	}

	public static void write(PlayerEntity player, NbtCompound nbt) {
		NbtList list = new NbtList();
		for (float[] meal : ((Holder) player).eternalreturn$meals()) {
			NbtCompound entry = new NbtCompound();
			entry.putFloat("Health", meal[0]);
			entry.putFloat("PerTick", meal[1]);
			list.add(entry);
		}
		nbt.put(NBT_KEY, list);
	}

	public static void read(PlayerEntity player, NbtCompound nbt) {
		List<float[]> meals = ((Holder) player).eternalreturn$meals();
		meals.clear();
		for (NbtElement element : nbt.getList(NBT_KEY, NbtElement.COMPOUND_TYPE)) {
			NbtCompound entry = (NbtCompound) element;
			meals.add(new float[]{entry.getFloat("Health"), entry.getFloat("PerTick")});
		}
	}

	private static JsonObject read() {
		try (InputStream in = Meals.class.getResourceAsStream("/eternalreturn/foods.json")) {
			if (in == null) {
				throw new IllegalStateException("eternalreturn/foods.json missing from the mod jar");
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			}
		} catch (IOException e) {
			throw new IllegalStateException("cannot read eternalreturn/foods.json", e);
		}
	}
}
