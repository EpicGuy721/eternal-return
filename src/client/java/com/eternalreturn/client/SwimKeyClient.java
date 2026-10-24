package com.eternalreturn.client;

import com.eternalreturn.logic.SwimKey;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * The swim key (SwimKey), Left Alt to begin with: held, the player swims in water and crawls on land. Its state goes to
 * the server when it changes (and each second while held). Swimming forward sprints, as swimming did before.
 */
public final class SwimKeyClient {
	public static final KeyBinding KEY = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.eternalreturn.swim", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, KeyBinding.MOVEMENT_CATEGORY));
	private static boolean sent;

	private SwimKeyClient() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientPlayerEntity player = client.player;
			if (player == null) {
				sent = false;
				return;
			}
			boolean held = KEY.isPressed() && client.currentScreen == null;
			((SwimKey.Holder) player).eternalreturn$setSwimKey(held);
			if ((held != sent || (held && player.age % 20 == 0)) && ClientPlayNetworking.canSend(SwimKey.KeyPayload.ID)) {
				ClientPlayNetworking.send(new SwimKey.KeyPayload(held));
				sent = held;
			}
			if (held && SwimKey.enabled() && player.isTouchingWater() && player.input.hasForwardMovement() && !player.isSprinting()) {
				player.setSprinting(true);
			}
		});
	}
}
