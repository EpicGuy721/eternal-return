package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.BeaconZones;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.spawner.PatrolSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No pillager patrol member spawns inside a working beacon's area (BeaconZones). */
@Mixin(PatrolSpawner.class)
public abstract class PatrolSpawnerMixin {
	@Inject(method = "spawnPillager", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$beaconShelters(ServerWorld world, BlockPos pos, Random random, boolean captain, CallbackInfoReturnable<Boolean> cir) {
		if (BeaconZones.blocksMonstersAt(world, pos)) {
			cir.setReturnValue(false);
		}
	}
}
