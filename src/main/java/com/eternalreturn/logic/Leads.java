package com.eternalreturn.logic;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Leads as in 1.21.6:
 * <ul>
 * <li>sneak and right-click a mob while holding others on leads: they're tied to it instead;</li>
 * <li>shears cut a mob's lead (the lead drops);</li>
 * <li>a lead used on a mob tied to something else snaps that lead (it drops) and ties the mob to the player;</li>
 * <li>a lead stretches to 12 blocks before it snaps (10 before), with a snap you can hear (LeashTickMixin);</li>
 * <li>leads are drawn twice as thick (client LeashThicknessMixin) and made of 5 string (two of them).</li>
 * </ul>
 */
public final class Leads {
	/** How far a lead stretches before it snaps. */
	public static final float SNAP = 12.0F;
	/** Vanilla's snapping length, from which it pulls hard (up to SNAP). */
	public static final float VANILLA_SNAP = 10.0F;

	private Leads() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register(Leads::use);
	}

	/** The mobs (and boats) this player holds on leads, near enough to hand over. */
	public static List<Entity> held(PlayerEntity player) {
		return player.getWorld().getOtherEntities(player, new Box(player.getBlockPos()).expand(SNAP),
				entity -> entity instanceof Leashable leashable && leashable.getLeashHolder() == player);
	}

	public static ActionResult use(PlayerEntity player, World world, Hand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (hit != null || player.isSpectator() || !(entity instanceof Leashable leashable)) {
			return ActionResult.PASS;
		}
		ItemStack stack = player.getStackInHand(hand);
		if (stack.isOf(Items.SHEARS) && leashable.isLeashed()) {
			if (!world.isClient) {
				leashable.detachLeash(true, true);
				world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.PLAYERS, 1.0F, 1.2F);
				stack.damage(1, player, LivingEntity.getSlotForHand(hand));
			}
			return ActionResult.success(world.isClient);
		}
		if (stack.isOf(Items.LEAD) && leashable.isLeashed() && leashable.getLeashHolder() != player && !player.isSneaking()) {
			if (!world.isClient) {
				snap(entity, leashable);
				leashable.attachLeash(player, true);
				world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENTITY_LEASH_KNOT_PLACE, SoundCategory.NEUTRAL, 1.0F, 1.0F);
				stack.decrementUnlessCreative(1, player);
			}
			return ActionResult.success(world.isClient);
		}
		if (player.isSneaking() && hand == Hand.MAIN_HAND) {
			List<Entity> held = held(player);
			held.remove(entity);
			if (held.isEmpty()) {
				return ActionResult.PASS;
			}
			if (!world.isClient) {
				for (Entity tied : held) {
					((Leashable) tied).attachLeash(entity, true);
				}
				world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENTITY_LEASH_KNOT_PLACE, SoundCategory.NEUTRAL, 1.0F, 1.0F);
			}
			return ActionResult.success(world.isClient);
		}
		return ActionResult.PASS;
	}

	/** The lead snaps: it drops, with a snap. */
	public static void snap(Entity entity, Leashable leashable) {
		leashable.detachLeash(true, true);
		entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENTITY_LEASH_KNOT_BREAK, SoundCategory.NEUTRAL, 1.0F,
				1.0F);
	}
}
