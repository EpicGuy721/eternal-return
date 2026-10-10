package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.Farming;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.VineBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shears on a vine stop it growing (Farming.CUT). */
@Mixin(VineBlock.class)
public abstract class VineBlockMixin extends Block {
	protected VineBlockMixin(Settings settings) {
		super(settings);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void eternalreturn$uncutByDefault(Settings settings, CallbackInfo ci) {
		this.setDefaultState(this.getDefaultState().with(Farming.CUT, false));
	}

	@Inject(method = "appendProperties", at = @At("TAIL"))
	private void eternalreturn$cutProperty(StateManager.Builder<Block, BlockState> builder, CallbackInfo ci) {
		builder.add(Farming.CUT);
	}

	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$cutStopsGrowing(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
		if (state.get(Farming.CUT)) {
			ci.cancel();
		}
	}

	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (!stack.isOf(Items.SHEARS) || state.get(Farming.CUT)) {
			return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
		}
		if (!world.isClient) {
			world.setBlockState(pos, state.with(Farming.CUT, true), Block.NOTIFY_ALL);
			world.playSound(null, pos, SoundEvents.BLOCK_GROWING_PLANT_CROP, SoundCategory.BLOCKS, 1.0F, 1.0F);
			world.emitGameEvent(player, GameEvent.SHEAR, pos);
			stack.damage(1, player, net.minecraft.entity.LivingEntity.getSlotForHand(hand));
		}
		return ItemActionResult.success(world.isClient);
	}
}
