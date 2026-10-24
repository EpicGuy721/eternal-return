package com.eternalreturn.mixin.enchantments;

import com.eternalreturn.logic.Telekinesis;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While a mob killed with a Telekinesis weapon drops its loot, the drops go to the killer (TelekinesisDropMixin). */
@Mixin(LivingEntity.class)
public abstract class TelekinesisMobMixin implements Telekinesis.Collected {
	@Unique
	@Nullable
	private PlayerEntity eternalreturn$collector;

	@Inject(method = "drop", at = @At("HEAD"))
	private void eternalreturn$collect(ServerWorld world, DamageSource source, CallbackInfo ci) {
		this.eternalreturn$collector = Telekinesis.collector(source.getAttacker(), source.getWeaponStack());
	}

	@Inject(method = "drop", at = @At("RETURN"))
	private void eternalreturn$collected(ServerWorld world, DamageSource source, CallbackInfo ci) {
		this.eternalreturn$collector = null;
	}

	@Override
	@Nullable
	public PlayerEntity eternalreturn$collector() {
		return this.eternalreturn$collector;
	}
}
