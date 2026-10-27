package com.eternalreturn.mixin.enchantments;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.EnchantCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;
import java.util.List;

/**
 * /enchant takes an enchantment the item already has (an inherent one, say: InherentEnchantments) and raises it to the
 * level given, where vanilla refuses it as not going with itself.
 */
@Mixin(EnchantCommand.class)
public abstract class EnchantCommandMixin {
	@WrapOperation(method = "execute", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/enchantment/EnchantmentHelper;isCompatible(Ljava/util/Collection;Lnet/minecraft/registry/entry/RegistryEntry;)Z"))
	private static boolean eternalreturn$raiseOwn(Collection<RegistryEntry<Enchantment>> existing, RegistryEntry<Enchantment> candidate,
			Operation<Boolean> original) {
		List<RegistryEntry<Enchantment>> others = existing.stream().filter(entry -> !entry.equals(candidate)).toList();
		return original.call(others, candidate);
	}
}
