package com.eternalreturn.mixin.redstone;

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
 * Minecarts (com.eternalreturn.blocks.Minecarts): several of vanilla's steps a tick on rails, a shorter smoothing of
 * the position players see, and the chain to the cart ahead (saved as eternalreturn_leader, its entity id sent to
 * players to draw it).
 */
@Mixin(AbstractMinecartEntity.class)
public abstract class MinecartMixin extends VehicleEntity implements Minecarts.Linked {
	@Unique
	private static final TrackedData<Integer> ETERNALRETURN$LEADER_ID = DataTracker.registerData(AbstractMinecartEntity.class, TrackedDataHandlerRegistry.INTEGER);

	@Shadow
	private int clientInterpolationSteps;

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
		original.call(cart, pos, state);
		int steps = Minecarts.steps();
		for (int step = 1; step < steps && !this.isRemoved(); step++) {
			BlockPos rail = Minecarts.railPos(this);
			BlockState at = this.getWorld().getBlockState(rail);
			if (!AbstractRailBlock.isRail(at)) {
				break;
			}
			this.checkBlockCollision();
			original.call(cart, rail, at);
			if (at.isOf(Blocks.ACTIVATOR_RAIL)) {
				cart.onActivatorRail(rail.getX(), rail.getY(), rail.getZ(), at.get(PoweredRailBlock.POWERED));
			}
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void eternalreturn$pull(CallbackInfo ci) {
		if (!this.getWorld().isClient) {
			Minecarts.follow((AbstractMinecartEntity) (Object) this);
		}
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
