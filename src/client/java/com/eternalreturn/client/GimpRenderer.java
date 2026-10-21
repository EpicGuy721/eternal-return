package com.eternalreturn.client;

import com.eternalreturn.mobs.entity.GimpEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

/** The gimp (GimpModel); on walls and ceilings turned to fit them, as all surface walkers are (ClimberRendering). */
public class GimpRenderer extends MobEntityRenderer<GimpEntity, GimpModel> {
	public GimpRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new GimpModel(ctx.getPart(GimpModel.LAYER)), 0.9F);
	}

	@Override
	public Identifier getTexture(GimpEntity gimp) {
		return GimpModel.TEXTURE;
	}

	/** Dies rolled onto its back, as a spider does. */
	@Override
	protected float getLyingAngle(GimpEntity gimp) {
		return 180.0F;
	}
}
