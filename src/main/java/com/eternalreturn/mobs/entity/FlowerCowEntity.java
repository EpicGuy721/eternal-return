package com.eternalreturn.mobs.entity;

import com.eternalreturn.mobs.NewMobs;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.SuspiciousStewEffectsComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.List;

/**
 * Mooblooms (autumnal forests) and moolips (cherry groves) (com.eternalreturn.mobs.NewMobs): cows with flowers on
 * their backs, dandelions and pink tulips. A bucket milks them as any cow; a bowl gives suspicious stew: a moobloom's
 * gives Saturation, a moolip's Fire Resistance or Weakness. They drop a cow's drops and one of their flower.
 */
public class FlowerCowEntity extends CowEntity {
	public FlowerCowEntity(EntityType<? extends CowEntity> type, World world) {
		super(type, world);
	}

	public boolean isMoolip() {
		return this.getType() == NewMobs.MOOLIP;
	}

	/** The flower on its back. */
	public BlockState flower() {
		return (this.isMoolip() ? Blocks.PINK_TULIP : Blocks.DANDELION).getDefaultState();
	}

	@Override
	public ActionResult interactMob(PlayerEntity player, Hand hand) {
		ItemStack held = player.getStackInHand(hand);
		if (held.isOf(Items.BOWL) && !this.isBaby()) {
			ItemStack stew = new ItemStack(Items.SUSPICIOUS_STEW);
			SuspiciousStewEffectsComponent.StewEffect effect;
			if (!this.isMoolip()) {
				effect = new SuspiciousStewEffectsComponent.StewEffect(StatusEffects.SATURATION, 7);
			} else if (this.random.nextBoolean()) {
				effect = new SuspiciousStewEffectsComponent.StewEffect(StatusEffects.FIRE_RESISTANCE, 80);
			} else {
				effect = new SuspiciousStewEffectsComponent.StewEffect(StatusEffects.WEAKNESS, 180);
			}
			stew.set(DataComponentTypes.SUSPICIOUS_STEW_EFFECTS, new SuspiciousStewEffectsComponent(List.of(effect)));
			player.setStackInHand(hand, ItemUsage.exchangeStack(held, player, stew, false));
			this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.ENTITY_MOOSHROOM_SUSPICIOUS_MILK, SoundCategory.PLAYERS, 1.0F, 1.0F);
			return ActionResult.success(this.getWorld().isClient);
		}
		return super.interactMob(player, hand);
	}

	@Override
	public CowEntity createChild(ServerWorld world, PassiveEntity mate) {
		return (CowEntity) this.getType().create(world);
	}
}
