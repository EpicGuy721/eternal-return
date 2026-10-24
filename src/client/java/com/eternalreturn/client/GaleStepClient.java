package com.eternalreturn.client;

import com.eternalreturn.logic.GaleStep;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Gale Step on the client: jump pressed again in the air (after letting go of it since leaving the ground) jumps
 * once more, and tells the server (GaleStep).
 */
public final class GaleStepClient {
	/** Jump was let go since the player left the ground. */
	private static boolean released;
	/** The second jump was used since the player left the ground. */
	private static boolean used;

	private GaleStepClient() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientPlayerEntity player = client.player;
			if (player == null || GaleStep.grounded(player)) {
				released = false;
				used = false;
				return;
			}
			if (!client.options.jumpKey.isPressed()) {
				released = true;
				return;
			}
			if (released && !used && client.currentScreen == null && GaleStep.canJump(player)) {
				used = true;
				GaleStep.jump(player);
				ClientPlayNetworking.send(new GaleStep.JumpPayload());
			}
			released = false;
		});
	}
}
