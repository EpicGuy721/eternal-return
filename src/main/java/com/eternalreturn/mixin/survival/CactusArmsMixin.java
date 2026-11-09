package com.eternalreturn.mixin.survival;

import com.eternalreturn.blocks.CactusArms;
import com.eternalreturn.worldgen.cactus.CactusShapes;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CactusBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cacti join sideways (CactusArms): four connections, joining when placed against another cactus, staying beside other
 * cacti, held through their joins; and in Eternal Return worlds they grow their biome's shape (CactusShapes).
 */
@Mixin(CactusBlock.class)
public abstract class CactusArmsMixin extends Block {
	protected CactusArmsMixin(Settings settings) {
		super(settings);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void eternalreturn$unjoined(AbstractBlock.Settings settings, CallbackInfo ci) {
		// A boolean property's first value is true: start every connection off.
		this.setDefaultState(this.getDefaultState().with(Properties.NORTH, false).with(Properties.EAST, false)
				.with(Properties.SOUTH, false).with(Properties.WEST, false));
	}

	@Inject(method = "appendProperties", at = @At("TAIL"))
	private void eternalreturn$joins(StateManager.Builder<Block, BlockState> builder, CallbackInfo ci) {
		builder.add(Properties.NORTH, Properties.EAST, Properties.SOUTH, Properties.WEST);
	}

	@Override
	@Nullable
	public BlockState getPlacementState(ItemPlacementContext context) {
		return CactusArms.placed(this.getDefaultState(), context.getWorld(), context.getBlockPos());
	}

	@Inject(method = "canPlaceAt", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$canStay(BlockState state, WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(CactusArms.canStay(state, world, pos));
	}

	@Inject(method = "getStateForNeighborUpdate", at = @At("RETURN"), cancellable = true)
	private void eternalreturn$followJoins(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos,
			BlockPos neighborPos, CallbackInfoReturnable<BlockState> cir) {
		cir.setReturnValue(CactusArms.neighbourChanged(cir.getReturnValue(), direction, neighborState));
	}

	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$growShape(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
		if (CactusShapes.randomTick(state, world, pos)) {
			ci.cancel();
		}
	}
}
