package com.eternalreturn.client;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.mobs.entity.SoldierEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * The soldier: a tall, thin player-shaped body (2.25 blocks with his helmet) in a greatcoat, gaiter and helmet. Made in
 * tools/models/soldier.bbmodel (parts written from it). Being a biped model, he walks as players do; his arms hold his
 * rifle at the ready (the model's "hold" pose) or, aiming, raised to his shoulder and following his head's tilt ("aim").
 */
public class SoldierModel extends BipedEntityModel<SoldierEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(EternalReturn.id("soldier"), "main");
	public static final Identifier TEXTURE = EternalReturn.id("textures/entity/soldier.png");

	public SoldierModel(ModelPart root) {
		super(root);
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("head", ModelPartBuilder.create()
					// head_cube
					.uv(0, 0).cuboid(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F)
					// gaiter
					.uv(0, 28).cuboid(-4.0F, -3.0F, -4.0F, 8.0F, 3.0F, 8.0F, new Dilation(0.35F))
					// helmet
					.uv(64, 96).cuboid(-4.5F, -9.0F, -4.5F, 9.0F, 4.0F, 9.0F),
				ModelTransform.pivot(0.0F, -3.0F, 0.0F));
		root.addChild("hat", ModelPartBuilder.create(),
				ModelTransform.pivot(0.0F, -3.0F, 0.0F));
		root.addChild("body", ModelPartBuilder.create()
					// body_cube
					.uv(32, 28).cuboid(-3.0F, 0.0F, -1.5F, 6.0F, 13.0F, 3.0F)
					// coat
					.uv(36, 8).cuboid(-3.0F, 0.0F, -1.5F, 6.0F, 13.0F, 3.0F, new Dilation(0.25F)),
				ModelTransform.pivot(0.0F, -3.0F, 0.0F));
		root.addChild("right_arm", ModelPartBuilder.create()
					// right_arm_cube
					.uv(24, 44).cuboid(-1.0F, -2.0F, -1.5F, 3.0F, 13.0F, 3.0F)
					// right_sleeve
					.uv(48, 44).cuboid(-1.0F, -2.0F, -1.5F, 3.0F, 13.0F, 3.0F, new Dilation(0.25F)),
				ModelTransform.pivot(-5.0F, -1.0F, 0.0F));
		root.addChild("left_arm", ModelPartBuilder.create()
					// left_arm_cube
					.uv(36, 44).cuboid(-2.0F, -2.0F, -1.5F, 3.0F, 13.0F, 3.0F)
					// left_sleeve
					.uv(50, 24).cuboid(-2.0F, -2.0F, -1.5F, 3.0F, 13.0F, 3.0F, new Dilation(0.25F)),
				ModelTransform.pivot(5.0F, -1.0F, 0.0F));
		root.addChild("right_leg", ModelPartBuilder.create()
					// right_leg_cube
					.uv(0, 39).cuboid(-1.5F, 0.0F, -1.5F, 3.0F, 14.0F, 3.0F)
					// right_coat_tail
					.uv(16, 60).cuboid(-1.5F, 0.0F, -1.5F, 3.0F, 8.0F, 3.0F, new Dilation(0.5F))
					// right_boot
					.uv(60, 0).cuboid(-1.5F, 10.0F, -1.5F, 3.0F, 4.0F, 3.0F, new Dilation(0.25F)),
				ModelTransform.pivot(-1.5F, 10.0F, 0.0F));
		root.addChild("left_leg", ModelPartBuilder.create()
					// left_leg_cube
					.uv(12, 39).cuboid(-1.5F, 0.0F, -1.5F, 3.0F, 14.0F, 3.0F)
					// left_coat_tail
					.uv(28, 60).cuboid(-1.5F, 0.0F, -1.5F, 3.0F, 8.0F, 3.0F, new Dilation(0.5F))
					// left_boot
					.uv(40, 60).cuboid(-1.5F, 10.0F, -1.5F, 3.0F, 4.0F, 3.0F, new Dilation(0.25F)),
				ModelTransform.pivot(1.5F, 10.0F, 0.0F));
		return TexturedModelData.of(data, 128, 128);
	}

	@Override
	public void setAngles(SoldierEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
		// The biped model puts each part back at a player's height; his body is taller.
		for (ModelPart part : new ModelPart[] {this.head, this.hat, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg}) {
			part.pivotY = part.getDefaultTransform().pivotY;
		}
		if (entity.isAiming()) {
			pose(this.rightArm, -84.0F, -10.0F, 0.0F);
			pose(this.leftArm, -86.0F, 25.0F, 0.0F);
			this.rightArm.pitch += this.head.pitch;
			this.leftArm.pitch += this.head.pitch;
		} else {
			pose(this.rightArm, -35.0F, -8.0F, 0.0F);
			pose(this.leftArm, -65.0F, 24.0F, 0.0F);
		}
	}

	/** Pitch, yaw and roll in degrees. */
	private static void pose(ModelPart arm, float pitch, float yaw, float roll) {
		arm.pitch = pitch * MathHelper.RADIANS_PER_DEGREE;
		arm.yaw = yaw * MathHelper.RADIANS_PER_DEGREE;
		arm.roll = roll * MathHelper.RADIANS_PER_DEGREE;
	}
}
