package com.eternalreturn.logic;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stand-in for EnchantmentHelper.generateEnchantments. Follows the vanilla algorithm
 * (same power randomisation, same "keep rolling while nextInt(50) <= power" loop),
 * with three additions:
 *  1. Shelved books raise the weight and the reachable level of their enchantment.
 *  2. Rolls never push an item past its enchant capacity (levels are trimmed to fit).
 *  3. Enchantments that would not improve the item (at or below inherent level) are skipped.
 *
 * Used by the enchanting table, villager-sold gear, mob gear and enchant_with_levels loot.
 */
public final class EnchantGenerator {
	private EnchantGenerator() {
	}

	private record Candidate(RegistryEntry<Enchantment> enchantment, int level, int baseline, int weight, boolean curse) {
		int cost() {
			return curse ? -level * EternalReturnConfig.get().pointsRefundedPerCurseLevel : level - baseline;
		}
	}

	/** Only take over when something actually differs from vanilla, to stay friendly with other mods. */
	public static boolean shouldTakeOver(ItemStack stack, ShelfBias bias) {
		return !bias.isEmpty()
				|| EnchantCaps.capFor(stack) != EnchantCaps.UNCAPPED
				|| InherentEnchantments.hasRules(stack);
	}

	public static List<EnchantmentLevelEntry> generate(Random random, ItemStack stack, int power,
			Stream<RegistryEntry<Enchantment>> possible, ShelfBias bias) {
		List<EnchantmentLevelEntry> result = new ArrayList<>();
		int enchantability = stack.getItem().getEnchantability();
		if (enchantability <= 0) {
			return result;
		}

		// Vanilla power randomisation.
		power += 1 + random.nextInt(enchantability / 4 + 1) + random.nextInt(enchantability / 4 + 1);
		float spread = (random.nextFloat() + random.nextFloat() - 1.0F) * 0.15F;
		power = MathHelper.clamp(Math.round(power + power * spread), 1, Integer.MAX_VALUE);

		List<Candidate> candidates = buildCandidates(stack, power, possible, bias);
		if (candidates.isEmpty()) {
			return result;
		}

		int budget = EnchantCaps.remaining(stack);

		Candidate picked = pick(random, candidates, budget);
		if (picked == null) {
			return result;
		}
		result.add(new EnchantmentLevelEntry(picked.enchantment(), picked.level()));
		budget = spend(budget, picked);

		while (random.nextInt(50) <= power) {
			RegistryEntry<Enchantment> last = picked.enchantment();
			candidates.removeIf(c -> !Enchantment.canBeCombined(last, c.enchantment()));
			if (candidates.isEmpty()) {
				break;
			}
			picked = pick(random, candidates, budget);
			if (picked == null) {
				break;
			}
			result.add(new EnchantmentLevelEntry(picked.enchantment(), picked.level()));
			budget = spend(budget, picked);
			power /= 2;
		}
		return result;
	}

	private static List<Candidate> buildCandidates(ItemStack stack, int power,
			Stream<RegistryEntry<Enchantment>> possible, ShelfBias bias) {
		EternalReturnConfig.BookshelfBias cfg = EternalReturnConfig.get().bookshelfBias;

		List<RegistryEntry<Enchantment>> pool = possible.collect(Collectors.toCollection(ArrayList::new));
		if (cfg.booksCanUnlockNonTableEnchantments) {
			for (RegistryEntry<Enchantment> shelved : bias.enchantments()) {
				if (!pool.contains(shelved)) {
					pool.add(shelved);
				}
			}
		}

		boolean book = stack.isOf(Items.BOOK);
		ItemEnchantmentsComponent existing = stack.getEnchantments();
		List<Candidate> out = new ArrayList<>();

		for (RegistryEntry<Enchantment> entry : pool) {
			Enchantment enchantment = entry.value();
			if (!book && !enchantment.isPrimaryItem(stack)) {
				continue;
			}
			if (EnchantLevels.isDisabled(entry) || !compatibleWithExisting(existing, entry)) {
				continue;
			}

			int level = naturalLevel(enchantment, power);
			double weightMultiplier = 1.0;

			ShelfBias.Influence influence = bias.get(entry);
			if (influence != null) {
				int boostedPower = power + Math.min(cfg.maxPowerBonus, cfg.powerBonusPerBookLevel * influence.totalLevels());
				int ceiling = Math.min(enchantment.getMaxLevel(), influence.highestLevel());
				for (int j = ceiling; j > level && j >= enchantment.getMinLevel(); j--) {
					if (boostedPower >= enchantment.getMinPower(j)) {
						level = j;
						break;
					}
				}
				weightMultiplier = Math.min(cfg.maxWeightMultiplier, 1.0 + cfg.weightBonusPerBookLevel * influence.totalLevels());
			}

			if (level <= 0) {
				continue;
			}
			int inherent = book ? 0 : InherentEnchantments.inherentLevel(stack, entry);
			int baseline = Math.max(existing.getLevel(entry), inherent);
			if (level <= baseline) {
				continue; // would not change the item
			}
			int weight = Math.max(1, (int) Math.round(enchantment.getWeight() * weightMultiplier));
			out.add(new Candidate(entry, level, baseline, weight, entry.isIn(EnchantmentTags.CURSE)));
		}
		return out;
	}

	/** Same selection rule vanilla uses in EnchantmentHelper.getPossibleEntries. */
	private static int naturalLevel(Enchantment enchantment, int power) {
		for (int j = enchantment.getMaxLevel(); j >= enchantment.getMinLevel(); j--) {
			if (power >= enchantment.getMinPower(j) && power <= enchantment.getMaxPower(j)) {
				return j;
			}
		}
		return 0;
	}

	private static boolean compatibleWithExisting(ItemEnchantmentsComponent existing, RegistryEntry<Enchantment> candidate) {
		for (RegistryEntry<Enchantment> other : existing.getEnchantments()) {
			if (other.equals(candidate)) {
				continue; // upgrading an existing enchantment is fine
			}
			if (!Enchantment.canBeCombined(other, candidate)) {
				return false;
			}
		}
		return true;
	}

	/** Weighted pick among candidates trimmed to the remaining budget. */
	private static Candidate pick(Random random, List<Candidate> candidates, int budget) {
		List<Candidate> affordable = new ArrayList<>(candidates.size());
		int totalWeight = 0;
		for (Candidate c : candidates) {
			Candidate fitted = fit(c, budget);
			if (fitted != null) {
				affordable.add(fitted);
				totalWeight += fitted.weight();
			}
		}
		if (affordable.isEmpty() || totalWeight <= 0) {
			return null;
		}
		int roll = random.nextInt(totalWeight);
		for (Candidate c : affordable) {
			roll -= c.weight();
			if (roll < 0) {
				return c;
			}
		}
		return affordable.get(affordable.size() - 1);
	}

	private static Candidate fit(Candidate c, int budget) {
		if (c.curse() || budget == EnchantCaps.UNCAPPED) {
			return c;
		}
		if (budget <= 0) {
			return null;
		}
		int level = Math.min(c.level(), c.baseline() + budget);
		if (level <= c.baseline() || level < c.enchantment().value().getMinLevel()) {
			return null;
		}
		return level == c.level() ? c : new Candidate(c.enchantment(), level, c.baseline(), c.weight(), false);
	}

	private static int spend(int budget, Candidate picked) {
		return budget == EnchantCaps.UNCAPPED ? budget : budget - picked.cost();
	}
}
