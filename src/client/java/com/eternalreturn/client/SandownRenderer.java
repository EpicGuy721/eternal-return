package com.eternalreturn.client;

import com.eternalreturn.mobs.entity.SandownEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Sandown (SandownModel) and his laser blaster, its grip in his right hand: pointing ahead at his side, or along his
 * arm as he aims.
 */
public class SandownRenderer extends MobEntityRenderer<SandownEntity, SandownModel> {
	public SandownRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new SandownModel(ctx.getPart(SandownModel.LAYER)), 0.7F);
		this.addFeature(new BlasterFeature(this));
	}

	@Override
	public Identifier getTexture(SandownEntity entity) {
		return SandownModel.TEXTURE;
	}

	static class BlasterFeature extends FeatureRenderer<SandownEntity, SandownModel> {
		/** His blaster is bigger than a player's. */
		private static final float SCALE = 1.2F;
		/**
		 * Where the icon's grip is from its middle (blocks, at scale 1): low on the left. The icon's barrel points to its
		 * right (+x), its top up (+y), its face out of the screen (+z).
		 */
		private static final Vector3f ICON_GRIP = new Vector3f(-4.5F / 16.0F, -3.5F / 16.0F, 0.0F);

		BlasterFeature(FeatureRendererContext<SandownEntity, SandownModel> context) {
			super(context);
		}

		@Override
		public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, SandownEntity entity, float limbAngle, float limbDistance,
				float tickDelta, float animationProgress, float headYaw, float headPitch) {
			ItemStack blaster = entity.getMainHandStack();
			if (blaster.isEmpty()) {
				return;
			}
			ModelPart arm = this.getContextModel().rightArm;
			Quaternionf armTurn = new Quaternionf().rotationZYX(arm.roll, arm.yaw, arm.pitch);
			// In the model's space (blocks; y points down, the front is -z).
			Vector3f hand = armTurn.transform(new Vector3f(0.0F, SandownModel.HAND, 0.0F)).add(arm.pivotX, arm.pivotY, arm.pivotZ).div(16.0F);
			Vector3f barrel = entity.isAiming() ? armTurn.transform(new Vector3f(0.0F, 1.0F, 0.0F)) : new Vector3f(0.0F, 0.0F, -1.0F);
			barrel.normalize();
			// Its top as near up as it can be, square to the barrel.
			Vector3f up = new Vector3f(0.0F, -1.0F, 0.0F);
			Vector3f top = new Vector3f(up).sub(new Vector3f(barrel).mul(barrel.dot(up)));
			if (top.lengthSquared() < 1.0E-6F) {
				top.set(0.0F, 0.0F, -1.0F);
			}
			top.normalize();
			Vector3f face = new Vector3f(barrel).cross(top);
			Matrix3f turn = new Matrix3f().setColumn(0, barrel).setColumn(1, top).setColumn(2, face);
			Vector3f middle = turn.transform(new Vector3f(ICON_GRIP).mul(-SCALE)).add(hand);
			matrices.push();
			matrices.translate(middle.x, middle.y, middle.z);
			matrices.multiply(new Quaternionf().setFromNormalized(turn));
			matrices.scale(SCALE, SCALE, SCALE);
			MinecraftClient.getInstance().getItemRenderer().renderItem(entity, blaster, ModelTransformationMode.NONE, false, matrices, consumers,
					entity.getWorld(), light, OverlayTexture.DEFAULT_UV, entity.getId());
			matrices.pop();
		}
	}
}
