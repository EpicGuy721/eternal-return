package com.eternalreturn.birds;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.Flutterer;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.SitGoal;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
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
 * model and hitbox; it is named after its preset (a real bird) or, if it is a random one, its shape. It flies and walks
 * as a parrot does (flight control, a flying pathfinder), flutters down rather than falls, and takes no fall damage.
 * It flies off from players who come too close (BirdGoals.FlyAway); perching shapes rest on leaves and logs, the
 * others wander low, and ducks swim. Each shape flies at its own speed. Flocking shapes take off together when one is
 * startled (or now and then on their own) and fly as a flock, steering by the boids rules (BirdGoals.FlockFlight).
 * Wild songbirds and corvids peck at seeds and young crops on farmland (CropPests).
 * Songbirds and corvids (shapes marked tameable) can be tamed with seeds, one try in three: a pet follows its owner,
 * sits or stands when its owner uses it with an empty hand, heals on seeds, and no longer flies off or flocks.
 * Drops feathers (loot table eternalreturn:entities/bird).
 */
public class BirdEntity extends TameableEntity implements Flutterer {
	private static final TrackedData<Integer> VARIANT = DataTracker.registerData(BirdEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** The flying-speed attribute every bird has (as a parrot's); the shape's own speed scales the push it gives in the air. */
	private static final double FLYING_SPEED = 0.4;
	/** Server side: ticks left of a flock flight (0: not flying with the flock), and where it is headed. */
	private int flockTicks;
	private Vec3d flockTarget = Vec3d.ZERO;
	/** Server side: the crop it is pecking at (CropPests), or null; and the age before which it won't go for crops. */
	@Nullable
	BlockPos peckingAt;
	int noPeckUntil;

	public BirdEntity(EntityType<? extends BirdEntity> type, World world) {
		super(type, world);
		this.calculateDimensions();
		this.moveControl = new FlightMoveControl(this, 10, false) {
			@Override
			public void tick() {
				// A flock flight steers the bird itself; the flight control stays out of the way, gravity off.
				if (BirdEntity.this.isFlocking()) {
					BirdEntity.this.setNoGravity(true);
					return;
				}
				super.tick();
			}
		};
		this.setPathfindingPenalty(PathNodeType.DANGER_FIRE, -1.0F);
		this.setPathfindingPenalty(PathNodeType.DAMAGE_FIRE, -1.0F);
		this.setPathfindingPenalty(PathNodeType.COCOA, -1.0F);
	}

	@Override
	protected EntityNavigation createNavigation(World world) {
		BirdNavigation navigation = new BirdNavigation(this, world);
		navigation.setCanPathThroughDoors(false);
		navigation.setCanSwim(true);
		navigation.setCanEnterOpenDoors(true);
		return navigation;
	}

	/** Flutters: falls slowly (unless it is flying somewhere). */
	@Override
	public void tickMovement() {
		super.tickMovement();
		Vec3d velocity = this.getVelocity();
		if (!this.isOnGround() && velocity.y < 0.0 && !this.isFlocking() && this.getNavigation().isIdle()) {
			this.setVelocity(velocity.multiply(1.0, 0.6, 1.0));
		}
	}

	/**
	 * The push it gives itself in the air: vanilla's is a flat 0.02 for every mob, whatever its speed (a parrot crawls
	 * along at about 2 blocks a second). Scaled so the bird reaches its shape's flight speed.
	 */
	@Override
	protected float getOffGroundSpeed() {
		return (float) (this.getShape().flightSpeed() * 0.09 / FLYING_SPEED);
	}

	/** At the crops, pecking (CropPests). */
	public boolean isPecking() {
		return this.peckingAt != null;
	}

	public boolean isFlocking() {
		return this.flockTicks > 0;
	}

	public Vec3d getFlockTarget() {
		return this.flockTarget;
	}

	/** Takes off with the flock toward target for a while (BirdGoals.FlockFlight steers). */
	public void startFlockFlight(Vec3d target, int ticks) {
		this.flockTarget = target;
		this.flockTicks = ticks;
		this.getNavigation().stop();
		if (this.isOnGround()) {
			this.setVelocity(this.getVelocity().add(0.0, 0.35, 0.0));
		}
	}

	public void tickFlockFlight() {
		if (--this.flockTicks <= 0) {
			this.endFlockFlight();
		}
	}

	public void endFlockFlight() {
		this.flockTicks = 0;
		this.setNoGravity(false);
	}

	@Override
	public boolean isInAir() {
		return !this.isOnGround();
	}

	@Override
	protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
	}

	public static DefaultAttributeContainer.Builder createBirdAttributes() {
		return AnimalEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 4.0).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2)
				.add(EntityAttributes.GENERIC_FLYING_SPEED, 0.4);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(1, new TameableEntity.TameableEscapeDangerGoal(1.6));
		this.goalSelector.add(1, new SitGoal(this));
		this.goalSelector.add(2, new FollowOwnerGoal(this, 1.0, 5.0F, 1.0F));
		this.goalSelector.add(2, new BirdGoals.FlyAway(this));
		this.goalSelector.add(3, new BirdGoals.FlockFlight(this));
		this.goalSelector.add(4, new CropPests.PeckCrops(this));
		this.goalSelector.add(5, new BirdGoals.FlockWander(this));
		this.goalSelector.add(5, new BirdGoals.Perch(this));
		this.goalSelector.add(5, new BirdGoals.WanderLow(this));
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
		boolean wild = reason == SpawnReason.NATURAL || reason == SpawnReason.CHUNK_GENERATION;
		this.setVariant(wild ? BirdVariant.forBiome(world.getBiome(this.getBlockPos()), world.getRandom()) : BirdVariant.random(world.getRandom()));
		if (wild) {
			Birds.spawnFlockmates(world, this);
		}
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

	/** Seeds tame a tameable bird (one try in three) and heal a pet; its owner's empty hand sits it down or stands it up. */
	@Override
	public ActionResult interactMob(PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		World world = this.getWorld();
		if (stack.isIn(ItemTags.PARROT_FOOD) && this.getShape().tameable() && (!this.isTamed() || this.getHealth() < this.getMaxHealth())) {
			stack.decrementUnlessCreative(1, player);
			if (!this.isSilent()) {
				world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_PARROT_EAT, this.getSoundCategory(), 1.0F,
						1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
			}
			if (!world.isClient) {
				if (this.isTamed()) {
					this.heal(1.0F);
				} else if (this.random.nextInt(3) == 0) {
					this.setOwner(player);
					this.endFlockFlight();
					this.getNavigation().stop();
					world.sendEntityStatus(this, EntityStatuses.ADD_POSITIVE_PLAYER_REACTION_PARTICLES);
				} else {
					world.sendEntityStatus(this, EntityStatuses.ADD_NEGATIVE_PLAYER_REACTION_PARTICLES);
				}
			}
			return ActionResult.success(world.isClient);
		}
		if (stack.isEmpty() && this.isTamed() && this.isOwner(player) && !this.isInAir()) {
			if (!world.isClient) {
				this.setSitting(!this.isSitting());
				this.getNavigation().stop();
			}
			return ActionResult.success(world.isClient);
		}
		return super.interactMob(player, hand);
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
