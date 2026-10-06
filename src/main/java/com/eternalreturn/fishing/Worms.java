package com.eternalreturn.fishing;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

/** Worms turn up now and then when a player digs dirt, grass or mud (block tag eternalreturn:drops_worms). */
public final class Worms {
	public static final TagKey<Block> DROPS_WORMS = TagKey.of(RegistryKeys.BLOCK, EternalReturn.id("drops_worms"));

	private Worms() {
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (!world.isClient && !player.isCreative() && state.isIn(DROPS_WORMS)
					&& world.random.nextFloat() < EternalReturnConfig.get().fishing.wormDropChance) {
				Block.dropStack(world, pos, new ItemStack(FishingItems.WORM));
			}
		});
	}
}
