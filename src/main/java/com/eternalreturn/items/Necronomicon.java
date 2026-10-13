package com.eternalreturn.items;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.mobs.Servants;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The Necronomicon (a philosopher's stone over a book, ringed by 7 wither skeleton skulls; 666 uses).
 * Right-click a block (or the air) to raise a random undead servant there (Servants), up to 3 at once.
 */
public final class Necronomicon extends Item {
	public static final Item ITEM = Registry.register(EternalReturn.id("necronomicon"), new Necronomicon(new Item.Settings().maxDamage(666).rarity(Rarity.EPIC)));

	private Necronomicon(Settings settings) {
		super(settings);
	}

	private static final class Registry {
		static Item register(net.minecraft.util.Identifier id, Item item) {
			return net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.ITEM, id, item);
		}
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.addAfter(PhilosophersStone.ITEM, ITEM));
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		BlockPos at = context.getBlockPos().offset(context.getSide());
		return this.summon(context.getWorld(), context.getPlayer(), context.getStack(), context.getHand(), Vec3d.ofBottomCenter(at));
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		Vec3d ahead = user.getPos().add(user.getRotationVector().multiply(2.0).withAxis(net.minecraft.util.math.Direction.Axis.Y, 0.0));
		return new TypedActionResult<>(this.summon(world, user, stack, hand, ahead), stack);
	}

	private ActionResult summon(World world, PlayerEntity user, ItemStack stack, Hand hand, Vec3d at) {
		if (user == null) {
			return ActionResult.PASS;
		}
		if (!(world instanceof ServerWorld server)) {
			return ActionResult.SUCCESS;
		}
		if (Servants.count(user) >= Servants.MAX) {
			user.sendMessage(Text.translatable("item.eternalreturn.necronomicon.full", Servants.MAX), true);
			return ActionResult.FAIL;
		}
		if (Servants.summon(server, user, at) == null) {
			return ActionResult.FAIL;
		}
		stack.damage(1, user, LivingEntity.getSlotForHand(hand));
		user.getItemCooldownManager().set(this, 20);
		return ActionResult.CONSUME;
	}
}
