package com.eternalreturn.client;

import com.eternalreturn.mobs.entity.GimpEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** The gimp (GimpModel). On a wall its body tips up to lie flat against it, belly to the wall and head up, as it climbs. */
public class GimpRenderer extends MobEntityRenderer<GimpEntity, GimpModel> {
	/** Degrees the body tips up on a wall. */
	static final float CLIMB_TILT = 90.0F;
	/** The body tips about a point this high (blocks): the spider's middle. */
	private static final float TILT_HEIGHT = 0.55F;
	/** And moves this far forward, to bring its legs to the wall. */
	private static final float TILT_REACH = 0.15F;

	public GimpRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new GimpModel(ctx.getPart(GimpModel.LAYER)), 0.9F);
	}

	@Override
	public Identifier getTexture(GimpEntity gimp) {
		return GimpModel.TEXTURE;
	}

	/** Dies rolled onto its back, as a spider does. */
	@Override
	protected float getLyingAngle(GimpEntity gimp) {
		return 180.0F;
	}

	@Override
	protected void setupTransforms(GimpEntity gimp, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, float scale) {
		super.setupTransforms(gimp, matrices, animationProgress, bodyYaw, tickDelta, scale);
		float climb = gimp.getClimb(tickDelta);
		if (climb > 0.0F) {
			// Here the body faces -z with y up; tipping about x by +90 degrees turns its front up and its belly forward.
			matrices.translate(0.0F, TILT_HEIGHT, -TILT_REACH * climb);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(CLIMB_TILT * climb));
			matrices.translate(0.0F, -TILT_HEIGHT, 0.0F);
		}
	}
}
