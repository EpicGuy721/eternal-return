package com.eternalreturn.mixin.mobs;

import com.eternalreturn.mobs.climbing.Climber;
import com.eternalreturn.mobs.climbing.SurfaceWalker;
import com.eternalreturn.mobs.climbing.SurfaceWanderGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spiders (cave spiders and bone spiders too) walk on walls and ceilings (Climber) in place of vanilla's ladder-style
 * climbing. The side they cling to rides in the spider's own flags byte, above vanilla's climbing bit.
 */
@Mixin(SpiderEntity.class)
public abstract class SpiderClimbingMixin extends HostileEntity implements SurfaceWalker {
	@Shadow
	@Final
	private static TrackedData<Byte> SPIDER_FLAGS;
	@Unique
	private Climber eternalreturn$climber;

	protected SpiderClimbingMixin(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
	}

	@Override
	public Climber eternalreturn$climber() {
		if (this.eternalreturn$climber == null) {
			this.eternalreturn$climber = new Climber(this, () -> (byte) ((this.dataTracker.get(SPIDER_FLAGS) >> 1) & 7),
					side -> this.dataTracker.set(SPIDER_FLAGS, (byte) ((this.dataTracker.get(SPIDER_FLAGS) & 1) | (side << 1))));
		}
		return this.eternalreturn$climber;
	}

	@Inject(method = "initGoals", at = @At("TAIL"))
	private void eternalreturn$wanderSurfaces(CallbackInfo ci) {
		this.goalSelector.add(4, new SurfaceWanderGoal(this));
	}

	/** Vanilla's climbing (straight up whatever it bumps into) gives way to walking on the wall. */
	@Inject(method = "isClimbing", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$noLadderClimb(CallbackInfoReturnable<Boolean> cir) {
		if (Climber.enabled()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void eternalreturn$climb(CallbackInfo ci) {
		this.eternalreturn$climber().tick();
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		this.eternalreturn$climber().writeNbt(nbt);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.eternalreturn$climber().readNbt(nbt);
	}
}
