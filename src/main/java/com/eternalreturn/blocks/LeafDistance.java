package com.eternalreturn.blocks;

/**
 * Leaves hold on further from a log: vanilla's limit is 7 (a leaf 7 or more blocks from a log, counting through
 * leaves, decays); here it is 10, so the wide crowns of the biome tree species (kapok, sugar maple, hornbeam...)
 * keep every leaf, as the owner wants the designs kept over vanilla's rule. Vanilla's leaf distance property
 * ("distance", shared by every leaves block) runs 1 to 10 (LeafDistancePropertyMixin), and the leaves' decay and
 * distance update and the tree generator's distance pass count to 10 (LeafDistanceMixin, TreeFeatureLeafDistanceMixin).
 * Every world: leaves of any tree reach 9 blocks from a log before they decay.
 */
public final class LeafDistance {
	/** The distance at which a leaf decays (vanilla 7). */
	public static final int MAX = 10;

	private LeafDistance() {
	}
}
