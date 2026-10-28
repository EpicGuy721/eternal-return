package com.eternalreturn.mixin.combat;

import com.eternalreturn.config.EternalReturnConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.SwordItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;

/**
 * A sword only sweeps with Sweeping Edge on it (OldCombat). (The sword check is named by a class literal, which the
 * released jar's names are mapped from; a "classValue=" string isn't, and would fail outside the development setup.)
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntitySweepMixin {
	@WrapOperation(method = "attack", constant = @Constant(classValue = SwordItem.class))
	private boolean eternalreturn$onlyWithSweepingEdge(Object held, Operation<Boolean> original) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		return original.call(held) && (!EternalReturnConfig.get().survival.sweepingNeedsEnchantment
				|| self.getAttributeValue(EntityAttributes.PLAYER_SWEEPING_DAMAGE_RATIO) > 0.0);
	}
}
