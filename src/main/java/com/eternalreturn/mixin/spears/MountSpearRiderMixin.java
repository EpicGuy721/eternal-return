package com.eternalreturn.mixin.spears;

import com.eternalreturn.mobs.SpearRiders;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Spears only for riders of a mount that charges (SpearRiders): checked as an unspawned zombie or piglin mounts (a horseman's horse). */
@Mixin(Entity.class)
public abstract class MountSpearRiderMixin {
	@Inject(method = "startRiding(Lnet/minecraft/entity/Entity;Z)Z", at = @At("RETURN"))
	private void eternalreturn$spear(Entity mount, boolean force, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && (Object) this instanceof MobEntity mob && (mob instanceof ZombieEntity || mob instanceof PiglinEntity)) {
			SpearRiders.arm(mob);
		}
	}
}
