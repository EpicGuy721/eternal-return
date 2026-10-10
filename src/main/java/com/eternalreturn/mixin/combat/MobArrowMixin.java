package com.eternalreturn.mixin.combat;

import com.eternalreturn.logic.NewEnchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Spread of Ailments (NewEnchantments) for arrows that skeletons and other mobs shoot from their bows. */
@Mixin(ProjectileUtil.class)
public abstract class MobArrowMixin {
	@Inject(method = "createArrowProjectile", at = @At("RETURN"))
	private static void eternalreturn$spread(LivingEntity shooter, ItemStack arrow, float damageModifier, ItemStack bow,
			CallbackInfoReturnable<PersistentProjectileEntity> cir) {
		NewEnchantments.spread(shooter, bow, cir.getReturnValue());
	}
}
