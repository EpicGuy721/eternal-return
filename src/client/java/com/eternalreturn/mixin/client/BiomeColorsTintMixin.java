package com.eternalreturn.mixin.client;

import com.eternalreturn.client.personality.ClientPersonality;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.world.biome.ColorResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Grass and leaves tinted for their personality patch (ClientPersonality), however the colour is blended (Sodium too). */
@Mixin(BiomeColors.class)
public abstract class BiomeColorsTintMixin {
	@Shadow
	@Final
	@Mutable
	public static ColorResolver GRASS_COLOR;
	@Shadow
	@Final
	@Mutable
	public static ColorResolver FOLIAGE_COLOR;

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void eternalreturn$tint(CallbackInfo ci) {
		GRASS_COLOR = ClientPersonality.tinted(GRASS_COLOR);
		FOLIAGE_COLOR = ClientPersonality.tinted(FOLIAGE_COLOR);
	}
}
