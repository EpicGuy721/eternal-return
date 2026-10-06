package com.eternalreturn.mixin;

import com.eternalreturn.logic.InherentEnchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class MobEntityMixin {
	/** Mobs get inherent enchantments too, so a chainmail zombie really does shrug off arrows. */
	@Inject(method = "equipStack", at = @At("HEAD"))
	private void eternalreturn$inherentOnEquip(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
		World world = ((Entity) (Object) this).getWorld();
		if (world != null && !world.isClient) {
			InherentEnchantments.apply(stack, world.getRegistryManager());
		}
	}
}
