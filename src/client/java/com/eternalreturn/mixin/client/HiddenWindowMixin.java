package com.eternalreturn.mixin.client;

import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dev checks only: with -Deternalreturn.hiddenWindow (./gradlew runClient or runClientCompat -PhiddenWindow), the game's
 * window opens without taking the keyboard and sits off the screen, so a scripted screenshot run doesn't pop up over
 * whatever else is open. The game still draws into its own framebuffer, which screenshots read. (A hidden window is
 * shown again every frame on Windows, and hiding it each time takes the keyboard.) Without the setting nothing changes.
 */
@Mixin(Window.class)
public abstract class HiddenWindowMixin {
	@Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false))
	private void eternalreturn$unfocused(CallbackInfo ci) {
		if (Boolean.getBoolean("eternalreturn.hiddenWindow")) {
			GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
			GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
			GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
		}
	}

	/** Off the screen once made, and again whenever the game sets its size and place. */
	@Inject(method = {"<init>", "updateWindowRegion"}, at = @At("TAIL"))
	private void eternalreturn$offScreen(CallbackInfo ci) {
		if (Boolean.getBoolean("eternalreturn.hiddenWindow")) {
			GLFW.glfwSetWindowPos(((Window) (Object) this).getHandle(), -32000, -32000);
		}
	}
}
