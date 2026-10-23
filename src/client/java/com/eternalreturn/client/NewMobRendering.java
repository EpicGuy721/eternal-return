package com.eternalreturn.client;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.items.IceBall;
import com.eternalreturn.logic.Necromancy;
import com.eternalreturn.mobs.NewMobs;
import com.eternalreturn.mobs.entity.BoneSpiderEntity;
import com.eternalreturn.mobs.entity.FlowerCowEntity;
import com.eternalreturn.mobs.entity.FrostbiteEntity;
import com.eternalreturn.mobs.entity.ParchedEntity;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.SkeletonEntityRenderer;
import net.minecraft.client.render.entity.ZombieBaseEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.SkeletonOverlayFeatureRenderer;
import net.minecraft.client.render.entity.model.CowEntityModel;
import net.minecraft.client.render.entity.model.DrownedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.SpiderEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Drawing frostbites, the parched, bone spiders, mooblooms and moolips (NewMobs), ice balls, and Necromancy's tooltip. */
public final class NewMobRendering {
	private NewMobRendering() {
	}

	public static void register() {
		EntityRendererRegistry.register(NewMobs.FROSTBITE, FrostbiteRenderer::new);
		EntityRendererRegistry.register(NewMobs.PARCHED, ParchedRenderer::new);
		EntityRendererRegistry.register(NewMobs.GRIMLOCK, GrimlockRenderer::new);
		EntityRendererRegistry.register(NewMobs.BONE_SPIDER, BoneSpiderRenderer::new);
		EntityModelLayerRegistry.registerModelLayer(BeholderModel.LAYER, BeholderModel::getTexturedModelData);
		EntityRendererRegistry.register(NewMobs.BEHOLDER, BeholderRenderer::new);
		EntityModelLayerRegistry.registerModelLayer(SoldierModel.LAYER, SoldierModel::getTexturedModelData);
		EntityRendererRegistry.register(NewMobs.SOLDIER, SoldierRenderer::new);
		EntityModelLayerRegistry.registerModelLayer(GimpModel.LAYER, GimpModel::getTexturedModelData);
		EntityRendererRegistry.register(NewMobs.GIMP, GimpRenderer::new);
		EntityModelLayerRegistry.registerModelLayer(AtropalModel.LAYER, AtropalModel::getTexturedModelData);
		EntityRendererRegistry.register(NewMobs.ATROPAL, AtropalRenderer::new);
		for (String model : BirdModels.MODELS) {
			EntityModelLayerRegistry.registerModelLayer(BirdRenderer.layer(model), () -> BirdModels.get(model));
		}
		EntityRendererRegistry.register(com.eternalreturn.birds.Birds.BIRD, BirdRenderer::new);
		EntityRendererRegistry.register(com.eternalreturn.items.Firearms.BULLET_ENTITY, ctx -> new FlyingItemEntityRenderer<>(ctx, 0.9F, true));
		EntityRendererRegistry.register(NewMobs.MOOBLOOM, ctx -> new FlowerCowRenderer(ctx, "moobloom"));
		EntityRendererRegistry.register(NewMobs.MOOLIP, ctx -> new FlowerCowRenderer(ctx, "moolip"));
		EntityRendererRegistry.register(IceBall.ENTITY, FlyingItemEntityRenderer::new);
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> lines.addAll(Necromancy.tooltip(stack)));
	}

	/** A frostbite: a drowned's body (a zombie's, with the drowned's arm poses), its skin, and an icy outer layer. */
	static class FrostbiteRenderer extends ZombieBaseEntityRenderer<FrostbiteEntity, DrownedEntityModel<FrostbiteEntity>> {
		private static final Identifier TEXTURE = EternalReturn.id("textures/entity/zombie/frostbite.png");
		private static final Identifier OUTER = EternalReturn.id("textures/entity/zombie/frostbite_outer_layer.png");

		FrostbiteRenderer(EntityRendererFactory.Context ctx) {
			super(ctx, new DrownedEntityModel<>(ctx.getPart(EntityModelLayers.DROWNED)), new DrownedEntityModel<>(ctx.getPart(EntityModelLayers.DROWNED_INNER_ARMOR)),
					new DrownedEntityModel<>(ctx.getPart(EntityModelLayers.DROWNED_OUTER_ARMOR)));
			DrownedEntityModel<FrostbiteEntity> outer = new DrownedEntityModel<>(ctx.getPart(EntityModelLayers.DROWNED_OUTER));
			this.addFeature(new FeatureRenderer<>(this) {
				@Override
				public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, FrostbiteEntity entity, float limbAngle, float limbDistance,
						float tickDelta, float animationProgress, float headYaw, float headPitch) {
					render(this.getContextModel(), outer, OUTER, matrices, consumers, light, entity, limbAngle, limbDistance, animationProgress, headYaw,
							headPitch, tickDelta, -1);
				}
			});
		}

		@Override
		public Identifier getTexture(net.minecraft.entity.mob.ZombieEntity entity) {
			return TEXTURE;
		}
	}

	/** The grimlock: a zombie's body, grey and eyeless, in tattered trousers. */
	static class GrimlockRenderer extends ZombieBaseEntityRenderer<net.minecraft.entity.mob.ZombieEntity, net.minecraft.client.render.entity.model.ZombieEntityModel<net.minecraft.entity.mob.ZombieEntity>> {
		private static final Identifier TEXTURE = EternalReturn.id("textures/entity/zombie/grimlock.png");

		GrimlockRenderer(EntityRendererFactory.Context ctx) {
			super(ctx, new net.minecraft.client.render.entity.model.ZombieEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE)),
					new net.minecraft.client.render.entity.model.ZombieEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE_INNER_ARMOR)),
					new net.minecraft.client.render.entity.model.ZombieEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE_OUTER_ARMOR)));
		}

		@Override
		public Identifier getTexture(net.minecraft.entity.mob.ZombieEntity entity) {
			return TEXTURE;
		}
	}

	/** The parched: a skeleton, with its wrappings as an outer layer (as a stray's clothes are). */
	static class ParchedRenderer extends SkeletonEntityRenderer<ParchedEntity> {
		private static final Identifier TEXTURE = EternalReturn.id("textures/entity/skeleton/parched.png");
		private static final Identifier OVERLAY = EternalReturn.id("textures/entity/skeleton/parched_overlay.png");

		ParchedRenderer(EntityRendererFactory.Context ctx) {
			super(ctx, EntityModelLayers.STRAY, EntityModelLayers.STRAY_INNER_ARMOR, EntityModelLayers.STRAY_OUTER_ARMOR);
			this.addFeature(new SkeletonOverlayFeatureRenderer<>(this, ctx.getModelLoader(), EntityModelLayers.STRAY_OUTER, OVERLAY));
		}

		@Override
		public Identifier getTexture(ParchedEntity entity) {
			return TEXTURE;
		}
	}

	/** A bone spider: a spider's body and its skin (its eyes are part of the skin, so no glowing eye layer). */
	static class BoneSpiderRenderer extends MobEntityRenderer<BoneSpiderEntity, SpiderEntityModel<BoneSpiderEntity>> {
		private static final Identifier TEXTURE = EternalReturn.id("textures/entity/spider/bone_spider.png");

		BoneSpiderRenderer(EntityRendererFactory.Context ctx) {
			super(ctx, new SpiderEntityModel<>(ctx.getPart(EntityModelLayers.SPIDER)), 0.8F);
		}

		@Override
		protected float getLyingAngle(BoneSpiderEntity spider) {
			return 180.0F;
		}

		@Override
		public Identifier getTexture(BoneSpiderEntity spider) {
			return TEXTURE;
		}
	}

	/** A moobloom or moolip: a cow with its skin and its flowers on its back and head, as a mooshroom's mushrooms. */
	static class FlowerCowRenderer extends MobEntityRenderer<FlowerCowEntity, CowEntityModel<FlowerCowEntity>> {
		private final Identifier texture;

		FlowerCowRenderer(EntityRendererFactory.Context ctx, String name) {
			super(ctx, new CowEntityModel<>(ctx.getPart(EntityModelLayers.COW)), 0.7F);
			this.texture = EternalReturn.id("textures/entity/cow/" + name + ".png");
			this.addFeature(new FlowerFeature(this, ctx.getBlockRenderManager()));
		}

		@Override
		public Identifier getTexture(FlowerCowEntity cow) {
			return this.texture;
		}
	}

	/** As vanilla's MooshroomMushroomFeatureRenderer places its mushrooms. */
	static class FlowerFeature extends FeatureRenderer<FlowerCowEntity, CowEntityModel<FlowerCowEntity>> {
		private final BlockRenderManager blocks;

		FlowerFeature(FeatureRendererContext<FlowerCowEntity, CowEntityModel<FlowerCowEntity>> context, BlockRenderManager blocks) {
			super(context);
			this.blocks = blocks;
		}

		@Override
		public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, FlowerCowEntity cow, float limbAngle, float limbDistance,
				float tickDelta, float animationProgress, float headYaw, float headPitch) {
			if (cow.isBaby() || cow.isInvisible() && !MinecraftClient.getInstance().hasOutline(cow)) {
				return;
			}
			BlockState flower = cow.flower();
			int overlay = LivingEntityRenderer.getOverlay(cow, 0.0F);
			matrices.push();
			matrices.translate(0.2F, -0.35F, 0.5F);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-48.0F));
			matrices.scale(-1.0F, -1.0F, 1.0F);
			matrices.translate(-0.5F, -0.5F, -0.5F);
			this.blocks.renderBlockAsEntity(flower, matrices, consumers, light, overlay);
			matrices.pop();
			matrices.push();
			matrices.translate(0.2F, -0.35F, 0.5F);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(42.0F));
			matrices.translate(0.1F, 0.0F, -0.6F);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-48.0F));
			matrices.scale(-1.0F, -1.0F, 1.0F);
			matrices.translate(-0.5F, -0.5F, -0.5F);
			this.blocks.renderBlockAsEntity(flower, matrices, consumers, light, overlay);
			matrices.pop();
			matrices.push();
			this.getContextModel().getHead().rotate(matrices);
			matrices.translate(0.0F, -0.7F, -0.2F);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-78.0F));
			matrices.scale(-1.0F, -1.0F, 1.0F);
			matrices.translate(-0.5F, -0.5F, -0.5F);
			this.blocks.renderBlockAsEntity(flower, matrices, consumers, light, overlay);
			matrices.pop();
		}
	}
}
