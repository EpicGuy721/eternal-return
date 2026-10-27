package com.eternalreturn.mixin.client;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The pause after breaking a block before the next one starts (vanilla's 5 ticks) shortens with how fast the held
 * tool mines that block: 5 ticks at a wooden pickaxe's speed (or slower, or by hand), 2 at an iron pickaxe's, 1 at a
 * diamond's; a pickaxe on wood (not its block) keeps the 5. Efficiency counts.
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class BreakingCooldownMixin {
	@Shadow
	@Final
	private MinecraftClient client;

	/** The block being broken this tick, before it goes. */
	@Unique
	private BlockState eternalreturn$breaking;

	@Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"))
	private void eternalreturn$noteBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		this.eternalreturn$breaking = this.client.world != null ? this.client.world.getBlockState(pos) : null;
	}

	@ModifyConstant(method = "updateBlockBreakingProgress", constant = @Constant(intValue = 5))
	private int eternalreturn$toolPause(int pause) {
		if (this.client.player == null || this.eternalreturn$breaking == null) {
			return pause;
		}
		ItemStack tool = this.client.player.getMainHandStack();
		float speed = tool.getMiningSpeedMultiplier(this.eternalreturn$breaking);
		if (speed > 1.0F) {
			speed += (float) this.client.player.getAttributeValue(EntityAttributes.PLAYER_MINING_EFFICIENCY);
		}
		return MathHelper.clamp(Math.round(pause * 2.0F / Math.max(speed, 1.0F)), 1, pause);
	}
}
