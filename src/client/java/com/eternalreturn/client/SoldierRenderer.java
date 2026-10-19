package com.eternalreturn.client;

import com.eternalreturn.mobs.entity.SoldierEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;

/** The soldier (SoldierModel), his rifle in his hand. */
public class SoldierRenderer extends BipedEntityRenderer<SoldierEntity, SoldierModel> {
	public SoldierRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new SoldierModel(ctx.getPart(SoldierModel.LAYER)), 0.5F);
	}

	@Override
	public Identifier getTexture(SoldierEntity entity) {
		return SoldierModel.TEXTURE;
	}
}
