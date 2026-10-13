package com.eternalreturn.fishing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.loot.context.LootContext;

import java.util.List;

/**
 * {"condition": "eternalreturn:moon_phase", "phases": [0]} passes when tonight's moon is one of the phases:
 * 0 is the full moon, 4 the new moon (the game's count; the phase changes each day at noon).
 */
public record MoonPhaseLootCondition(List<Integer> phases) implements LootCondition {
	public static final MapCodec<MoonPhaseLootCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.INT.listOf().fieldOf("phases").forGetter(MoonPhaseLootCondition::phases)
	).apply(instance, MoonPhaseLootCondition::new));

	@Override
	public LootConditionType getType() {
		return FishingLoot.MOON_PHASE_CONDITION;
	}

	@Override
	public boolean test(LootContext context) {
		return this.phases.contains(context.getWorld().getMoonPhase());
	}
}
