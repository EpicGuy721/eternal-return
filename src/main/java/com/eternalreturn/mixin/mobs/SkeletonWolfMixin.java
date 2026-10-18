package com.eternalreturn.mixin.mobs;

import com.eternalreturn.EternalReturn;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.passive.WolfVariant;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Skeleton wolves (NewMobs) don't burn: fire and lava can't hurt them, as if under Fire Resistance for good. */
@Mixin(WolfEntity.class)
public abstract class SkeletonWolfMixin extends TameableEntity {
	@Unique
	private static final RegistryKey<WolfVariant> ETERNALRETURN$SKELETON = RegistryKey.of(RegistryKeys.WOLF_VARIANT, EternalReturn.id("skeleton"));

	protected SkeletonWolfMixin(EntityType<? extends TameableEntity> type, World world) {
		super(type, world);
	}

	@Override
	public boolean isFireImmune() {
		return ((WolfEntity) (Object) this).getVariant().matchesKey(ETERNALRETURN$SKELETON) || super.isFireImmune();
	}
}
