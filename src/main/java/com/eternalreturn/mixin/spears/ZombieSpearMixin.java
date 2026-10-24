package com.eternalreturn.mixin.spears;

import com.eternalreturn.items.Spears;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Of the zombies (husks, zombie villagers) that spawn armed, a third carry an iron spear instead of a sword or shovel. */
@Mixin(ZombieEntity.class)
public abstract class ZombieSpearMixin {
	@Inject(method = "initEquipment", at = @At("TAIL"))
	private void eternalreturn$spear(Random random, LocalDifficulty difficulty, CallbackInfo ci) {
		ZombieEntity zombie = (ZombieEntity) (Object) this;
		ItemStack weapon = zombie.getMainHandStack();
		if ((weapon.isOf(Items.IRON_SWORD) || weapon.isOf(Items.IRON_SHOVEL)) && random.nextInt(3) == 0) {
			zombie.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Spears.IRON));
		}
	}
}
