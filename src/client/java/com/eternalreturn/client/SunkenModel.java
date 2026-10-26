package com.eternalreturn.client;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.items.Speargun;
import com.eternalreturn.mobs.entity.SunkenEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.CrossbowPosing;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.SkeletonEntityModel;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;

/**
 * The Sunken: a skeleton's body (its texture in a skeleton's layout), with coral growing out of its head: a fan through
 * it, a strip out of its left side and a curl out of its right (tools/models/sunken.bbmodel). It draws and holds its
 * speargun as a pillager does a crossbow.
 */
public class SunkenModel extends SkeletonEntityModel<SunkenEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(EternalReturn.id("sunken"), "main");

	public SunkenModel(ModelPart root) {
		super(root);
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = BipedEntityModel.getModelData(Dilation.NONE, 0.0F);
		ModelPartData root = data.getRoot();
		SkeletonEntityModel.addLimbs(root);
		ModelPartData coral = root.getChild("head").addChild("coral", ModelPartBuilder.create()
					// coral_strip
					.uv(26, 0).cuboid(3.0F, -6.0F, 1.0F, 6.0F, 2.0F, 0.0F)
					// coral_curl
					.uv(28, 4).cuboid(-6.0F, -7.0F, 0.0F, 2.0F, 4.0F, 0.0F),
				ModelTransform.NONE);
		coral.addChild("coral_fan", ModelPartBuilder.create()
					.uv(31, 1).cuboid(-5.0F, -9.0F, 0.0F, 10.0F, 9.0F, 0.0F),
				ModelTransform.of(-1.5F, -4.0F, -2.0F, 0.0F, -0.7854F, 0.0F));
		return TexturedModelData.of(data, 64, 32);
	}

	@Override
	public void animateModel(SunkenEntity sunken, float limbAngle, float limbDistance, float tickDelta) {
		super.animateModel(sunken, limbAngle, limbDistance, tickDelta);
		ArmPose pose = this.speargunPose(sunken);
		if (pose != null) {
			if (sunken.getMainArm() == Arm.RIGHT) {
				this.rightArmPose = pose;
			} else {
				this.leftArmPose = pose;
			}
		}
	}

	private ArmPose speargunPose(SunkenEntity sunken) {
		ItemStack stack = sunken.getMainHandStack();
		if (!stack.isOf(Speargun.SPEARGUN)) {
			return null;
		}
		if (sunken.isCharging()) {
			return ArmPose.CROSSBOW_CHARGE;
		}
		return CrossbowItem.isCharged(stack) && sunken.isAttacking() ? ArmPose.CROSSBOW_HOLD : ArmPose.ITEM;
	}

	@Override
	public void setAngles(SunkenEntity sunken, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		super.setAngles(sunken, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
		// Its texture has coral where a skeleton's hat layer would be.
		this.hat.visible = false;
		// A skeleton without a bow holds its arms out when it fights; with a speargun drawn or held, the crossbow's pose.
		boolean right = sunken.getMainArm() == Arm.RIGHT;
		ArmPose pose = right ? this.rightArmPose : this.leftArmPose;
		ModelPart holding = right ? this.rightArm : this.leftArm;
		ModelPart other = right ? this.leftArm : this.rightArm;
		if (pose == ArmPose.CROSSBOW_CHARGE) {
			CrossbowPosing.charge(holding, other, sunken, right);
		} else if (pose == ArmPose.CROSSBOW_HOLD) {
			CrossbowPosing.hold(holding, other, this.head, right);
		}
	}
}
