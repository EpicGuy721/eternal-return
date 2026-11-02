package com.eternalreturn.mixin.survival;

import com.eternalreturn.blocks.LeafDistance;
import net.minecraft.block.LeavesBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Leaves decay at LeafDistance.MAX from a log, not 7: their default state, decay checks and distance update. */
@Mixin(LeavesBlock.class)
public abstract class LeafDistanceMixin {
	@ModifyConstant(method = {"<init>", "hasRandomTicks", "shouldDecay"}, constant = @Constant(intValue = 7))
	private int eternalreturn$leafDistance(int vanilla) {
		return LeafDistance.MAX;
	}

	@ModifyConstant(method = {"updateDistanceFromLogs", "getDistanceFromLog"}, constant = @Constant(intValue = 7))
	private static int eternalreturn$leafDistanceStatic(int vanilla) {
		return LeafDistance.MAX;
	}
}
