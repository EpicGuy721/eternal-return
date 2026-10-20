package com.eternalreturn.mixin.mobs;

import com.eternalreturn.config.EternalReturnConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Wither (mobs.witherRework):
 * <ul>
 * <li>first phase: it closes in more slowly, and within 10 blocks of its target it circles round it, about 8 blocks
 * out, instead of hovering in place;</li>
 * <li>at half health, once: an explosion, three wither skeletons (which drop no skulls), and the second phase. It is faster now, drops down
 * to its target's level (no faster than a walking player), and passes through blocks while it has a target. It
 * can't heal back above half.</li>
 * </ul>
 * The second phase is saved as eternalreturn_phase_two.
 */
@Mixin(WitherEntity.class)
public abstract class WitherBossMixin extends HostileEntity {
	@Unique
	private boolean eternalreturn$phaseTwo;

	@Shadow
	public abstract int getInvulnerableTimer();

	@Shadow
	public abstract int getTrackedEntityId(int headIndex);

	protected WitherBossMixin(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
	}

	@WrapOperation(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/boss/WitherEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V"))
	private void eternalreturn$move(WitherEntity self, Vec3d velocity, Operation<Void> original) {
		Entity target = this.getWorld().isClient || !EternalReturnConfig.get().mobs.witherRework ? null
				: this.getWorld().getEntityById(this.getTrackedEntityId(0));
		this.noClip = target != null && this.eternalreturn$phaseTwo;
		if (target == null) {
			original.call(self, velocity);
			return;
		}
		Vec3d flat = new Vec3d(target.getX() - this.getX(), 0.0, target.getZ() - this.getZ());
		double distance = flat.length();
		Vec3d moved;
		if (this.eternalreturn$phaseTwo) {
			// A little faster than the first phase's approach but still slow enough to hit (at most about a
			// walking player's speed), and down to its target's level.
			double dy = MathHelper.clamp((target.getY() + 1.0 - this.getY()) * 0.1, -0.2, 0.2);
			Vec3d flatVelocity = new Vec3d(velocity.x * 0.7, 0.0, velocity.z * 0.7);
			double speed = flatVelocity.length();
			if (speed > 0.2) {
				flatVelocity = flatVelocity.multiply(0.2 / speed);
			}
			moved = new Vec3d(flatVelocity.x, dy, flatVelocity.z);
		} else if (distance > 10.0) {
			moved = new Vec3d(velocity.x * 0.6, velocity.y, velocity.z * 0.6);
		} else {
			Vec3d out = distance < 1.0E-3 ? new Vec3d(1.0, 0.0, 0.0) : flat.multiply(1.0 / distance);
			Vec3d around = new Vec3d(-out.z, 0.0, out.x).multiply(0.25);
			Vec3d keep = out.multiply((distance - 8.0) * 0.04);
			moved = new Vec3d(around.x + keep.x, velocity.y, around.z + keep.z);
		}
		original.call(self, moved);
	}

	@Inject(method = "mobTick", at = @At("TAIL"))
	private void eternalreturn$phases(CallbackInfo ci) {
		if (!EternalReturnConfig.get().mobs.witherRework || this.getInvulnerableTimer() > 0 || !(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		float half = this.getMaxHealth() / 2.0F;
		if (!this.eternalreturn$phaseTwo && this.getHealth() <= half) {
			this.eternalreturn$phaseTwo = true;
			world.createExplosion(this, this.getX(), this.getEyeY(), this.getZ(), 6.0F, false, World.ExplosionSourceType.MOB);
			for (int i = 0; i < 3; i++) {
				WitherSkeletonEntity skeleton = EntityType.WITHER_SKELETON.create(world);
				if (skeleton != null) {
					double angle = i * Math.PI * 2.0 / 3.0;
					skeleton.refreshPositionAndAngles(this.getX() + Math.cos(angle) * 2.0, this.getY(), this.getZ() + Math.sin(angle) * 2.0,
							this.getYaw(), 0.0F);
					skeleton.initialize(world, world.getLocalDifficulty(skeleton.getBlockPos()), SpawnReason.MOB_SUMMONED, null);
					// Its skeletons drop no skull (the wither skeleton loot table skips this tag).
					skeleton.addCommandTag("eternalreturn_wither_minion");
					world.spawnEntity(skeleton);
				}
			}
			this.playSound(SoundEvents.ENTITY_WITHER_SPAWN, 1.0F, 1.2F);
		}
		if (this.eternalreturn$phaseTwo && this.getHealth() > half) {
			this.setHealth(half);
		}
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void eternalreturn$savePhase(NbtCompound nbt, CallbackInfo ci) {
		nbt.putBoolean("eternalreturn_phase_two", this.eternalreturn$phaseTwo);
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void eternalreturn$loadPhase(NbtCompound nbt, CallbackInfo ci) {
		this.eternalreturn$phaseTwo = nbt.getBoolean("eternalreturn_phase_two");
	}
}
