package com.eternalreturn.logic;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Gale Step (boots, one level): pressing jump again in the air jumps once more, as high as from the ground, and
 * forgives the fall so far. Once until the player lands (or swims, climbs or flies). The client jumps
 * (GaleStepClient) and tells the server, which forgives the fall and plays the gust for everyone.
 */
public final class GaleStep {
	public static final Identifier ID = EternalReturn.id("gale_step");
	/** Players who have used their second jump and not landed since. */
	private static final Set<UUID> USED = new HashSet<>();

	private GaleStep() {
	}

	public record JumpPayload() implements CustomPayload {
		public static final CustomPayload.Id<JumpPayload> ID = new CustomPayload.Id<>(EternalReturn.id("gale_step"));
		public static final PacketCodec<RegistryByteBuf, JumpPayload> CODEC = PacketCodec.unit(new JumpPayload());

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Whether the player stands on something to jump from again: ground, water, a ladder, or flight. */
	public static boolean grounded(PlayerEntity player) {
		return player.isOnGround() || player.isTouchingWater() || player.isInLava() || player.isClimbing() || player.getAbilities().flying
				|| player.hasVehicle();
	}

	/** Whether this player can jump in the air now (their second jump not yet used). */
	public static boolean canJump(PlayerEntity player) {
		return !grounded(player) && !player.isFallFlying() && !player.hasStatusEffect(StatusEffects.LEVITATION)
				&& NewEnchantments.level(player.getEquippedStack(EquipmentSlot.FEET), ID) > 0;
	}

	/** The jump in the air: as from the ground (and forward too when sprinting), the fall so far forgiven. */
	public static void jump(PlayerEntity player) {
		player.jump();
		player.fallDistance = 0.0F;
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(JumpPayload.ID, JumpPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(JumpPayload.ID, (payload, context) -> jumped(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(server -> USED.removeIf(uuid -> {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
			return player == null || grounded(player);
		}));
	}

	/** The server hears that this player jumped in the air. */
	public static void jumped(ServerPlayerEntity player) {
		if (!canJump(player) || !USED.add(player.getUuid())) {
			return;
		}
		player.fallDistance = 0.0F;
		player.incrementStat(Stats.JUMP);
		player.addExhaustion(player.isSprinting() ? 0.2F : 0.05F);
		ServerWorld world = player.getServerWorld();
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_BREEZE_JUMP, SoundCategory.PLAYERS, 0.7F,
				0.9F + world.random.nextFloat() * 0.2F);
		world.spawnParticles(ParticleTypes.SMALL_GUST, player.getX(), player.getY(), player.getZ(), 4, 0.3, 0.05, 0.3, 0.0);
	}
}
