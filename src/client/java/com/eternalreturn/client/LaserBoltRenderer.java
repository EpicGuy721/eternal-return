package com.eternalreturn.client;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.items.LaserBlaster;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/** A laser bolt: a glowing red streak along its flight, two strips crossed so it shows from every side. */
public class LaserBoltRenderer extends EntityRenderer<LaserBlaster.Bolt> {
	private static final Identifier TEXTURE = EternalReturn.id("textures/entity/laser_bolt.png");
	private static final RenderLayer LAYER = RenderLayer.getEyes(TEXTURE);
	private static final float LENGTH = 1.2F;
	private static final float WIDTH = 0.16F;

	public LaserBoltRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public void render(LaserBlaster.Bolt bolt, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		Vec3d velocity = bolt.getVelocity();
		if (velocity.lengthSquared() > 1.0E-6) {
			matrices.push();
			// Its length along +z, turned to point where it flies.
			matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float) MathHelper.atan2(velocity.x, velocity.z)));
			matrices.multiply(RotationAxis.NEGATIVE_X.rotation((float) MathHelper.atan2(velocity.y, velocity.horizontalLength())));
			// Trailing behind where it is.
			matrices.translate(0.0F, 0.0F, -LENGTH * 0.5F);
			VertexConsumer buffer = consumers.getBuffer(LAYER);
			MatrixStack.Entry entry = matrices.peek();
			float h = LENGTH * 0.5F;
			float w = WIDTH * 0.5F;
			strip(buffer, entry, new float[][] {{0, -w, -h}, {0, w, -h}, {0, w, h}, {0, -w, h}});
			strip(buffer, entry, new float[][] {{-w, 0, -h}, {w, 0, -h}, {w, 0, h}, {-w, 0, h}});
			matrices.pop();
		}
		super.render(bolt, yaw, tickDelta, matrices, consumers, light);
	}

	/** A quad, both faces: the texture's width across it, its length along. */
	private static void strip(VertexConsumer buffer, MatrixStack.Entry entry, float[][] corners) {
		float[][] uv = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
		for (int side = 0; side < 2; side++) {
			for (int i = 0; i < 4; i++) {
				int k = side == 0 ? i : 3 - i;
				float[] c = corners[k];
				buffer.vertex(entry, c[0], c[1], c[2]).color(255, 255, 255, 255).texture(uv[k][1], uv[k][0]).overlay(OverlayTexture.DEFAULT_UV)
						.light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0.0F, 1.0F, 0.0F);
			}
		}
	}

	@Override
	public Identifier getTexture(LaserBlaster.Bolt bolt) {
		return TEXTURE;
	}
}
