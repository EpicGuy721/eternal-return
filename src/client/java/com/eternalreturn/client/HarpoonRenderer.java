package com.eternalreturn.client;

import com.eternalreturn.items.Speargun;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ProjectileEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * A harpoon: an arrow, with a line back to the hand of whoever shot it while it flies and while it's hooked in
 * something (drawn as a fishing line is).
 */
public class HarpoonRenderer extends ProjectileEntityRenderer<Speargun.Harpoon> {
	private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/projectiles/arrow.png");
	private static final int SEGMENTS = 16;

	public HarpoonRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public Identifier getTexture(Speargun.Harpoon harpoon) {
		return TEXTURE;
	}

	@Override
	public void render(Speargun.Harpoon harpoon, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		super.render(harpoon, yaw, tickDelta, matrices, consumers, light);
		if (!(harpoon.getOwner() instanceof LivingEntity owner) || harpoon.isStuck()) {
			return;
		}
		Vec3d hand = this.handPos(owner, tickDelta);
		Vec3d at = harpoon.getLerpedPos(tickDelta);
		float x = (float) (hand.x - at.x);
		float y = (float) (hand.y - at.y);
		float z = (float) (hand.z - at.z);
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLineStrip());
		MatrixStack.Entry entry = matrices.peek();
		for (int i = 0; i <= SEGMENTS; i++) {
			line(x, y, z, buffer, entry, i / (float) SEGMENTS, (i + 1) / (float) SEGMENTS);
		}
	}

	/** Where the hand holding the speargun is, as FishingBobberEntityRenderer finds a rod's. */
	private Vec3d handPos(LivingEntity owner, float tickDelta) {
		int side = owner.getMainArm() == Arm.RIGHT ? 1 : -1;
		if (!owner.getMainHandStack().isOf(Speargun.SPEARGUN)) {
			side = -side;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (owner == client.player && this.dispatcher.gameOptions.getPerspective().isFirstPerson()) {
			double scale = 960.0 / this.dispatcher.gameOptions.getFov().getValue().intValue();
			Vec3d offset = this.dispatcher.camera.getProjection().getPosition(side * 0.525F, -0.1F).multiply(scale);
			return owner.getCameraPosVec(tickDelta).add(offset);
		}
		float bodyYaw = MathHelper.lerp(tickDelta, owner.prevBodyYaw, owner.bodyYaw) * MathHelper.RADIANS_PER_DEGREE;
		double sin = MathHelper.sin(bodyYaw);
		double cos = MathHelper.cos(bodyYaw);
		float size = owner.getScale();
		double across = side * 0.35 * size;
		double ahead = 0.8 * size;
		float sneak = owner.isInSneakingPose() ? -0.1875F : 0.0F;
		return owner.getCameraPosVec(tickDelta).add(-cos * across - sin * ahead, sneak - 0.45 * size, -sin * across + cos * ahead);
	}

	/** One point of the line, sagging a little between its ends. */
	private static void line(float x, float y, float z, VertexConsumer buffer, MatrixStack.Entry entry, float start, float end) {
		float px = x * start;
		float py = y * (start * start + start) * 0.5F;
		float pz = z * start;
		float nx = x * end - px;
		float ny = y * (end * end + end) * 0.5F - py;
		float nz = z * end - pz;
		float length = MathHelper.sqrt(nx * nx + ny * ny + nz * nz);
		if (length > 1.0E-6F) {
			nx /= length;
			ny /= length;
			nz /= length;
		}
		buffer.vertex(entry, px, py, pz).color(Colors.BLACK).normal(entry, nx, ny, nz);
	}

	@Override
	public boolean shouldRender(Speargun.Harpoon harpoon, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
		// Its line can be on screen when it isn't.
		Entity owner = harpoon.getOwner();
		return super.shouldRender(harpoon, frustum, x, y, z) || owner != null && owner.squaredDistanceTo(harpoon) < Speargun.LINE_LENGTH * Speargun.LINE_LENGTH;
	}
}
