package com.eternalreturn.mixin.combat;

import com.eternalreturn.items.PhilosophersStone;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The philosopher's stone acts before a creature's own right-click: a villager would open its trades, and a
 * donkey or llama would be mounted, before the item got its turn.
 */
@Mixin(MobEntity.class)
public abstract class MobInteractMixin {
	@Inject(method = "interact", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$philosophersStone(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		ItemStack stack = player.getStackInHand(hand);
		if (stack.isOf(PhilosophersStone.ITEM)) {
			ActionResult result = stack.useOnEntity(player, (MobEntity) (Object) this, hand);
			if (result.isAccepted()) {
				cir.setReturnValue(result);
			}
		}
	}
}
