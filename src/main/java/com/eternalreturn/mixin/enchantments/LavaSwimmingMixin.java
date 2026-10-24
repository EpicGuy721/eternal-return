package com.eternalreturn.mixin.enchantments;

import com.eternalreturn.logic.LavaSwimming;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A lava swimmer (LavaSwimming) isn't hurt or set alight by lava, and sprint-swims in it as in water. */
@Mixin(Entity.class)
public abstract class LavaSwimmingMixin {
	@Shadow
	public abstract boolean isSprinting();

	@Shadow
	public abstract boolean hasVehicle();

	@Shadow
	public abstract boolean isInLava();

	@Shadow
	public abstract boolean isSwimming();

	@Shadow
	public abstract void setSwimming(boolean swimming);

	@Shadow
	public abstract boolean isSubmergedIn(TagKey<Fluid> fluidTag);

	@Inject(method = "setOnFireFromLava", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$lavaProof(CallbackInfo ci) {
		if (LavaSwimming.swims((Entity) (Object) this)) {
			ci.cancel();
		}
	}

	/** In lava as vanilla does in water: start swimming when sprinting under the surface, keep on while sprinting in it. */
	@Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$swimInLava(CallbackInfo ci) {
		if (this.isInLava() && LavaSwimming.swims((Entity) (Object) this)) {
			this.setSwimming(this.isSprinting() && !this.hasVehicle() && (this.isSwimming() || this.isSubmergedIn(FluidTags.LAVA)));
			ci.cancel();
		}
	}
}
