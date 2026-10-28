package com.eternalreturn.gametest.mixin;

import com.eternalreturn.gametest.Heard;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records the sounds and world events of the test server (Heard). */
@Mixin(ServerWorld.class)
public abstract class HeardMixin {
	@Inject(method = "playSound(Lnet/minecraft/entity/player/PlayerEntity;DDDLnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/sound/SoundCategory;FFJ)V",
			at = @At("HEAD"))
	private void eternalreturn$hear(PlayerEntity source, double x, double y, double z, RegistryEntry<SoundEvent> sound, SoundCategory category, float volume,
			float pitch, long seed, CallbackInfo ci) {
		Heard.sound(new Vec3d(x, y, z), sound.value());
	}

	@Inject(method = "syncWorldEvent(Lnet/minecraft/entity/player/PlayerEntity;ILnet/minecraft/util/math/BlockPos;I)V", at = @At("HEAD"))
	private void eternalreturn$see(PlayerEntity player, int eventId, BlockPos pos, int data, CallbackInfo ci) {
		Heard.event(pos, eventId, data);
	}
}
