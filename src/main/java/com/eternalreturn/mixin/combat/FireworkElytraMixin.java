package com.eternalreturn.mixin.combat;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A firework rocket no longer boosts a gliding elytra (OldCombat). */
@Mixin(FireworkRocketItem.class)
public abstract class FireworkElytraMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$noBoost(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
		if (user.isFallFlying() && !EternalReturnConfig.get().survival.elytraRocketBoost) {
			cir.setReturnValue(TypedActionResult.pass(user.getStackInHand(hand)));
		}
	}
}
