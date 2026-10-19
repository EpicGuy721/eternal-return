package com.eternalreturn.client;

import com.eternalreturn.mobs.entity.SoldierEntity;
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
 * The soldier (SoldierModel) and his rifle, held in both hands: at the ready the flat rifle icon lies from his right hand
 * through his left, across his chest; aiming, it points along his raised arms, between his hands.
 */
public class SoldierRenderer extends MobEntityRenderer<SoldierEntity, SoldierModel> {
	public SoldierRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new SoldierModel(ctx.getPart(SoldierModel.LAYER)), 0.5F);
		this.addFeature(new RifleFeature(this));
	}

	@Override
	public Identifier getTexture(SoldierEntity entity) {
		return SoldierModel.TEXTURE;
	}

	static class RifleFeature extends FeatureRenderer<SoldierEntity, SoldierModel> {
		/** The icon's barrel points to its top left; its top (sights, bolt) to the top right; its face out of the screen. */
		private static final Vector3f ICON_BARREL = new Vector3f(-1.0F, 1.0F, 0.0F).normalize();
		private static final Vector3f ICON_FACE = new Vector3f(0.0F, 0.0F, 1.0F);
		private static final Vector3f ICON_TOP = new Vector3f(ICON_BARREL).cross(ICON_FACE);
		/** How far ahead of the gripping hand (in blocks, along the barrel) the icon's middle lies: the hand holds the stock's grip. */
		private static final float GRIP = 0.3F;

		RifleFeature(FeatureRendererContext<SoldierEntity, SoldierModel> context) {
			super(context);
		}

		/** Where an arm's hand is, in the model's space (blocks; y points down). */
		private static Vector3f hand(ModelPart arm, float side) {
			Vector3f point = new Quaternionf().rotationZYX(arm.roll, arm.yaw, arm.pitch).transform(new Vector3f(side, 10.0F, 0.0F));
			return point.add(arm.pivotX, arm.pivotY, arm.pivotZ).div(16.0F);
		}

		@Override
		public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, SoldierEntity entity, float limbAngle, float limbDistance,
				float tickDelta, float animationProgress, float headYaw, float headPitch) {
			ItemStack rifle = entity.getMainHandStack();
			if (rifle.isEmpty()) {
				return;
			}
			SoldierModel model = this.getContextModel();
			Vector3f right = hand(model.rightArm, 0.5F);
			Vector3f left = hand(model.leftArm, -0.5F);
			Vector3f barrel;
			Vector3f grip;
			if (entity.isAiming()) {
				ModelPart arm = model.rightArm;
				barrel = new Quaternionf().rotationZYX(arm.roll, arm.yaw, arm.pitch).transform(new Vector3f(0.0F, 1.0F, 0.0F));
				grip = new Vector3f(right).add(left).mul(0.5F);
			} else {
				barrel = new Vector3f(left).sub(right);
				grip = right;
			}
			if (barrel.lengthSquared() < 1.0E-6F) {
				return;
			}
			barrel.normalize();
			// The rifle's side faces out (its face square to the barrel and to up, model up being -y), its top up.
			Vector3f face = new Vector3f(0.0F, -1.0F, 0.0F).cross(barrel);
			if (face.lengthSquared() < 1.0E-6F) {
				face.set(1.0F, 0.0F, 0.0F);
			}
			face.normalize();
			Vector3f top = new Vector3f(barrel).cross(face);
			// The turn that takes the icon's barrel, face and top onto these.
			Matrix3f to = new Matrix3f().setColumn(0, barrel).setColumn(1, face).setColumn(2, top);
			Matrix3f from = new Matrix3f().setColumn(0, ICON_BARREL).setColumn(1, ICON_FACE).setColumn(2, ICON_TOP);
			Quaternionf turn = new Quaternionf().setFromNormalized(to.mul(from.transpose()));
			Vector3f middle = new Vector3f(barrel).mul(GRIP).add(grip);
			matrices.push();
			matrices.translate(middle.x, middle.y, middle.z);
			matrices.multiply(turn);
			MinecraftClient.getInstance().getItemRenderer().renderItem(entity, rifle, ModelTransformationMode.NONE, false, matrices, consumers,
					entity.getWorld(), light, OverlayTexture.DEFAULT_UV, entity.getId());
			matrices.pop();
		}
	}
}
