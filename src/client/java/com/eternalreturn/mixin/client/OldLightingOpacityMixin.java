package com.eternalreturn.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.EmptyBlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nostalgic Tweaks' old world lighting remembers, on the render thread, how much light each kind of block blocks, one
 * value per block rather than per state: whichever state the client meets first stands for all of them. An extended
 * piston (open to the sky) then blocks all light like a retracted one, its own space goes dark and the face under its
 * arm is drawn black; a bottom slab can do the same after a double one. For blocks whose states block different
 * amounts of light, the state's own amount is put back (after Nostalgic Tweaks' change: its mixins have priority 1001);
 * a state holding water keeps Nostalgic Tweaks' old water lighting.
 */
@Mixin(value = AbstractBlock.AbstractBlockState.class, priority = 1500)
public abstract class OldLightingOpacityMixin {
	@Unique
	private static final boolean ETERNALRETURN$NOSTALGIC = FabricLoader.getInstance().isModLoaded("nostalgic_tweaks");
	@Unique
	private static final Map<Block, Boolean> ETERNALRETURN$VARIES = new ConcurrentHashMap<>();

	@Shadow
	public abstract Block getBlock();

	@Shadow
	protected abstract BlockState asBlockState();

	@ModifyReturnValue(method = "getOpacity", at = @At("RETURN"))
	private int eternalreturn$ownOpacity(int opacity, @Local(argsOnly = true) BlockView world, @Local(argsOnly = true) BlockPos pos) {
		if (!ETERNALRETURN$NOSTALGIC || !RenderSystem.isOnRenderThread()) {
			return opacity;
		}
		Block block = this.getBlock();
		if (!ETERNALRETURN$VARIES.computeIfAbsent(block, OldLightingOpacityMixin::eternalreturn$varies)) {
			return opacity;
		}
		BlockState state = this.asBlockState();
		if (state.getFluidState().isIn(FluidTags.WATER)) {
			return opacity;
		}
		return ((AbstractBlockOpacityInvoker) block).eternalreturn$opacity(state, world, pos);
	}

	@Unique
	private static boolean eternalreturn$varies(Block block) {
		AbstractBlockOpacityInvoker own = (AbstractBlockOpacityInvoker) block;
		int first = -1;
		for (BlockState state : block.getStateManager().getStates()) {
			int opacity = own.eternalreturn$opacity(state, EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
			if (first < 0) {
				first = opacity;
			} else if (opacity != first) {
				return true;
			}
		}
		return false;
	}
}
