package com.eternalreturn.mixin.combat;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Loading a crossbow from the shooter's ammunition (Autoload). */
@Mixin(CrossbowItem.class)
public interface CrossbowItemInvoker {
	@Invoker("loadProjectiles")
	static boolean eternalreturn$loadProjectiles(LivingEntity shooter, ItemStack crossbow) {
		throw new AssertionError();
	}
}
