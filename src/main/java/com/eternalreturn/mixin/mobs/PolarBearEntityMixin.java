package com.eternalreturn.mixin.mobs;

import com.eternalreturn.duck.JockeyMount;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PolarBearEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Polar bears spawned under a stray despawn like hostile mobs, the way chicken jockey chickens do. */
@Mixin(PolarBearEntity.class)
public abstract class PolarBearEntityMixin extends AnimalEntity implements JockeyMount {
	@Unique
	private boolean eternalreturn$jockeyMount;

	protected PolarBearEntityMixin(EntityType<? extends AnimalEntity> entityType, World world) {
		super(entityType, world);
	}

	@Override
	public boolean eternalreturn$isJockeyMount() {
		return this.eternalreturn$jockeyMount;
	}

	@Override
	public void eternalreturn$setJockeyMount(boolean mount) {
		this.eternalreturn$jockeyMount = mount;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return this.eternalreturn$jockeyMount || super.canImmediatelyDespawn(distanceSquared);
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void eternalreturn$saveMount(NbtCompound nbt, CallbackInfo ci) {
		if (this.eternalreturn$jockeyMount) {
			nbt.putBoolean("EternalReturnJockeyMount", true);
		}
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void eternalreturn$loadMount(NbtCompound nbt, CallbackInfo ci) {
		this.eternalreturn$jockeyMount = nbt.getBoolean("EternalReturnJockeyMount");
	}
}
