package com.eternalreturn.mixin.mobs;

import com.eternalreturn.mobs.NewMobs;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A zombie stuck in powder snow for 15 seconds turns into a frostbite (NewMobs), as a skeleton turns into a stray. */
@Mixin(ZombieEntity.class)
public abstract class ZombieFrostbiteMixin extends HostileEntity {
	@Unique
	private int eternalreturn$snowTicks;

	protected ZombieFrostbiteMixin(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void eternalreturn$freezeOver(CallbackInfo ci) {
		if (this.getWorld().isClient || this.getType() != EntityType.ZOMBIE || !this.isAlive() || this.isAiDisabled()) {
			return;
		}
		this.eternalreturn$snowTicks = this.inPowderSnow ? this.eternalreturn$snowTicks + 1 : 0;
		if (this.eternalreturn$snowTicks >= 300) {
			this.convertTo(NewMobs.FROSTBITE, true);
			this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.ENTITY_SKELETON_CONVERTED_TO_STRAY, this.getSoundCategory(), 1.0F, 0.8F);
		}
	}
}
