package com.eternalreturn.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Status of the hooks into Moderner Beta's surface pass (com.eternalreturn.mixin.compat), for the startup
 * check and the tests. Plain Java with no game classes, because the mixin plugin uses it before the game
 * loads.
 */
public final class ModernerBetaHooks {
	/** Hook name to whether it was applied (false: skipped because its target changed). */
	public static final Map<String, Boolean> APPLIED = new ConcurrentHashMap<>();
	/** Times each hook ran. */
	public static final AtomicLong SUITABLE_CHECKS = new AtomicLong();
	public static final AtomicLong SURFACE_PASSES = new AtomicLong();

	private ModernerBetaHooks() {
	}
}
