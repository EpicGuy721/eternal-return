package com.eternalreturn.gametest.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets a test make the arrow a bow or crossbow would shoot, the way a player's shot does. */
@Mixin(RangedWeaponItem.class)
public interface RangedWeaponInvoker {
	@Invoker("createArrowEntity")
	ProjectileEntity eternalreturn$createArrowEntity(World world, LivingEntity shooter, ItemStack weapon, ItemStack projectile, boolean critical);
}
