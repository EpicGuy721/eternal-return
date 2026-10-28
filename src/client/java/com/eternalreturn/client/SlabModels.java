package com.eternalreturn.client;

import com.eternalreturn.blocks.MixedSlabs;
import com.eternalreturn.blocks.Slabs;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.ModelBakeSettings;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.registry.Registries;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Vertical slabs are drawn with their slab's own model lying flat, turned on edge as the model is drawn (so any slab,
 * other mods' too, needs no model of its own, and a resource pack's slab turns with it); a mixed slab (MixedSlabs) is
 * drawn as its two halves, each with its own model. Both go through Fabric's rendering API, which Sodium also draws.
 */
public final class SlabModels {
	private static final SpriteIdentifier STONE = new SpriteIdentifier(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, Identifier.ofVanilla("block/stone"));

	private SlabModels() {
	}

	public static void register() {
		MixedSlabs.clientPlayer = () -> MinecraftClient.getInstance().player;
		ModelLoadingPlugin.register(plugin -> {
			// Every slab's states on edge, by their model ids (their blockstate file's flat variants match them too).
			Map<ModelIdentifier, BlockState> vertical = new HashMap<>();
			for (Block block : Registries.BLOCK) {
				if (block instanceof SlabBlock) {
					for (BlockState state : block.getStateManager().getStates()) {
						if (state.contains(Slabs.AXIS) && state.get(Slabs.AXIS) != Direction.Axis.Y) {
							vertical.put(BlockModels.getModelId(state), state);
						}
					}
				}
			}
			plugin.modifyModelAfterBake().register((model, context) -> {
				BlockState state = context.topLevelId() == null ? null : vertical.get(context.topLevelId());
				return state == null || model == null ? model : new VerticalSlabModel(model, state);
			});
			plugin.registerBlockStateResolver(MixedSlabs.MIXED_SLAB, context -> {
				UnbakedModel mixed = new MixedSlabUnbaked();
				for (BlockState state : MixedSlabs.MIXED_SLAB.getStateManager().getStates()) {
					context.setModel(state, mixed);
				}
			});
		});
	}

	private static BakedModel modelOf(BlockState state) {
		return MinecraftClient.getInstance().getBlockRenderManager().getModel(state);
	}

	/** A point or direction turned by the quarter turn that takes a slab's bottom (down) to a side (relative to the block's middle). */
	private static float[] turned(Direction side, float x, float y, float z) {
		return switch (side) {
			case NORTH -> new float[] {x, -z, y};
			case SOUTH -> new float[] {x, z, -y};
			case WEST -> new float[] {y, -x, z};
			case EAST -> new float[] {-y, x, z};
			default -> new float[] {x, y, z};
		};
	}

	@Nullable
	private static Direction turned(Direction side, @Nullable Direction face) {
		if (face == null) {
			return null;
		}
		float[] v = turned(side, face.getOffsetX(), face.getOffsetY(), face.getOffsetZ());
		return Direction.fromVector(Math.round(v[0]), Math.round(v[1]), Math.round(v[2]));
	}

	/** The quarter turn taking a slab's bottom to a side, on every quad of a model. */
	private static RenderContext.QuadTransform turning(Direction side) {
		return quad -> {
			for (int i = 0; i < 4; i++) {
				float[] p = turned(side, quad.x(i) - 0.5F, quad.y(i) - 0.5F, quad.z(i) - 0.5F);
				quad.pos(i, p[0] + 0.5F, p[1] + 0.5F, p[2] + 0.5F);
				if (quad.hasNormal(i)) {
					float[] n = turned(side, quad.normalX(i), quad.normalY(i), quad.normalZ(i));
					quad.normal(i, n[0], n[1], n[2]);
				}
			}
			Direction nominal = turned(side, quad.nominalFace());
			quad.cullFace(turned(side, quad.cullFace()));
			quad.nominalFace(nominal);
			return true;
		};
	}

	/**
	 * A slab on edge: its slab's flat model (a single slab's bottom, or the pair) turned so the bottom faces the side it
	 * fills (a pair: west or north), its top facing the open half.
	 */
	static final class VerticalSlabModel extends ForwardingBakedModel {
		private final BlockState flat;
		private final Direction side;

