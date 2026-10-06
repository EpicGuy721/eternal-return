package com.eternalreturn.mobs.entity;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.mobs.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The bobber a fishing-rod zombie casts. Vanilla's FishingBobberEntity only works for players, so
 * this is a plain thrown projectile: it flies, latches onto the first entity it hits, then yanks
 * that entity toward the zombie a moment later and is reeled back in.
 */
public class ZombieHookEntity extends ThrownEntity {
	private static final TrackedData<Integer> HOOKED_ENTITY_ID = DataTracker.registerData(ZombieHookEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final int PULL_DELAY_TICKS = 6;
	private static final int MAX_FLIGHT_TICKS = 60;
	private static final int GROUND_LINGER_TICKS = 20;
	private static final double MAX_LINE_LENGTH = 32.0;

	@Nullable
	private Vec3d stuckAt;
	private int stuckTicks;
	private int flightTicks;

	public ZombieHookEntity(EntityType<? extends ZombieHookEntity> type, World world) {
		super(type, world);
	}

	public ZombieHookEntity(World world, LivingEntity owner) {
		super(ModEntities.ZOMBIE_HOOK, owner, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(HOOKED_ENTITY_ID, 0);
	}

	@Nullable
	public Entity getHookedEntity() {
		int id = this.dataTracker.get(HOOKED_ENTITY_ID);
		return id > 0 ? this.getWorld().getEntityById(id - 1) : null;
	}

	@Override
	public void tick() {
		World world = this.getWorld();
		if (!world.isClient && !this.ownerCanReel()) {
			this.discard();
			return;
		}

		Entity hooked = this.getHookedEntity();
		if (hooked != null) {
			if (!world.isClient && (!hooked.isAlive() || hooked.getWorld() != world)) {
				this.discard();
				return;
			}
			this.setPosition(hooked.getX(), hooked.getBodyY(0.8), hooked.getZ());
			if (!world.isClient && ++this.stuckTicks >= PULL_DELAY_TICKS) {
				this.reel(hooked);
			}
			return;
		}

		if (this.stuckAt != null) {
			this.setPosition(this.stuckAt);
			if (!world.isClient && ++this.stuckTicks >= GROUND_LINGER_TICKS) {
				this.retract();
			}
			return;
		}

		super.tick();
		if (this.stuckAt != null) {
			// ThrownEntity moves the full step after a block hit; pull the hook back to where it struck.
			this.setPosition(this.stuckAt);
		}
		if (!world.isClient && ++this.flightTicks > MAX_FLIGHT_TICKS) {
			this.discard();
		}
	}

	@Override
	protected void onEntityHit(EntityHitResult hit) {
		super.onEntityHit(hit);
		if (!this.getWorld().isClient) {
			this.dataTracker.set(HOOKED_ENTITY_ID, hit.getEntity().getId() + 1);
			this.stuckTicks = 0;
		}
	}

	@Override
	protected void onBlockHit(BlockHitResult hit) {
		super.onBlockHit(hit);
		this.stuckAt = hit.getPos();
		this.stuckTicks = 0;
		this.setVelocity(Vec3d.ZERO);
	}

	private boolean ownerCanReel() {
		return this.getOwner() instanceof LivingEntity owner
				&& owner.isAlive()
				&& owner.getWorld() == this.getWorld()
				&& owner.getMainHandStack().isOf(Items.FISHING_ROD)
				&& this.squaredDistanceTo(owner) <= MAX_LINE_LENGTH * MAX_LINE_LENGTH;
	}

	/** Like a player reeling in a hooked mob, plus a little lift so ground friction doesn't eat the pull. */
	private void reel(Entity hooked) {
		if (this.getOwner() instanceof LivingEntity owner) {
			double strength = EternalReturnConfig.get().mobs.zombieFishingRodPullStrength;
			Vec3d toOwner = owner.getPos().subtract(hooked.getPos());
			double lift = Math.sqrt(toOwner.horizontalLength()) * 0.08;
			hooked.addVelocity(toOwner.x * strength, toOwner.y * strength + lift, toOwner.z * strength);
			// Players own their movement; this makes the server send them the new velocity.
			hooked.velocityModified = true;
			owner.swingHand(Hand.MAIN_HAND);
		}
		this.retract();
	}

	private void retract() {
		this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE,
				SoundCategory.HOSTILE, 1.0F, 0.4F / (this.random.nextFloat() * 0.4F + 0.8F));
		this.discard();
	}
}
