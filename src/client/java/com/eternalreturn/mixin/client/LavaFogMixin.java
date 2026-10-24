package com.eternalreturn.mixin.client;

import com.eternalreturn.logic.LavaSwimming;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A lava swimmer (LavaSwimming) sees through lava as with Fire Resistance. */
@Mixin(BackgroundRenderer.class)
public abstract class LavaFogMixin {
	@WrapOperation(method = "applyFog", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z"))
	private static boolean eternalreturn$lavaSight(LivingEntity entity, RegistryEntry<StatusEffect> effect, Operation<Boolean> original) {
		return original.call(entity, effect) || (effect == StatusEffects.FIRE_RESISTANCE && LavaSwimming.swims(entity));
	}
}
