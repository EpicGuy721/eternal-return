package com.eternalreturn.logic;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.random.Random;

/**
 * Five more curses, found like the others (loot, trades, never the table):
 * <ul>
 * <li>Curse of Visibility (chestplates): whoever wears it glows, for as long as they wear it;</li>
 * <li>Curse of Blindness (helmets): whoever wears it is blind, for as long as they wear it;</li>
 * <li>Curse of Heaviness (armor): the piece weighs twice as much (ArmorWeight); not with Weightless;</li>
 * <li>Curse of Slipperiness (tools and weapons): each time the player uses it (hits, starts digging, uses
 * it on a block or in the air), a 0.5% chance it slips out of their hand onto the ground instead;</li>
 * <li>Curse of Frailness (anything with durability): whenever it takes damage, half the time it takes
 * twice as much; not with Unbreaking.</li>
 * </ul>
 */
public final class Curses {
	public static final Identifier VISIBILITY = EternalReturn.id("visibility_curse");
	public static final Identifier BLINDNESS = EternalReturn.id("blindness_curse");
	public static final Identifier HEAVINESS = EternalReturn.id("heaviness_curse");
	public static final Identifier SLIPPERINESS = EternalReturn.id("slipperiness_curse");
	public static final Identifier FRAILNESS = EternalReturn.id("frailness_curse");
	public static final double SLIP_CHANCE = 0.005;

	private Curses() {
	}

	public static boolean has(ItemStack stack, Identifier curse) {
		return NewEnchantments.level(stack, curse) > 0;
	}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> slip(player, hand) ? ActionResult.FAIL : ActionResult.PASS);
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> slip(player, hand) ? ActionResult.FAIL : ActionResult.PASS);
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> slip(player, hand) ? ActionResult.FAIL : ActionResult.PASS);
		UseItemCallback.EVENT.register((player, world, hand) -> slip(player, hand)
				? TypedActionResult.fail(player.getStackInHand(hand)) : TypedActionResult.pass(player.getStackInHand(hand)));
	}

	/** Curse of Slipperiness: maybe the tool in that hand falls out of it. Server side; true if it did. */
	public static boolean slip(PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (player.getWorld().isClient || player.isSpectator() || !has(stack, SLIPPERINESS) || player.getRandom().nextDouble() >= SLIP_CHANCE) {
			return false;
		}
		player.setStackInHand(hand, ItemStack.EMPTY);
		player.dropItem(stack, false, true);
		return true;
	}

	/** Every second, server side: the wearer of a cursed chestplate glows, of a cursed helmet is blind. */
	public static void tickWorn(LivingEntity wearer) {
		if (has(wearer.getEquippedStack(EquipmentSlot.CHEST), VISIBILITY)) {
			wearer.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 40, 0, true, false, true));
		}
		if (has(wearer.getEquippedStack(EquipmentSlot.HEAD), BLINDNESS)) {
			wearer.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0, true, false, true));
		}
	}

	/** Curse of Frailness: the damage an item is about to take, doubled half the time. */
	public static int frail(ItemStack stack, int amount, Random random) {
		return amount > 0 && has(stack, FRAILNESS) && random.nextBoolean() ? amount * 2 : amount;
	}
}
