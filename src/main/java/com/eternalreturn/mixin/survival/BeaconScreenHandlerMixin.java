package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.BeaconPower;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.BeaconScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Paying a beacon records what it was paid with, its tier; and it takes only one effect (BeaconPower). The payment
 * slot is the screen's first.
 */
@Mixin(BeaconScreenHandler.class)
public abstract class BeaconScreenHandlerMixin extends ScreenHandler {
	@Shadow
	@Final
	private ScreenHandlerContext context;

	protected BeaconScreenHandlerMixin(ScreenHandlerType<?> type, int syncId) {
		super(type, syncId);
	}

	@Inject(method = "setEffects", at = @At("HEAD"))
	private void eternalreturn$recordPayment(Optional<RegistryEntry<StatusEffect>> primary, Optional<RegistryEntry<StatusEffect>> secondary, CallbackInfo ci) {
		Slot payment = this.getSlot(0);
		if (!BeaconPower.enabled() || !payment.hasStack()) {
			return;
		}
		int tier = BeaconPower.tierOf(payment.getStack());
		this.context.run((world, pos) -> {
			if (world.getBlockEntity(pos) instanceof BeaconBlockEntity beacon && tier > 0) {
				((BeaconPower.Holder) beacon).eternalreturn$setTier(tier);
				beacon.markDirty();
			}
		});
	}

	@ModifyVariable(method = "setEffects", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private Optional<RegistryEntry<StatusEffect>> eternalreturn$noSecond(Optional<RegistryEntry<StatusEffect>> secondary) {
		return BeaconPower.enabled() ? Optional.empty() : secondary;
	}
}
