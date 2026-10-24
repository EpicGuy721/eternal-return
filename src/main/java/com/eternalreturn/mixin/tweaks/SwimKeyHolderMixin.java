package com.eternalreturn.mixin.tweaks;

import com.eternalreturn.logic.SwimKey;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Whether the player holds the swim key (SwimKey). */
@Mixin(PlayerEntity.class)
public abstract class SwimKeyHolderMixin implements SwimKey.Holder {
	@Unique
	private boolean eternalreturn$swimKey;

	@Override
	public boolean eternalreturn$swimKey() {
		return this.eternalreturn$swimKey;
	}

	@Override
	public void eternalreturn$setSwimKey(boolean held) {
		this.eternalreturn$swimKey = held;
	}
}
