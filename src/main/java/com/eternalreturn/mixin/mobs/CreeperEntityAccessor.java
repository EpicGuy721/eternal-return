package com.eternalreturn.mixin.mobs;

import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.mob.CreeperEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The creeper's "charged" flag (the philosopher's stone flips it), and its fuse and blast (large creepers, LargeMobs). */
@Mixin(CreeperEntity.class)
public interface CreeperEntityAccessor {
	@Accessor("CHARGED")
	static TrackedData<Boolean> eternalreturn$charged() {
		throw new AssertionError();
	}

	@Accessor("fuseTime")
	int eternalreturn$getFuseTime();

	@Accessor("fuseTime")
	void eternalreturn$setFuseTime(int fuseTime);

	@Accessor("explosionRadius")
	int eternalreturn$getExplosionRadius();

	@Accessor("explosionRadius")
	void eternalreturn$setExplosionRadius(int radius);
}
