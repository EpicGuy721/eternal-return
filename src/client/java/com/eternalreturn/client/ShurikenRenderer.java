package com.eternalreturn.client;

import com.eternalreturn.items.Shuriken;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** A thrown shuriken (Shuriken) flies flat, spinning, not standing up facing the camera as other thrown items do. */
public class ShurikenRenderer extends EntityRenderer<Shuriken.Thrown> {
	/** Turns a tick. */
	private static final float SPIN = 50.0F;
	private final ItemRenderer items;

	public ShurikenRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.items = context.getItemRenderer();
	}

	@Override
	public void render(Shuriken.Thrown shuriken, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		if (shuriken.age >= 2 || !(this.dispatcher.camera.getFocusedEntity().squaredDistanceTo(shuriken) < 12.25)) {
			matrices.push();
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((shuriken.age + tickDelta) * SPIN));
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
			matrices.scale(0.5F, 0.5F, 0.5F);
			this.items.renderItem(shuriken.getStack(), ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV, matrices, vertexConsumers,
					shuriken.getWorld(), shuriken.getId());
			matrices.pop();
		}
		super.render(shuriken, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public Identifier getTexture(Shuriken.Thrown shuriken) {
		return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
	}
}
