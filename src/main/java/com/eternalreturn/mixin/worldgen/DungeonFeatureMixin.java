package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.worldgen.Dungeons;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.DungeonFeature;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dungeon styles, Nether spawner mobs, and twice as many dungeons in Eternal Return worlds (Dungeons). */
@Mixin(DungeonFeature.class)
public abstract class DungeonFeatureMixin {
	@Inject(method = "generate", at = @At("HEAD"))
	private void eternalreturn$style(FeatureContext<DefaultFeatureConfig> context, CallbackInfoReturnable<Boolean> cir,
			@Share("previous") LocalRef<Dungeons.Style> previous) {
		previous.set(Dungeons.begin(context));
	}

	@Inject(method = "generate", at = @At("RETURN"))
	private void eternalreturn$again(FeatureContext<DefaultFeatureConfig> context, CallbackInfoReturnable<Boolean> cir,
			@Share("previous") LocalRef<Dungeons.Style> previous) {
		try {
			Dungeons.tryAgain(context);
		} finally {
			Dungeons.end(previous.get());
		}
	}

	@ModifyArg(method = "generate", index = 2, at = @At(value = "INVOKE", ordinal = 0,
			target = "Lnet/minecraft/world/gen/feature/DungeonFeature;setBlockStateIf(Lnet/minecraft/world/StructureWorldAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Ljava/util/function/Predicate;)V"))
	private BlockState eternalreturn$floor(BlockState vanilla) {
		return Dungeons.floor(vanilla);
	}

	@ModifyArg(method = "generate", index = 2, at = @At(value = "INVOKE", ordinal = 1,
			target = "Lnet/minecraft/world/gen/feature/DungeonFeature;setBlockStateIf(Lnet/minecraft/world/StructureWorldAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Ljava/util/function/Predicate;)V"))
	private BlockState eternalreturn$wall(BlockState vanilla) {
		return Dungeons.wall(vanilla);
	}

	@Inject(method = "getMobSpawnerEntity", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$netherMobs(Random random, CallbackInfoReturnable<EntityType<?>> cir) {
		EntityType<?> mob = Dungeons.spawnerMob(random);
		if (mob != null) {
			cir.setReturnValue(mob);
		}
	}
}
