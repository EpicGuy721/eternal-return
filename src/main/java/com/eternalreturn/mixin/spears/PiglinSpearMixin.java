package com.eternalreturn.mixin.spears;

import com.eternalreturn.items.Spears;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Half the piglins that would carry a golden sword carry a golden spear instead. */
@Mixin(PiglinEntity.class)
public abstract class PiglinSpearMixin {
	@ModifyReturnValue(method = "makeInitialWeapon", at = @At("RETURN"))
	private ItemStack eternalreturn$spear(ItemStack weapon) {
		return weapon.isOf(Items.GOLDEN_SWORD) && ((PiglinEntity) (Object) this).getRandom().nextBoolean() ? new ItemStack(Spears.GOLDEN) : weapon;
	}
}
