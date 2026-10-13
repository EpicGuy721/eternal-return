package com.eternalreturn.mixin.combat;

import com.eternalreturn.logic.Curses;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Curse of Frailness (Curses): half the time, an item takes twice the damage. */
@Mixin(ItemStack.class)
public abstract class ItemStackFrailMixin {
	@ModifyVariable(method = "damage(ILnet/minecraft/server/world/ServerWorld;Lnet/minecraft/server/network/ServerPlayerEntity;Ljava/util/function/Consumer;)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int eternalreturn$frail(int amount, int original, ServerWorld world, ServerPlayerEntity player) {
		return Curses.frail((ItemStack) (Object) this, amount, world.random);
	}
}
