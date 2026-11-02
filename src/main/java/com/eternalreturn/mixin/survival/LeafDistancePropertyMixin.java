package com.eternalreturn.mixin.survival;

import com.eternalreturn.blocks.LeafDistance;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The leaves' distance property (Properties.DISTANCE_1_7) runs 1 to LeafDistance.MAX instead of 1 to 7. */
@Mixin(Properties.class)
public abstract class LeafDistancePropertyMixin {
	@Redirect(method = "<clinit>", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/state/property/IntProperty;of(Ljava/lang/String;II)Lnet/minecraft/state/property/IntProperty;"))
	private static IntProperty eternalreturn$leafDistance(String name, int min, int max) {
		return name.equals("distance") && min == 1 && max == 7 ? IntProperty.of(name, min, LeafDistance.MAX) : IntProperty.of(name, min, max);
	}
}
