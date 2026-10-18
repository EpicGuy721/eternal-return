package com.eternalreturn.client;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.blocks.Woods;
import com.eternalreturn.items.WoodBoats;
import com.eternalreturn.mobs.ModEntities;
import com.eternalreturn.mobs.entity.BearEntity;
import com.eternalreturn.mobs.entity.VampireEntity;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.block.Block;
import net.minecraft.client.particle.CherryLeavesParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.BatEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.IllagerEntityRenderer;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.IllagerEntityModel;
import net.minecraft.client.render.entity.model.PolarBearEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;

/** Drawing the autumnal forest's woods, leaves, boats, bears and vampires (Woods, WoodBoats, ForestMobs). */
public final class ForestRendering {
	private ForestRendering() {
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void register() {
		for (Woods.Wood wood : Woods.all()) {
			BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), wood.door, wood.trapdoor);
			if (wood.sapling != null) {
				BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), wood.sapling, wood.pottedSapling);
			}
			BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutoutMipped(), wood.leaves.toArray(Block[]::new));
			EntityRendererRegistry.register(WoodBoats.boat(wood.name), ctx -> new WoodBoatRenderer(ctx, wood.name, false));
			EntityRendererRegistry.register(WoodBoats.chestBoat(wood.name), ctx -> new WoodBoatRenderer(ctx, wood.name, true));
		}
		BlockRenderLayerMap.INSTANCE.putBlock(Woods.MAPLE_PETALS, RenderLayer.getCutout());
		ParticleFactoryRegistry.getInstance().register(Woods.MAPLE_LEAF_PARTICLE,
				sprites -> (type, world, x, y, z, vx, vy, vz) -> new MapleLeafParticle(world, x, y, z, sprites));
		EntityRendererRegistry.register(ModEntities.VAMPIRE, VampireRenderer::new);
		EntityRendererRegistry.register(ModEntities.VAMPIRE_BAT, (EntityRendererFactory) BatEntityRenderer::new);
		bear(ModEntities.BROWN_BEAR, "brown_bear", 1.0F);
		bear(ModEntities.BLACK_BEAR, "black_bear", 0.9F);
		bear(ModEntities.SUN_BEAR, "sun_bear", 0.75F);
	}

	private static void bear(EntityType<BearEntity> type, String texture, float size) {
		EntityRendererRegistry.register(type, ctx -> new BearRenderer(ctx, EternalReturn.id("textures/entity/bear/" + texture + ".png"), size));
	}

	/** A falling maple leaf: the cherry tree's falling petal with the maple leaf sprites. */
	static class MapleLeafParticle extends CherryLeavesParticle {
		MapleLeafParticle(ClientWorld world, double x, double y, double z, SpriteProvider sprites) {
			super(world, x, y, z, sprites);
		}
	}

	/** A bear: the polar bear model (which the coats are drawn for), scaled to the bear's size. */
	static class BearRenderer extends MobEntityRenderer<BearEntity, PolarBearEntityModel<BearEntity>> {
		private final Identifier texture;
		private final float size;

		BearRenderer(EntityRendererFactory.Context ctx, Identifier texture, float size) {
			super(ctx, new PolarBearEntityModel<>(ctx.getPart(EntityModelLayers.POLAR_BEAR)), 0.9F * size);
			this.texture = texture;
			this.size = size;
		}

		@Override
		public Identifier getTexture(BearEntity bear) {
			return this.texture;
		}

		@Override
		protected void scale(BearEntity bear, MatrixStack matrices, float tickDelta) {
			// As the polar bear renderer does, times this bear's size.
			matrices.scale(1.2F * this.size, 1.2F * this.size, 1.2F * this.size);
			super.scale(bear, matrices, tickDelta);
		}
	}

	/** The vampire: the illager model (a vindicator's), arms crossed until it fights. */
	static class VampireRenderer extends IllagerEntityRenderer<VampireEntity> {
		private static final Identifier TEXTURE = EternalReturn.id("textures/entity/illager/vampire.png");

		VampireRenderer(EntityRendererFactory.Context ctx) {
			super(ctx, new IllagerEntityModel<>(ctx.getPart(EntityModelLayers.VINDICATOR)), 0.5F);
		}

		@Override
		public Identifier getTexture(VampireEntity vampire) {
			return TEXTURE;
		}
	}
}
