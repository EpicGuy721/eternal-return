package com.eternalreturn.items;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * The dragon rocket (8 firework rockets around a phantom membrane make 8): the one rocket that boosts a gliding
 * elytra, now that firework rockets don't (OldCombat). Used while not gliding, it does nothing.
 */
public final class DragonRocket extends Item {
	public static final Item ITEM = Registry.register(Registries.ITEM, EternalReturn.id("dragon_rocket"), new DragonRocket(new Item.Settings()));

	private DragonRocket(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!user.isFallFlying()) {
			return TypedActionResult.pass(stack);
		}
		if (!world.isClient) {
			ItemStack boost = new ItemStack(Items.FIREWORK_ROCKET);
			boost.set(DataComponentTypes.FIREWORKS, new FireworksComponent(1, List.of()));
			world.spawnEntity(new FireworkRocketEntity(world, boost, user));
			stack.decrementUnlessCreative(1, user);
			user.incrementStat(Stats.USED.getOrCreateStat(this));
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.addAfter(Items.FIREWORK_ROCKET, ITEM));
	}
}
