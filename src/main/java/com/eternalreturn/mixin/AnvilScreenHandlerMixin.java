package com.eternalreturn.mixin;

import com.eternalreturn.duck.AnvilCapView;
import com.eternalreturn.logic.EnchantCaps;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin implements AnvilCapView {
	@Shadow
	@Final
	private Property levelCost;

	@Unique
	private int eternalreturn$refusedPoints = -1;
	@Unique
	private int eternalreturn$refusedCap = -1;

	/** No "Too Expensive!" limit: the anvil no longer costs anything. */
	@ModifyConstant(method = "updateResult", constant = @Constant(intValue = 40))
	private int eternalreturn$removeTooExpensive(int original) {
		return Integer.MAX_VALUE;
	}

	@Inject(method = "updateResult", at = @At("HEAD"))
	private void eternalreturn$resetCapInfo(CallbackInfo ci) {
		this.eternalreturn$refusedPoints = -1;
		this.eternalreturn$refusedCap = -1;
	}

	/**
	 * Refuse results that push a capped item over its capacity. Results that do not add points
	 * (renaming, repairing, adding curses) are always allowed, even on an item already over its cap.
	 */
	@Inject(method = "updateResult", at = @At("TAIL"))
	private void eternalreturn$enforceCap(CallbackInfo ci) {
		ScreenHandler self = (ScreenHandler) (Object) this;
		ItemStack result = self.getSlot(2).getStack();
		if (result.isEmpty()) {
			return;
		}
		int cap = EnchantCaps.capFor(result);
		if (cap == EnchantCaps.UNCAPPED) {
			return;
		}
		int after = EnchantCaps.pointsUsed(result);
		int before = EnchantCaps.pointsUsed(self.getSlot(0).getStack());
		if (after > cap && after > before) {
			this.eternalreturn$refusedPoints = after;
			this.eternalreturn$refusedCap = cap;
			self.getSlot(2).setStack(ItemStack.EMPTY);
			this.levelCost.set(0);
			self.sendContentUpdates();
		}
	}

	/** Output can be taken whenever there is a valid result, regardless of the player's level. */
	@ModifyReturnValue(method = "canTakeOutput", at = @At("RETURN"))
	private boolean eternalreturn$noLevelNeeded(boolean original) {
		return this.levelCost.get() > 0;
	}

	@WrapOperation(method = "onTakeOutput",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;addExperienceLevels(I)V"))
	private void eternalreturn$noXpCharge(PlayerEntity player, int levels, Operation<Void> original) {
		// intentionally empty: anvils are free
	}

	@Override
	public int eternalreturn$getRefusedPoints() {
		return this.eternalreturn$refusedPoints;
	}

	@Override
	public int eternalreturn$getRefusedCap() {
		return this.eternalreturn$refusedCap;
	}
}
