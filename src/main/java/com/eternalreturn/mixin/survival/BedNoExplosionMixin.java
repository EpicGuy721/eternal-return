package com.eternalreturn.mixin.survival;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A bed in the Nether or the End doesn't explode: it just can't be slept in there (survival.bedsExplode, off). */
@Mixin(BedBlock.class)
public abstract class BedNoExplosionMixin {
	@Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$noExplosion(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit,
			CallbackInfoReturnable<ActionResult> cir) {
		if (!BedBlock.isBedWorking(world) && !EternalReturnConfig.get().survival.bedsExplode) {
			if (!world.isClient) {
				player.sendMessage(Text.translatable("block.eternalreturn.bed.not_here"), true);
			}
			cir.setReturnValue(ActionResult.SUCCESS);
		}
	}
}
