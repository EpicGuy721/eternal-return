package com.eternalreturn.items;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Subtle potions (survival.subtlePotions): an amethyst shard in a brewing stand makes any potion, splash potion or
 * lingering potion with effects subtle (SubtleBrewingMixin). A subtle potion's effects show no particles on whoever has
 * them, its splash breaks without the coloured burst (SubtleSplashMixin), its lingering cloud is invisible
 * (SubtleCloudMixin), and tipped arrows made from it fly with no trail and stay subtle (SubtleArrowMixin).
 * <p>
 * A subtle potion is kept as plain effects (no potion id, its own colour, every effect without particles), which every
 * use of a potion already honours, and tipped arrows copy. So it can't be lengthened or strengthened afterwards (brew
 * the redstone or glowstone first), but gunpowder and dragon's breath still turn it splash and lingering. Its name,
 * "Subtle Potion of Swiftness", comes from the potion with those effects (SubtleNameMixin).
 */
public final class SubtlePotions {
	private static final Map<List<String>, Optional<RegistryEntry<Potion>>> MATCHES = new ConcurrentHashMap<>();

	private SubtlePotions() {
	}

	public static boolean enabled() {
		return EternalReturnConfig.get().survival.subtlePotions;
	}

	public static boolean isIngredient(ItemStack stack) {
		return enabled() && stack.isOf(Items.AMETHYST_SHARD);
	}

	public static boolean isPotion(ItemStack stack) {
		return stack.isOf(Items.POTION) || stack.isOf(Items.SPLASH_POTION) || stack.isOf(Items.LINGERING_POTION);
	}

	/** Effects with none of them showing particles, and no potion id: subtle. */
	public static boolean isSubtle(@Nullable PotionContentsComponent contents) {
		if (contents == null || contents.potion().isPresent() || contents.customEffects().isEmpty()) {
			return false;
		}
		for (StatusEffectInstance effect : contents.customEffects()) {
			if (effect.shouldShowParticles()) {
				return false;
			}
		}
		return true;
	}

	public static boolean isSubtle(ItemStack stack) {
		return isSubtle(stack.get(DataComponentTypes.POTION_CONTENTS));
	}

	/** Whether an amethyst shard can make this subtle: a potion with effects, not subtle yet. */
	public static boolean canMakeSubtle(ItemStack stack) {
		if (!isPotion(stack) || isSubtle(stack)) {
			return false;
		}
		PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
		return contents != null && contents.hasEffects();
	}

	/** The subtle form of a potion: the same item, effects and colour, no particles. */
	public static ItemStack makeSubtle(ItemStack stack) {
		PotionContentsComponent contents = stack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT);
		List<StatusEffectInstance> effects = new ArrayList<>();
		for (StatusEffectInstance effect : contents.getEffects()) {
			effects.add(new StatusEffectInstance(effect.getEffectType(), effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), false,
					effect.shouldShowIcon()));
		}
		ItemStack subtle = stack.copyWithCount(1);
		subtle.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Optional.empty(), Optional.of(contents.getColor()), effects));
		return subtle;
	}

	/** The potion with these effects (by type, length and strength), the one a subtle potion was made from. */
	public static Optional<RegistryEntry<Potion>> source(PotionContentsComponent contents) {
		List<String> key = key(contents.customEffects());
		return MATCHES.computeIfAbsent(key, k -> Registries.POTION.streamEntries()
				.filter(entry -> !entry.value().getEffects().isEmpty() && key(entry.value().getEffects()).equals(k))
				.<RegistryEntry<Potion>>map(entry -> entry)
				.findFirst());
	}

	private static List<String> key(List<StatusEffectInstance> effects) {
		List<String> key = new ArrayList<>();
		for (StatusEffectInstance effect : effects) {
			key.add(effect.getEffectType().getIdAsString() + "/" + effect.getDuration() + "/" + effect.getAmplifier());
		}
		return key;
	}

	/** "Subtle Potion of Swiftness", "Subtle Arrow of Swiftness"; "Subtle Potion" for effects no potion has. */
	public static Text name(Item item, PotionContentsComponent contents) {
		Optional<RegistryEntry<Potion>> potion = source(contents);
		String key = potion.isPresent() ? Potion.finishTranslationKey(potion, item.getTranslationKey() + ".effect.") : item.getTranslationKey();
		return Text.translatable("item.eternalreturn.subtle", Text.translatable(key));
	}
}
