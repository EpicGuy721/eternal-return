package com.eternalreturn.mixin.scrolls;

import com.eternalreturn.scrolls.Scrolls;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A left click swings the main hand, at the air, a block or a mob alike: with a scroll in it, the scroll is read (Scrolls). */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
	@Shadow
	public ServerPlayerEntity player;

	@Inject(method = "onHandSwing", at = @At("TAIL"))
	private void eternalreturn$readScroll(HandSwingC2SPacket packet, CallbackInfo ci) {
		if (packet.getHand() == Hand.MAIN_HAND) {
			Scrolls.read(this.player);
		}
	}
}
