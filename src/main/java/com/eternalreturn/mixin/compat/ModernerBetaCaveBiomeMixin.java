package com.eternalreturn.mixin.compat;

import com.eternalreturn.compat.ModernerBetaHooks;
import com.eternalreturn.worldgen.caves.CaveBiomeFilter;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Moderner Beta's cave biome injector: one cave biome map for all the land. A cave biome of this mod where it may not
 * lie (the ice caves outside the cold, the mushroom caves under it, the magma caves above y 0; CaveBiomeFilter) becomes
 * none, so the next injection rules see the column's own biome, as if the map had no cave biome there.
 */
@Mixin(targets = "mod.bluestaggo.modernerbeta.level.biome.injection.injector.CaveBiomeInjector", remap = false)
public abstract class ModernerBetaCaveBiomeMixin {
	@Unique
	private static volatile MethodHandle eternalreturn$biome;
	@Unique
	private static volatile MethodHandle eternalreturn$y;

	@SuppressWarnings("unchecked")
	@Inject(method = "apply", at = @At("RETURN"), cancellable = true, remap = false)
	private void eternalreturn$filter(@org.spongepowered.asm.mixin.injection.Coerce Object context, int x, int y, int z,
			CallbackInfoReturnable<RegistryEntry<Biome>> cir) {
		RegistryEntry<Biome> cave = cir.getReturnValue();
		ModernerBetaHooks.CAVE_FILTER_CALLS.incrementAndGet();
		if (cave == null) {
			return;
		}
		try {
			if (eternalreturn$biome == null) {
				MethodHandles.Lookup lookup = MethodHandles.publicLookup();
				eternalreturn$y = lookup.findVirtual(context.getClass(), "getY", MethodType.methodType(int.class));
				eternalreturn$biome = lookup.findVirtual(context.getClass(), "getBiome", MethodType.methodType(RegistryEntry.class));
			}
			RegistryEntry<Biome> land = (RegistryEntry<Biome>) eternalreturn$biome.invoke(context);
			int blockY = (int) eternalreturn$y.invoke(context);
			if (!CaveBiomeFilter.allows(cave, land, blockY)) {
				cir.setReturnValue(null);
			}
		} catch (Throwable e) {
			// Leave Moderner Beta's own value.
		}
	}
}
