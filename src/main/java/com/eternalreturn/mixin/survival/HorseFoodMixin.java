package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.AnimalHunger;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.CamelEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Horses, donkeys, mules, llamas and camels take food through their own code, which turns it down at full
 * health. Food they take counts as a meal (AnimalHunger); food they would turn down they take anyway when
 * hungry (the caller then uses up the item).
 */
@Mixin({AbstractHorseEntity.class, LlamaEntity.class, CamelEntity.class})
public abstract class HorseFoodMixin {
	@Inject(method = "receiveFood", at = @At("RETURN"), cancellable = true)
	private void eternalreturn$hungryHorse(PlayerEntity player, ItemStack item, CallbackInfoReturnable<Boolean> cir) {
		AbstractHorseEntity horse = (AbstractHorseEntity) (Object) this;
		if (horse.getWorld().isClient || !AnimalHunger.hungers(horse)) {
			return;
		}
		if (cir.getReturnValueZ()) {
			AnimalHunger.fed(horse);
		} else if (AnimalHunger.isHungry(horse) && isFood(horse, item)) {
			AnimalHunger.fed(horse);
			cir.setReturnValue(true);
		}
	}

	@Unique
	private static boolean isFood(AbstractHorseEntity horse, ItemStack item) {
		if (horse instanceof CamelEntity) {
			return horse.isBreedingItem(item);
		}
		if (horse instanceof LlamaEntity) {
			return item.isOf(Items.WHEAT) || item.isOf(Blocks.HAY_BLOCK.asItem());
		}
		return item.isOf(Items.WHEAT) || item.isOf(Items.SUGAR) || item.isOf(Blocks.HAY_BLOCK.asItem()) || item.isOf(Items.APPLE)
				|| item.isOf(Items.GOLDEN_CARROT) || item.isOf(Items.GOLDEN_APPLE) || item.isOf(Items.ENCHANTED_GOLDEN_APPLE);
	}
}
