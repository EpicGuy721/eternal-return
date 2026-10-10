package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.BedMemory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.TeleportTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** The beds a player remembers (BedMemory): set by beds, saved, carried over on respawn, and used when the newest is gone. */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin implements BedMemory.Holder {
	@Unique
	private final List<BedMemory.Bed> eternalreturn$beds = new ArrayList<>();

	@Override
	public List<BedMemory.Bed> eternalreturn$beds() {
		return this.eternalreturn$beds;
	}

	/** trySleep sets the spawn point (by day too, as vanilla does) before checking anything else: that bed is remembered. */
	@Inject(method = "trySleep", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
			target = "Lnet/minecraft/server/network/ServerPlayerEntity;setSpawnPoint(Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/util/math/BlockPos;FZZ)V"))
	private void eternalreturn$rememberBed(BlockPos pos, CallbackInfoReturnable<?> cir) {
		ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
		BedMemory.remember(self, pos, self.getYaw());
	}

	@Inject(method = "getRespawnTarget", at = @At("HEAD"))
	private void eternalreturn$fallBackToOlderBed(boolean alive, TeleportTarget.PostDimensionTransition transition, CallbackInfoReturnable<TeleportTarget> cir) {
		BedMemory.fallBack((ServerPlayerEntity) (Object) this);
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void eternalreturn$saveBeds(NbtCompound nbt, CallbackInfo ci) {
		nbt.put(BedMemory.NBT_KEY, BedMemory.write(this.eternalreturn$beds));
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void eternalreturn$loadBeds(NbtCompound nbt, CallbackInfo ci) {
		BedMemory.read(nbt.getList(BedMemory.NBT_KEY, NbtCompound.COMPOUND_TYPE), this.eternalreturn$beds);
	}

	/** The new player object after death (or returning from the End) keeps the list. */
	@Inject(method = "copyFrom", at = @At("TAIL"))
	private void eternalreturn$keepBeds(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
		this.eternalreturn$beds.clear();
		this.eternalreturn$beds.addAll(((BedMemory.Holder) oldPlayer).eternalreturn$beds());
	}
}
