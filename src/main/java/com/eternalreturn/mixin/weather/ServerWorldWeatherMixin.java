package com.eternalreturn.mixin.weather;

import com.eternalreturn.weather.LocalWeather;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The overworld's weather with storms (LocalWeather): vanilla's weather cycle is off; lightning strikes only under
 * thunderstorms (and more often there); snow piles up and cauldrons fill only under storms, snow faster in a blizzard;
 * and setWeather (used by other code than /weather) makes storms over the players or clears them.
 */
@Mixin(ServerWorld.class)
public abstract class ServerWorldWeatherMixin {
	@Unique
	private ServerWorld eternalreturn$self() {
		return (ServerWorld) (Object) this;
	}

	@Inject(method = "tickWeather", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$noGlobalWeather(CallbackInfo ci) {
		if (LocalWeather.field(this.eternalreturn$self()) != null) {
			ci.cancel();
		}
	}

	@Inject(method = "setWeather", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$storms(int clearDuration, int rainDuration, boolean raining, boolean thundering, CallbackInfo ci) {
		ServerWorld world = this.eternalreturn$self();
		if (LocalWeather.field(world) == null) {
			return;
		}
		LocalWeather weather = LocalWeather.get(world);
		if (raining) {
			for (ServerPlayerEntity player : world.getPlayers()) {
				weather.storm(world, player.getPos(), thundering, rainDuration);
			}
		} else {
			weather.clear(world, clearDuration);
		}
		ci.cancel();
	}

	/** Lightning: only where a thunderstorm is over the chunk. */
	@WrapOperation(method = "tickChunk", at = {@At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;isRaining()Z"),
			@At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;isThundering()Z")})
	private boolean eternalreturn$thunderHere(ServerWorld world, Operation<Boolean> original, @Local(argsOnly = true) WorldChunk chunk) {
		if (LocalWeather.field(world) == null) {
			return original.call(world);
		}
		ChunkPos pos = chunk.getPos();
		return LocalWeather.thunderAt(world, new BlockPos(pos.getCenterX(), 0, pos.getCenterZ()));
	}

	/** Thunderstorms are smaller than everywhere at once: lightning strikes in them two and a half times as often. */
	@ModifyConstant(method = "tickChunk", constant = @Constant(intValue = 100000))
	private int eternalreturn$moreLightning(int chance) {
		return LocalWeather.field(this.eternalreturn$self()) != null ? 40000 : chance;
	}

	@Inject(method = "tickChunk", at = @At("TAIL"))
	private void eternalreturn$blizzardSnow(WorldChunk chunk, int randomTickSpeed, CallbackInfo ci) {
		ServerWorld world = this.eternalreturn$self();
		ChunkPos pos = chunk.getPos();
		if (randomTickSpeed > 0 && LocalWeather.field(world) != null
				&& LocalWeather.blizzardAt(world, world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, pos.getCenterAtY(0)))) {
			for (int i = 0; i < randomTickSpeed; i++) {
				if (world.random.nextInt(16) == 0) {
					world.tickIceAndSnow(world.getRandomPosInChunk(pos.getStartX(), 0, pos.getStartZ(), 15));
				}
			}
		}
	}

	/** Snow piles up and cauldrons fill only under a storm. */
	@WrapOperation(method = "tickIceAndSnow", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;isRaining()Z"))
	private boolean eternalreturn$stormOverhead(ServerWorld world, Operation<Boolean> original, @Local(argsOnly = true) BlockPos pos) {
		return LocalWeather.field(world) != null ? LocalWeather.rainingAt(world, pos) : original.call(world);
	}
}
