package com.eternalreturn.fishing;

import com.eternalreturn.config.EternalReturnConfig;
import com.mojang.serialization.MapCodec;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameter;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Set;

/**
 * {"condition": "eternalreturn:deep_water"} passes when the water under the fishing hook (origin)
 * is at least fishing.anglerfishMinWaterDepth blocks deep.
 */
public record DeepWaterLootCondition() implements LootCondition {
	public static final MapCodec<DeepWaterLootCondition> CODEC = MapCodec.unit(new DeepWaterLootCondition());
	/** Deeper than this counts as deep enough for any sensible config, and keeps the scan short. */
	private static final int MAX_SCAN = 128;

	@Override
	public LootConditionType getType() {
		return FishingLoot.DEEP_WATER_CONDITION;
	}

	@Override
	public Set<LootContextParameter<?>> getRequiredParameters() {
		return Set.of(LootContextParameters.ORIGIN);
	}

	@Override
	public boolean test(LootContext context) {
		Vec3d origin = context.get(LootContextParameters.ORIGIN);
		return origin != null && waterDepth(context.getWorld(), BlockPos.ofFloored(origin)) >= EternalReturnConfig.get().fishing.anglerfishMinWaterDepth;
	}

	/** Water blocks in a straight line down from the hook. A bobbing hook can sit just above the surface, so that block is skipped. */
	public static int waterDepth(World world, BlockPos hook) {
		BlockPos.Mutable pos = hook.mutableCopy();
		if (!world.getFluidState(pos).isIn(FluidTags.WATER)) {
			pos.move(0, -1, 0);
		}
		int depth = 0;
		while (depth < MAX_SCAN && pos.getY() >= world.getBottomY() && world.getFluidState(pos).isIn(FluidTags.WATER)) {
			depth++;
			pos.move(0, -1, 0);
		}
		return depth;
	}
}