		VerticalSlabModel(BakedModel wrapped, BlockState state) {
			this.wrapped = wrapped;
			boolean pair = state.get(SlabBlock.TYPE) == SlabType.DOUBLE;
			this.flat = (pair ? state : state.with(SlabBlock.TYPE, SlabType.BOTTOM)).with(Slabs.AXIS, Direction.Axis.Y);
			this.side = pair ? Direction.from(state.get(Slabs.AXIS), Direction.AxisDirection.NEGATIVE) : Slabs.side(state);
		}

		@Override
		public boolean isVanillaAdapter() {
			return false;
		}

		@Override
		public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
			context.pushTransform(turning(this.side));
			modelOf(this.flat).emitBlockQuads(blockView, this.flat, pos, randomSupplier, context);
			context.popTransform();
		}
	}

	/** Bakes the mixed slab's model (stone's particles stand for it whole: its halves' own are used where they show). */
	static final class MixedSlabUnbaked implements UnbakedModel {
		@Override
		public Collection<Identifier> getModelDependencies() {
			return List.of();
		}

		@Override
		public void setParents(Function<Identifier, UnbakedModel> modelLoader) {
		}

		@Override
		public BakedModel bake(Baker baker, Function<SpriteIdentifier, Sprite> textureGetter, ModelBakeSettings rotationContainer) {
			return new MixedSlabModel(textureGetter.apply(STONE));
		}
	}

	/**
	 * A mixed slab: each half drawn with its own model, in its own render layer, tinted by its own colours; nothing until
	 * its halves come from the server.
	 */
	static final class MixedSlabModel implements BakedModel, FabricBakedModel {
		private final Sprite particle;

		MixedSlabModel(Sprite particle) {
			this.particle = particle;
		}

		@Override
		public boolean isVanillaAdapter() {
			return false;
		}

		@Override
		public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
			if (!(blockView.getBlockEntityRenderData(pos) instanceof MixedSlabs.Halves halves)) {
				return;
			}
			emitHalf(blockView, MixedSlabs.half(state, halves, false), pos, randomSupplier, context);
			emitHalf(blockView, MixedSlabs.half(state, halves, true), pos, randomSupplier, context);
		}

		private static void emitHalf(BlockRenderView blockView, BlockState half, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
			RenderLayer layer = RenderLayers.getBlockLayer(half);
			BlendMode blend = layer == RenderLayer.getSolid() ? null : BlendMode.fromRenderLayer(layer);
			context.pushTransform(quad -> {
				if (quad.colorIndex() != -1) {
					tint(quad, MinecraftClient.getInstance().getBlockColors().getColor(half, blockView, pos, quad.colorIndex()));
				}
				if (blend != null) {
					quad.material(RendererAccess.INSTANCE.getRenderer().materialFinder().copyFrom(quad.material()).blendMode(blend).find());
				}
				return true;
			});
			modelOf(half).emitBlockQuads(blockView, half, pos, randomSupplier, context);
			context.popTransform();
		}

		/** A half's tint put on its quad (the renderer would tint it with the mixed slab's colours, which have none). */
		private static void tint(MutableQuadView quad, int color) {
			quad.colorIndex(-1);
			if (color == -1) {
				return;
			}
			for (int i = 0; i < 4; i++) {
				int c = quad.color(i);
				int r = (c >> 16 & 255) * (color >> 16 & 255) / 255;
				int g = (c >> 8 & 255) * (color >> 8 & 255) / 255;
				int b = (c & 255) * (color & 255) / 255;
				quad.color(i, c & 0xFF000000 | r << 16 | g << 8 | b);
			}
		}

		@Override
		public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, Random random) {
			return List.of();
		}

		@Override
		public boolean useAmbientOcclusion() {
			return true;
		}

		@Override
		public boolean hasDepth() {
			return false;
		}

		@Override
		public boolean isSideLit() {
			return true;
		}

		@Override
		public boolean isBuiltin() {
			return false;
		}

		@Override
		public Sprite getParticleSprite() {
			return this.particle;
		}

		@Override
		public ModelTransformation getTransformation() {
			return ModelTransformation.NONE;
		}

		@Override
		public ModelOverrideList getOverrides() {
			return ModelOverrideList.EMPTY;
		}
	}
}
