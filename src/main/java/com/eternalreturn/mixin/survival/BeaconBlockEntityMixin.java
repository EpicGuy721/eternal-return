package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.BeaconPower;
import com.eternalreturn.survival.BeaconZones;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Beacons: no pyramid, one effect, range and effect time from the payment (BeaconPower); and the area each working
 * beacon covers for BeaconZones, given as it ticks and dropped when it is removed or unloaded.
 */
@Mixin(BeaconBlockEntity.class)
public abstract class BeaconBlockEntityMixin extends BlockEntity implements BeaconPower.Holder {
	@Unique
	private int eternalreturn$tier;

	protected BeaconBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public int eternalreturn$tier() {
		return this.eternalreturn$tier;
	}

	@Override
	public void eternalreturn$setTier(int tier) {
		this.eternalreturn$tier = tier;
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private static void eternalreturn$registerZone(World world, BlockPos pos, BlockState state, BeaconBlockEntity beacon, CallbackInfo ci) {
		if (!world.isClient) {
			int range = BeaconPower.enabled()
					? BeaconPower.range(BeaconPower.tier(beacon, ((BeaconBlockEntityAccessor) beacon).eternalreturn$primary()))
					: ((BeaconBlockEntityAccessor) beacon).eternalreturn$level() * 10 + 10;
			boolean working = ((BeaconBlockEntityAccessor) beacon).eternalreturn$level() > 0 && range > 0;
			BeaconZones.update(world, pos, working ? range : 0, !beacon.getBeamSegments().isEmpty());
		}
	}

	/** No pyramid: a beacon with a clear beam is always at full power, so every effect can be picked. */
	@Inject(method = "updateLevel", at = @At("HEAD"), cancellable = true)
	private static void eternalreturn$noPyramid(World world, int x, int y, int z, CallbackInfoReturnable<Integer> cir) {
		if (BeaconPower.enabled()) {
			cir.setReturnValue(4);
		}
	}

	@Inject(method = "applyPlayerEffects", at = @At("HEAD"), cancellable = true)
	private static void eternalreturn$oneEffect(World world, BlockPos pos, int level, @Nullable RegistryEntry<StatusEffect> primary,
			@Nullable RegistryEntry<StatusEffect> secondary, CallbackInfo ci) {
		if (BeaconPower.enabled()) {
			BeaconPower.apply(world, pos, primary);
			ci.cancel();
		}
	}

	@Inject(method = "writeNbt", at = @At("TAIL"))
	private void eternalreturn$saveTier(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup, CallbackInfo ci) {
		nbt.putInt("eternalreturn_tier", this.eternalreturn$tier);
	}

	@Inject(method = "readNbt", at = @At("TAIL"))
	private void eternalreturn$loadTier(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup, CallbackInfo ci) {
		this.eternalreturn$tier = nbt.getInt("eternalreturn_tier");
	}

	@Inject(method = "markRemoved", at = @At("HEAD"))
	private void eternalreturn$dropZone(CallbackInfo ci) {
		if (this.world != null && !this.world.isClient) {
			BeaconZones.remove(this.world, this.pos);
		}
	}
}
