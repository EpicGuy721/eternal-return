package com.eternalreturn.mixin.survival;

import com.eternalreturn.items.Milk;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Milking (Milk): a glass bottle fills with milk; a cow gives a bottle at a time, or a bucket when full, and fills up
 * again only slowly (Milk.CowMilk). Milked dry, it gives nothing. (The client can't tell how much milk a cow has: it
 * takes the milking as done, so it doesn't go on to use the bottle or bucket as well.)
 */
@Mixin(CowEntity.class)
public abstract class CowMilkMixin extends AnimalEntity {
	protected CowMilkMixin(EntityType<? extends AnimalEntity> type, World world) {
		super(type, world);
	}

	@Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$milk(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		ItemStack stack = player.getStackInHand(hand);
		boolean bucket = stack.isOf(Items.BUCKET);
		if (!bucket && !stack.isOf(Items.GLASS_BOTTLE) || this.isBaby()) {
			return;
		}
		if (this.getWorld().isClient) {
			cir.setReturnValue(ActionResult.SUCCESS);
			return;
		}
		CowEntity cow = (CowEntity) (Object) this;
		if (!Milk.CowMilk.take(cow, bucket ? Milk.CowMilk.BOTTLES : 1)) {
			cir.setReturnValue(ActionResult.CONSUME);
			return;
		}
		if (!bucket) {
			player.playSound(SoundEvents.ENTITY_COW_MILK, 1.0F, 1.2F);
			player.setStackInHand(hand, ItemUsage.exchangeStack(stack, player, new ItemStack(Milk.BOTTLE)));
			cir.setReturnValue(ActionResult.SUCCESS);
		}
	}
}
