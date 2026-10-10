package com.eternalreturn.mixin.redstone;

import com.eternalreturn.blocks.Redstone;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.DispenserBehavior;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Dispensers place blocks that have no dispenser behaviour of their own (Redstone). Droppers keep dropping. */
@Mixin(DispenserBlock.class)
public abstract class DispenserBlockMixin {
	@ModifyReturnValue(method = "getBehaviorForItem", at = @At("RETURN"))
	private DispenserBehavior eternalreturn$placeBlocks(DispenserBehavior behavior, World world, ItemStack stack) {
		return (Object) this.getClass() == DispenserBlock.class ? Redstone.withBlockPlacing(behavior, stack) : behavior;
	}
}
