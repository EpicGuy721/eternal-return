package com.eternalreturn.client;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.mobs.entity.BeholderEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.animation.AnimationHelper;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The beholder: a ghast-sized cube with one great eye and a fanged mouth painted on its front, and eight eyestalks (four on top, two on
 * each side) of three segments each, ending in an eyeball. The great eye's iris slides, and each eyeball turns its pupil (its top
 * face), toward the player it hunts (its beam target, else the nearest player within 32 blocks). Made in tools/models/beholder.bbmodel (the shooting texture is its hidden "shooting" layer); like the
 * ghast's, it is meant to be scaled up by its renderer. Always plays BeholderAnimations.IDLE.
 */
public class BeholderModel<T extends Entity> extends SinglePartEntityModel<T> {
	private static final List<String> STALKS = List.of("stalk_top_front_right", "stalk_top_front_left", "stalk_top_back_right",
			"stalk_top_back_left", "stalk_side_upper_right", "stalk_side_upper_left", "stalk_side_lower_right", "stalk_side_lower_left");
	public static final EntityModelLayer LAYER = new EntityModelLayer(EternalReturn.id("beholder"), "main");
	public static final Identifier TEXTURE = EternalReturn.id("textures/entity/beholder/beholder.png");
	/** Mouth open on a fireball, and the great eye bloodshot (as the ghast has ghast_shooting.png). */
	public static final Identifier SHOOTING_TEXTURE = EternalReturn.id("textures/entity/beholder/beholder_shooting.png");
	/** How far the great eye's iris can move from the middle of its white, sideways and up or down, in pixels. */
	private static final float IRIS_REACH_X = 3.0F;
	private static final float IRIS_REACH_Y = 1.5F;
	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart eye;
	/** Each eyestalk: its three segments, then its eyeball. */
	private final List<ModelPart[]> stalks = new ArrayList<>();
	private final Vector3f animationVector = new Vector3f();

	public BeholderModel(ModelPart root) {
		this.root = root;
		this.body = root.getChild("body");
		this.eye = this.body.getChild("eye");
		for (String name : STALKS) {
			ModelPart base = this.body.getChild(name + "_1");
			ModelPart middle = base.getChild(name + "_2");
			ModelPart tip = middle.getChild(name + "_3");
			this.stalks.add(new ModelPart[] {base, middle, tip, tip.getChild(name + "_eye")});
		}
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData body = data.getRoot().addChild("body", ModelPartBuilder.create().uv(0, 0).cuboid(-8.0F, -8.0F, -8.0F, 16.0F, 16.0F, 16.0F),
				ModelTransform.pivot(0.0F, 8.0F, 0.0F));
		// The great eye's iris: a flat square just in front of the white painted on the face, free to slide around it.
		body.addChild("eye", ModelPartBuilder.create().uv(80, 0).cuboid(-2.0F, -2.0F, -0.1F, 4.0F, 4.0F, 0.0F), ModelTransform.pivot(0.0F, -3.5F, -8.0F));
		// Top: the front pair leans forward and bends back up, the back pair leans back and bends forward.
		addStalk(body, "stalk_top_front_right", new int[] {32, 48, 48, 56, 56, 54, 0, 32}, -4.0F, -8.0F, -4.0F, 20.0F, -20.0F, -10.0F, 0.0F);
		addStalk(body, "stalk_top_front_left", new int[] {48, 32, 56, 48, 60, 32, 16, 32}, 4.0F, -8.0F, -4.0F, 20.0F, 20.0F, -10.0F, 0.0F);
		addStalk(body, "stalk_top_back_right", new int[] {48, 40, 60, 38, 48, 62, 32, 32}, -3.0F, -8.0F, 4.0F, -25.0F, -12.0F, 15.0F, 0.0F);
		addStalk(body, "stalk_top_back_left", new int[] {44, 48, 56, 60, 0, 64, 0, 40}, 3.0F, -8.0F, 4.0F, -25.0F, 12.0F, 15.0F, 0.0F);
		// Sides: the upper pair reaches out and up, the lower pair out and down, curling up at the end.
		addStalk(body, "stalk_side_upper_right", new int[] {0, 56, 64, 0, 8, 64, 16, 40}, -8.0F, -3.0F, -3.0F, 0.0F, -65.0F, 0.0F, 20.0F);
		addStalk(body, "stalk_side_upper_left", new int[] {12, 56, 64, 6, 64, 12, 32, 40}, 8.0F, -3.0F, -3.0F, 0.0F, 65.0F, 0.0F, -20.0F);
		addStalk(body, "stalk_side_lower_right", new int[] {24, 56, 16, 64, 24, 64, 0, 48}, -8.0F, 4.0F, 3.0F, 0.0F, -110.0F, 0.0F, 30.0F);
		addStalk(body, "stalk_side_lower_left", new int[] {36, 56, 64, 18, 64, 24, 16, 48}, 8.0F, 4.0F, 3.0F, 0.0F, 110.0F, 0.0F, -30.0F);
		return TexturedModelData.of(data, 128, 128);
	}

