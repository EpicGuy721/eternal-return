package com.eternalreturn.survival;

import com.eternalreturn.EternalReturn;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

import java.util.Map;

/**
 * Soulbound (one level, any enchantable item): the item stays with you when you die. Just before a dying
 * player's inventory is dropped, Soulbound items are taken out and kept with the player (saved, in case
 * they log off before respawning); on respawn each goes back to the slot it was in (or the first free
 * slot, if something is there).
 */
public final class Soulbound {
	public static final Identifier ID = EternalReturn.id("soulbound");
	public static final String NBT_KEY = "EternalReturnSoulbound";

	/** What a player keeps between death and respawn (survival/ServerPlayerEntityMixin). */
	public interface Holder {
		Map<Integer, ItemStack> eternalreturn$soulbound();
	}

	private Soulbound() {
	}

	public static boolean has(ItemStack stack) {
		ItemEnchantmentsComponent enchantments = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
		return enchantments.getEnchantments().stream().anyMatch(entry -> entry.matchesId(ID));
	}

	/** Before the inventory drops: takes the Soulbound items out, remembering their slots. */
	public static void keep(PlayerEntity player) {
		Map<Integer, ItemStack> kept = ((Holder) player).eternalreturn$soulbound();
		PlayerInventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (!stack.isEmpty() && has(stack)) {
				kept.put(slot, stack);
				inventory.setStack(slot, ItemStack.EMPTY);
			}
		}
	}

	/** On respawn: gives back what the old player kept. */
	public static void restore(PlayerEntity oldPlayer, PlayerEntity newPlayer) {
		Map<Integer, ItemStack> kept = ((Holder) oldPlayer).eternalreturn$soulbound();
		PlayerInventory inventory = newPlayer.getInventory();
		for (Map.Entry<Integer, ItemStack> entry : kept.entrySet()) {
			if (entry.getKey() < inventory.size() && inventory.getStack(entry.getKey()).isEmpty()) {
				inventory.setStack(entry.getKey(), entry.getValue());
			} else if (!inventory.insertStack(entry.getValue())) {
				newPlayer.dropItem(entry.getValue(), false);
			}
		}
		kept.clear();
	}

	public static NbtList write(Map<Integer, ItemStack> kept, RegistryWrapper.WrapperLookup registries) {
		NbtList list = new NbtList();
		kept.forEach((slot, stack) -> {
			NbtCompound entry = (NbtCompound) stack.encode(registries);
			entry.putInt("EternalReturnSlot", slot);
			list.add(entry);
		});
		return list;
	}

	public static void read(NbtList list, Map<Integer, ItemStack> kept, RegistryWrapper.WrapperLookup registries) {
		kept.clear();
		for (int i = 0; i < list.size(); i++) {
			NbtCompound entry = list.getCompound(i);
			ItemStack.fromNbt(registries, entry).ifPresent(stack -> kept.put(entry.getInt("EternalReturnSlot"), stack));
		}
	}

	public static final int NBT_TYPE = NbtElement.LIST_TYPE;
}
