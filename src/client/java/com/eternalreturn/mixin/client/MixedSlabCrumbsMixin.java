package com.eternalreturn.mixin.client;

import com.eternalreturn.blocks.MixedSlabs;
import com.eternalreturn.blocks.SnowCover;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The crumbs flying off a mixed slab (MixedSlabs) being mined are the half's, from the half's surface; off snow logged in a block (SnowCover), snow's. */
@Mixin(ParticleManager.class)
public abstract class MixedSlabCrumbsMixin {
	@ModifyVariable(method = "addBlockBreakingParticles", at = @At("STORE"), ordinal = 0)
	private BlockState eternalreturn$halfCrumbs(BlockState state, @Local(argsOnly = true) BlockPos pos) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null && client.world != null && SnowCover.aimsSnow(client.player, client.world, pos, state)) {
			return SnowCover.asSnowLayer(SnowCover.layers(client.world, pos, state));
		}
		return state.isOf(MixedSlabs.MIXED_SLAB) && client.player != null && client.world != null
				? MixedSlabs.aimedHalf(client.player, client.world, pos, state) : state;
	}
}
