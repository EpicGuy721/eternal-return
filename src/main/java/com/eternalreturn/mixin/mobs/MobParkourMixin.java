package com.eternalreturn.mixin.mobs;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.mobs.ai.LeapGapGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Humanoid monsters jump gaps to reach their target (LeapGapGoal; entity tag eternalreturn:parkour_mobs). */
@Mixin(MobEntity.class)
public abstract class MobParkourMixin {
	@Unique
	private static final TagKey<EntityType<?>> PARKOUR = TagKey.of(RegistryKeys.ENTITY_TYPE, EternalReturn.id("parkour_mobs"));

	@Inject(method = "<init>", at = @At("TAIL"))
	private void eternalreturn$parkour(EntityType<? extends MobEntity> type, World world, CallbackInfo ci) {
		MobEntity self = (MobEntity) (Object) this;
		if (world != null && !world.isClient && type.isIn(PARKOUR)) {
			((MobEntityAccessor) self).eternalreturn$goalSelector().add(1, new LeapGapGoal(self));
		}
	}
}
