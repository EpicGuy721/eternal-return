package com.eternalreturn.mixin.combat;

import com.eternalreturn.logic.NewEnchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Spread of Ailments (NewEnchantments): arrows from bows and crossbows take the shooter's effects. */
@Mixin(RangedWeaponItem.class)
public abstract class RangedWeaponMixin {
	@Inject(method = "createArrowEntity", at = @At("RETURN"))
	private void eternalreturn$spread(World world, LivingEntity shooter, ItemStack weapon, ItemStack projectile, boolean critical,
			CallbackInfoReturnable<ProjectileEntity> cir) {
		NewEnchantments.spread(shooter, weapon, cir.getReturnValue());
	}
}
