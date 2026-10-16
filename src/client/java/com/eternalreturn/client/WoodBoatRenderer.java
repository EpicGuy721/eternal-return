package com.eternalreturn.client;

import com.eternalreturn.EternalReturn;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BoatEntityModel;
import net.minecraft.client.render.entity.model.ChestBoatEntityModel;
import net.minecraft.client.render.entity.model.CompositeEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.ModelWithWaterPatch;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;

/** A boat of this mod's woods (WoodBoats): vanilla's boat model (an oak boat's shape) with the wood's texture. */
public class WoodBoatRenderer extends EntityRenderer<BoatEntity> {
	private final Identifier texture;
	private final CompositeEntityModel<BoatEntity> model;

	public WoodBoatRenderer(EntityRendererFactory.Context ctx, String wood, boolean chest) {
		super(ctx);
		this.shadowRadius = 0.8F;
		this.texture = EternalReturn.id("textures/entity/" + (chest ? "chest_boat/" : "boat/") + wood + ".png");
		this.model = chest ? new ChestBoatEntityModel(ctx.getPart(EntityModelLayers.createChestBoat(BoatEntity.Type.OAK)))
				: new BoatEntityModel(ctx.getPart(EntityModelLayers.createBoat(BoatEntity.Type.OAK)));
	}

	/** As vanilla's BoatEntityRenderer draws a boat. */
	@Override
	public void render(BoatEntity boat, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		matrices.push();
		matrices.translate(0.0F, 0.375F, 0.0F);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));
		float wobble = boat.getDamageWobbleTicks() - tickDelta;
		float strength = Math.max(0.0F, boat.getDamageWobbleStrength() - tickDelta);
		if (wobble > 0.0F) {
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.sin(wobble) * wobble * strength / 10.0F * boat.getDamageWobbleSide()));
		}
		float bubble = boat.interpolateBubbleWobble(tickDelta);
		if (!MathHelper.approximatelyEquals(bubble, 0.0F)) {
			matrices.multiply(new Quaternionf().setAngleAxis(bubble * (float) (Math.PI / 180.0), 1.0F, 0.0F, 1.0F));
		}
		matrices.scale(-1.0F, -1.0F, 1.0F);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
		this.model.setAngles(boat, tickDelta, 0.0F, -0.1F, 0.0F, 0.0F);
		VertexConsumer buffer = consumers.getBuffer(this.model.getLayer(this.texture));
		this.model.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV);
		if (!boat.isSubmergedInWater() && this.model instanceof ModelWithWaterPatch patched) {
			patched.getWaterPatch().render(matrices, consumers.getBuffer(RenderLayer.getWaterMask()), light, OverlayTexture.DEFAULT_UV);
		}
		matrices.pop();
		super.render(boat, yaw, tickDelta, matrices, consumers, light);
	}

	@Override
	public Identifier getTexture(BoatEntity boat) {
		return this.texture;
	}
}