	/**
	 * One eyestalk named name_1, _2, _3 and _eye: a thick base segment at (x, y, z) tilted by pitch and roll, two thinner segments each
	 * bent by bendPitch and bendRoll at the end of the one before, and an eyeball at the tip. Angles in degrees; uv holds the texture
	 * corner of each of the four parts.
	 */
	private static void addStalk(ModelPartData body, String name, int[] uv, float x, float y, float z, float pitch, float roll, float bendPitch,
			float bendRoll) {
		ModelTransform bend = ModelTransform.of(0.0F, -3.0F, 0.0F, bendPitch * MathHelper.RADIANS_PER_DEGREE, 0.0F, bendRoll * MathHelper.RADIANS_PER_DEGREE);
		ModelPartData base = body.addChild(name + "_1", ModelPartBuilder.create().uv(uv[0], uv[1]).cuboid(-1.5F, -4.0F, -1.5F, 3.0F, 5.0F, 3.0F),
				ModelTransform.of(x, y, z, pitch * MathHelper.RADIANS_PER_DEGREE, 0.0F, roll * MathHelper.RADIANS_PER_DEGREE));
		ModelPartData middle = base.addChild(name + "_2", ModelPartBuilder.create().uv(uv[2], uv[3]).cuboid(-1.0F, -3.0F, -1.0F, 2.0F, 4.0F, 2.0F),
				ModelTransform.of(0.0F, -4.0F, 0.0F, bend.pitch, 0.0F, bend.roll));
		ModelPartData tip = middle.addChild(name + "_3", ModelPartBuilder.create().uv(uv[4], uv[5]).cuboid(-1.0F, -3.0F, -1.0F, 2.0F, 4.0F, 2.0F), bend);
		tip.addChild(name + "_eye", ModelPartBuilder.create().uv(uv[6], uv[7]).cuboid(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F), ModelTransform.pivot(0.0F, -4.0F, 0.0F));
	}

	@Override
	public void setAngles(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		this.root.traverse().forEach(ModelPart::resetTransform);
		// animationProgress is in ticks; the animation wants milliseconds.
		AnimationHelper.animate(this, BeholderAnimations.IDLE, (long) (animationProgress * 50.0F), 1.0F, this.animationVector);
		Vector3f look = entity instanceof BeholderEntity beholder ? lookDirection(beholder, animationProgress - beholder.age) : null;
		if (look == null) {
			return;
		}
		// The iris slides toward it: all the way at 45 degrees aside or 30 up or down, kept inside the white's oval.
		Vector3f inBody = toLocal(new Vector3f(look), this.body);
		float aside = MathHelper.clamp((float) Math.atan2(inBody.x, -inBody.z) * MathHelper.DEGREES_PER_RADIAN / 45.0F, -1.0F, 1.0F);
		float lift = MathHelper.clamp((float) Math.atan2(inBody.y, Math.sqrt(inBody.x * inBody.x + inBody.z * inBody.z)) * MathHelper.DEGREES_PER_RADIAN / 30.0F,
				-1.0F, 1.0F);
		float length = MathHelper.sqrt(aside * aside + lift * lift);
		if (length > 1.0F) {
			aside /= length;
			lift /= length;
		}
		this.eye.pivotX += aside * IRIS_REACH_X;
		this.eye.pivotY += lift * IRIS_REACH_Y;
		// Each eyeball turns its top (its pupil, model up: -y) to face it, wherever the swaying stalk has put it.
		for (ModelPart[] stalk : this.stalks) {
			Vector3f local = toLocal(toLocal(toLocal(new Vector3f(inBody), stalk[0]), stalk[1]), stalk[2]).normalize();
			ModelPart eyeball = stalk[3];
			eyeball.pitch = (float) Math.acos(MathHelper.clamp(-local.y, -1.0F, 1.0F));
			eyeball.yaw = (float) Math.atan2(-local.x, -local.z);
			eyeball.roll = 0.0F;
		}
	}

	/**
	 * Where the beholder looks, in model space (the model faces -z, y points down): at its beam target, else the nearest player
	 * within 32 blocks; null if there's none.
	 */
	private static Vector3f lookDirection(BeholderEntity beholder, float tickDelta) {
		LivingEntity target = beholder.getBeamTarget();
		if (target == null) {
			target = beholder.getWorld().getClosestPlayer(beholder, 32.0);
		}
		if (target == null) {
			return null;
		}
		Vec3d from = beholder.getLerpedPos(tickDelta).add(0.0, beholder.getStandingEyeHeight(), 0.0);
		Vec3d to = target.getLerpedPos(tickDelta).add(0.0, target.getStandingEyeHeight(), 0.0);
		Vec3d world = to.subtract(from);
		if (world.lengthSquared() < 1.0E-4) {
			return null;
		}
		// Undo the renderer's turn (180 - body yaw about y) and its flip of x and y.
		float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, beholder.prevBodyYaw, beholder.bodyYaw);
		Vector3f turned = new Quaternionf().rotationY((180.0F - bodyYaw) * MathHelper.RADIANS_PER_DEGREE).conjugate()
				.transform(new Vector3f((float) world.x, (float) world.y, (float) world.z));
		return new Vector3f(-turned.x, -turned.y, turned.z).normalize();
	}

	/** A direction in a part's parent space, in the part's own (undoing its rotation, which ModelPart applies as z, then y, then x). */
	private static Vector3f toLocal(Vector3f direction, ModelPart part) {
		return new Quaternionf().rotationZYX(part.roll, part.yaw, part.pitch).conjugate().transform(direction);
	}

	@Override
	public ModelPart getPart() {
		return this.root;
	}
}
