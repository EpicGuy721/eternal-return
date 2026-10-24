package com.eternalreturn.client;

import com.eternalreturn.config.EternalReturnConfig;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Damage numbers (survival.damageNumbers): when a creature within 32 blocks loses health, the amount rises from it
 * as a number and fades over a second and a half: white for a little, yellow for more, red for a lot. Read from the
 * health the game already sends, so it shows all damage, whoever dealt it, but not the player's own.
 */
public final class DamageNumbers {
	private static final int LIFE = 30;
	private static final double RANGE = 32.0;
	private static final Int2FloatMap HEALTH = new Int2FloatOpenHashMap();
	private static final List<Popup> POPUPS = new ArrayList<>();
	private static final Random RANDOM = Random.create();

	private record Popup(Vec3d pos, float amount, int[] age) {
	}

	private DamageNumbers() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(DamageNumbers::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(DamageNumbers::render);
	}

	private static void tick(MinecraftClient client) {
		POPUPS.removeIf(popup -> ++popup.age()[0] >= LIFE);
		if (client.world == null || client.player == null || !EternalReturnConfig.get().survival.damageNumbers) {
			HEALTH.clear();
			POPUPS.clear();
			return;
		}
		Int2FloatMap seen = new Int2FloatOpenHashMap();
		for (Entity entity : client.world.getEntities()) {
			if (!(entity instanceof LivingEntity living) || entity == client.player || entity.squaredDistanceTo(client.player) > RANGE * RANGE) {
				continue;
			}
			float health = living.getHealth();
			if (HEALTH.containsKey(entity.getId())) {
				float lost = HEALTH.get(entity.getId()) - health;
				if (lost > 0.05F) {
					Vec3d at = new Vec3d(entity.getX() + (RANDOM.nextDouble() - 0.5) * entity.getWidth(), entity.getY() + entity.getHeight() + 0.2,
							entity.getZ() + (RANDOM.nextDouble() - 0.5) * entity.getWidth());
					POPUPS.add(new Popup(at, lost, new int[]{0}));
				}
			}
			seen.put(entity.getId(), health);
		}
		HEALTH.clear();
		HEALTH.putAll(seen);
	}

	private static String text(float amount) {
		float rounded = Math.round(amount * 10.0F) / 10.0F;
		return rounded == (int) rounded ? Integer.toString((int) rounded) : Float.toString(rounded);
	}

	private static int colour(float amount) {
		return amount < 4.0F ? 0xFFFFFF : amount < 10.0F ? 0xFFE14D : 0xFF5555;
	}

	private static void render(WorldRenderContext context) {
		if (POPUPS.isEmpty() || context.matrixStack() == null) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		TextRenderer text = client.textRenderer;
		Camera camera = context.camera();
		MatrixStack matrices = context.matrixStack();
		VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
		float tickDelta = context.tickCounter().getTickDelta(true);
		for (Iterator<Popup> it = POPUPS.iterator(); it.hasNext(); ) {
			Popup popup = it.next();
			float age = (popup.age()[0] + tickDelta) / LIFE;
			Vec3d at = popup.pos().add(0.0, 0.7 * age, 0.0).subtract(camera.getPos());
			int alpha = MathHelper.clamp((int) (255 * (1.0F - age * age)), 8, 255);
			String label = text(popup.amount());
			matrices.push();
			matrices.translate(at.x, at.y, at.z);
			matrices.multiply(camera.getRotation());
			float scale = 0.025F * (1.0F + 0.25F * (1.0F - age));
			matrices.scale(scale, -scale, scale);
			text.draw(label, -text.getWidth(label) / 2.0F, 0.0F, alpha << 24 | colour(popup.amount()), true, matrices.peek().getPositionMatrix(), consumers,
					TextRenderer.TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
			matrices.pop();
		}
		consumers.draw();
	}
}
