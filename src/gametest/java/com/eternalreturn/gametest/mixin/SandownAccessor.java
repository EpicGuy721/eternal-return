package com.eternalreturn.gametest.mixin;

import com.eternalreturn.mobs.entity.SandownEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets a test keep a Sandown from teleporting for a while (SandownTests). */
@Mixin(SandownEntity.class)
public interface SandownAccessor {
	@Accessor("teleportCooldown")
	void eternalreturn$setTeleportCooldown(int ticks);
}
