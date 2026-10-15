package com.eternalreturn.mixin.frames;

import com.eternalreturn.blocks.InvisibleSigns;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A sign remembers being made invisible (InvisibleSigns); it goes to players with the rest of the sign's data. */
@Mixin(SignBlockEntity.class)
public abstract class SignBlockEntityMixin implements InvisibleSigns.Holder {
	@Unique
	private boolean eternalreturn$invisible;

	@Override
	public boolean eternalreturn$invisible() {
		return this.eternalreturn$invisible;
	}

	@Override
	public void eternalreturn$setInvisible(boolean invisible) {
		this.eternalreturn$invisible = invisible;
	}

	@Inject(method = "writeNbt", at = @At("TAIL"))
	private void eternalreturn$save(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup, CallbackInfo ci) {
		if (this.eternalreturn$invisible) {
			nbt.putBoolean("eternalreturn_invisible", true);
		}
	}

	@Inject(method = "readNbt", at = @At("TAIL"))
	private void eternalreturn$load(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup, CallbackInfo ci) {
		this.eternalreturn$invisible = nbt.getBoolean("eternalreturn_invisible");
	}
}
