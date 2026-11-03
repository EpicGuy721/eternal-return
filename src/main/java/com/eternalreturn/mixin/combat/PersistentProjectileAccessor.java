package com.eternalreturn.mixin.combat;

import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether an arrow or trident is stuck in the ground (Homing leaves those alone). */
@Mixin(PersistentProjectileEntity.class)
public interface PersistentProjectileAccessor {
	@Accessor("inGround")
	boolean eternalreturn$inGround();
}
