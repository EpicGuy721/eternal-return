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

/** Spears only for riders of a mount that charges (SpearRiders): checked as a zombie is fitted out (a rider mounted first). */
@Mixin(ZombieEntity.class)
public abstract class ZombieSpearRiderMixin {
	@Inject(method = "initialize", at = @At("RETURN"))
	private void eternalreturn$spear(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, EntityData data,
			CallbackInfoReturnable<EntityData> cir) {
		SpearRiders.arm((MobEntity) (Object) this);
	}
}
