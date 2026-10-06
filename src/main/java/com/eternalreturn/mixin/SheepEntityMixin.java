package com.eternalreturn.mixin;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.logic.EnchantLevels;
import com.eternalreturn.logic.FortuneHelper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SheepEntity.class)
public abstract class SheepEntityMixin {
	/** Remember the Fortune level of the shears the player is using for the duration of the shear. */
	@WrapOperation(method = "interactMob",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/passive/SheepEntity;sheared(Lnet/minecraft/sound/SoundCategory;)V"))
	private void eternalreturn$rememberShears(SheepEntity sheep, SoundCategory category, Operation<Void> original,
			@Local(argsOnly = true) PlayerEntity player, @Local(argsOnly = true) Hand hand) {
		FortuneHelper.SHEAR_FORTUNE.set(EnchantLevels.levelOf(player.getStackInHand(hand), Enchantments.FORTUNE));
		try {
			original.call(sheep, category);
		} finally {
			FortuneHelper.SHEAR_FORTUNE.remove();
		}
	}

	/** The wool count ("1 + random(3)") gets the ore Fortune multiplier. */
	@ModifyVariable(method = "sheared", at = @At("STORE"), ordinal = 0)
	private int eternalreturn$moreWool(int count) {
		Integer fortune = FortuneHelper.SHEAR_FORTUNE.get();
		if (fortune == null || fortune <= 0 || !EternalReturnConfig.get().fortune.shearsMultiplyWool) {
			return count;
		}
		return count * FortuneHelper.oreMultiplier(((Entity) (Object) this).getRandom(), fortune);
	}
}
