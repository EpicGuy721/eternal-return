package com.eternalreturn.survival;

import com.eternalreturn.EternalReturn;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;

/**
 * Freezing, a status effect: it freezes whoever has it as powder snow does (the frost on the screen, the shivering),
 * slowing them over 7 seconds to half their speed; then it hurts, 1.5 every 2 seconds, or 7.5 for blazes, magma
 * cubes and striders (LivingEntityFreezingMixin). Leather armor (a leather horse armor too) keeps it off, as does
 * standing in fire or lava (being on fire doesn't); the effect stays, but they don't freeze while that holds.
 * Frostbites give it with their hits; potions of Freezing are brewed from ice balls (Potions2).
 */
public final class Freezing {
	public static final RegistryEntry<StatusEffect> EFFECT = Registry.registerReference(Registries.STATUS_EFFECT, EternalReturn.id("freezing"),
			new Effect());

	private Freezing() {
	}

	/** Whether something keeps this entity from freezing just now. */
	public static boolean shielded(LivingEntity entity) {
		if (!entity.canFreeze() || entity.getEquippedStack(EquipmentSlot.BODY).isOf(Items.LEATHER_HORSE_ARMOR)) {
			return true;
		}
		for (BlockPos pos : BlockPos.iterate(entity.getBlockPos(), entity.getBlockPos().up())) {
			BlockState state = entity.getWorld().getBlockState(pos);
			if (state.isIn(BlockTags.FIRE) || state.getFluidState().isIn(FluidTags.LAVA)) {
				return true;
			}
		}
		return false;
	}

	public static boolean freezing(LivingEntity entity) {
		return entity.hasStatusEffect(EFFECT);
	}

	static class Effect extends StatusEffect {
		Effect() {
			super(StatusEffectCategory.HARMFUL, 0x9FE4FF);
		}

		@Override
		public boolean canApplyUpdateEffect(int duration, int amplifier) {
			return true;
		}

		/** Each tick: one tick colder (vanilla thaws everyone 2 a tick out of powder snow, so this adds 3), up to frozen. */
		@Override
		public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
			if (!entity.getWorld().isClient && !shielded(entity)) {
				entity.setFrozenTicks(Math.min(entity.getMinFreezeDamageTicks() + 2, entity.getFrozenTicks() + 3));
			}
			return true;
		}
	}
}
