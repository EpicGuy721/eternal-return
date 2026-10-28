package com.eternalreturn.mixin.slabs;

import com.eternalreturn.blocks.MixedSlabs;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A slab placed into a different slab makes a mixed slab of the two (MixedSlabs), and sounds as the slab placed. */
@Mixin(BlockItem.class)
public abstract class SlabPlacementMixin {
	@Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;Lnet/minecraft/block/BlockState;)Z", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$mixedSlab(ItemPlacementContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (state.isOf(MixedSlabs.MIXED_SLAB)) {
			cir.setReturnValue(MixedSlabs.place(context, state));
		}
	}

	@WrapOperation(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;getSoundGroup()Lnet/minecraft/sound/BlockSoundGroup;"))
	private BlockSoundGroup eternalreturn$placedHalfSounds(BlockState placed, Operation<BlockSoundGroup> original) {
		return original.call(placed.isOf(MixedSlabs.MIXED_SLAB) ? ((BlockItem) (Object) this).getBlock().getDefaultState() : placed);
	}

	@WrapOperation(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/item/BlockItem;getPlaceSound(Lnet/minecraft/block/BlockState;)Lnet/minecraft/sound/SoundEvent;"))
	private SoundEvent eternalreturn$placedHalfSound(BlockItem item, BlockState placed, Operation<SoundEvent> original) {
		return original.call(item, placed.isOf(MixedSlabs.MIXED_SLAB) ? item.getBlock().getDefaultState() : placed);
	}
}
