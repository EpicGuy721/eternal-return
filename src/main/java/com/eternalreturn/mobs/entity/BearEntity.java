package com.eternalreturn.mobs.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.PolarBearEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * Brown, black and sun bears (com.eternalreturn.mobs.ForestMobs): polar bears in every way (neutral, they fight back
 * when hit, a mother goes for anyone near her cub, they drop fish) but their coat, size and where they live.
 */
public class BearEntity extends PolarBearEntity {
	public BearEntity(EntityType<? extends PolarBearEntity> type, World world) {
		super(type, world);
	}

	/** A cub is a bear of its parents' kind. */
	@Override
	public PassiveEntity createChild(ServerWorld world, PassiveEntity mate) {
		return (PassiveEntity) this.getType().create(world);
	}
}
