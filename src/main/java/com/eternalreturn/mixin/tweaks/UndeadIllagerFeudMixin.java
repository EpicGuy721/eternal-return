package com.eternalreturn.mixin.tweaks;

import com.eternalreturn.mixin.mobs.MobEntityAccessor;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The undead and the illagers are enemies: each hunts the other on sight (after players and their usual targets). */
@Mixin(MobEntity.class)
public abstract class UndeadIllagerFeudMixin {
	@Unique
	private static boolean eternalreturn$fighter(MobEntity mob) {
		return mob instanceof HostileEntity && !(mob instanceof WitherEntity);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void eternalreturn$feud(EntityType<? extends MobEntity> type, World world, CallbackInfo ci) {
		MobEntity self = (MobEntity) (Object) this;
		if (world == null || world.isClient || !eternalreturn$fighter(self)) {
			return;
		}
		if (type.isIn(EntityTypeTags.UNDEAD)) {
			((MobEntityAccessor) self).eternalreturn$targetSelector().add(4, new ActiveTargetGoal<>(self, MobEntity.class, 10, true, false,
					other -> other.getType().isIn(EntityTypeTags.ILLAGER)));
		} else if (type.isIn(EntityTypeTags.ILLAGER)) {
			((MobEntityAccessor) self).eternalreturn$targetSelector().add(4, new ActiveTargetGoal<>(self, MobEntity.class, 10, true, false,
					other -> other.getType().isIn(EntityTypeTags.UNDEAD) && other instanceof MobEntity mob && eternalreturn$fighter(mob)));
		}
	}
}
