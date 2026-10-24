package com.eternalreturn.client;

import com.eternalreturn.logic.EnchantCaps;
import com.eternalreturn.logic.EnchantLevels;
import com.eternalreturn.logic.InherentEnchantments;
import com.eternalreturn.mobs.ModEntities;
import com.eternalreturn.survival.ArmorWeight;
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
		EntityRendererRegistry.register(ModEntities.WEB, net.minecraft.client.render.entity.FlyingItemEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.DYNAMITE, net.minecraft.client.render.entity.FlyingItemEntityRenderer::new);
		ForestRendering.register();
		NewMobRendering.register();
		HardenedBlockModels.register();
		OreVariantRendering.register();
		FrameModels.register();
		GaleStepClient.register();
		SpearClient.register();
		SwimKeyClient.register();
		DamageNumbers.register();
		net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(com.eternalreturn.blocks.CopperRail.BLOCK,
				net.minecraft.client.render.RenderLayer.getCutout());

		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (lines.isEmpty() || EnchantLevels.isConcealed(stack)) {
				return;
			}
			List<Text> extra = new ArrayList<>();

			// Food: how much it heals and how fast, and its effects (Meals).
			com.eternalreturn.survival.Meals.Heal heal = com.eternalreturn.survival.Meals.enabled() ? com.eternalreturn.survival.Meals.heal(stack) : null;
			if (heal != null) {
				String hearts = String.format(java.util.Locale.ROOT, heal.health() % 2.0F == 0.0F ? "%.0f" : "%.1f", heal.health() / 2.0F);
				extra.add((heal.seconds() > 0.0F
						? Text.translatable("tooltip.eternalreturn.heals_over", hearts, String.format(java.util.Locale.ROOT, "%.0f", heal.seconds()))
						: Text.translatable("tooltip.eternalreturn.heals", hearts)).formatted(Formatting.RED));
				var food = stack.get(net.minecraft.component.DataComponentTypes.FOOD);
				if (food != null) {
					for (var effect : food.effects()) {
						MutableText line = Text.translatable(effect.effect().getTranslationKey());
						if (effect.effect().getAmplifier() > 0) {
							line.append(" ").append(Text.translatable("enchantment.level." + (effect.effect().getAmplifier() + 1)));
						}
						line.append(" (").append(net.minecraft.entity.effect.StatusEffectUtil.getDurationText(effect.effect(), 1.0F, 20.0F)).append(")");
						if (effect.probability() < 1.0F) {
							line.append(String.format(java.util.Locale.ROOT, ", %.0f%%", effect.probability() * 100.0F));
						}
						extra.add(line.formatted(effect.effect().getEffectType().value().isBeneficial() ? Formatting.BLUE : Formatting.RED));
					}
				}
			}

			int cap = EnchantCaps.capFor(stack);
			if (cap != EnchantCaps.UNCAPPED) {
				int used = EnchantCaps.pointsUsed(stack);
				extra.add(Text.translatable("tooltip.eternalreturn.capacity", used, cap)
						.formatted(used > cap ? Formatting.RED : Formatting.DARK_GRAY));
			}

			// Armor weight: how much a piece slows its wearer (ArmorWeight).
			if (stack.getItem() instanceof net.minecraft.item.ArmorItem) {
				double weight = ArmorWeight.weight(stack);
				if (weight > 0.0) {
					extra.add(Text.translatable("tooltip.eternalreturn.weight", String.format(java.util.Locale.ROOT, "%.1f", weight * 100.0)).formatted(Formatting.DARK_GRAY));
				} else if (ArmorWeight.isWeightless(stack)) {
					extra.add(Text.translatable("tooltip.eternalreturn.weightless").formatted(Formatting.DARK_GRAY));
				}
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
