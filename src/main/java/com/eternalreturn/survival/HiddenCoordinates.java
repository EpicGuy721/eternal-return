package com.eternalreturn.survival;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameRules;

/**
 * Coordinates are hidden in survival and adventure (survival.hideCoordinatesInSurvival): those players get the game's
 * own reduced debug info, as the reducedDebugInfo game rule gives everyone, so F3 (vanilla's, or Nostalgic Tweaks'
 * old-style one, which asks the same thing) shows no position, chunk, targeted block, light or biome. Creative and
 * spectator players see everything (unless the game rule is on). Done on the server, so it holds on servers and for
 * any client: the reduced flag goes out as a player joins (HiddenCoordinatesMixin, in the join packet), whenever the
 * game rule changes (in the status the server sends, EntityStatusPacketMixin), and when their game mode changes.
 */
public final class HiddenCoordinates {
	private HiddenCoordinates() {
	}

	/** Whether this player's coordinates are hidden from them by this rule. */
	public static boolean hidden(ServerPlayerEntity player) {
		return EternalReturnConfig.get().survival.hideCoordinatesInSurvival && player.interactionManager.isSurvivalLike();
	}

	/** Whether the player should have reduced debug info: the game rule, or this rule. */
	public static boolean reduced(ServerPlayerEntity player) {
		return player.getServerWorld().getGameRules().getBoolean(GameRules.REDUCED_DEBUG_INFO) || hidden(player);
	}

	/** Tells the player's client whether its debug info is reduced (after a game mode change). */
	public static void send(ServerPlayerEntity player) {
		if (player.networkHandler != null) {
			player.networkHandler.sendPacket(new EntityStatusS2CPacket(player,
					reduced(player) ? EntityStatuses.USE_REDUCED_DEBUG_INFO : EntityStatuses.USE_FULL_DEBUG_INFO));
		}
	}
}
