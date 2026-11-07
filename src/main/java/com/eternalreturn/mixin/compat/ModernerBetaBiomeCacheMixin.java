package com.eternalreturn.mixin.compat;

import com.eternalreturn.compat.ModernerBetaHooks;
import com.eternalreturn.worldgen.caves.CaveWorlds;
import com.eternalreturn.worldgen.perf.ColumnHeightCache;
import com.eternalreturn.worldgen.perf.QuartBiomeCache;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.EnumSet;

/**
 * Remembers Moderner Beta's injected biome per 4 x 4 x 4 cell in Eternal Return worlds (QuartBiomeCache): the same
 * answer, worked out once instead of once per block. Other worlds are left alone. -Deternalreturn.noBiomeCache=true
 * turns it off (to measure it).
 */
@Mixin(targets = "mod.bluestaggo.modernerbeta.level.biome.injection.handler.SimpleBiomeInjectionHandler", remap = false)
public abstract class ModernerBetaBiomeCacheMixin {
	@Unique
	private static final boolean DISABLED = Boolean.getBoolean("eternalreturn.noBiomeCache");
	/** Questions being answered on this thread (calls nest), each as key part one, part two, needs before. */
	@Unique
	private static final ThreadLocal<long[]> PENDING = ThreadLocal.withInitial(() -> new long[3 * 33]);
	@Unique
	private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

	/** The enum constants of Moderner Beta's injection needs, by ordinal (known from the first set seen). */
	@Unique
	private static volatile Enum<?>[] NEEDS;

	/** Made on first use (not in the constructor: a field initializer here did not always run). */
	@Unique
	private ThreadLocal<QuartBiomeCache> eternalreturn$cache;
	@Unique
	private ThreadLocal<ColumnHeightCache> eternalreturn$tops;
	@Unique
	private ThreadLocal<ColumnHeightCache> eternalreturn$floors;
	/** 0 not known yet, 1 an Eternal Return world, 2 another world. */
	@Unique
	private volatile int eternalreturn$state;

	@Unique
	private boolean eternalreturn$active() {
		int state = this.eternalreturn$state;
		return (state == 0 ? this.eternalreturn$decide() : state) == 1;
	}

