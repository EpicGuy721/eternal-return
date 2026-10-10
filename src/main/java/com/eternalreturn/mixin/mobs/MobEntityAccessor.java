package com.eternalreturn.mixin.mobs;

import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Elder guardians make themselves persistent when created; ones that spawn in open water or on a hook shouldn't be. */
@Mixin(MobEntity.class)
public interface MobEntityAccessor {
	@Accessor("persistent")
	void eternalreturn$setPersistent(boolean persistent);
}
