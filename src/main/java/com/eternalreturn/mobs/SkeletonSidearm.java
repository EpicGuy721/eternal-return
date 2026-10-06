package com.eternalreturn.mobs;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

/**
 * For skeletons carrying a bow and a sword: swap the sword into the main hand when the target gets
 * close and the bow back when it backs off. AbstractSkeletonEntity.updateAttackType (called from
 * equipStack) then switches between its bow and melee goals.
 *
 * This must not run from inside a goal: updateAttackType adds and removes goals, and doing that
 * while the goal selector is iterating crashes the server.
 */
public final class SkeletonSidearm {
	private static final double DRAW_SWORD_DISTANCE = 4.0;
	private static final double DRAW_BOW_DISTANCE = 7.0;
	public static final int SWAP_COOLDOWN_TICKS = 20;

	private SkeletonSidearm() {
	}

	/** Returns true if it swapped hands. */
	public static boolean tick(MobEntity mob) {
		ItemStack main = mob.getMainHandStack();
		ItemStack off = mob.getOffHandStack();
		boolean holdingSword = main.isIn(ItemTags.SWORDS) && off.isOf(Items.BOW);
		boolean holdingBow = main.isOf(Items.BOW) && off.isIn(ItemTags.SWORDS);
		if (!holdingSword && !holdingBow) {
			return false;
		}

		LivingEntity target = mob.getTarget();
		boolean wantSword = false;
		if (target != null && target.isAlive()) {
			double range = holdingSword ? DRAW_BOW_DISTANCE : DRAW_SWORD_DISTANCE;
			wantSword = mob.squaredDistanceTo(target) < range * range;
		}
		if (wantSword == holdingSword) {
			return false;
		}
		mob.equipStack(EquipmentSlot.MAINHAND, off);
		mob.equipStack(EquipmentSlot.OFFHAND, main);
		return true;
	}
}
