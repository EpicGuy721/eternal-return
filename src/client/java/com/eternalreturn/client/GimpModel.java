package com.eternalreturn.client;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.mobs.entity.GimpEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The gimp: a man's torso, arms and hooded head (player-sized) rising from a spider's body, with a big abdomen behind
 * and eight jointed legs whose knees rise above the body. Made in tools/models/gimp.bbmodel (parts written from it).
 * The spider's legs walk as a spider's do; the man hunches a little, holds his fists up while he fights and punches
 * with his right. His pupils (each its own part in an eye hole) slide toward the nearest player. Climbing a wall
 * (GimpRenderer lays the body flat against it), he bends upright at the waist and turns his face out from the wall.
 */
public class GimpModel extends SinglePartEntityModel<GimpEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(EternalReturn.id("gimp"), "main");
	public static final Identifier TEXTURE = EternalReturn.id("textures/entity/gimp.png");
	/** How far a pupil moves from the middle of its eye hole, in pixels. */
	private static final float PUPIL_REACH = 0.5F;
	private static final float HUNCH = 0.12F;
	/** On a wall: how far the man bends up from the spider's back. */
	private static final float CLIMB_BEND = 30.0F * MathHelper.RADIANS_PER_DEGREE;
	/** The spider's legs, front to back, as a spider's front, middle front, middle and hind legs: their step phases (times pi). */
	private static final float[] LEG_PHASES = {1.5F, 0.5F, 1.0F, 0.0F};
	private final ModelPart root;
	private final ModelPart torso;
	private final ModelPart head;
	private final ModelPart rightEye;
	private final ModelPart leftEye;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	/** Front to back: each leg's turn (yaw) part and its thigh (roll, lifting it). */
	private final ModelPart[] rightLegs = new ModelPart[4];
	private final ModelPart[] leftLegs = new ModelPart[4];
	private final ModelPart[] rightThighs = new ModelPart[4];
	private final ModelPart[] leftThighs = new ModelPart[4];

	public GimpModel(ModelPart root) {
		this.root = root;
		this.torso = root.getChild("torso");
		this.head = this.torso.getChild("head");
		this.rightEye = this.head.getChild("right_eye");
		this.leftEye = this.head.getChild("left_eye");
		this.rightArm = this.torso.getChild("right_arm");
		this.leftArm = this.torso.getChild("left_arm");
		for (int i = 0; i < 4; i++) {
			this.rightLegs[i] = root.getChild("leg_r" + (i + 1));
			this.leftLegs[i] = root.getChild("leg_l" + (i + 1));
			this.rightThighs[i] = this.rightLegs[i].getChild("thigh_r" + (i + 1));
			this.leftThighs[i] = this.leftLegs[i].getChild("thigh_l" + (i + 1));
		}
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData torso = root.addChild("torso", ModelPartBuilder.create()
					// torso_cube
					.uv(32, 0).cuboid(-4.0F, -12.0F, -2.0F, 8.0F, 12.0F, 4.0F)
					// collar
					.uv(88, 0).cuboid(-4.0F, -12.0F, -2.0F, 8.0F, 2.0F, 4.0F, new Dilation(0.35F))
					// d_ring
					.uv(112, 0).cuboid(-1.0F, -10.6F, -2.4F, 2.0F, 2.0F, 0.0F),
				ModelTransform.pivot(0.0F, 11.0F, 0.0F));
		ModelPartData head = torso.addChild("head", ModelPartBuilder.create()
					// head_cube
					.uv(0, 0).cuboid(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
				ModelTransform.pivot(0.0F, -12.0F, 0.0F));
		head.addChild("right_eye", ModelPartBuilder.create()
					// right_iris
					.uv(116, 0).cuboid(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 0.0F),
				ModelTransform.pivot(-2.0F, -4.0F, -4.05F));
		head.addChild("left_eye", ModelPartBuilder.create()
					// left_iris
					.uv(118, 0).cuboid(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 0.0F),
				ModelTransform.pivot(2.0F, -4.0F, -4.05F));
		torso.addChild("right_arm", ModelPartBuilder.create()
					// right_arm_cube
					.uv(56, 0).cuboid(-2.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F)
					// right_cuff
					.uv(88, 6).cuboid(-2.0F, 8.0F, -2.0F, 4.0F, 2.0F, 4.0F, new Dilation(0.3F)),
				ModelTransform.pivot(-6.0F, -10.0F, 0.0F));
		torso.addChild("left_arm", ModelPartBuilder.create()
					// left_arm_cube
					.uv(72, 0).cuboid(-2.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F)
					// left_cuff
					.uv(104, 6).cuboid(-2.0F, 8.0F, -2.0F, 4.0F, 2.0F, 4.0F, new Dilation(0.3F)),
				ModelTransform.pivot(6.0F, -10.0F, 0.0F));
		root.addChild("thorax", ModelPartBuilder.create()
					// thorax_cube
					.uv(0, 16).cuboid(-5.0F, -4.0F, -4.0F, 10.0F, 7.0F, 8.0F),
				ModelTransform.pivot(0.0F, 15.0F, 0.0F));
		root.addChild("abdomen", ModelPartBuilder.create()
					// abdomen_cube
					.uv(36, 16).cuboid(-6.0F, -4.0F, 0.0F, 12.0F, 10.0F, 14.0F),
				ModelTransform.of(0.0F, 13.0F, 4.0F, 0.2618F, 0.0F, 0.0F));
		ModelPartData leg_r1 = root.addChild("leg_r1", ModelPartBuilder.create(),
				ModelTransform.of(-5.0F, 16.0F, -3.0F, 0.0F, -0.6981F, 0.0F));
		ModelPartData thigh_r1 = leg_r1.addChild("thigh_r1", ModelPartBuilder.create()
					// thigh_r1_cube
					.uv(88, 16).cuboid(-12.0F, -1.0F, -1.0F, 12.0F, 2.0F, 2.0F),
				ModelTransform.of(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.7854F));
		thigh_r1.addChild("shin_r1", ModelPartBuilder.create()
					// shin_r1_cube
					.uv(88, 20).cuboid(-17.0F, -1.0F, -1.0F, 17.0F, 2.0F, 2.0F),
				ModelTransform.of(-12.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.9199F));
		ModelPartData leg_l1 = root.addChild("leg_l1", ModelPartBuilder.create(),
				ModelTransform.of(5.0F, 16.0F, -3.0F, 0.0F, 0.6981F, 0.0F));
		ModelPartData thigh_l1 = leg_l1.addChild("thigh_l1", ModelPartBuilder.create()
					// thigh_l1_cube
					.uv(88, 16).mirrored().cuboid(0.0F, -1.0F, -1.0F, 12.0F, 2.0F, 2.0F).mirrored(false),
				ModelTransform.of(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.7854F));
		thigh_l1.addChild("shin_l1", ModelPartBuilder.create()
					// shin_l1_cube
					.uv(88, 20).mirrored().cuboid(0.0F, -1.0F, -1.0F, 17.0F, 2.0F, 2.0F).mirrored(false),
				ModelTransform.of(12.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.9199F));
		ModelPartData leg_r2 = root.addChild("leg_r2", ModelPartBuilder.create(),
				ModelTransform.of(-5.0F, 16.0F, -1.0F, 0.0F, -0.2443F, 0.0F));
		ModelPartData thigh_r2 = leg_r2.addChild("thigh_r2", ModelPartBuilder.create()
					// thigh_r2_cube
					.uv(88, 16).cuboid(-12.0F, -1.0F, -1.0F, 12.0F, 2.0F, 2.0F),
				ModelTransform.of(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.7854F));
		thigh_r2.addChild("shin_r2", ModelPartBuilder.create()
					// shin_r2_cube
					.uv(88, 20).cuboid(-17.0F, -1.0F, -1.0F, 17.0F, 2.0F, 2.0F),
				ModelTransform.of(-12.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.9199F));
		ModelPartData leg_l2 = root.addChild("leg_l2", ModelPartBuilder.create(),
				ModelTransform.of(5.0F, 16.0F, -1.0F, 0.0F, 0.2443F, 0.0F));
		ModelPartData thigh_l2 = leg_l2.addChild("thigh_l2", ModelPartBuilder.create()
					// thigh_l2_cube
					.uv(88, 16).mirrored().cuboid(0.0F, -1.0F, -1.0F, 12.0F, 2.0F, 2.0F).mirrored(false),
				ModelTransform.of(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.7854F));
		thigh_l2.addChild("shin_l2", ModelPartBuilder.create()
					// shin_l2_cube
					.uv(88, 20).mirrored().cuboid(0.0F, -1.0F, -1.0F, 17.0F, 2.0F, 2.0F).mirrored(false),
				ModelTransform.of(12.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.9199F));
		ModelPartData leg_r3 = root.addChild("leg_r3", ModelPartBuilder.create(),
				ModelTransform.of(-5.0F, 16.0F, 1.0F, 0.0F, 0.2443F, 0.0F));
		ModelPartData thigh_r3 = leg_r3.addChild("thigh_r3", ModelPartBuilder.create()
					// thigh_r3_cube
					.uv(88, 16).cuboid(-12.0F, -1.0F, -1.0F, 12.0F, 2.0F, 2.0F),
				ModelTransform.of(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.7854F));
		thigh_r3.addChild("shin_r3", ModelPartBuilder.create()
					// shin_r3_cube
					.uv(88, 20).cuboid(-17.0F, -1.0F, -1.0F, 17.0F, 2.0F, 2.0F),
				ModelTransform.of(-12.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.9199F));
		ModelPartData leg_l3 = root.addChild("leg_l3", ModelPartBuilder.create(),
				ModelTransform.of(5.0F, 16.0F, 1.0F, 0.0F, -0.2443F, 0.0F));
		ModelPartData thigh_l3 = leg_l3.addChild("thigh_l3", ModelPartBuilder.create()
					// thigh_l3_cube
					.uv(88, 16).mirrored().cuboid(0.0F, -1.0F, -1.0F, 12.0F, 2.0F, 2.0F).mirrored(false),
				ModelTransform.of(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.7854F));
		thigh_l3.addChild("shin_l3", ModelPartBuilder.create()
					// shin_l3_cube
					.uv(88, 20).mirrored().cuboid(0.0F, -1.0F, -1.0F, 17.0F, 2.0F, 2.0F).mirrored(false),
				ModelTransform.of(12.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.9199F));
		ModelPartData leg_r4 = root.addChild("leg_r4", ModelPartBuilder.create(),
				ModelTransform.of(-5.0F, 16.0F, 3.0F, 0.0F, 0.6981F, 0.0F));
		ModelPartData thigh_r4 = leg_r4.addChild("thigh_r4", ModelPartBuilder.create()
					// thigh_r4_cube
					.uv(88, 16).cuboid(-12.0F, -1.0F, -1.0F, 12.0F, 2.0F, 2.0F),
				ModelTransform.of(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.7854F));
		thigh_r4.addChild("shin_r4", ModelPartBuilder.create()
					// shin_r4_cube
					.uv(88, 20).cuboid(-17.0F, -1.0F, -1.0F, 17.0F, 2.0F, 2.0F),
				ModelTransform.of(-12.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.9199F));
		ModelPartData leg_l4 = root.addChild("leg_l4", ModelPartBuilder.create(),
				ModelTransform.of(5.0F, 16.0F, 3.0F, 0.0F, -0.6981F, 0.0F));
		ModelPartData thigh_l4 = leg_l4.addChild("thigh_l4", ModelPartBuilder.create()
					// thigh_l4_cube
					.uv(88, 16).mirrored().cuboid(0.0F, -1.0F, -1.0F, 12.0F, 2.0F, 2.0F).mirrored(false),
				ModelTransform.of(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.7854F));
		thigh_l4.addChild("shin_l4", ModelPartBuilder.create()
					// shin_l4_cube
					.uv(88, 20).mirrored().cuboid(0.0F, -1.0F, -1.0F, 17.0F, 2.0F, 2.0F).mirrored(false),
				ModelTransform.of(12.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.9199F));
		return TexturedModelData.of(data, 128, 64);
	}

	@Override
	public void setAngles(GimpEntity gimp, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		this.root.traverse().forEach(ModelPart::resetTransform);
		float tickDelta = animationProgress - gimp.age;
		float climb = gimp.getClimb(tickDelta);
		// The man: hunched a little; on a wall bent up from the spider's back, his face turned out from it.
		this.torso.pitch += HUNCH + climb * CLIMB_BEND;
		this.head.yaw += headYaw * MathHelper.RADIANS_PER_DEGREE * (1.0F - climb);
		this.head.pitch += headPitch * MathHelper.RADIANS_PER_DEGREE * (1.0F - climb) - climb * (MathHelper.HALF_PI + CLIMB_BEND + HUNCH);
		// The spider's legs: a spider's walk (SpiderEntityModel), pairs out of step, each swinging and lifting.
		float step = limbAngle * 0.6662F;
		for (int i = 0; i < 4; i++) {
			float phase = LEG_PHASES[i] * MathHelper.PI;
			float swing = -(MathHelper.cos(step * 2.0F + phase) * 0.4F) * limbDistance;
			float lift = Math.abs(MathHelper.sin(step + phase) * 0.4F) * limbDistance;
			this.rightLegs[i].yaw += swing;
			this.leftLegs[i].yaw -= swing;
			this.rightThighs[i].roll += lift;
			this.leftThighs[i].roll -= lift;
		}
		// Arms: hanging, swaying a little; fists up while he fights; a punch with the right.
		float sway = MathHelper.sin(animationProgress * 0.067F) * 0.05F;
		float spread = 0.05F + MathHelper.cos(animationProgress * 0.09F) * 0.03F;
		this.rightArm.pitch += sway;
		this.leftArm.pitch -= sway;
		this.rightArm.roll += spread;
		this.leftArm.roll -= spread;
		if (gimp.isAttacking()) {
			this.rightArm.pitch -= 1.1F;
			this.leftArm.pitch -= 1.1F;
			this.rightArm.yaw -= 0.25F;
			this.leftArm.yaw += 0.25F;
		}
		if (this.handSwingProgress > 0.0F) {
			float swing = MathHelper.sqrt(this.handSwingProgress);
			float punch = MathHelper.sin(swing * MathHelper.PI);
			this.rightArm.pitch -= punch;
			this.rightArm.yaw += punch * 0.35F;
			this.torso.yaw += MathHelper.sin(swing * MathHelper.TAU) * 0.15F;
		}
		// Pupils: toward the nearest player, all the way at 45 degrees aside or 30 up or down.
		Vector3f look = lookDirection(gimp, tickDelta, climb);
		if (look != null) {
			Vector3f inHead = toLocal(toLocal(look, this.torso), this.head);
			float aside = MathHelper.clamp((float) Math.atan2(inHead.x, -inHead.z) * MathHelper.DEGREES_PER_RADIAN / 45.0F, -1.0F, 1.0F);
			float lift = MathHelper.clamp((float) Math.atan2(inHead.y, Math.sqrt(inHead.x * inHead.x + inHead.z * inHead.z)) * MathHelper.DEGREES_PER_RADIAN
					/ 30.0F, -1.0F, 1.0F);
			for (ModelPart eye : new ModelPart[] {this.rightEye, this.leftEye}) {
				eye.pivotX += aside * PUPIL_REACH;
				eye.pivotY += lift * PUPIL_REACH;
			}
		}
	}

	/**
	 * Toward the nearest player within 32 blocks, in model space (the model faces -z, y points down), undoing the
	 * renderer's turn, its tilt onto a wall and its flip; null if there's none.
	 */
	private static Vector3f lookDirection(GimpEntity gimp, float tickDelta, float climb) {
		PlayerEntity player = gimp.getWorld().getClosestPlayer(gimp, 32.0);
		if (player == null) {
			return null;
		}
		Vec3d from = gimp.getLerpedPos(tickDelta).add(0.0, gimp.getStandingEyeHeight(), 0.0);
		Vec3d world = player.getLerpedPos(tickDelta).add(0.0, player.getStandingEyeHeight(), 0.0).subtract(from);
		if (world.lengthSquared() < 1.0E-4) {
			return null;
		}
		float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, gimp.prevBodyYaw, gimp.bodyYaw);
		Vector3f turned = new Quaternionf().rotationY((180.0F - bodyYaw) * MathHelper.RADIANS_PER_DEGREE)
				.rotateX(climb * GimpRenderer.CLIMB_TILT * MathHelper.RADIANS_PER_DEGREE).conjugate()
				.transform(new Vector3f((float) world.x, (float) world.y, (float) world.z));
		return new Vector3f(-turned.x, -turned.y, turned.z).normalize();
	}

	/** A direction in a part's parent space, in the part's own (undoing its rotation, which ModelPart applies as z, then y, then x). */
	private static Vector3f toLocal(Vector3f direction, ModelPart part) {
		return new Quaternionf().rotationZYX(part.roll, part.yaw, part.pitch).conjugate().transform(new Vector3f(direction));
	}

	@Override
	public ModelPart getPart() {
		return this.root;
	}
}
