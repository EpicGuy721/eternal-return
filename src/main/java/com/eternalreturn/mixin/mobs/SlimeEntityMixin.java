package com.eternalreturn.mixin.mobs;

import com.eternalreturn.mobs.Slimes;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bigger slimes (Slimes.maybeGrow), and slimes with a skeleton on top (ZombieHabits). */
@Mixin(SlimeEntity.class)
public abstract class SlimeEntityMixin {
	@Inject(method = "initialize", at = @At("TAIL"))
	private void eternalreturn$bigger(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, EntityData data, CallbackInfoReturnable<EntityData> cir) {
		SlimeEntity slime = (SlimeEntity) (Object) this;
		Slimes.maybeGrow(slime, world.getRandom());
		if (slime.getType() == net.minecraft.entity.EntityType.SLIME && slime.getSize() >= 2) {
			com.eternalreturn.mobs.ZombieHabits.trySkeletonRider(slime, world, difficulty, reason, com.eternalreturn.config.EternalReturnConfig.get().mobs.slimeSkeletonChance);
		}
	}
}
