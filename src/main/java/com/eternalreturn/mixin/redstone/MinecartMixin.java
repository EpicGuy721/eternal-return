package com.eternalreturn.mixin.redstone;

import com.eternalreturn.blocks.CopperRail;
import com.eternalreturn.blocks.Minecarts;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PoweredRailBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.enums.RailShape;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Minecarts (com.eternalreturn.blocks.Minecarts): several of vanilla's steps a tick on rails (one on copper rails), a
 * shorter smoothing of the position players see, and the chain to the cart ahead (saved as eternalreturn_leader, its
 * entity id sent to players to draw it). As in 1.21.2: a cart running up a slope that ends flies on up off it; in the
 * air it tilts the way it flies, smoothly; and a cart standing on a straight rail faces along it, so it doesn't turn
 * when the rail is broken.
 */
@Mixin(AbstractMinecartEntity.class)
public abstract class MinecartMixin extends VehicleEntity implements Minecarts.Linked {
	@Unique
	private static final TrackedData<Integer> ETERNALRETURN$LEADER_ID = DataTracker.registerData(AbstractMinecartEntity.class, TrackedDataHandlerRegistry.INTEGER);

	@Shadow
	private int clientInterpolationSteps;

	@Shadow
	private boolean yawFlipped;

	/** The fastest a cart flies up off a slope, in blocks a tick (vanilla's top speed on rails). */
	@Unique
	private static final double ETERNALRETURN$MAX_LAUNCH = 0.4;

	/** Whether this tick was spent on rails, and how fast the slope it was last on was carrying it up. */
	@Unique
	private boolean eternalreturn$onRailThisTick;
	@Unique
	private double eternalreturn$climb;

	@Unique
	@Nullable
	private UUID eternalreturn$leader;

