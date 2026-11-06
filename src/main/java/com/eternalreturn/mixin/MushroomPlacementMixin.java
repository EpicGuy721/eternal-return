package com.eternalreturn.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.MushroomPlantBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A small mushroom (vanilla's and this mod's) can't be placed in the open under the sky unless it stands on mycelium or
 * podzol (owner's report, 2026-11-05). Vanilla already refuses there by light (13 or more), but the player's game guesses
 * the placement with its own copy of the light, which in this pack can read darker than the server's: the mushroom showed
 * for a tick, then the server took it back. Open sky, read from the height map, is the same on both sides, so the game
 * now refuses at once. Only in a running world: world generation (a chunk region) is untouched.
 */
@Mixin(MushroomPlantBlock.class)
public abstract class MushroomPlacementMixin {
	@Inject(method = "canPlaceAt", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$notUnderOpenSky(BlockState state, WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (!(world instanceof World live) || !live.getDimension().hasSkyLight() || world.getBlockState(pos.down()).isIn(BlockTags.MUSHROOM_GROW_BLOCK)) {
			return;
		}
		if (pos.getY() >= world.getTopY(Heightmap.Type.MOTION_BLOCKING, pos.getX(), pos.getZ())) {
			cir.setReturnValue(false);
		}
	}
}
