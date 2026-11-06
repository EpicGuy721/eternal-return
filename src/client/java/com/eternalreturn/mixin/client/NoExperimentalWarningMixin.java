package com.eternalreturn.mixin.client;

import com.mojang.serialization.Lifecycle;
import net.minecraft.server.integrated.IntegratedServerLoader;
import net.minecraft.world.SaveProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * No "experimental settings" warnings for this modpack's worlds (owner's request, 2026-11-05): the mods' world types and
 * data make the game call every world experimental. Creating a world skips the "Are you sure?" screen, and opening one
 * skips the "make a backup?" screen that follows from it. The backup prompt for old customized worlds stays.
 */
@Mixin(IntegratedServerLoader.class)
public abstract class NoExperimentalWarningMixin {
	@ModifyVariable(method = "tryLoad", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private static boolean eternalreturn$bypassWarnings(boolean bypassWarnings) {
		return true;
	}

	@Redirect(method = "checkBackupAndStart", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/SaveProperties;getLifecycle()Lcom/mojang/serialization/Lifecycle;"))
	private Lifecycle eternalreturn$stable(SaveProperties properties) {
		return Lifecycle.stable();
	}
}
