package com.eternalreturn.client;

import com.eternalreturn.mobs.entity.ZombieHookEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** FishingBobberEntityRenderer, with the line drawn from a mob's hand instead of a player's. */
public class ZombieHookEntityRenderer extends EntityRenderer<ZombieHookEntity> {
	private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/fishing_hook.png");
	private static final RenderLayer LAYER = RenderLayer.getEntityCutout(TEXTURE);
	private static final int LINE_SEGMENTS = 16;

	public ZombieHookEntityRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public void render(ZombieHookEntity hook, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		matrices.push();
		matrices.push();
		matrices.scale(0.5F, 0.5F, 0.5F);
		matrices.multiply(this.dispatcher.getRotation());
		MatrixStack.Entry entry = matrices.peek();
		VertexConsumer quad = vertexConsumers.getBuffer(LAYER);
		vertex(quad, entry, light, 0.0F, 0, 0, 1);
		vertex(quad, entry, light, 1.0F, 0, 1, 1);
		vertex(quad, entry, light, 1.0F, 1, 1, 0);
		vertex(quad, entry, light, 0.0F, 1, 0, 0);
		matrices.pop();

		if (hook.getOwner() instanceof LivingEntity owner) {
			Vec3d hand = handPos(owner, tickDelta);
			Vec3d bobber = hook.getLerpedPos(tickDelta).add(0.0, 0.25, 0.0);
			float dx = (float) (hand.x - bobber.x);
			float dy = (float) (hand.y - bobber.y);
			float dz = (float) (hand.z - bobber.z);
			VertexConsumer line = vertexConsumers.getBuffer(RenderLayer.getLineStrip());
			MatrixStack.Entry lineEntry = matrices.peek();
			for (int i = 0; i <= LINE_SEGMENTS; i++) {
				lineVertex(dx, dy, dz, line, lineEntry, (float) i / LINE_SEGMENTS, (float) (i + 1) / LINE_SEGMENTS);
			}
		}
		matrices.pop();
		super.render(hook, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	/** The third-person branch of FishingBobberEntityRenderer.getHandPos, scaled down for baby zombies. */
	private static Vec3d handPos(LivingEntity owner, float tickDelta) {
		int side = owner.getMainArm() == Arm.RIGHT ? 1 : -1;
		if (!owner.getMainHandStack().isOf(Items.FISHING_ROD)) {
			side = -side;
		}
		float bodyYaw = MathHelper.lerp(tickDelta, owner.prevBodyYaw, owner.bodyYaw) * (float) (Math.PI / 180.0);
		double sin = MathHelper.sin(bodyYaw);
		double cos = MathHelper.cos(bodyYaw);
		float scale = owner.getScale() * owner.getScaleFactor();
		double sideways = side * 0.35 * scale;
		double forward = 0.8 * scale;
		return owner.getCameraPosVec(tickDelta).add(-cos * sideways - sin * forward, -0.45 * scale, -sin * sideways + cos * forward);
	}

	private static void vertex(VertexConsumer buffer, MatrixStack.Entry matrix, int light, float x, int y, int u, int v) {
		buffer.vertex(matrix, x - 0.5F, y - 0.5F, 0.0F)
				.color(Colors.WHITE)
				.texture(u, v)
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(light)
				.normal(matrix, 0.0F, 1.0F, 0.0F);
	}

	private static void lineVertex(float x, float y, float z, VertexConsumer buffer, MatrixStack.Entry matrices, float segmentStart, float segmentEnd) {
		float startX = x * segmentStart;
		float startY = y * (segmentStart * segmentStart + segmentStart) * 0.5F + 0.25F;
		float startZ = z * segmentStart;
		float nx = x * segmentEnd - startX;
		float ny = y * (segmentEnd * segmentEnd + segmentEnd) * 0.5F + 0.25F - startY;
		float nz = z * segmentEnd - startZ;
		float length = MathHelper.sqrt(nx * nx + ny * ny + nz * nz);
		buffer.vertex(matrices, startX, startY, startZ).color(Colors.BLACK).normal(matrices, nx / length, ny / length, nz / length);
	}

	@Override
	public Identifier getTexture(ZombieHookEntity hook) {
		return TEXTURE;
	}
}
