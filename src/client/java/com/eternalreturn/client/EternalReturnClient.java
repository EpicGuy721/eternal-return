package com.eternalreturn.client;

import com.eternalreturn.logic.EnchantCaps;
import com.eternalreturn.logic.EnchantLevels;
import com.eternalreturn.logic.InherentEnchantments;
import com.eternalreturn.mobs.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EternalReturnClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.ZOMBIE_HOOK, ZombieHookEntityRenderer::new);

		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (lines.isEmpty() || EnchantLevels.isConcealed(stack)) {
				return;
			}
			List<Text> extra = new ArrayList<>();

			int cap = EnchantCaps.capFor(stack);
			if (cap != EnchantCaps.UNCAPPED) {
				int used = EnchantCaps.pointsUsed(stack);
				extra.add(Text.translatable("tooltip.eternalreturn.capacity", used, cap)
						.formatted(used > cap ? Formatting.RED : Formatting.DARK_GRAY));
			}

			Map<RegistryEntry<Enchantment>, Integer> inherent = InherentEnchantments.inherentPortion(stack);
			if (!inherent.isEmpty()) {
				MutableText names = Text.empty();
				boolean first = true;
				for (Map.Entry<RegistryEntry<Enchantment>, Integer> e : inherent.entrySet()) {
					if (!first) {
						names.append(", ");
					}
					names.append(Enchantment.getName(e.getKey(), e.getValue()));
					first = false;
				}
				extra.add(Text.translatable("tooltip.eternalreturn.inherent", names).formatted(Formatting.DARK_GRAY));
			}

			// Right under the item name, above the enchantment list.
			lines.addAll(1, extra);
		});
	}
}
