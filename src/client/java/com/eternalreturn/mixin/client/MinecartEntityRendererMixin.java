package com.eternalreturn.mixin.client;

import com.eternalreturn.blocks.Minecarts;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.MinecartEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A chained minecart (Minecarts) draws its chain to the cart ahead: grey links, drawn the way leads are, nearly taut. */
@Mixin(MinecartEntityRenderer.class)
public abstract class MinecartEntityRendererMixin {
	@Unique
	private static final int SEGMENTS = 12;
	@Unique
	private static final float HEIGHT = 0.45F;
	@Unique
	private static final float WIDTH = 0.05F;

	@Inject(method = "render(Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"))
	private void eternalreturn$chain(AbstractMinecartEntity cart, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light,
			CallbackInfo ci) {
		int id = ((Minecarts.Linked) cart).eternalreturn$leaderId();
		Entity leader = id < 0 ? null : cart.getWorld().getEntityById(id);
		if (leader == null) {
			return;
		}
		Vec3d to = leader.getLerpedPos(tickDelta).subtract(cart.getLerpedPos(tickDelta));
		float dx = (float) to.x;
		float dy = (float) to.y;
		float dz = (float) to.z;
		float across = MathHelper.inverseSqrt(Math.max(dx * dx + dz * dz, 1.0E-4F)) * WIDTH / 2.0F;
		float ox = dz * across;
		float oz = dx * across;
		matrices.push();
		matrices.translate(0.0F, HEIGHT, 0.0F);
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLeash());
		for (int i = 0; i <= SEGMENTS; i++) {
			eternalreturn$segment(buffer, matrix, dx, dy, dz, light, WIDTH, WIDTH, ox, oz, i, false);
		}
		for (int i = SEGMENTS; i >= 0; i--) {
			eternalreturn$segment(buffer, matrix, dx, dy, dz, light, WIDTH, 0.0F, ox, oz, i, true);
		}
		matrices.pop();
	}

	@Unique
	private static void eternalreturn$segment(VertexConsumer buffer, Matrix4f matrix, float dx, float dy, float dz, int light, float f, float g, float ox, float oz,
			int i, boolean second) {
		float t = i / (float) SEGMENTS;
		float shade = i % 2 == (second ? 1 : 0) ? 0.55F : 0.8F;
		float r = 0.32F * shade;
		float gr = 0.34F * shade;
		float b = 0.4F * shade;
		float x = dx * t;
		float y = dy * t - 0.02F * MathHelper.sin(t * MathHelper.PI);
		float z = dz * t;
		buffer.vertex(matrix, x - ox, y + g, z + oz).color(r, gr, b, 1.0F).light(light);
		buffer.vertex(matrix, x + ox, y + f - g, z - oz).color(r, gr, b, 1.0F).light(light);
	}
}
