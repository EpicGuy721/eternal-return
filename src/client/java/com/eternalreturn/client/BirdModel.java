package com.eternalreturn.client;

import com.eternalreturn.birds.BirdEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * One bird shape's model (BirdModels, from its Blockbench model), tinted with the bird's base colour as a tropical fish's
 * is. The head follows the bird's look, the legs step as it walks; in the air the wings spread and flap and the legs
 * tuck back; sitting (a pet told to sit), it tucks its legs away and settles its body on the ground. The tufts (an owl's
 * ear tufts, a woodpecker's crest) show only on shapes that have them. Drawn with back faces culled: a flat part (wing, tail, beak, leg) is a box
 * with no thickness, whose two faces lie in one plane, and drawn from both sides they would flicker against each other.
 */
public class BirdModel extends SinglePartEntityModel<BirdEntity> {
	private int colour = -1;
	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart leftLeg;
	private final ModelPart rightLeg;
	private final ModelPart leftWing;
	private final ModelPart rightWing;
	private final List<ModelPart> tufts = new ArrayList<>();

	public BirdModel(ModelPart root, BirdModels.Parts parts) {
		super(RenderLayer::getEntityCutout);
		this.root = root;
		this.body = root.getChild("body");
		this.head = find(root, parts.head());
		this.leftLeg = find(root, parts.leftLeg());
		this.rightLeg = find(root, parts.rightLeg());
		this.leftWing = find(root, parts.leftWing());
		this.rightWing = find(root, parts.rightWing());
		for (String[] tuft : parts.tufts()) {
			this.tufts.add(find(root, tuft));
		}
	}

	private static ModelPart find(ModelPart root, String[] path) {
		ModelPart part = root;
		for (String name : path) {
			part = part.getChild(name);
		}
		return part;
	}

	/** The colour the model is tinted with (0xAARRGGBB; -1 for none). */
	public void setColorMultiplier(int colour) {
		this.colour = colour;
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
		super.render(matrices, vertices, light, overlay, ColorHelper.Argb.mixColor(color, this.colour));
	}

	@Override
	public ModelPart getPart() {
		return this.root;
	}

	@Override
	public void setAngles(BirdEntity bird, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		this.root.traverse().forEach(ModelPart::resetTransform);
		this.leftLeg.visible = true;
		this.rightLeg.visible = true;
		this.head.yaw += headYaw * MathHelper.RADIANS_PER_DEGREE;
		this.head.pitch += headPitch * MathHelper.RADIANS_PER_DEGREE;
		float step = MathHelper.cos(limbAngle * 1.3F) * 1.2F * limbDistance;
		if (bird.isInSittingPose()) {
			// Settled on the ground: legs tucked away, the body down by their height (the legs hang from 24 minus it).
			this.leftLeg.visible = false;
			this.rightLeg.visible = false;
			this.body.pivotY += 24.0F - this.leftLeg.getDefaultTransform().pivotY;
		} else if (bird.isInAir() && !bird.isTouchingWater()) {
			// Wings out from the body, beating; legs tucked back. (The model's "left" wing is on Minecraft's -x side.)
			float beat = 0.7F + MathHelper.sin(animationProgress * 1.6F) * 0.6F;
			this.leftWing.roll += beat;
			this.rightWing.roll -= beat;
			this.leftLeg.pitch += 0.9F;
			this.rightLeg.pitch += 0.9F;
		} else {
			this.leftLeg.pitch += step;
			this.rightLeg.pitch -= step;
		}
		boolean tufts = bird.getShape().tufts();
		this.tufts.forEach(tuft -> tuft.visible = tufts);
	}
}
