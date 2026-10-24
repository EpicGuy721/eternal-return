package com.eternalreturn.client;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.items.Spears;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.Identifier;

/**
 * Spears on the client (Spears): a spear is drawn from its in-hand model (models/item/spear_in_hand.json: pointing
 * ahead in first person, a spear's size in third) when held, and from its flat icon in inventories, on the ground and
 * in frames (SpearItemModelMixin). Jabs, charging at a run and the levelled spear: SpearAttackMixin,
 * SpearMovementMixin, SpearArmPoseMixin, SpearFirstPersonMixin.
 */
public final class SpearClient {
	private SpearClient() {
	}

	private static Identifier inHandId(Spears.Spear spear) {
		return EternalReturn.id("item/" + spear.tier().name() + "_spear_in_hand");
	}

	public static void register() {
		ModelLoadingPlugin.register(context -> context.addModels(Spears.ALL.stream().map(SpearClient::inHandId).toList()));
	}

	public static BakedModel inHand(Spears.Spear spear) {
		return MinecraftClient.getInstance().getBakedModelManager().getModel(inHandId(spear));
	}
}
