package com.eternalreturn.mixin.mobs;

import com.eternalreturn.mobs.PiglinTrades;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.mob.PiglinBrain;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Piglins barter for rubies as for gold, for their own loot, and only so many times before their trades come back
 * (PiglinTrades): past that they don't take a thrown ingot or ruby, and grumble at one held out to them.
 */
@Mixin(PiglinBrain.class)
public abstract class PiglinBarterMixin {
	@Inject(method = "acceptsForBarter", at = @At("HEAD"), cancellable = true)
	private static void eternalreturn$rubies(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (PiglinTrades.isRuby(stack)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "isWillingToTrade", at = @At("HEAD"), cancellable = true)
	private static void eternalreturn$tradedOut(PiglinEntity piglin, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (!PiglinTrades.willTrade(piglin, stack)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canGather", at = @At("HEAD"), cancellable = true)
	private static void eternalreturn$ignoreWhenTradedOut(PiglinEntity piglin, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (piglin.isAdult() && !PiglinTrades.willTrade(piglin, stack)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "playerInteract", at = @At("HEAD"))
	private static void eternalreturn$grumble(PiglinEntity piglin, PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		if (piglin.isAdult() && !PiglinTrades.willTrade(piglin, player.getStackInHand(hand)) && !piglin.getWorld().isClient) {
			piglin.playSound(SoundEvents.ENTITY_PIGLIN_JEALOUS, 1.0F, piglin.getSoundPitch());
		}
	}

	@WrapOperation(method = "consumeOffHandItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/mob/PiglinBrain;getBarteredItem(Lnet/minecraft/entity/mob/PiglinEntity;)Ljava/util/List;"))
	private static List<ItemStack> eternalreturn$barter(PiglinEntity piglin, Operation<List<ItemStack>> original, @Local(ordinal = 0) ItemStack offered) {
		if (!PiglinTrades.willTrade(piglin, offered)) {
			return List.of(offered);
		}
		PiglinTrades.traded(piglin, offered);
		return PiglinTrades.isRuby(offered) ? PiglinTrades.rubyLoot(piglin) : original.call(piglin);
	}
}
