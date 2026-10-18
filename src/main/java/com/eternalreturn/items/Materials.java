package com.eternalreturn.items;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.Block;
import net.minecraft.item.AnimalArmorItem;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.AxeItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Util;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;

/**
 * Copper and rose gold gear. Copper sits between stone and iron (as in the modern game): it mines what stone does,
 * a bit faster, 190 uses; its armor protects between leather and chainmail. Rose gold (4 gold and 4 copper ingots
 * make 8) mines as fast as gold with copper's 190 uses and mines what stone does; its armor protects like gold's.
 * Inherent enchantments and capacity: tags inherent/* and enchant_cap/* (EternalReturnConfig). Resources from
 * tools/materials/build_materials.py.
 */
public final class Materials {
	public static final Item ROSEGOLD_INGOT = item("rosegold_ingot", new Item(new Item.Settings()));

	public enum Tool implements ToolMaterial {
		COPPER(BlockTags.INCORRECT_FOR_STONE_TOOL, 190, 5.0F, 1.0F, 13, () -> Ingredient.ofItems(Items.COPPER_INGOT)),
		ROSEGOLD(BlockTags.INCORRECT_FOR_STONE_TOOL, 190, 12.0F, 0.0F, 22, () -> Ingredient.ofItems(ROSEGOLD_INGOT));

		private final TagKey<Block> incorrect;
		private final int durability;
		private final float speed;
		private final float damage;
		private final int enchantability;
		private final Supplier<Ingredient> repair;

		Tool(TagKey<Block> incorrect, int durability, float speed, float damage, int enchantability, Supplier<Ingredient> repair) {
			this.incorrect = incorrect;
			this.durability = durability;
			this.speed = speed;
			this.damage = damage;
			this.enchantability = enchantability;
			this.repair = repair;
		}

		@Override
		public int getDurability() {
			return this.durability;
		}

		@Override
		public float getMiningSpeedMultiplier() {
			return this.speed;
		}

		@Override
		public float getAttackDamage() {
			return this.damage;
		}

		@Override
		public TagKey<Block> getInverseTag() {
			return this.incorrect;
		}

		@Override
		public int getEnchantability() {
			return this.enchantability;
		}

		@Override
		public Ingredient getRepairIngredient() {
			return this.repair.get();
		}
	}

	public static final RegistryEntry<ArmorMaterial> COPPER_ARMOR = armor("copper", 1, 3, 4, 2, 5, 8, SoundEvents.ITEM_ARMOR_EQUIP_IRON,
			() -> Ingredient.ofItems(Items.COPPER_INGOT));
	public static final RegistryEntry<ArmorMaterial> ROSEGOLD_ARMOR = armor("rosegold", 1, 3, 5, 2, 7, 25, SoundEvents.ITEM_ARMOR_EQUIP_GOLD,
			() -> Ingredient.ofItems(ROSEGOLD_INGOT));
	/** Copper armor lasts as long as the modern game's (11 times a piece's base durability), rose gold too. */
	private static final int DURABILITY = 11;

	public static final List<Item> COPPER = gear("copper", Tool.COPPER, COPPER_ARMOR);
	public static final List<Item> ROSEGOLD = gear("rosegold", Tool.ROSEGOLD, ROSEGOLD_ARMOR);

	private Materials() {
	}

	private static RegistryEntry<ArmorMaterial> armor(String name, int boots, int leggings, int chestplate, int helmet, int body, int enchantability,
			RegistryEntry<net.minecraft.sound.SoundEvent> sound, Supplier<Ingredient> repair) {
		return Registry.registerReference(Registries.ARMOR_MATERIAL, EternalReturn.id(name), new ArmorMaterial(
				Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
					map.put(ArmorItem.Type.BOOTS, boots);
					map.put(ArmorItem.Type.LEGGINGS, leggings);
					map.put(ArmorItem.Type.CHESTPLATE, chestplate);
					map.put(ArmorItem.Type.HELMET, helmet);
					map.put(ArmorItem.Type.BODY, body);
				}),
				enchantability, sound, repair, List.of(new ArmorMaterial.Layer(EternalReturn.id(name))), 0.0F, 0.0F));
	}

	private static List<Item> gear(String name, Tool tool, RegistryEntry<ArmorMaterial> armor) {
		return List.of(
				item(name + "_sword", new SwordItem(tool, new Item.Settings().attributeModifiers(SwordItem.createAttributeModifiers(tool, 3, -2.4F)))),
				item(name + "_shovel", new ShovelItem(tool, new Item.Settings().attributeModifiers(ShovelItem.createAttributeModifiers(tool, 1.5F, -3.0F)))),
				item(name + "_pickaxe", new PickaxeItem(tool, new Item.Settings().attributeModifiers(PickaxeItem.createAttributeModifiers(tool, 1.0F, -2.8F)))),
				item(name + "_axe", new AxeItem(tool, new Item.Settings().attributeModifiers(AxeItem.createAttributeModifiers(tool, 7.0F, -3.2F)))),
				item(name + "_hoe", new HoeItem(tool, new Item.Settings().attributeModifiers(HoeItem.createAttributeModifiers(tool, -1.0F, -2.0F)))),
				item(name + "_helmet", new ArmorItem(armor, ArmorItem.Type.HELMET, new Item.Settings().maxDamage(ArmorItem.Type.HELMET.getMaxDamage(DURABILITY)))),
				item(name + "_chestplate", new ArmorItem(armor, ArmorItem.Type.CHESTPLATE,
						new Item.Settings().maxDamage(ArmorItem.Type.CHESTPLATE.getMaxDamage(DURABILITY)))),
				item(name + "_leggings", new ArmorItem(armor, ArmorItem.Type.LEGGINGS, new Item.Settings().maxDamage(ArmorItem.Type.LEGGINGS.getMaxDamage(DURABILITY)))),
				item(name + "_boots", new ArmorItem(armor, ArmorItem.Type.BOOTS, new Item.Settings().maxDamage(ArmorItem.Type.BOOTS.getMaxDamage(DURABILITY)))),
				item(name + "_horse_armor", new AnimalArmorItem(armor, AnimalArmorItem.Type.EQUESTRIAN, false, new Item.Settings().maxCount(1))));
	}

	private static Item item(String id, Item item) {
		return Registry.register(Registries.ITEM, EternalReturn.id(id), item);
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.addAfter(Items.GOLD_INGOT, ROSEGOLD_INGOT));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
			entries.addAfter(Items.STONE_HOE, COPPER.subList(1, 5).toArray(Item[]::new));
			entries.addAfter(Items.GOLDEN_HOE, ROSEGOLD.subList(1, 5).toArray(Item[]::new));
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
			entries.addAfter(Items.STONE_SWORD, COPPER.get(0));
			entries.addAfter(Items.GOLDEN_SWORD, ROSEGOLD.get(0));
			entries.addAfter(Items.CHAINMAIL_BOOTS, COPPER.subList(5, 9).toArray(Item[]::new));
			entries.addAfter(Items.GOLDEN_BOOTS, ROSEGOLD.subList(5, 9).toArray(Item[]::new));
			entries.addAfter(Items.LEATHER_HORSE_ARMOR, COPPER.get(9));
			entries.addAfter(Items.GOLDEN_HORSE_ARMOR, ROSEGOLD.get(9));
		});
	}
}
