package com.eternalreturn.mixin.mobs;

import com.eternalreturn.mobs.Servants;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** An undead servant's master (Servants), saved with it; a loaded servant is set up again. */
@Mixin(MobEntity.class)
public abstract class MobEntityServantMixin implements Servants.Holder {
	@Unique
	private static final String MASTER_KEY = "eternalreturn_master";
	@Unique
	@Nullable
	private UUID eternalreturn$master;

	@Override
	@Nullable
	public UUID eternalreturn$master() {
		return this.eternalreturn$master;
	}

	@Override
	public void eternalreturn$setMaster(@Nullable UUID master) {
		this.eternalreturn$master = master;
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void eternalreturn$saveMaster(NbtCompound nbt, CallbackInfo ci) {
		if (this.eternalreturn$master != null) {
			nbt.putUuid(MASTER_KEY, this.eternalreturn$master);
		}
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void eternalreturn$loadMaster(NbtCompound nbt, CallbackInfo ci) {
		if (nbt.containsUuid(MASTER_KEY)) {
			this.eternalreturn$master = nbt.getUuid(MASTER_KEY);
			Servants.setUp((MobEntity) (Object) this);
		}
	}
}
