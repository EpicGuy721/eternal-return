package com.eternalreturn.client;

import com.eternalreturn.mobs.entity.BeholderEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

/**
 * The beholder (BeholderModel, scaled up 4.5 times as the ghast is; its shooting texture, mouth open, as it spits a fireball) and its eye beam, drawn as the guardian's is but
 * from dull red to burning orange as it charges.
 */
public class BeholderRenderer extends MobEntityRenderer<BeholderEntity, BeholderModel<BeholderEntity>> {
	public BeholderRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new BeholderModel<>(ctx.getPart(BeholderModel.LAYER)), 1.5F);
	}

	@Override
	public Identifier getTexture(BeholderEntity entity) {
		return entity.isShooting() ? BeholderModel.SHOOTING_TEXTURE : BeholderModel.TEXTURE;
	}

	@Override
	protected void scale(BeholderEntity entity, MatrixStack matrices, float tickDelta) {
		matrices.scale(4.5F, 4.5F, 4.5F);
		// The model's body is centred 8 pixels up; this sets its bottom on the hitbox's floor.
		matrices.translate(0.0F, 0.5F, 0.0F);
	}

	@Override
	public boolean shouldRender(BeholderEntity entity, Frustum frustum, double x, double y, double z) {
		if (super.shouldRender(entity, frustum, x, y, z)) {
			return true;
		}
		LivingEntity target = entity.getBeamTarget();
		return target != null && GuardianBeam.visible(entity, target, frustum);
	}

	@Override
	public void render(BeholderEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
		LivingEntity target = entity.getBeamTarget();
		if (target == null) {
			return;
		}
		// From dull red to burning orange as it charges.
		float heat = entity.getBeamProgress(tickDelta) * entity.getBeamProgress(tickDelta);
		GuardianBeam.draw(entity, target, entity.getBeamTicks() + tickDelta, tickDelta, 120 + (int) (heat * 135.0F), 20 + (int) (heat * 120.0F),
				30 - (int) (heat * 20.0F), matrices, consumers);
	}
}
