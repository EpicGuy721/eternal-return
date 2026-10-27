package com.eternalreturn.mixin.tweaks;

import com.eternalreturn.logic.LightningGlass;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Where lightning strikes sand it leaves a small cluster of glass (LightningGlass). */
@Mixin(LightningEntity.class)
public abstract class LightningGlassMixin extends Entity {
	protected LightningGlassMixin(EntityType<?> type, World world) {
		super(type, world);
	}

	@Inject(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/LightningEntity;cleanOxidation(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V"))
	private void eternalreturn$fuseSand(CallbackInfo ci) {
		if (this.getWorld() instanceof ServerWorld world) {
			LightningGlass.strike(world, BlockPos.ofFloored(this.getX(), this.getY() - 1.0E-6, this.getZ()), this.random);
		}
	}
}
