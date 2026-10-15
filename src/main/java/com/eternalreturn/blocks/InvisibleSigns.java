package com.eternalreturn.blocks;

/**
 * Invisible signs: right-click a sign with a phantom membrane (used up) and the sign itself disappears, leaving only
 * its text (SignInvisibilityMixin, client SignRendererMixin). Saved with the sign as eternalreturn_invisible.
 */
public final class InvisibleSigns {
	/** What a sign keeps. */
	public interface Holder {
		boolean eternalreturn$invisible();

		void eternalreturn$setInvisible(boolean invisible);
	}

	private InvisibleSigns() {
	}

	public static boolean isInvisible(Object sign) {
		return sign instanceof Holder holder && holder.eternalreturn$invisible();
	}
}
