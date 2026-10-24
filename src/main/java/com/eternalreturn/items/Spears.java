package com.eternalreturn.items;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.logic.SpearCombat;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ToolItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Spears, as in later versions: wooden, stone, copper, iron, golden and diamond (the material over two sticks on a
 * slant), and netherite (a diamond spear upgraded at a smithing table). They fight two ways (SpearCombat):
 * <ul>
 * <li>a jab (attack), once the attack is fully charged: a stab along the line of sight at everything between 2 and
 * 4.5 blocks off (6.5 in creative), through grass and other blocks one can walk through. No critical hits, no
 * sweeps, and they break no blocks;</li>
 * <li>a charge (hold use): after a moment to level the spear, it hurts whatever it meets by how fast they meet, the
 * tier's multiplier times the closing speed in blocks a second (4.6 or more). While fresh it also knocks back (5.1 or
 * more) and, freshest, throws riders off their mounts (at a speed by tier); then it tires and ends by itself. The
 * player can sprint while charging.</li>
 * </ul>
 * The numbers per tier are the later versions' (Tier). They take the sword enchantments but Sweeping Edge
 * (tools/enchantments/build_enchantments.py) and their material's enchant capacity. Zombies, husks and zombie villagers
 * may carry an iron spear, piglins and zombified piglins a golden one; those charge with it now and then (SpearMobs).
 */
public final class Spears {
	public static final TagKey<Item> TAG = TagKey.of(RegistryKeys.ITEM, EternalReturn.id("spears"));

	/**
	 * A tier: its jab's damage and speed (as attribute bonuses), and its charge: the ticks to level it, the damage per
	 * block a second of closing speed, and for how long (ticks once level) it unhorses riders (and from what speed),
	 * knocks back, and hurts at all.
	 */
	public record Tier(String name, ToolMaterial material, double damage, double speed, int delay, float multiplier, int dismountTicks,
			float dismountSpeed, int knockbackTicks, int damageTicks) {
	}

	public static final Spear WOODEN = register(new Tier("wooden", ToolMaterials.WOOD, 0.0, -2.4615, 15, 0.7F, 100, 14.0F, 200, 300), new Item.Settings());
	public static final Spear STONE = register(new Tier("stone", ToolMaterials.STONE, 1.0, -2.6667, 14, 0.82F, 90, 13.0F, 180, 275), new Item.Settings());
	public static final Spear COPPER = register(new Tier("copper", Materials.Tool.COPPER, 1.0, -2.8235, 13, 0.82F, 80, 12.0F, 165, 250), new Item.Settings());
	public static final Spear IRON = register(new Tier("iron", ToolMaterials.IRON, 2.0, -2.9474, 12, 0.95F, 50, 11.0F, 135, 225), new Item.Settings());
	public static final Spear GOLDEN = register(new Tier("golden", ToolMaterials.GOLD, 0.0, -2.9474, 14, 0.7F, 70, 13.0F, 170, 275), new Item.Settings());
	public static final Spear DIAMOND = register(new Tier("diamond", ToolMaterials.DIAMOND, 3.0, -3.0476, 10, 1.075F, 60, 10.0F, 130, 200),
			new Item.Settings());
	public static final Spear NETHERITE = register(new Tier("netherite", ToolMaterials.NETHERITE, 4.0, -3.1304, 8, 1.2F, 50, 9.0F, 110, 175),
			new Item.Settings().fireproof());
	public static final List<Spear> ALL = List.of(WOODEN, STONE, COPPER, IRON, GOLDEN, DIAMOND, NETHERITE);

	private Spears() {
	}

	private static Spear register(Tier tier, Item.Settings settings) {
		return Registry.register(Registries.ITEM, EternalReturn.id(tier.name() + "_spear"), new Spear(tier, settings));
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.addAfter(Items.NETHERITE_SWORD, ALL.toArray(Item[]::new)));
		FuelRegistry.INSTANCE.add(WOODEN, 200);
		SpearCombat.register();
	}

	public static class Spear extends ToolItem {
		private final Tier tier;

		public Spear(Tier tier, Item.Settings settings) {
			super(tier.material(), settings.attributeModifiers(AttributeModifiersComponent.builder()
					.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(BASE_ATTACK_DAMAGE_MODIFIER_ID, tier.damage(),
							EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND)
					.add(EntityAttributes.GENERIC_ATTACK_SPEED, new EntityAttributeModifier(BASE_ATTACK_SPEED_MODIFIER_ID, tier.speed(),
							EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND)
					.build()));
			this.tier = tier;
		}

		public Tier tier() {
			return this.tier;
		}

		/** Spears break no blocks. */
		@Override
		public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity miner) {
			return false;
		}

		@Override
		public UseAction getUseAction(ItemStack stack) {
			return UseAction.NONE;
		}

		/** A charge lasts until it has tired out completely. */
		@Override
		public int getMaxUseTime(ItemStack stack, LivingEntity user) {
			return this.tier.delay() + this.tier.damageTicks();
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);
			user.setCurrentHand(hand);
			SpearCombat.lowered(user);
			return TypedActionResult.consume(stack);
		}

		@Override
		public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
			SpearCombat.charge(user, stack, this.getMaxUseTime(stack, user) - remainingUseTicks);
		}
	}
}
