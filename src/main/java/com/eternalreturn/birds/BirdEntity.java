package com.eternalreturn.birds;

import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A bird: one mob with many looks, as the tropical fish is (BirdVariant, the table in BirdTable). Its shape sets its
 * model and hitbox; it is named after its preset (a real bird) or, if it is a random one, its shape. For now (stage 1)
 * it walks and looks about, and runs when hurt; it takes no fall damage.
 */
public class BirdEntity extends AnimalEntity {
	private static final TrackedData<Integer> VARIANT = DataTracker.registerData(BirdEntity.class, TrackedDataHandlerRegistry.INTEGER);

	public BirdEntity(EntityType<? extends BirdEntity> type, World world) {
		super(type, world);
		this.calculateDimensions();
	}

	public static DefaultAttributeContainer.Builder createBirdAttributes() {
		return AnimalEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 4.0).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(1, new EscapeDangerGoal(this, 1.5));
		this.goalSelector.add(5, new WanderAroundFarGoal(this, 1.0));
		this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 6.0F));
		this.goalSelector.add(7, new LookAroundGoal(this));
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(VARIANT, BirdTable.PRESETS.get(0).variant());
	}

	public int getVariant() {
		return this.dataTracker.get(VARIANT);
	}

	public void setVariant(int variant) {
		this.dataTracker.set(VARIANT, variant);
		// Also when the look is unchanged: the tracker only reports changes, and a new bird starts at the type's size.
		this.calculateDimensions();
	}

	public BirdTable.Shape getShape() {
		return BirdVariant.shapeOf(this.getVariant());
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (VARIANT.equals(data)) {
			this.calculateDimensions();
		}
	}

	@Override
	public EntityDimensions getBaseDimensions(EntityPose pose) {
		BirdTable.Shape shape = this.getShape();
		return EntityDimensions.changing(shape.width(), shape.height());
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, @Nullable EntityData data) {
		this.setVariant(BirdVariant.random(world.getRandom()));
		return super.initialize(world, difficulty, reason, data);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Variant", this.getVariant());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("Variant")) {
			this.setVariant(nbt.getInt("Variant"));
		}
	}

	/** Named after its preset (a real bird), or its shape. */
	@Override
	protected Text getDefaultName() {
		BirdTable.Preset preset = BirdTable.preset(this.getVariant());
		return preset != null ? Text.translatable("entity.eternalreturn.bird." + preset.id())
				: Text.translatable("entity.eternalreturn.bird.shape." + this.getShape().id());
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean isBreedingItem(ItemStack stack) {
		return false;
	}

	@Nullable
	@Override
	public PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
		return null;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_PARROT_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_PARROT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_PARROT_DEATH;
	}
}
