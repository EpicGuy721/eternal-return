package com.eternalreturn.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Status of the hooks into Moderner Beta's surface pass and column heights (com.eternalreturn.mixin.compat), for the startup
 * check and the tests. Plain Java with no game classes, because the mixin plugin uses it before the game
 * loads.
 */
public final class ModernerBetaHooks {
	/** Hook name to whether it was applied (false: skipped because its target changed). */
	public static final Map<String, Boolean> APPLIED = new ConcurrentHashMap<>();
	/** Times each hook ran. */
	public static final AtomicLong SUITABLE_CHECKS = new AtomicLong();
	public static final AtomicLong SURFACE_PASSES = new AtomicLong();
	/** Chunk providers the column-height hook (ModernerBetaHeightMixin) has seen. */
	public static final AtomicLong HEIGHT_PROVIDERS = new AtomicLong();
	/** Calls of the cave biome injector seen by ModernerBetaCaveBiomeMixin. */
	public static final AtomicLong CAVE_FILTER_CALLS = new AtomicLong();
	/** The world Moderner Beta's snow-and-freeze step is decorating, while it runs on this thread (ModernerBetaSnowMixin). */
	public static final ThreadLocal<net.minecraft.world.StructureWorldAccess> SNOWING = new ThreadLocal<>();
	/** Snow checks seen during that step (ModernerBetaSnowCheckMixin). */
	public static final AtomicLong SNOW_CHECKS = new AtomicLong();

	private ModernerBetaHooks() {
	}
}
