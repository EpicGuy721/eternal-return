package com.eternalreturn.mixin.mobs;

import com.eternalreturn.birds.CropPests;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tamed wolves and cats chase birds off crops (CropPests.ShooBirds), before following their owner. */
@Mixin({WolfEntity.class, CatEntity.class})
public abstract class PetsShooBirdsMixin extends TameableEntity {
	protected PetsShooBirdsMixin(EntityType<? extends TameableEntity> type, World world) {
		super(type, world);
	}

	@Inject(method = "initGoals", at = @At("TAIL"))
	private void eternalreturn$shooBirds(CallbackInfo ci) {
		this.goalSelector.add(4, new CropPests.ShooBirds(this));
	}
}
