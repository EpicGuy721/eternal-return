package com.eternalreturn.mixin.mobs;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.block.Blocks;
import net.minecraft.block.WitherSkullBlock;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.block.pattern.BlockPattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The wither won't be summoned within mobs.witherBedrockDistance (8) blocks of bedrock, so it can't be trapped under
 * the Nether roof or at the End's portal. The skull stays placed and nothing is used up; players nearby are told why.
 */
@Mixin(WitherSkullBlock.class)
public abstract class WitherSummonMixin {
	@Shadow
	private static BlockPattern getWitherBossPattern() {
		throw new AssertionError();
	}

	@Inject(method = "onPlaced(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/SkullBlockEntity;)V",
			at = @At("HEAD"), cancellable = true)
	private static void eternalreturn$notNearBedrock(World world, BlockPos pos, SkullBlockEntity skull, CallbackInfo ci) {
		int reach = EternalReturnConfig.get().mobs.witherBedrockDistance;
		if (world.isClient || reach <= 0 || getWitherBossPattern().searchAround(world, pos) == null) {
			return;
		}
		for (BlockPos near : BlockPos.iterate(pos.add(-reach, -reach, -reach), pos.add(reach, reach, reach))) {
			if (world.getBlockState(near).isOf(Blocks.BEDROCK)) {
				for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class, new Box(pos).expand(16.0), player -> true)) {
					player.sendMessage(Text.translatable("block.eternalreturn.wither.too_close_to_bedrock"), true);
				}
				ci.cancel();
				return;
			}
		}
	}
}
