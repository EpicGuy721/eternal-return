package com.eternalreturn.mixin.tweaks;

import com.eternalreturn.logic.ArmorStandPoses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Armor stands as in Bedrock (ArmorStandPoses): arms always, poses stepped through by sneaking players or set by redstone. */
@Mixin(ArmorStandEntity.class)
public abstract class ArmorStandPosesMixin extends LivingEntity implements ArmorStandPoses.Holder {
	@Unique
	private int eternalreturn$pose;
	@Unique
	private int eternalreturn$power;

	protected ArmorStandPosesMixin(EntityType<? extends LivingEntity> type, World world) {
		super(type, world);
	}

	@Override
	public int eternalreturn$pose() {
		return this.eternalreturn$pose;
	}

	@Override
	public void eternalreturn$setPose(int pose) {
		this.eternalreturn$pose = pose;
	}

	@Override
	public int eternalreturn$power() {
		return this.eternalreturn$power;
	}

	@Override
	public void eternalreturn$setPower(int power) {
		this.eternalreturn$power = power;
	}

	@Inject(method = "shouldShowArms", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$arms(CallbackInfoReturnable<Boolean> cir) {
		if (ArmorStandPoses.enabled()) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "interactAt", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$stepPose(PlayerEntity player, Vec3d hitPos, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		if (!ArmorStandPoses.enabled() || !player.isSneaking() || player.isSpectator() || ((ArmorStandEntity) (Object) this).isMarker()) {
			return;
		}
		if (hand == Hand.MAIN_HAND && !this.getWorld().isClient) {
			ArmorStandPoses.step((ArmorStandEntity) (Object) this);
		}
		cir.setReturnValue(ActionResult.success(this.getWorld().isClient));
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void eternalreturn$redstonePose(CallbackInfo ci) {
		if (!this.getWorld().isClient && ArmorStandPoses.enabled()) {
			ArmorStandPoses.tick((ArmorStandEntity) (Object) this);
		}
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void eternalreturn$writePose(NbtCompound nbt, CallbackInfo ci) {
		nbt.putInt("EternalReturnPose", this.eternalreturn$pose);
		nbt.putInt("EternalReturnPower", this.eternalreturn$power);
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void eternalreturn$readPose(NbtCompound nbt, CallbackInfo ci) {
		this.eternalreturn$pose = nbt.getInt("EternalReturnPose");
		this.eternalreturn$power = nbt.getInt("EternalReturnPower");
	}
}
