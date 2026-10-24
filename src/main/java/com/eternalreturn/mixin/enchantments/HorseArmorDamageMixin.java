package com.eternalreturn.mixin.enchantments;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

/** A horse's armor takes damage as a rider's does when the horse is hurt (HorseArmor). */
@Mixin(HorseEntity.class)
public abstract class HorseArmorDamageMixin extends AbstractHorseEntity {
	protected HorseArmorDamageMixin(EntityType<? extends AbstractHorseEntity> type, World world) {
		super(type, world);
	}

	@Override
	public void damageArmor(DamageSource source, float amount) {
		this.damageEquipment(source, amount, EquipmentSlot.BODY);
	}
}
