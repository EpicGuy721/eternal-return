package com.eternalreturn.survival;

import com.eternalreturn.config.EternalReturnConfig;

/**
 * Blindness on a mob limits its follow range, how far it notices and keeps chasing a target, to
 * survival.blindFollowRange (2 blocks), as if feeling around (applied in LivingEntitySurvivalMixin).
 * The follow range also limits how far a mob plans a path, so a blinded mob only shuffles about.
 */
public final class BlindMobs {
	private BlindMobs() {
	}

	public static double followRange() {
		return EternalReturnConfig.get().survival.blindFollowRange;
	}
}