	/** Whether this handler belongs to an Eternal Return world; worked out once. */
	@Unique
	private synchronized int eternalreturn$decide() {
		if (this.eternalreturn$state != 0) {
			return this.eternalreturn$state;
		}
		int state = 2;
		try {
			Field field = this.getClass().getDeclaredField("modernBetaChunkGenerator");
			field.setAccessible(true);
			if (!DISABLED && field.get(this) instanceof NoiseChunkGenerator generator && CaveWorlds.usesEternalReturnSettings(generator)) {
				this.eternalreturn$cache = ThreadLocal.withInitial(QuartBiomeCache::new);
				this.eternalreturn$tops = ThreadLocal.withInitial(ColumnHeightCache::new);
				this.eternalreturn$floors = ThreadLocal.withInitial(ColumnHeightCache::new);
				state = 1;
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			state = 2;
		}
		ModernerBetaHooks.BIOME_CACHES.put(System.identityHashCode(this), state == 1);
		this.eternalreturn$state = state;
		return state;
	}

	@Unique
	private static int eternalreturn$mask(EnumSet<?> needs) {
		int mask = 0;
		if (needs != null) {
			for (Object need : needs) {
				Enum<?> value = (Enum<?>) need;
				mask |= 1 << value.ordinal();
				if (NEEDS == null) {
					NEEDS = value.getDeclaringClass().getEnumConstants();
				}
			}
		}
		return mask;
	}

	@Inject(method = "getBiome", at = @At("HEAD"), cancellable = true, remap = false)
	private void eternalreturn$cached(HeightLimitView view, int x, int y, int z, @Coerce Object step, EnumSet<?> needs, boolean flag,
			CallbackInfoReturnable<RegistryEntry<Biome>> cir) {
		if (!this.eternalreturn$active()) {
			return;
		}
		int before = eternalreturn$mask(needs);
		long a = QuartBiomeCache.keyA(x, z);
		long b = QuartBiomeCache.keyB(y, ((Enum<?>) step).ordinal(), before, flag, view == null ? 0x7FFF : view.getBottomY(), view == null ? 0x7FFF : view.getHeight());
		QuartBiomeCache cache = this.eternalreturn$cache.get();
		Object value = cache.get(a, b);
		if (value != null) {
			int after = cache.afterNeeds(a, b);
			int dropped = before & ~after;
			if (dropped != 0 && needs != null) {
				// Moderner Beta drops a need it can't use from the caller's set while answering: do the same.
				Enum<?>[] constants = NEEDS;
				for (int i = 0; i < constants.length; i++) {
					if ((dropped & (1 << i)) != 0) {
						needs.remove(constants[i]);
					}
				}
			}
			ModernerBetaHooks.BIOME_CACHE_HITS.increment();
			@SuppressWarnings("unchecked")
			RegistryEntry<Biome> biome = value == QuartBiomeCache.none() ? null : (RegistryEntry<Biome>) value;
			cir.setReturnValue(biome);
			return;
		}
		int[] depth = DEPTH.get();
		if (depth[0] >= 32) {
			// Only after calls ended in an exception (worldgen has crashed anyway): start the stack again.
			depth[0] = 0;
		}
		long[] pending = PENDING.get();
		int at = 3 * depth[0]++;
		pending[at] = a;
		pending[at + 1] = b;
		pending[at + 2] = before;
	}

	@Inject(method = "getBiome", at = @At("RETURN"), remap = false)
	private void eternalreturn$store(HeightLimitView view, int x, int y, int z, @Coerce Object step, EnumSet<?> needs, boolean flag,
			CallbackInfoReturnable<RegistryEntry<Biome>> cir) {
		if (this.eternalreturn$state != 1) {
			return;
		}
		int[] depth = DEPTH.get();
		if (depth[0] <= 0) {
			return;
		}
		long[] pending = PENDING.get();
		int at = 3 * --depth[0];
		ModernerBetaHooks.BIOME_CACHE_MISSES.increment();
		this.eternalreturn$cache.get().put(pending[at], pending[at + 1], cir.getReturnValue(), eternalreturn$mask(needs));
	}

	// Column heights: the same for every cell up a column.

	@Inject(method = "sampleTopHeight", at = @At("HEAD"), cancellable = true, remap = false)
	private void eternalreturn$cachedTop(HeightLimitView view, int x, int z, CallbackInfoReturnable<Integer> cir) {
		if (this.eternalreturn$state == 1) {
			ColumnHeightCache cache = this.eternalreturn$tops.get();
			long a = QuartBiomeCache.keyA(x, z);
			int b = view == null ? 0x7FFF7FFF : ColumnHeightCache.keyB(view.getBottomY(), view.getHeight());
			if (cache.has(a, b)) {
				cir.setReturnValue(cache.value(a, b));
			}
		}
	}

	@Inject(method = "sampleTopHeight", at = @At("RETURN"), remap = false)
	private void eternalreturn$storeTop(HeightLimitView view, int x, int z, CallbackInfoReturnable<Integer> cir) {
		if (this.eternalreturn$state == 1) {
			this.eternalreturn$tops.get().put(QuartBiomeCache.keyA(x, z), view == null ? 0x7FFF7FFF : ColumnHeightCache.keyB(view.getBottomY(), view.getHeight()),
					cir.getReturnValueI());
		}
	}

	@Inject(method = "sampleMinHeight", at = @At("HEAD"), cancellable = true, remap = false)
	private void eternalreturn$cachedMin(HeightLimitView view, int x, int z, CallbackInfoReturnable<Integer> cir) {
		if (this.eternalreturn$state == 1) {
			ColumnHeightCache cache = this.eternalreturn$floors.get();
			long a = QuartBiomeCache.keyA(x, z);
			int b = view == null ? 0x7FFF7FFF : ColumnHeightCache.keyB(view.getBottomY(), view.getHeight());
			if (cache.has(a, b)) {
				cir.setReturnValue(cache.value(a, b));
			}
		}
	}

	@Inject(method = "sampleMinHeight", at = @At("RETURN"), remap = false)
	private void eternalreturn$storeMin(HeightLimitView view, int x, int z, CallbackInfoReturnable<Integer> cir) {
		if (this.eternalreturn$state == 1) {
			this.eternalreturn$floors.get().put(QuartBiomeCache.keyA(x, z), view == null ? 0x7FFF7FFF : ColumnHeightCache.keyB(view.getBottomY(), view.getHeight()),
					cir.getReturnValueI());
		}
	}
}
