package com.eternalreturn.mixin.mobs;

import com.eternalreturn.mobs.ai.Lounging;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lounging (Lounging): a sitting monster's AI is skipped; its timer lives here. */
@Mixin(MobEntity.class)
public abstract class MobLoungeMixin implements Lounging.Lounger {
	@Unique
	private int eternalreturn$loungeTicks;

	@Override
	public int eternalreturn$loungeTicks() {
		return this.eternalreturn$loungeTicks;
	}

	@Override
	public void eternalreturn$setLoungeTicks(int ticks) {
		this.eternalreturn$loungeTicks = ticks;
	}

	@Inject(method = "tickNewAi", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$lounge(CallbackInfo ci) {
		if (Lounging.tick((MobEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
