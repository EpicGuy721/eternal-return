package com.eternalreturn.mixin.mobs;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Skeleton wolves (NewMobs): in the Nether a wolf may spawn on any solid ground, in the dark (vanilla wants grass and
 * light). The skeleton variant is picked for them there by its biomes (wolf_variant/skeleton.json).
 */
@Mixin(WolfEntity.class)
public abstract class WolfNetherSpawnMixin {
	@Inject(method = "canSpawn", at = @At("HEAD"), cancellable = true)
	private static void eternalreturn$nether(EntityType<WolfEntity> type, WorldAccess world, SpawnReason reason, BlockPos pos, Random random,
			CallbackInfoReturnable<Boolean> cir) {
		if (world instanceof net.minecraft.world.ServerWorldAccess server && server.toServerWorld().getRegistryKey() == World.NETHER) {
			BlockPos below = pos.down();
			cir.setReturnValue(world.getBlockState(below).isSolidBlock(world, below) && world.getFluidState(pos).isEmpty());
		}
	}
}
