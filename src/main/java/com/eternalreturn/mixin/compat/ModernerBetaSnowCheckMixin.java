package com.eternalreturn.mixin.compat;

import com.eternalreturn.blocks.Snowlogging;
import com.eternalreturn.compat.ModernerBetaHooks;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Moderner Beta's snow check, during its snow-and-freeze step (ModernerBetaSnowMixin), at the point where it has found the
 * spot cold, low enough and dark enough for snow and looks at what is there: snow it will lay on a whole top marks that
 * top (Snowlogging.snowLiesOn); a plant there, or a slab, stairs, wall, fence or gate under it, gets a layer of snow
 * logged in it (Snowlogging.snowfallCold), as vanilla's step gives them (FreezeTopLayerMixin).
 */
@Mixin(targets = "mod.bluestaggo.modernerbeta.level.biome.ClimateHelper", remap = false)
public abstract class ModernerBetaSnowCheckMixin {
	@org.spongepowered.asm.mixin.Shadow(remap = false)
	public static boolean coldEnoughToSnow(net.minecraft.world.biome.Biome biome, BlockPos pos, WorldView world) {
		throw new AssertionError();
	}

	/**
	 * In an Eternal Return world, snow settles on a tree's top leaves only where the ground under the tree is cold enough
	 * for it, by the ground's own biome and height: the icy mountains lie over the high ground's columns above y 105, so
	 * the tops of tall trees on lower ground reached into them and were snowed on over bare green ground (the owner's
	 * report, 2026-11-11).
	 */
	@Inject(method = "shouldSnow", remap = false, at = @At("HEAD"), cancellable = true)
	private static void eternalreturn$leavesFollowTheGround(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) WorldView view, @Local(argsOnly = true) BlockPos pos) {
		StructureWorldAccess world = ModernerBetaHooks.SNOWING.get();
		if (world == null || world != view || !com.eternalreturn.worldgen.EternalReturnServer.active()
				|| !world.getBlockState(pos.down()).isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) {
			return;
		}
		BlockPos ground = new BlockPos(pos.getX(), world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()), pos.getZ());
		if (ground.getY() < pos.getY() && !coldEnoughToSnow(world.getBiome(ground).value(), ground, world)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "shouldSnow", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;isAir()Z", remap = true))
	private static void eternalreturn$snowInBlocks(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) WorldView view, @Local(argsOnly = true) BlockPos pos) {
		StructureWorldAccess world = ModernerBetaHooks.SNOWING.get();
		if (world == null || world != view) {
			return;
		}
		BlockPos space = pos.toImmutable();
		if (world.getBlockState(space).isAir() && Blocks.SNOW.getDefaultState().canPlaceAt(world, space)) {
			Snowlogging.snowLiesOn(world, space, Block.NOTIFY_LISTENERS);
		} else {
			Snowlogging.snowfallCold(world, space, 1, Block.NOTIFY_LISTENERS);
		}
		ModernerBetaHooks.SNOW_CHECKS.incrementAndGet();
	}
}
