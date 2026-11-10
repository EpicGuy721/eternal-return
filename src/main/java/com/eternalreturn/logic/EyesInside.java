package com.eternalreturn.logic;

import net.minecraft.entity.EntityDimensions;

/**
 * A player's eyes never sit above the top of their hitbox (EyesInsideMixin). Nostalgic Tweaks' "Old Creative
 * Crouching" lets a flying player crouch but keeps their eyes at standing height, 1.62, in the crouching hitbox, 1.5
 * tall: flying up under a block then pushed the camera 0.12 into it. Where the eyes reach within the camera's near
 * plane (0.05) of the top, the hitbox grows to the standing proportion above them (as it was before 1.14, when
 * crouching never shrank it). Vanilla's sizes and Nostalgic Tweaks' old sneaking (eyes at 1.41) never trigger it.
 */
public final class EyesInside {
	/** Standing eyes at 1.62 in a 1.8 hitbox. */
	private static final float STANDING_EYES = 0.9F;
	/** The camera's near plane: eyes closer than this to the top see into the block above. */
	private static final float NEAR_PLANE = 0.05F;

	private EyesInside() {
	}

	public static EntityDimensions fit(EntityDimensions dimensions) {
		if (dimensions.eyeHeight() <= dimensions.height() - NEAR_PLANE) {
			return dimensions;
		}
		return new EntityDimensions(dimensions.width(), dimensions.eyeHeight() / STANDING_EYES, dimensions.eyeHeight(), dimensions.attachments(), dimensions.fixed());
	}
}
