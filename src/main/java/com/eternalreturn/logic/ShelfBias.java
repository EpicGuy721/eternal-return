package com.eternalreturn.logic;

import com.eternalreturn.config.EternalReturnConfig;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChiseledBookshelfBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Enchanted books stored in chiseled bookshelves around an enchanting table.
 * Uses the same ring of positions (and the same "air in between" rule) as regular bookshelves.
 */
public final class ShelfBias {
	public static final ShelfBias NONE = new ShelfBias(Map.of());
	private static final ThreadLocal<ShelfBias> ACTIVE = new ThreadLocal<>();

	/** totalLevels: sum of levels across all shelved books. highestLevel: best single book. */
	public record Influence(int totalLevels, int highestLevel) {
	}

	private final Map<RegistryEntry<Enchantment>, Influence> influences;

	private ShelfBias(Map<RegistryEntry<Enchantment>, Influence> influences) {
		this.influences = influences;
	}

	public boolean isEmpty() {
		return influences.isEmpty();
	}

	public Influence get(RegistryEntry<Enchantment> enchantment) {
		return influences.get(enchantment);
	}

	public Collection<RegistryEntry<Enchantment>> enchantments() {
		return influences.keySet();
	}

	public static ShelfBias active() {
		ShelfBias bias = ACTIVE.get();
		return bias == null ? NONE : bias;
	}

	public static void begin(ShelfBias bias) {
		ACTIVE.set(bias);
	}

	public static void end() {
		ACTIVE.remove();
	}

	public static ShelfBias scan(World world, BlockPos tablePos) {
		if (!EternalReturnConfig.get().bookshelfBias.enabled) {
			return NONE;
		}

		Map<RegistryEntry<Enchantment>, int[]> totals = new HashMap<>();
		for (BlockPos offset : EnchantingTableBlock.POWER_PROVIDER_OFFSETS) {
			BlockPos between = tablePos.add(offset.getX() / 2, offset.getY(), offset.getZ() / 2);
			if (!world.getBlockState(between).isIn(BlockTags.ENCHANTMENT_POWER_TRANSMITTER)) {
				continue;
			}
			BlockEntity blockEntity = world.getBlockEntity(tablePos.add(offset));
			if (!(blockEntity instanceof ChiseledBookshelfBlockEntity shelf)) {
				continue;
			}
			for (int slot = 0; slot < shelf.size(); slot++) {
				ItemEnchantmentsComponent stored = shelf.getStack(slot).get(DataComponentTypes.STORED_ENCHANTMENTS);
				if (stored == null || stored.isEmpty()) {
					continue;
				}
				for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : stored.getEnchantmentEntries()) {
					RegistryEntry<Enchantment> enchantment = entry.getKey();
					if (enchantment.isIn(EnchantmentTags.CURSE) || EnchantLevels.isDisabled(enchantment)) {
						continue;
					}
					int[] t = totals.computeIfAbsent(enchantment, e -> new int[2]);
					t[0] += entry.getIntValue();
					t[1] = Math.max(t[1], entry.getIntValue());
				}
			}
		}

		if (totals.isEmpty()) {
			return NONE;
		}
		Map<RegistryEntry<Enchantment>, Influence> influences = new HashMap<>();
		totals.forEach((enchantment, t) -> influences.put(enchantment, new Influence(t[0], t[1])));
		return new ShelfBias(Map.copyOf(influences));
	}
}
