package com.eternalreturn.client;

import com.eternalreturn.EternalReturn;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;

/**
 * The beholder: a ghast-sized cube with one great eye and a fanged mouth painted on its front, and eight eyestalks (four on top, two on
 * each side) of three segments each, ending in an eyeball that looks along the stalk. Made in tools/models/beholder.bbmodel; like the
 * ghast's, it is meant to be scaled up by its renderer. Always plays BeholderAnimations.IDLE.
 */
public class BeholderModel<T extends Entity> extends SinglePartEntityModel<T> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(EternalReturn.id("beholder"), "main");
	public static final Identifier TEXTURE = EternalReturn.id("textures/entity/beholder/beholder.png");
	private final ModelPart root;
	private final Vector3f animationVector = new Vector3f();

	public BeholderModel(ModelPart root) {
		this.root = root;
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData body = data.getRoot().addChild("body", ModelPartBuilder.create().uv(0, 0).cuboid(-8.0F, -8.0F, -8.0F, 16.0F, 16.0F, 16.0F),
				ModelTransform.pivot(0.0F, 8.0F, 0.0F));
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
		tip.addChild(name + "_eye", ModelPartBuilder.create().uv(uv[6], uv[7]).cuboid(-2.0F, -3.0F, -2.0F, 4.0F, 4.0F, 4.0F), ModelTransform.pivot(0.0F, -3.0F, 0.0F));
	}

	@Override
	public void setAngles(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		this.root.traverse().forEach(ModelPart::resetTransform);
		// animationProgress is in ticks; the animation wants milliseconds.
		AnimationHelper.animate(this, BeholderAnimations.IDLE, (long) (animationProgress * 50.0F), 1.0F, this.animationVector);
	}

	@Override
	public ModelPart getPart() {
		return this.root;
	}
}
