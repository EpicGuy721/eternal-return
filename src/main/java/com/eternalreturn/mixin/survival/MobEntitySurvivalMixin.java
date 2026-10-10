package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.AnimalHunger;
import com.eternalreturn.survival.SpawnerMobs;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two survival rules that sit on every mob: whether it came from a spawner (SpawnerMobs: no item drops),
 * and feeding hungry animals by hand (AnimalHunger).
 */
@Mixin(MobEntity.class)
public abstract class MobEntitySurvivalMixin extends LivingEntity implements SpawnerMobs.Holder {
	@Unique
	private static final String SPAWNER_KEY = "EternalReturnFromSpawner";

	@Unique
	private boolean eternalreturn$fromSpawner;

	protected MobEntitySurvivalMixin(EntityType<? extends LivingEntity> entityType, World world) {
		super(entityType, world);
	}

	@Override
	public boolean eternalreturn$fromSpawner() {
		return this.eternalreturn$fromSpawner;
	}

	@Override
	public void eternalreturn$setFromSpawner(boolean fromSpawner) {
		this.eternalreturn$fromSpawner = fromSpawner;
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void eternalreturn$saveSpawner(NbtCompound nbt, CallbackInfo ci) {
		if (this.eternalreturn$fromSpawner) {
			nbt.putBoolean(SPAWNER_KEY, true);
		}
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void eternalreturn$loadSpawner(NbtCompound nbt, CallbackInfo ci) {
		this.eternalreturn$fromSpawner = nbt.getBoolean(SPAWNER_KEY);
	}

	/**
	 * After the animal's own handling (which counts as a meal through AnimalEntity.eat when it takes the
	 * food): if it turned its breeding food down (in love already, or resting after breeding) but is
	 * hungry, it eats anyway. Horses, llamas and camels go through their own food code (HorseFoodMixin).
	 */
	@Inject(method = "interact", at = @At("RETURN"), cancellable = true)
	private void eternalreturn$feedHungry(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		if (this.getWorld().isClient || !((Object) this instanceof AnimalEntity animal) || cir.getReturnValue().isAccepted()) {
			return;
		}
		ItemStack held = player.getStackInHand(hand);
		// A baby fed a poisonous potato (Farming).
		if (animal.isBaby() && held.isOf(net.minecraft.item.Items.POISONOUS_POTATO)) {
			held.decrementUnlessCreative(1, player);
			animal.playSound(SoundEvents.ENTITY_GENERIC_EAT, 1.0F, 1.0F);
			com.eternalreturn.survival.Farming.poisonousPotato(animal);
			cir.setReturnValue(ActionResult.SUCCESS);
			return;
		}
		if (animal instanceof AbstractHorseEntity || !AnimalHunger.isHungry(animal)) {
			return;
		}
		ItemStack stack = player.getStackInHand(hand);
		if (animal.isBreedingItem(stack)) {
			stack.decrementUnlessCreative(1, player);
			animal.playSound(SoundEvents.ENTITY_GENERIC_EAT, 1.0F, 1.0F);
			AnimalHunger.fed(animal);
			cir.setReturnValue(ActionResult.SUCCESS);
		}
	}
}
