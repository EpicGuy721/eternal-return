package com.eternalreturn.mixin.client;

import net.minecraft.client.render.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Leads are drawn twice as thick, as in 1.21.6 (Leads). */
@Mixin(EntityRenderer.class)
public abstract class LeashThicknessMixin {
	@ModifyConstant(method = "renderLeash", constant = @Constant(floatValue = 0.025F))
	private float eternalreturn$thickerLead(float thickness) {
		return thickness * 2.0F;
	}
}
