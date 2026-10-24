package com.eternalreturn.mixin.tweaks;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A falling lantern (LanternFallMixin) shatters where it lands and sets fire there (soul fire for a soul lantern). */
@Mixin(FallingBlockEntity.class)
public abstract class FallingLanternMixin {
	@Inject(method = "onDestroyedOnLanding", at = @At("HEAD"))
	private void eternalreturn$burst(Block block, BlockPos pos, CallbackInfo ci) {
		FallingBlockEntity falling = (FallingBlockEntity) (Object) this;
		if (!(block instanceof LanternBlock) || !(falling.getWorld() instanceof ServerWorld world)) {
			return;
		}
		world.playSound(null, pos, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.0F, 0.9F + world.random.nextFloat() * 0.2F);
		world.spawnParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.02);
		BlockState fire = block == Blocks.SOUL_LANTERN ? Blocks.SOUL_FIRE.getDefaultState() : AbstractFireBlock.getState(world, pos);
		if (world.getBlockState(pos).isReplaceable() && world.getFluidState(pos).isEmpty() && fire.canPlaceAt(world, pos)) {
			world.setBlockState(pos, fire);
			world.playSound(null, pos, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS, 0.8F, 1.0F);
		}
	}
}
