package com.eternalreturn.client;

import com.eternalreturn.mobs.entity.AtropalEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

/** The atropal (AtropalModel) and its psionic beam from its head, drawn as the guardian's is, from dim violet to a pale glare as it charges. */
public class AtropalRenderer extends MobEntityRenderer<AtropalEntity, AtropalModel> {
	public AtropalRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new AtropalModel(ctx.getPart(AtropalModel.LAYER)), 0.3F);
	}

	@Override
	public Identifier getTexture(AtropalEntity atropal) {
		return AtropalModel.TEXTURE;
	}

	@Override
	public boolean shouldRender(AtropalEntity atropal, Frustum frustum, double x, double y, double z) {
		if (super.shouldRender(atropal, frustum, x, y, z)) {
			return true;
		}
		LivingEntity target = atropal.getBeamTarget();
		return target != null && GuardianBeam.visible(atropal, target, frustum);
	}

	@Override
	public void render(AtropalEntity atropal, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		super.render(atropal, yaw, tickDelta, matrices, consumers, light);
		LivingEntity target = atropal.getBeamTarget();
		if (target == null) {
			return;
		}
		float charge = atropal.getBeamProgress(tickDelta);
		charge *= charge;
		GuardianBeam.draw(atropal, target, atropal.getBeamTicks() + tickDelta, tickDelta, 70 + (int) (charge * 160.0F), 50 + (int) (charge * 150.0F),
				120 + (int) (charge * 135.0F), matrices, consumers);
	}
}