	protected MinecartMixin(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	@Nullable
	public UUID eternalreturn$leader() {
		return this.eternalreturn$leader;
	}

	@Override
	public void eternalreturn$setLeader(@Nullable UUID leader) {
		this.eternalreturn$leader = leader;
	}

	@Override
	public int eternalreturn$leaderId() {
		return this.dataTracker.get(ETERNALRETURN$LEADER_ID);
	}

	@Override
	public void eternalreturn$setLeaderId(int id) {
		this.dataTracker.set(ETERNALRETURN$LEADER_ID, id);
	}

	@Inject(method = "initDataTracker", at = @At("TAIL"))
	private void eternalreturn$trackLeader(DataTracker.Builder builder, CallbackInfo ci) {
		builder.add(ETERNALRETURN$LEADER_ID, -1);
	}

	/**
	 * The extra steps: each one finds the rail under the cart again and moves it no further than vanilla would, so a
	 * fast cart takes curves and slopes one rail at a time; rails it passes over are touched (detector rails) and
	 * activator rails act.
	 */
	@WrapOperation(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;moveOnRail(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V"))
	private void eternalreturn$steps(AbstractMinecartEntity cart, BlockPos pos, BlockState state, Operation<Void> original) {
		this.eternalreturn$onRailThisTick = true;
		original.call(cart, pos, state);
		this.eternalreturn$climb = this.eternalreturn$climbing(state);
		int steps = state.isOf(CopperRail.BLOCK) ? 1 : Minecarts.steps();
		for (int step = 1; step < steps && !this.isRemoved(); step++) {
			BlockPos rail = Minecarts.railPos(this);
			BlockState at = this.getWorld().getBlockState(rail);
			if (!AbstractRailBlock.isRail(at) || at.isOf(CopperRail.BLOCK)) {
				break;
			}
			this.checkBlockCollision();
			original.call(cart, rail, at);
			this.eternalreturn$climb = this.eternalreturn$climbing(at);
			if (at.isOf(Blocks.ACTIVATOR_RAIL)) {
				cart.onActivatorRail(rail.getX(), rail.getY(), rail.getZ(), at.get(PoweredRailBlock.POWERED));
			}
		}
	}

	/** How fast a step along this rail carried the cart up: its speed along a slope rising the way it goes, else 0. */
	@Unique
	private double eternalreturn$climbing(BlockState state) {
		if (!(state.getBlock() instanceof AbstractRailBlock rail)) {
			return 0.0;
		}
		Vec3d velocity = this.getVelocity();
		double up = switch (state.get(rail.getShapeProperty())) {
			case ASCENDING_EAST -> velocity.x;
			case ASCENDING_WEST -> -velocity.x;
			case ASCENDING_SOUTH -> velocity.z;
			case ASCENDING_NORTH -> -velocity.z;
			default -> 0.0;
		};
		return Math.max(0.0, up);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void eternalreturn$pull(CallbackInfo ci) {
		if (!this.getWorld().isClient) {
			Minecarts.follow((AbstractMinecartEntity) (Object) this);
			if (this.eternalreturn$onRailThisTick) {
				this.eternalreturn$faceAlongRail();
			} else if (!this.isOnGround()) {
				this.eternalreturn$tiltInFlight();
			}
		}
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void eternalreturn$startTick(CallbackInfo ci) {
		this.eternalreturn$onRailThisTick = false;
	}

	/** Leaving the top of a slope it was climbing, the cart keeps climbing (up to vanilla's top speed) and arcs down. */
	@Inject(method = "moveOffRail", at = @At("HEAD"))
	private void eternalreturn$launch(CallbackInfo ci) {
		if (this.eternalreturn$climb > 0.0) {
			Vec3d velocity = this.getVelocity();
			this.setVelocity(velocity.x, Math.max(velocity.y, Math.min(this.eternalreturn$climb, ETERNALRETURN$MAX_LAUNCH)), velocity.z);
		}
		this.eternalreturn$climb = 0.0;
	}

	/**
	 * On a rail a cart is drawn along the rail; off it, by its own facing. A cart that hasn't moved since it was put
	 * on a straight rail may face across it, so it turned when the rail went: standing still, it faces along it.
	 */
	@Unique
	private void eternalreturn$faceAlongRail() {
		BlockState state = this.getWorld().getBlockState(Minecarts.railPos(this));
		if (!(state.getBlock() instanceof AbstractRailBlock rail) || this.getVelocity().horizontalLengthSquared() > 1.0E-4) {
			return;
		}
		RailShape shape = state.get(rail.getShapeProperty());
		float along = switch (shape) {
			case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> 90.0F;
			case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> 0.0F;
			default -> Float.NaN;
		};
		if (Float.isNaN(along)) {
			return;
		}
		float off = MathHelper.wrapDegrees(this.getYaw() - along);
		if (Math.abs(off) > 1.0F && Math.abs(off) < 179.0F) {
			this.setYaw(Math.abs(off) <= 90.0F ? along : along + 180.0F);
			this.prevYaw = this.getYaw();
		}
	}

	/** In the air the cart tilts the way it flies (vanilla keeps it level), easing into it. */
	@Unique
	private void eternalreturn$tiltInFlight() {
		Vec3d velocity = this.getVelocity();
		double across = velocity.horizontalLength();
		if (across < 1.0E-3) {
			return;
		}
		// The cart faces back the way it came unless flipped; the tilt is about that facing.
		float tilt = (float) (MathHelper.atan2(velocity.y, across) * MathHelper.DEGREES_PER_RADIAN) * (this.yawFlipped ? 1.0F : -1.0F);
		this.setPitch(this.prevPitch + (tilt - this.prevPitch) * 0.5F);
	}

	/** Positions arrive every tick (MinecartTrackingMixin): ease toward each over 3 ticks, not 5, so the cart keeps up. */
	@Inject(method = "updateTrackedPositionAndAngles", at = @At("TAIL"))
	private void eternalreturn$shorterSmoothing(double x, double y, double z, float yaw, float pitch, int interpolationSteps, CallbackInfo ci) {
		this.clientInterpolationSteps = Math.min(this.clientInterpolationSteps, 3);
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void eternalreturn$saveLeader(NbtCompound nbt, CallbackInfo ci) {
		if (this.eternalreturn$leader != null) {
			nbt.putUuid("eternalreturn_leader", this.eternalreturn$leader);
		}
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void eternalreturn$loadLeader(NbtCompound nbt, CallbackInfo ci) {
		this.eternalreturn$leader = nbt.containsUuid("eternalreturn_leader") ? nbt.getUuid("eternalreturn_leader") : null;
	}
}
