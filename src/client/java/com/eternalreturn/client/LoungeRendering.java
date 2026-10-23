package com.eternalreturn.client;

import com.eternalreturn.mobs.ai.Lounging;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

/**
 * A lounging monster (Lounging) drawn sitting on the ground: legs out in front, hands on its knees, head bowed, its
 * body lowered so it sits on the floor.
 */
public final class LoungeRendering {
	/** How far a sitting biped's body drops (blocks, at full size): its legs are 12 pixels, the sitting hips about 2 up. */
	private static final float DROP = 0.6F;

	private LoungeRendering() {
	}

	/** After vanilla's turn of the body (setupTransforms): lowered to the floor. */
	public static void lower(LivingEntity entity, MatrixStack matrices, float scale) {
		if (Lounging.isLounging(entity)) {
			matrices.translate(0.0F, -DROP * scale, 0.0F);
		}
	}

	/** After the model's own posing (setAngles): the sitting pose. */
	public static void pose(LivingEntity entity, EntityModel<?> model, float animationProgress) {
		if (!(model instanceof BipedEntityModel<?> biped) || !Lounging.isLounging(entity)) {
			return;
		}
		biped.rightLeg.pitch = -1.4137167F;
		biped.rightLeg.yaw = 0.31415927F;
		biped.rightLeg.roll = 0.07853982F;
		biped.leftLeg.pitch = -1.4137167F;
		biped.leftLeg.yaw = -0.31415927F;
		biped.leftLeg.roll = -0.07853982F;
		float breath = MathHelper.sin(animationProgress * 0.05F) * 0.03F;
		biped.rightArm.pitch = -0.55F + breath;
		biped.rightArm.yaw = -0.1F;
		biped.rightArm.roll = 0.05F;
		biped.leftArm.pitch = -0.55F + breath;
		biped.leftArm.yaw = 0.1F;
		biped.leftArm.roll = -0.05F;
		biped.head.pitch = 0.75F + breath;
		biped.head.yaw = 0.0F;
		biped.head.roll = 0.0F;
		biped.hat.copyTransform(biped.head);
	}
}
