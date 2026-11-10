package com.eternalreturn.survival;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.weather.LocalWeather;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The cold (survival.coldFreezeSeconds): a player out in the cold (where it would snow: cold biomes, and high up in
 * the mountains) for long enough begins to freeze, as in powder snow (the frost creeping over the screen, then the
 * hurt). Time in a blizzard counts double. Warmth near them (block tag eternalreturn:heat_sources within RADIUS: fire,
 * lit campfires and furnaces, lava, magma) keeps it off and warms them back up, four times as fast as the cold
 * came on; out of the cold they warm up twice as fast. Each piece of leather armor (item tag minecraft:freeze_immune_wearables) lets them stay out longer
 * (survival.leatherColdSeconds), but it doesn't keep the cold off for good, as it keeps powder snow off.
 */
public final class ColdExposure {
	public static final TagKey<Block> HEAT_SOURCES = TagKey.of(RegistryKeys.BLOCK, EternalReturn.id("heat_sources"));
	private static final int RADIUS = 4;
	private static final int CHECK_EVERY = 20;
	private static final Map<UUID, State> STATES = new HashMap<>();

	private static final class State {
		int cold;
		boolean warm;
	}

	private ColdExposure() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (EternalReturnConfig.get().survival.coldFreezeSeconds <= 0) {
				STATES.clear();
				return;
			}
			STATES.keySet().removeIf(id -> server.getPlayerManager().getPlayer(id) == null);
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				tick(player);
			}
		});
	}

	/** How long (in ticks) this player has been out in the cold, net of warming up. */
	public static int exposure(PlayerEntity player) {
		State state = STATES.get(player.getUuid());
		return state != null ? state.cold : 0;
	}

	/** For tests. */
	public static void setExposure(PlayerEntity player, int ticks) {
		STATES.computeIfAbsent(player.getUuid(), id -> new State()).cold = ticks;
	}

	/** How long (in ticks) this player can be out in the cold before freezing: longer for each piece of leather worn. */
	public static int allowance(PlayerEntity player) {
		EternalReturnConfig.SurvivalTweaks config = EternalReturnConfig.get().survival;
		int leather = 0;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
				if (player.getEquippedStack(slot).isIn(ItemTags.FREEZE_IMMUNE_WEARABLES)) {
					leather++;
				}
			}
		}
		return 20 * (config.coldFreezeSeconds + leather * config.leatherColdSeconds);
	}

	/** Whether it's cold here: where it would snow. */
	public static boolean cold(World world, BlockPos pos) {
		return world.getDimension().natural() && world.getBiome(pos).value().isCold(pos);
	}

	/** Whether there's warmth near: a heat source (lit, if it can be unlit) within RADIUS. */
	public static boolean warm(World world, BlockPos center) {
		for (BlockPos pos : BlockPos.iterate(center.add(-RADIUS, -2, -RADIUS), center.add(RADIUS, 2, RADIUS))) {
			BlockState state = world.getBlockState(pos);
			if (state.isIn(HEAT_SOURCES) && (!state.contains(Properties.LIT) || state.get(Properties.LIT))) {
				return true;
			}
		}
		return false;
	}

	private static void tick(ServerPlayerEntity player) {
		if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
			STATES.remove(player.getUuid());
			return;
		}
		State state = STATES.computeIfAbsent(player.getUuid(), id -> new State());
		World world = player.getWorld();
		if (player.age % CHECK_EVERY == 0) {
			BlockPos pos = player.getBlockPos();
			state.warm = warm(world, pos);
			if (state.warm) {
				state.cold = Math.max(0, state.cold - 4 * CHECK_EVERY);
			} else if (cold(world, pos)) {
				state.cold += LocalWeather.blizzardAt(world, pos) ? 2 * CHECK_EVERY : CHECK_EVERY;
			} else {
				state.cold = Math.max(0, state.cold - 2 * CHECK_EVERY);
			}
			state.cold = Math.min(state.cold, allowance(player) + 600);
		}
		if (state.warm || state.cold <= allowance(player)) {
			return;
		}
		// Vanilla thaws everyone 2 a tick out of powder snow, so this freezes 1 a tick: 7 seconds to frozen, as powder snow.
		player.setFrozenTicks(Math.min(player.getMinFreezeDamageTicks() + 2, player.getFrozenTicks() + 3));
		// Vanilla's freezing hurts only those who can freeze (no leather on): the cold gets through leather in the end.
		if (!player.canFreeze() && player.isFrozen() && player.age % 40 == 0 && !player.getAbilities().invulnerable) {
			player.damage(player.getDamageSources().freeze(), 1.0F);
		}
	}
}
