package com.eternalreturn.client;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.mobs.entity.SandownEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * The Sandown: a clown 4.4 blocks tall to the tip of his hat, a stepped costume broadening to the shoulders, a ruff, a
 * stacked cone of a hat with two antennae, long sleeves, and long one-piece legs in pointed shoes. Made in
 * tools/models/sandown.bbmodel (parts written from it). His legs swing stiffly from the hip, as an iron golem's do, his
 * hat nods with his steps, and aiming he holds his right arm out along his gaze.
 */
public class SandownModel extends SinglePartEntityModel<SandownEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(EternalReturn.id("sandown"), "main");
	public static final Identifier TEXTURE = EternalReturn.id("textures/entity/sandown.png");
	/** How far the middle of the hand is from the shoulder, in pixels down the arm. */
	public static final float HAND = 22.0F;
	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart hat;
	final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public SandownModel(ModelPart root) {
		this.root = root;
		this.head = root.getChild("head");
		this.hat = this.head.getChild("hat");
		this.rightArm = root.getChild("right_arm");
		this.leftArm = root.getChild("left_arm");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("body", ModelPartBuilder.create()
					// pelvis
					.uv(32, 28).cuboid(-5.0F, 0.0F, -3.0F, 10.0F, 3.0F, 6.0F)
					// belt
					.uv(88, 16).cuboid(-5.5F, -3.0F, -3.5F, 11.0F, 3.0F, 7.0F)
					// waist
					.uv(0, 28).cuboid(-5.0F, -7.0F, -3.0F, 10.0F, 4.0F, 6.0F)
					// belly
					.uv(48, 16).cuboid(-6.5F, -12.0F, -3.5F, 13.0F, 5.0F, 7.0F)
					// chest
					.uv(0, 0).cuboid(-9.0F, -20.0F, -4.0F, 18.0F, 8.0F, 8.0F)
					// collar
					.uv(0, 16).cuboid(-7.0F, -22.0F, -5.0F, 14.0F, 2.0F, 10.0F),
				ModelTransform.pivot(0.0F, -5.0F, 0.0F));
		ModelPartData head = root.addChild("head", ModelPartBuilder.create()
					// head_cube
					.uv(52, 0).cuboid(-5.0F, -8.0F, -4.0F, 10.0F, 8.0F, 8.0F),
				ModelTransform.pivot(0.0F, -26.0F, 0.0F));
		ModelPartData hat = head.addChild("hat", ModelPartBuilder.create()
					// hat_brim
					.uv(88, 0).cuboid(-5.0F, -1.0F, -4.5F, 10.0F, 1.0F, 9.0F)
					// hat_2
					.uv(64, 28).cuboid(-4.0F, -3.0F, -3.5F, 8.0F, 2.0F, 7.0F)
					// hat_3
					.uv(94, 28).cuboid(-3.0F, -5.0F, -2.5F, 6.0F, 2.0F, 5.0F)
					// hat_4
					.uv(88, 38).cuboid(-2.0F, -7.0F, -1.5F, 4.0F, 2.0F, 3.0F)
					// hat_stem
					.uv(114, 38).cuboid(-1.0F, -9.0F, -1.0F, 2.0F, 2.0F, 2.0F)
					// hat_ball
					.uv(102, 38).cuboid(-1.5F, -12.0F, -1.5F, 3.0F, 3.0F, 3.0F),
				ModelTransform.pivot(0.0F, -8.0F, 0.0F));
		hat.addChild("right_antenna", ModelPartBuilder.create()
					.uv(122, 38).cuboid(-0.5F, -8.0F, -0.5F, 1.0F, 8.0F, 1.0F),
				ModelTransform.of(-3.0F, -3.0F, 0.0F, 0.0F, 0.0F, -0.7854F));
		hat.addChild("left_antenna", ModelPartBuilder.create()
					.uv(122, 38).mirrored().cuboid(-0.5F, -8.0F, -0.5F, 1.0F, 8.0F, 1.0F),
				ModelTransform.of(3.0F, -3.0F, 0.0F, 0.0F, 0.0F, 0.7854F));
		ModelPartData rightArm = root.addChild("right_arm", ModelPartBuilder.create()
					// right_sleeve
					.uv(0, 38).cuboid(-2.5F, -2.0F, -2.5F, 5.0F, 22.0F, 5.0F),
				ModelTransform.pivot(-11.5F, -23.0F, 0.0F));
		rightArm.addChild("right_hand", ModelPartBuilder.create()
					// right_palm
					.uv(56, 38).cuboid(-2.0F, 0.0F, -1.5F, 4.0F, 4.0F, 3.0F),
				ModelTransform.pivot(0.0F, 20.0F, 0.0F));
		ModelPartData leftArm = root.addChild("left_arm", ModelPartBuilder.create()
					// left_sleeve
					.uv(0, 38).mirrored().cuboid(-2.5F, -2.0F, -2.5F, 5.0F, 22.0F, 5.0F),
				ModelTransform.pivot(11.5F, -23.0F, 0.0F));
		leftArm.addChild("left_hand", ModelPartBuilder.create()
					// left_palm
					.uv(56, 38).mirrored().cuboid(-2.0F, 0.0F, -1.5F, 4.0F, 4.0F, 3.0F),
				ModelTransform.pivot(0.0F, 20.0F, 0.0F));
		ModelPartData rightLeg = root.addChild("right_leg", ModelPartBuilder.create()
					// right_leg_cube
					.uv(20, 38).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 23.0F, 4.0F),
				ModelTransform.pivot(-3.0F, -3.0F, 0.0F));
		rightLeg.addChild("right_foot", ModelPartBuilder.create()
					// right_shoe
					.uv(36, 38).cuboid(-2.0F, 0.0F, -4.0F, 4.0F, 4.0F, 6.0F)
					// right_toe
					.uv(70, 38).cuboid(-1.5F, 2.0F, -7.0F, 3.0F, 2.0F, 3.0F)
					// right_toe_tip
					.uv(82, 38).cuboid(-0.5F, 0.0F, -9.0F, 1.0F, 3.0F, 2.0F),
				ModelTransform.of(0.0F, 23.0F, 0.0F, 0.0F, 0.2618F, 0.0F));
		ModelPartData leftLeg = root.addChild("left_leg", ModelPartBuilder.create()
					// left_leg_cube
					.uv(20, 38).mirrored().cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 23.0F, 4.0F),
				ModelTransform.pivot(3.0F, -3.0F, 0.0F));
		leftLeg.addChild("left_foot", ModelPartBuilder.create().mirrored()
					// left_shoe
					.uv(36, 38).cuboid(-2.0F, 0.0F, -4.0F, 4.0F, 4.0F, 6.0F)
					// left_toe
					.uv(70, 38).cuboid(-1.5F, 2.0F, -7.0F, 3.0F, 2.0F, 3.0F)
					// left_toe_tip
					.uv(82, 38).cuboid(-0.5F, 0.0F, -9.0F, 1.0F, 3.0F, 2.0F),
				ModelTransform.of(0.0F, 23.0F, 0.0F, 0.0F, -0.2618F, 0.0F));
		return TexturedModelData.of(data, 128, 128);
	}

	@Override
	public ModelPart getPart() {
		return this.root;
	}

	@Override
	public void setAngles(SandownEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		this.root.traverse().forEach(ModelPart::resetTransform);
		this.head.yaw = headYaw * MathHelper.RADIANS_PER_DEGREE;
		this.head.pitch = headPitch * MathHelper.RADIANS_PER_DEGREE;
		// Long, stiff legs: a slow swing from the hip; the arms swing a little against them.
		float step = limbAngle * 0.45F;
		float swing = MathHelper.cos(step) * 0.7F * limbDistance;
		this.rightLeg.pitch = swing;
		this.leftLeg.pitch = -swing;
		this.rightArm.pitch = -swing * 0.5F;
		this.leftArm.pitch = swing * 0.5F;
		float sway = MathHelper.sin(animationProgress * 0.067F) * 0.05F;
		this.rightArm.roll = sway;
		this.leftArm.roll = -sway;
		this.hat.roll = MathHelper.sin(step) * 0.08F * limbDistance + MathHelper.sin(animationProgress * 0.05F) * 0.03F;
		if (entity.isAiming()) {
			this.rightArm.pitch = -MathHelper.HALF_PI + this.head.pitch;
			this.rightArm.yaw = this.head.yaw;
			this.rightArm.roll = 0.0F;
		}
	}
}
