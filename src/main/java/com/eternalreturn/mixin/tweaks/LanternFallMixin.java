package com.eternalreturn.mixin.tweaks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.LanternBlock;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A lantern that loses what holds it (the block it stands on or hangs from) falls, and bursts into fire where it
 * lands (FallingLanternMixin) instead of dropping as an item.
 */
@Mixin(LanternBlock.class)
public abstract class LanternFallMixin extends Block {
	protected LanternFallMixin(Settings settings) {
		super(settings);
	}

	@Inject(method = "getStateForNeighborUpdate", at = @At("RETURN"), cancellable = true)
	private void eternalreturn$fallInstead(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos,
			BlockPos neighborPos, CallbackInfoReturnable<BlockState> cir) {
		if (cir.getReturnValue().isAir() && state.getBlock() instanceof LanternBlock) {
			world.scheduleBlockTick(pos, this, 1);
			cir.setReturnValue(state);
		}
	}

	@Override
	protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		super.scheduledTick(state, world, pos, random);
		if (!state.canPlaceAt(world, pos)) {
			FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(world, pos, state.with(LanternBlock.HANGING, false).with(Properties.WATERLOGGED, false));
			falling.setDestroyedOnLanding();
		}
	}
}
