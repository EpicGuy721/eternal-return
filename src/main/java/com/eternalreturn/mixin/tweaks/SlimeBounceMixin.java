package com.eternalreturn.mixin.tweaks;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Slimes (and magma cubes) take no fall damage: they bounce back up, about two thirds as fast as they came down. */
@Mixin(SlimeEntity.class)
public abstract class SlimeBounceMixin extends MobEntity {
	/** Upward speed to bounce with next tick (the landing itself stops it dead first). */
	@Unique
	private double eternalreturn$bounce;

	protected SlimeBounceMixin(EntityType<? extends MobEntity> type, World world) {
		super(type, world);
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		if (fallDistance > 1.5F) {
			// The speed of a fall from that height (gravity 0.08 a tick, a little drag), two thirds of it back.
			this.eternalreturn$bounce = Math.min(1.2, MathHelper.sqrt(0.16F * fallDistance)) * 0.66;
		}
		return false;
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void eternalreturn$bounce(CallbackInfo ci) {
		if (this.eternalreturn$bounce > 0.0) {
			this.setVelocity(this.getVelocity().x, this.eternalreturn$bounce, this.getVelocity().z);
			this.velocityDirty = true;
			this.eternalreturn$bounce = 0.0;
		}
	}
}
