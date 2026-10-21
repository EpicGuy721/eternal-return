package com.eternalreturn.client;

import com.eternalreturn.mobs.climbing.Climber;
import com.eternalreturn.mobs.climbing.SurfaceWalker;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * Turning a surface walker's model (Climber) onto its wall or ceiling: in place of vanilla's turn to its body's yaw,
 * the climber's smoothed orientation, about the middle of its hitbox, and set against the surface (feet on it).
 */
public final class ClimberRendering {
	private ClimberRendering() {
	}

	@Nullable
	private static Climber climber(LivingEntity entity) {
		if (!(entity instanceof SurfaceWalker walker) || entity.deathTime > 0 || entity.isInPose(EntityPose.SLEEPING) || !Climber.enabled()) {
			return null;
		}
		Climber climber = walker.eternalreturn$climber();
		return climber.turned() ? climber : null;
	}

	/** Called where vanilla has just turned the model to its body's yaw. */
	public static void turn(LivingEntity entity, MatrixStack matrices, float bodyYaw, float tickDelta) {
		Climber climber = climber(entity);
		if (climber == null) {
			return;
		}
		// Undo vanilla's turn; the climber's orientation includes the heading.
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - bodyYaw).conjugate());
		float middle = entity.getHeight() * 0.5F;
		// Feet on the surface: on a wall the hitbox reaches half its width from the middle, the turned model half its height.
		Vec3d up = climber.up(tickDelta);
		float shift = (entity.getHeight() - entity.getWidth()) * 0.5F;
		matrices.translate(up.x * shift, middle, up.z * shift);
		matrices.multiply(climber.rotation(tickDelta));
		matrices.translate(0.0F, -middle, 0.0F);
	}

	/** The rotation from the model's frame (forward -z, up +y) to the world as drawn: vanilla's 180 - body yaw turn, or the climber's. */
	public static Quaternionf rotation(LivingEntity entity, float tickDelta) {
		Climber climber = climber(entity);
		if (climber != null) {
			return climber.rotation(tickDelta);
		}
		float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevBodyYaw, entity.bodyYaw);
		return new Quaternionf().rotationY((180.0F - bodyYaw) * MathHelper.RADIANS_PER_DEGREE);
	}

	/** How far it's turned onto a wall: 0 upright or upside down, 1 flat on a wall. */
	public static float wallness(LivingEntity entity, float tickDelta) {
		Climber climber = climber(entity);
		return climber == null ? 0.0F : climber.wallness(tickDelta);
	}
}
