package com.eternalreturn.mixin.combat;

import com.eternalreturn.config.EternalReturnConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A sword only sweeps with Sweeping Edge on it (OldCombat). */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntitySweepMixin {
	@ModifyExpressionValue(method = "attack", at = @At(value = "CONSTANT", args = "classValue=net/minecraft/item/SwordItem"))
	private boolean eternalreturn$onlyWithSweepingEdge(boolean sword) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		return sword && (!EternalReturnConfig.get().survival.sweepingNeedsEnchantment
				|| self.getAttributeValue(EntityAttributes.PLAYER_SWEEPING_DAMAGE_RATIO) > 0.0);
	}
}
