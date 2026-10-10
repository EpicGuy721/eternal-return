package com.eternalreturn.mixin.mobs;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.GuardianEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

/**
 * A drowned riding a guardian (a fished-up jockey, see HostileCatches) lets the guardian swim by its own
 * AI and only throws its trident; steering from the rider would leave the guardian drifting.
 */
@Mixin(DrownedEntity.class)
public abstract class DrownedEntityMixin extends ZombieEntity {
	protected DrownedEntityMixin(EntityType<? extends ZombieEntity> entityType, World world) {
		super(entityType, world);
	}

	@Override
	public boolean shouldControlVehicles() {
		return !(this.getVehicle() instanceof GuardianEntity) && super.shouldControlVehicles();
	}
}
