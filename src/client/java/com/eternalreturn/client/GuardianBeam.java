package com.eternalreturn.client;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/** The guardian's beam (GuardianEntityRenderer) in any colour, from a mob's eyes to its target: the beholder's and the atropal's. */
final class GuardianBeam {
	private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/guardian_beam.png");
	private static final RenderLayer LAYER = RenderLayer.getEntityCutoutNoCull(TEXTURE);

	private GuardianBeam() {
	}

	static Vec3d lerped(LivingEntity entity, double yOffset, float delta) {
		return new Vec3d(MathHelper.lerp(delta, entity.lastRenderX, entity.getX()), MathHelper.lerp(delta, entity.lastRenderY, entity.getY()) + yOffset,
				MathHelper.lerp(delta, entity.lastRenderZ, entity.getZ()));
	}

	/** Whether a beam from the mob's eyes to the target is in view (to keep drawing it when the mob itself is not). */
	static boolean visible(LivingEntity mob, LivingEntity target, Frustum frustum) {
		Vec3d to = lerped(target, target.getHeight() * 0.5, 1.0F);
		Vec3d from = lerped(mob, mob.getStandingEyeHeight(), 1.0F);
		return frustum.isVisible(new Box(from.x, from.y, from.z, to.x, to.y, to.z));
	}

	/**
	 * Draws it, in the mob's render space (matrices at its feet), from its eyes to the middle of target; ticks scrolls and
	 * spins it.
	 */
	static void draw(LivingEntity mob, LivingEntity target, float ticks, float tickDelta, int red, int green, int blue, MatrixStack matrices,
			VertexConsumerProvider consumers) {
		float scroll = ticks * 0.5F % 1.0F;
		float eye = mob.getStandingEyeHeight();
		matrices.push();
		matrices.translate(0.0F, eye, 0.0F);
		Vec3d to = lerped(target, target.getHeight() * 0.5, tickDelta);
		Vec3d from = lerped(mob, eye, tickDelta);
		Vec3d line = to.subtract(from);
		float length = (float) (line.length() + 1.0);
		line = line.normalize();
		float pitch = (float) Math.acos(line.y);
		float turn = (float) Math.atan2(line.z, line.x);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((MathHelper.HALF_PI - turn) * MathHelper.DEGREES_PER_RADIAN));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch * MathHelper.DEGREES_PER_RADIAN));
		float spin = ticks * 0.05F * -1.5F;
		float x1 = MathHelper.cos(spin + MathHelper.PI * 0.75F) * 0.282F;
		float z1 = MathHelper.sin(spin + MathHelper.PI * 0.75F) * 0.282F;
		float x2 = MathHelper.cos(spin + MathHelper.PI * 0.25F) * 0.282F;
		float z2 = MathHelper.sin(spin + MathHelper.PI * 0.25F) * 0.282F;
		float x3 = MathHelper.cos(spin + MathHelper.PI * 1.25F) * 0.282F;
		float z3 = MathHelper.sin(spin + MathHelper.PI * 1.25F) * 0.282F;
		float x4 = MathHelper.cos(spin + MathHelper.PI * 1.75F) * 0.282F;
		float z4 = MathHelper.sin(spin + MathHelper.PI * 1.75F) * 0.282F;
		float x5 = MathHelper.cos(spin + MathHelper.PI) * 0.2F;
		float z5 = MathHelper.sin(spin + MathHelper.PI) * 0.2F;
		float x6 = MathHelper.cos(spin) * 0.2F;
		float z6 = MathHelper.sin(spin) * 0.2F;
		float x7 = MathHelper.cos(spin + MathHelper.HALF_PI) * 0.2F;
		float z7 = MathHelper.sin(spin + MathHelper.HALF_PI) * 0.2F;
		float x8 = MathHelper.cos(spin + MathHelper.PI * 1.5F) * 0.2F;
		float z8 = MathHelper.sin(spin + MathHelper.PI * 1.5F) * 0.2F;
		float v0 = -1.0F + scroll;
		float v1 = length * 2.5F + v0;
		VertexConsumer buffer = consumers.getBuffer(LAYER);
		MatrixStack.Entry entry = matrices.peek();
		vertex(buffer, entry, x5, length, z5, red, green, blue, 0.4999F, v1);
		vertex(buffer, entry, x5, 0.0F, z5, red, green, blue, 0.4999F, v0);
		vertex(buffer, entry, x6, 0.0F, z6, red, green, blue, 0.0F, v0);
		vertex(buffer, entry, x6, length, z6, red, green, blue, 0.0F, v1);
		vertex(buffer, entry, x7, length, z7, red, green, blue, 0.4999F, v1);
		vertex(buffer, entry, x7, 0.0F, z7, red, green, blue, 0.4999F, v0);
		vertex(buffer, entry, x8, 0.0F, z8, red, green, blue, 0.0F, v0);
		vertex(buffer, entry, x8, length, z8, red, green, blue, 0.0F, v1);
		float cap = mob.age % 2 == 0 ? 0.5F : 0.0F;
		vertex(buffer, entry, x1, length, z1, red, green, blue, 0.5F, cap + 0.5F);
		vertex(buffer, entry, x2, length, z2, red, green, blue, 1.0F, cap + 0.5F);
		vertex(buffer, entry, x4, length, z4, red, green, blue, 1.0F, cap);
		vertex(buffer, entry, x3, length, z3, red, green, blue, 0.5F, cap);
		matrices.pop();
	}

	private static void vertex(VertexConsumer buffer, MatrixStack.Entry entry, float x, float y, float z, int red, int green, int blue, float u, float v) {
		buffer.vertex(entry, x, y, z).color(red, green, blue, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
				.light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0.0F, 1.0F, 0.0F);
	}
}
