package com.eternalreturn.client;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.birds.BirdEntity;
import com.eternalreturn.birds.BirdTable;
import com.eternalreturn.birds.BirdVariant;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Birds, drawn as tropical fish are: the shape's model in its gray base texture tinted with the base colour, then each
 * of its patterns (a mask texture tinted with that pattern's colour), then the parts that are never tinted (eyes, beak,
 * legs). Textures: textures/entity/bird/<model>/, painted by tools/birds/paint_birds.py.
 */
public class BirdRenderer extends MobEntityRenderer<BirdEntity, BirdModel> {
	private final Map<String, BirdModel> models = new HashMap<>();

	public BirdRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new BirdModel(ctx.getPart(layer("songbird")), BirdModels.PARTS.get("songbird")), 0.2F);
		for (String model : BirdModels.MODELS) {
			this.models.put(model, new BirdModel(ctx.getPart(layer(model)), BirdModels.PARTS.get(model)));
		}
		this.addFeature(new Layers(this));
	}

	public static EntityModelLayer layer(String model) {
		return new EntityModelLayer(EternalReturn.id("bird_" + model), "main");
	}

	static Identifier texture(String model, String layer) {
		return EternalReturn.id("textures/entity/bird/" + model + "/" + layer + ".png");
	}

	@Override
	public Identifier getTexture(BirdEntity bird) {
		return texture(bird.getShape().model(), "base");
	}

	@Override
	public void render(BirdEntity bird, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		BirdModel model = this.models.get(bird.getShape().model());
		this.model = model;
		model.setColorMultiplier(0xFF000000 | BirdTable.RGB.get(BirdVariant.base(bird.getVariant())));
		super.render(bird, yaw, tickDelta, matrices, consumers, light);
		model.setColorMultiplier(-1);
	}

	/** The patterns, each tinted with its colour, and the untinted eyes, beak and legs. */
	static class Layers extends FeatureRenderer<BirdEntity, BirdModel> {
		Layers(FeatureRendererContext<BirdEntity, BirdModel> context) {
			super(context);
		}

		@Override
		public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, BirdEntity bird, float limbAngle, float limbDistance,
				float tickDelta, float animationProgress, float headYaw, float headPitch) {
			if (bird.isInvisible()) {
				return;
			}
			BirdModel model = this.getContextModel();
			String folder = bird.getShape().model();
			int variant = bird.getVariant();
			model.setColorMultiplier(-1);
			for (int layer = 0; layer < 2; layer++) {
				int pattern = BirdVariant.pattern(variant, layer);
				if (pattern > 0 && pattern <= BirdTable.PATTERNS.size()) {
					int colour = 0xFF000000 | BirdTable.RGB.get(BirdVariant.colour(variant, layer));
					renderModel(model, texture(folder, "pattern_" + BirdTable.PATTERNS.get(pattern - 1)), matrices, consumers, light, bird, colour);
				}
			}
			renderModel(model, texture(folder, "details"), matrices, consumers, light, bird, -1);
		}
	}
}
