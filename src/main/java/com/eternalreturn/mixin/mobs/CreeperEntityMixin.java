package com.eternalreturn.mixin.mobs;

import com.eternalreturn.mobs.BabyVariants;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreeperEntity.class)
public abstract class CreeperEntityMixin extends HostileEntity {
	@Unique
	private static final TrackedData<Boolean> ETERNALRETURN_BABY = DataTracker.registerData(CreeperEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	protected CreeperEntityMixin(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
	}

	@Inject(method = "initDataTracker", at = @At("TAIL"))
	private void eternalreturn$trackBaby(DataTracker.Builder builder, CallbackInfo ci) {
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

	/**
	 * Shorter fuse for babies. Applied where the fuse is read rather than to the field, so the saved
	 * "Fuse" value stays the adult one and the client's swelling animation (which runs off the same
	 * reads) stays in step with the server.
	 */
	@ModifyExpressionValue(method = {"tick", "getClientFuseTime", "handleFallDamage"},
			at = @At(value = "FIELD", target = "Lnet/minecraft/entity/mob/CreeperEntity;fuseTime:I", opcode = Opcodes.GETFIELD))
	private int eternalreturn$babyFuse(int fuseTime) {
		return this.isBaby() ? BabyVariants.creeperFuse(fuseTime) : fuseTime;
	}

	@ModifyArg(method = "explode", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/World;createExplosion(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/world/World$ExplosionSourceType;)Lnet/minecraft/world/explosion/Explosion;"),
			index = 4)
	private float eternalreturn$babyExplosion(float power) {
		return this.isBaby() ? BabyVariants.creeperExplosionPower(power) : power;
	}
}
