package com.eternalreturn.blocks;

import net.minecraft.block.BlockState;
import net.minecraft.block.MushroomBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A purple or blue huge mushroom's cap block, puffed or unpuffed (owner's request, 2026-11-05), as the small mushrooms are
 * (GasMushroomBlock): a puffed one lets out its gas when it is destroyed by anything but a Silk Touch tool; with Silk Touch
 * it drops itself, still puffed. An unpuffed one is quiet. Huge mushrooms that grow (generated or from bone meal) are puffed.
 */
public class GasMushroomCapBlock extends MushroomBlock {
	public final MushroomGas.Kind gas;
	public final boolean puffed;

	public GasMushroomCapBlock(Settings settings, MushroomGas.Kind gas, boolean puffed) {
		super(settings);
		this.gas = gas;
		this.puffed = puffed;
	}

	@Override
	public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		if (this.puffed) {
			GasMushroomBlock.quietIfSilkTouch(world, pos, player);
		}
		return super.onBreak(world, pos, state, player);
	}

	@Override
	public void afterBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
		super.afterBreak(world, player, pos, state, blockEntity, tool);
		GasMushroomBlock.forget(pos);
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		super.onStateReplaced(state, world, pos, newState, moved);
		// A face turning (another cap block placed beside it) keeps the block: no gas then.
		if (this.puffed && !newState.isOf(this)) {
			GasMushroomBlock.releaseOnRemoval(world, pos, this.gas);
		}
	}
}
