package com.eternalreturn.client;

import com.eternalreturn.items.Speargun;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.item.CrossbowItem;
import net.minecraft.util.Identifier;

/**
 * The speargun drawn as a crossbow is (pulling, pull, charged: models/item/speargun*.json), held as one
 * (SpeargunHeldItemMixin, SpeargunArmPoseMixin); its harpoons (HarpoonRenderer).
 */
public final class SpeargunClient {
	private SpeargunClient() {
	}

	public static void register() {
		ModelPredicateProviderRegistry.register(Speargun.SPEARGUN, Identifier.ofVanilla("pull"), (stack, world, entity, seed) -> entity == null
				|| CrossbowItem.isCharged(stack) ? 0.0F : (float) (stack.getMaxUseTime(entity) - entity.getItemUseTimeLeft()) / CrossbowItem.getPullTime(stack, entity));
		ModelPredicateProviderRegistry.register(Speargun.SPEARGUN, Identifier.ofVanilla("pulling"), (stack, world, entity, seed) -> entity != null
				&& entity.isUsingItem() && entity.getActiveItem() == stack && !CrossbowItem.isCharged(stack) ? 1.0F : 0.0F);
		ModelPredicateProviderRegistry.register(Speargun.SPEARGUN, Identifier.ofVanilla("charged"),
				(stack, world, entity, seed) -> CrossbowItem.isCharged(stack) ? 1.0F : 0.0F);
		EntityRendererRegistry.register(Speargun.HARPOON, HarpoonRenderer::new);
	}
}
