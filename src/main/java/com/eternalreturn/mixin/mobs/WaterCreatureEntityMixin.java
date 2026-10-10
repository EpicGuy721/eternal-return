package com.eternalreturn.mixin.mobs;

import com.eternalreturn.mobs.BabyVariants;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Baby fish and squids (BabyVariants): the flag lives in a tracked data entry, as for baby skeletons and
 * creepers; vanilla's isBaby() hooks then halve the hitbox. Only the types in eternalreturn:baby_variants
 * ever get it set (not dolphins or tadpoles).
 */
@Mixin(WaterCreatureEntity.class)
public abstract class WaterCreatureEntityMixin extends PathAwareEntity {
	@Unique
	private static final TrackedData<Boolean> ETERNALRETURN_BABY = DataTracker.registerData(WaterCreatureEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	protected WaterCreatureEntityMixin(EntityType<? extends PathAwareEntity> entityType, World world) {
		super(entityType, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ETERNALRETURN_BABY, false);
	}

	@Override
	public boolean isBaby() {
		return this.getDataTracker().get(ETERNALRETURN_BABY);
	}

	@Override
	public void setBaby(boolean baby) {
		this.getDataTracker().set(ETERNALRETURN_BABY, baby);
		BabyVariants.applySpeed(this, baby);
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		if (ETERNALRETURN_BABY.equals(data)) {
			this.calculateDimensions();
		}
		super.onTrackedDataSet(data);
	}
}
