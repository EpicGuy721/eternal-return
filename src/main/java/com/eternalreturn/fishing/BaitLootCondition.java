package com.eternalreturn.fishing;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.duck.BaitedHook;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameter;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.registry.RegistryCodecs;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntryList;

import java.util.Optional;
import java.util.Set;

/**
 * {"condition": "eternalreturn:bait"} passes when the fishing hook (this_entity) was baited.
 * With "items" (an item, list or #tag) it only passes for that bait.
 */
public record BaitLootCondition(Optional<RegistryEntryList<Item>> items) implements LootCondition {
	public static final MapCodec<BaitLootCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			RegistryCodecs.entryList(RegistryKeys.ITEM).optionalFieldOf("items").forGetter(BaitLootCondition::items)
	).apply(instance, BaitLootCondition::new));

	@Override
	public LootConditionType getType() {
		return FishingLoot.BAIT_CONDITION;
	}

	@Override
	public Set<LootContextParameter<?>> getRequiredParameters() {
		return Set.of(LootContextParameters.THIS_ENTITY);
	}

	@Override
	public boolean test(LootContext context) {
		ItemStack bait = context.get(LootContextParameters.THIS_ENTITY) instanceof BaitedHook hook ? hook.eternalreturn$getBait() : ItemStack.EMPTY;
		if (this.items.isEmpty()) {
			return !bait.isEmpty() || !EternalReturnConfig.get().fishing.fishRequireBait;
		}
		return !bait.isEmpty() && bait.isIn(this.items.get());
	}
}
