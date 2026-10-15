package com.eternalreturn.mixin.frames;

import com.eternalreturn.blocks.InvisibleSigns;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A phantom membrane makes a sign invisible, text and all but the sign (InvisibleSigns). */
@Mixin(AbstractSignBlock.class)
public abstract class SignInvisibilityMixin {
	@Inject(method = "onUseWithItem", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$membrane(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit,
			CallbackInfoReturnable<ItemActionResult> cir) {
		if (!stack.isOf(Items.PHANTOM_MEMBRANE) || !(world.getBlockEntity(pos) instanceof SignBlockEntity sign) || InvisibleSigns.isInvisible(sign)) {
			return;
		}
		if (!world.isClient) {
			((InvisibleSigns.Holder) sign).eternalreturn$setInvisible(true);
			sign.markDirty();
			world.updateListeners(pos, state, state, Block.NOTIFY_ALL);
			world.playSound(null, pos, SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.BLOCKS, 0.8F, 1.4F);
			stack.decrementUnlessCreative(1, player);
		}
		cir.setReturnValue(ItemActionResult.success(world.isClient));
	}
}
