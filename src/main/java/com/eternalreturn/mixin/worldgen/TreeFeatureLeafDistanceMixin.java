package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.blocks.LeafDistance;
import net.minecraft.world.gen.feature.TreeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** A growing tree sets its leaves' distances out to LeafDistance.MAX, not 7 (LeafDistance). */
@Mixin(TreeFeature.class)
public abstract class TreeFeatureLeafDistanceMixin {
	@ModifyConstant(method = "placeLogsAndLeaves", constant = @Constant(intValue = 7))
	private static int eternalreturn$leafDistance(int vanilla) {
		return LeafDistance.MAX;
	}
}
