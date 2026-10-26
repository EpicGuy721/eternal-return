package com.eternalreturn.client;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.mobs.entity.SunkenEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.SkeletonEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

/** The Sunken (SunkenModel), in its coral's colours (textures/entity/sunken/<variant>.png), armor as a skeleton wears it. */
public class SunkenRenderer extends SkeletonEntityRenderer<SunkenEntity> {
	private static final Identifier[] TEXTURES = new Identifier[SunkenEntity.VARIANTS.length];

	static {
		for (int i = 0; i < TEXTURES.length; i++) {
			TEXTURES[i] = EternalReturn.id("textures/entity/sunken/" + SunkenEntity.VARIANTS[i] + ".png");
		}
	}

	public SunkenRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, EntityModelLayers.SKELETON_INNER_ARMOR, EntityModelLayers.SKELETON_OUTER_ARMOR, new SunkenModel(ctx.getPart(SunkenModel.LAYER)));
	}

	@Override
	public Identifier getTexture(SunkenEntity sunken) {
		return TEXTURES[sunken.getVariant()];
	}
}
