package com.eternalreturn.mixin.mobs;

import net.minecraft.entity.ai.pathing.EntityNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A schooling fish drops the path it was swimming for good (FishSchoolGoal). */
@Mixin(EntityNavigation.class)
public interface EntityNavigationAccessor {
	@Accessor("inRecalculationCooldown")
	void eternalreturn$setInRecalculationCooldown(boolean cooldown);
}
