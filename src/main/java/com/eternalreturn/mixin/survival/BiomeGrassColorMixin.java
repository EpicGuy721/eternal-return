package com.eternalreturn.mixin.survival;

import com.eternalreturn.worldgen.biome.AutumnGrass;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeEffects;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The autumnal forest's grass varies from gold to orange and red across the ground (AutumnGrass). */
@Mixin(Biome.class)
public abstract class BiomeGrassColorMixin {
	@Shadow
	@Final
	private BiomeEffects effects;

	@Inject(method = "getGrassColorAt", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$autumn(double x, double z, CallbackInfoReturnable<Integer> cir) {
		if (this.effects.getGrassColor().filter(color -> color == AutumnGrass.GOLD).isPresent()) {
			cir.setReturnValue(AutumnGrass.colorAt(x, z));
		}
	}
}
