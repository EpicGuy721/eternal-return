package com.eternalreturn.logic;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.tag.FluidTags;

/**
 * Swimming is on a key of its own (survival.swimKey; Left Alt to begin with, client SwimKeyClient): held in water, the
 * player swims (from under the surface, or from standing in shallow water, and on while in the water), and held on land
 * they crawl. Sprinting under water no longer swims. A player who swims in lava (LavaSwimming) swims there the same
 * way; anyone else can't swim or crawl in lava. The client tells the server whether the key is held (SwimKeyMixin
 * decides).
 */
public final class SwimKey {
	private SwimKey() {
	}

	/** A player's swim key, held or not, as their client last said. */
	public interface Holder {
		boolean eternalreturn$swimKey();

		void eternalreturn$setSwimKey(boolean held);
	}

	public record KeyPayload(boolean held) implements CustomPayload {
		public static final CustomPayload.Id<KeyPayload> ID = new CustomPayload.Id<>(EternalReturn.id("swim_key"));
		public static final PacketCodec<RegistryByteBuf, KeyPayload> CODEC = PacketCodecs.BOOL.<RegistryByteBuf>cast().xmap(KeyPayload::new, KeyPayload::held);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(KeyPayload.ID, KeyPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(KeyPayload.ID, (payload, context) -> ((Holder) context.player()).eternalreturn$setSwimKey(payload.held()));
	}

	public static boolean enabled() {
		return EternalReturnConfig.get().survival.swimKey;
	}

	/** Whether the player should swim (or crawl) now. */
	public static boolean swimming(PlayerEntity player) {
		if (!((Holder) player).eternalreturn$swimKey() || player.hasVehicle() || player.isFallFlying() || player.getAbilities().flying || player.isSpectator()) {
			return false;
		}
		if (player.isInLava() && !LavaSwimming.swims(player)) {
			return false;
		}
		boolean lava = player.isInLava();
		if (player.isTouchingWater() || lava) {
			// From under the surface, or standing in shallow water; then on while in it.
			if (player.isSwimming() || player.isOnGround()) {
				return true;
			}
			FluidState fluid = player.getWorld().getFluidState(player.getBlockPos());
			return lava ? player.isSubmergedIn(FluidTags.LAVA) : player.isSubmergedInWater() && fluid.isIn(FluidTags.WATER);
		}
		return player.isOnGround() || player.isSwimming();
	}
}
