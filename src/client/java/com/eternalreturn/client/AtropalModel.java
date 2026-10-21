package com.eternalreturn.client;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.mobs.entity.AtropalEntity;
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

import java.util.List;

/**
 * The atropal: a curled, dead fetus with an oversized, lumpen head, one withered arm, and three umbilical cords of
 * three segments each (from its belly, its mouth and its crown). Made in tools/models/atropal.bbmodel (parts written
 * from it). It bobs as it floats, its cords sway, its limbs shiver; its head turns to what it looks at, and trembles
 * while its beam charges.
 */
public class AtropalModel extends SinglePartEntityModel<AtropalEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(EternalReturn.id("atropal"), "main");
	public static final Identifier TEXTURE = EternalReturn.id("textures/entity/atropal.png");
	private static final List<String> CORDS = List.of("cord", "mouth_cord", "head_cord");
	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;
	/** Each cord's three segments, base first. */
	private final ModelPart[][] cords = new ModelPart[3][3];

	public AtropalModel(ModelPart root) {
		this.root = root;
		this.body = root.getChild("atropal");
		this.head = this.body.getChild("head");
		ModelPart torso = this.body.getChild("torso");
		this.rightArm = torso.getChild("right_arm");
		this.leftArm = torso.getChild("left_arm");
		this.rightLeg = torso.getChild("right_leg");
		this.leftLeg = torso.getChild("left_leg");
		ModelPart[] bases = {torso, this.head, this.head};
		for (int c = 0; c < 3; c++) {
			ModelPart part = bases[c];
			for (int s = 0; s < 3; s++) {
				part = part.getChild(CORDS.get(c) + (s + 1));
				this.cords[c][s] = part;
			}
		}
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData atropal = root.addChild("atropal", ModelPartBuilder.create(),
				ModelTransform.pivot(0.0F, 12.0F, 0.0F));
		ModelPartData head = atropal.addChild("head", ModelPartBuilder.create()
					// head_cube
					.uv(0, 0).cuboid(-4.5F, -8.0F, -5.0F, 9.0F, 8.0F, 8.0F)
					// lump
					.uv(34, 0).cuboid(-5.0F, -10.0F, -4.0F, 4.0F, 3.0F, 4.0F),
				ModelTransform.of(0.0F, -3.0F, 0.0F, -0.2094F, 0.0F, -0.1745F));
		ModelPartData mouth_cord1 = head.addChild("mouth_cord1", ModelPartBuilder.create()
					// mouth_cord1_cube
					.uv(50, 5).cuboid(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				ModelTransform.of(-0.5F, -0.5F, -5.0F, -0.2618F, 0.0F, 0.1745F));
		ModelPartData mouth_cord2 = mouth_cord1.addChild("mouth_cord2", ModelPartBuilder.create()
					// mouth_cord2_cube
					.uv(54, 5).cuboid(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				ModelTransform.of(0.0F, 4.0F, 0.0F, 0.4363F, 0.0F, -0.2618F));
		mouth_cord2.addChild("mouth_cord3", ModelPartBuilder.create()
					// mouth_cord3_cube
					.uv(58, 5).cuboid(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				ModelTransform.of(0.0F, 4.0F, 0.0F, -0.5236F, 0.0F, 0.2618F));
		ModelPartData head_cord1 = head.addChild("head_cord1", ModelPartBuilder.create()
					// head_cord1_cube
					.uv(50, 10).cuboid(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				ModelTransform.of(2.5F, -8.0F, 0.5F, -0.6109F, 0.0F, 0.3491F));
		ModelPartData head_cord2 = head_cord1.addChild("head_cord2", ModelPartBuilder.create()
					// head_cord2_cube
					.uv(54, 10).cuboid(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				ModelTransform.of(0.0F, -4.0F, 0.0F, -0.7854F, 0.0F, 0.2618F));
		head_cord2.addChild("head_cord3", ModelPartBuilder.create()
					// head_cord3_cube
					.uv(58, 10).cuboid(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				ModelTransform.of(0.0F, -4.0F, 0.0F, -1.0472F, 0.0F, -0.1745F));
		ModelPartData torso = atropal.addChild("torso", ModelPartBuilder.create()
					// torso_cube
					.uv(0, 16).cuboid(-3.0F, 0.0F, -3.0F, 6.0F, 6.0F, 5.0F),
				ModelTransform.of(0.0F, -3.0F, 0.0F, -0.4363F, 0.0F, 0.0F));
		torso.addChild("right_arm", ModelPartBuilder.create()
					// right_arm_cube
					.uv(22, 16).cuboid(-2.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F),
				ModelTransform.of(-3.0F, 1.0F, -1.0F, -2.3562F, -0.4363F, 0.0F));
		torso.addChild("left_arm", ModelPartBuilder.create()
					// left_arm_cube
					.uv(30, 16).cuboid(0.0F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F),
				ModelTransform.of(3.0F, 1.0F, -1.0F, -1.9199F, 0.3491F, 0.0F));
		torso.addChild("right_leg", ModelPartBuilder.create()
					// right_leg_cube
					.uv(34, 16).cuboid(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
				ModelTransform.of(-1.5F, 5.5F, -0.5F, -1.4835F, 0.1745F, 0.0F));
		torso.addChild("left_leg", ModelPartBuilder.create()
					// left_leg_cube
					.uv(42, 16).cuboid(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
				ModelTransform.of(1.5F, 5.5F, -0.5F, -1.309F, -0.2618F, 0.0F));
		ModelPartData cord1 = torso.addChild("cord1", ModelPartBuilder.create()
					// cord1_cube
					.uv(50, 0).cuboid(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				ModelTransform.of(0.0F, 5.0F, -3.0F, -0.3491F, 0.0F, 0.0F));
		ModelPartData cord2 = cord1.addChild("cord2", ModelPartBuilder.create()
					// cord2_cube
					.uv(54, 0).cuboid(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				ModelTransform.of(0.0F, 4.0F, 0.0F, 0.6109F, 0.0F, 0.2618F));
		cord2.addChild("cord3", ModelPartBuilder.create()
					// cord3_cube
					.uv(58, 0).cuboid(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				ModelTransform.of(0.0F, 4.0F, 0.0F, -0.5236F, 0.0F, -0.3491F));
		return TexturedModelData.of(data, 64, 32);
	}

	@Override
	public void setAngles(AtropalEntity atropal, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		this.root.traverse().forEach(ModelPart::resetTransform);
		float t = animationProgress;
		this.body.pivotY += MathHelper.sin(t * 0.08F) * 1.2F;
		this.body.roll += MathHelper.sin(t * 0.05F) * 0.06F;
		this.body.pitch += MathHelper.cos(t * 0.06F) * 0.05F;
		this.head.yaw += headYaw * MathHelper.RADIANS_PER_DEGREE * 0.7F;
		this.head.pitch += headPitch * MathHelper.RADIANS_PER_DEGREE * 0.7F;
		if (atropal.hasBeamTarget()) {
			float charge = atropal.getBeamProgress(t - atropal.age);
			this.head.roll += MathHelper.sin(t * 2.5F) * 0.05F * charge;
			this.head.yaw += MathHelper.cos(t * 3.1F) * 0.03F * charge;
		}
		for (int c = 0; c < 3; c++) {
			for (int s = 0; s < 3; s++) {
				this.cords[c][s].pitch += MathHelper.sin(t * 0.12F + c * 1.7F + s * 0.9F) * 0.18F;
				this.cords[c][s].roll += MathHelper.cos(t * 0.1F + c * 2.3F + s * 0.7F) * 0.14F;
			}
		}
		this.rightArm.pitch += MathHelper.sin(t * 0.3F) * 0.04F;
		this.leftArm.roll += MathHelper.sin(t * 0.9F) * 0.06F;
		this.rightLeg.pitch += MathHelper.sin(t * 0.07F) * 0.08F;
		this.leftLeg.pitch -= MathHelper.sin(t * 0.07F + 1.0F) * 0.08F;
	}

	@Override
	public ModelPart getPart() {
		return this.root;
	}
}
