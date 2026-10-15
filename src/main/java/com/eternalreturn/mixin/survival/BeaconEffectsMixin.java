package com.eternalreturn.mixin.survival;

import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * The beacon's effect choices (BeaconPower): Regeneration joins Strength among the choices for the one effect, and
 * nothing is left for the second (the screen hides its column, client BeaconScreenMixin). Set as the class loads,
 * before the config, so it doesn't depend on it.
 */
@Mixin(BeaconBlockEntity.class)
public abstract class BeaconEffectsMixin {
	@Shadow
	@Final
	@Mutable
	public static List<List<RegistryEntry<StatusEffect>>> EFFECTS_BY_LEVEL;

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void eternalreturn$oneEffect(CallbackInfo ci) {
		EFFECTS_BY_LEVEL = List.of(List.of(StatusEffects.SPEED, StatusEffects.HASTE), List.of(StatusEffects.RESISTANCE, StatusEffects.JUMP_BOOST),
				List.of(StatusEffects.STRENGTH, StatusEffects.REGENERATION), List.of());
	}
}
