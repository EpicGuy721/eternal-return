package com.eternalreturn.mixin.spears;

import com.eternalreturn.items.Spears;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A third of zombified piglins carry a golden spear instead of the golden sword. */
@Mixin(ZombifiedPiglinEntity.class)
public abstract class ZombifiedPiglinSpearMixin {
	@Inject(method = "initEquipment", at = @At("TAIL"))
	private void eternalreturn$spear(Random random, LocalDifficulty difficulty, CallbackInfo ci) {
		ZombifiedPiglinEntity piglin = (ZombifiedPiglinEntity) (Object) this;
		if (piglin.getMainHandStack().isOf(Items.GOLDEN_SWORD) && random.nextInt(3) == 0) {
			piglin.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Spears.GOLDEN));
		}
	}
}
