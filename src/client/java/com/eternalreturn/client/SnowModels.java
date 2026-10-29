package com.eternalreturn.client;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.blocks.MixedSlabs;
import com.eternalreturn.blocks.SnowCover;
import com.eternalreturn.blocks.Snowlogging;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.material.ShadeMode;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SnowBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.BlockModels;
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
import net.minecraft.registry.Registries;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Snow on blocks (SnowCover), drawn on the blocks' own models as they're drawn, so any shape (another mod's, a resource
 * pack's, a slab turned on edge) gets snow that follows it:
 * <ul>
 * <li>a snowy block (snow logged in it, or lying on it) is drawn as it is, then the snow logged in it on the topmost
 * thing in each column of the space it leaves open, as many layers deep as the player's game knows (ClientSnow), then
 * the snow drip (textures/block/snow_drip, drips along its top edge) over every face whose top edge has snow on it;</li>
 * <li>a snow cover is drawn over the parts of the block below it reaching the top of its space.</li>
 * </ul>
 * <p>So that the snow leaves no holes or flicker, with Sodium too (which draws through the same Fabric rendering API):
 * what a block hides of its neighbours is the bare block's (SnowShapeMixin), so no neighbour leaves out a face the snow
 * doesn't cover; the snow's own faces are left out only where snow beside them or an opaque neighbour covers them, as
 * checked here, never by the renderer's cull faces (a face against the block is drawn and hidden by it); and each drip
 * has exactly the corners of the face under it (a face with snow over only part of its top edge is drawn in pieces to
 * match), as vanilla's snowy grass has, so neither wins over the other by a hair. Texture coordinates are pulled in
 * from the texture's edges as vanilla's are.
 */
public final class SnowModels {
	private static final SpriteIdentifier SNOW = new SpriteIdentifier(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, Identifier.ofVanilla("block/snow"));
	private static final SpriteIdentifier DRIP = new SpriteIdentifier(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, EternalReturn.id("block/snow_drip"));
	/** How far the drips hang (the rows of the drip texture drawn): on a face less than twice that high, half its height. */
	private static final int DRIP_ROWS = 4;
	private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
	/** The tops of the blocks snow covers lie on, as drawn (per bare state; made anew as models load). */
	private static Map<BlockState, int[]> coveredTops = new ConcurrentHashMap<>();

	private SnowModels() {
	}

	public static void register() {
		ModelLoadingPlugin.register(plugin -> {
			coveredTops = new ConcurrentHashMap<>();
			// Every snowy state, by its model id (its blockstate file's variants match it too).
			Set<ModelIdentifier> snowy = new HashSet<>();
			for (Block block : Registries.BLOCK) {
				if (SnowCover.isHost(block.getDefaultState())) {
					for (BlockState state : block.getStateManager().getStates()) {
						if (SnowCover.marked(state)) {
							snowy.add(BlockModels.getModelId(state));
						}
					}
				}
			}
			plugin.modifyModelAfterBake().register((model, context) -> model == null || context.topLevelId() == null || !snowy.contains(context.topLevelId())
					? model : new SnowyModel(model, context.textureGetter().apply(SNOW), context.textureGetter().apply(DRIP)));
			plugin.registerBlockStateResolver(Snowlogging.SNOW_COVER, context -> {
				UnbakedModel cover = new CoverUnbaked();
				for (BlockState state : Snowlogging.SNOW_COVER.getStateManager().getStates()) {
					context.setModel(state, cover);
				}
			});
		});
	}

	private static Renderer renderer() {
		return RendererAccess.INSTANCE.getRenderer();
	}

	private static RenderMaterial snowMaterial() {
		return renderer().materialFinder().shadeMode(ShadeMode.VANILLA).blendMode(BlendMode.SOLID).find();
	}

	/** A quad's extent, in pixels. */
	private static float[][] bounds(QuadView quad) {
		float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
		float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		for (int i = 0; i < 4; i++) {
			float[] p = {quad.x(i) * 16.0F, quad.y(i) * 16.0F, quad.z(i) * 16.0F};
			for (int axis = 0; axis < 3; axis++) {
				min[axis] = Math.min(min[axis], p[axis]);
				max[axis] = Math.max(max[axis], p[axis]);
			}
		}
		return new float[][] {min, max};
	}

	private static int pixel(float pixels) {
		return Math.max(0, Math.min(16, Math.round(pixels)));
	}

	/** A face toward a side flat in its plane: its axis across the side (x for north and south, z for west and east). */
	private static int alongAxis(Direction side) {
		return side.getAxis() == Direction.Axis.Z ? 0 : 2;
	}

	/** Measures a model's top as drawn: how high it reaches in each pixel column (index x + 16 z, -1 where it has nothing). */
	private static final class Measure implements RenderContext.QuadTransform {
		private final int[] heights = new int[256];
		private boolean any;

		Measure() {
			Arrays.fill(this.heights, -1);
		}

		@Override
		public boolean transform(MutableQuadView quad) {
			this.any = true;
			float[][] b = bounds(quad);
			if (quad.lightFace() == Direction.UP && b[1][1] - b[0][1] < 0.01F) {
				int top = Math.max(0, Math.min(32, Math.round(b[1][1])));
				for (int x = pixel(b[0][0]); x < pixel(b[1][0]); x++) {
					for (int z = pixel(b[0][2]); z < pixel(b[1][2]); z++) {
						this.heights[x + 16 * z] = Math.max(this.heights[x + 16 * z], top);
					}
				}
			}
			// Measuring only: nothing is drawn.
			return false;
		}
	}

	/** The top of a block a snow cover lies on, as drawn, measured once per bare state. */
	private static int[] coveredTop(BlockRenderView view, BlockState below, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
		BlockState bare = SnowCover.bare(below);
		int[] known = coveredTops.get(bare);
		if (known != null) {
			return known;
		}
		Measure measure = new Measure();
		context.pushTransform(measure);
		MinecraftClient.getInstance().getBlockRenderManager().getModel(bare).emitBlockQuads(view, bare, pos, randomSupplier, context);
		context.popTransform();
		if (measure.any) {
			coveredTops.put(bare, measure.heights);
		}
		return measure.heights;
	}

	/**
	 * Notes the model's faces whose top edge has snow on it, for their drips, as the model draws them: a face with snow
	 * along all its top edge is drawn as it is; one with snow along only part of it is kept back, to be drawn in pieces.
	 */
	private static final class Drips implements RenderContext.QuadTransform {
		private final int[] heights;
		private final boolean[] snowy;
		/** The faces with snow all along their top edge. */
		private final List<Face> whole = new ArrayList<>();
		private final MeshBuilder parts = renderer().meshBuilder();
		private boolean anyParts;

		Drips(int[] heights, boolean[] snowy) {
			this.heights = heights;
			this.snowy = snowy;
		}

		/** Whether the column behind a face at a pixel along it has snow at the face's top edge. */
		private boolean dripsAt(Direction side, float plane, int along, int top) {
			int behind = side.getDirection() == Direction.AxisDirection.POSITIVE ? Math.round(plane) - 1 : Math.round(plane);
			if (behind < 0 || behind > 15 || along < 0 || along > 15) {
				return false;
			}
			int i = side.getAxis() == Direction.Axis.Z ? along + 16 * behind : behind + 16 * along;
			return this.snowy[i] && this.heights[i] == top;
		}

		@Override
		public boolean transform(MutableQuadView quad) {
			Direction side = quad.lightFace();
			if (!side.getAxis().isHorizontal()) {
				return true;
			}
			float[][] b = bounds(quad);
			int flat = side.getAxis().ordinal();
			if (b[1][flat] - b[0][flat] > 0.01F) {
				return true;
			}
			int along = alongAxis(side);
			int top = Math.round(b[1][1]);
			int from = pixel(b[0][along]);
			int to = pixel(b[1][along]);
			int dripping = 0;
			for (int c = from; c < to; c++) {
				if (this.dripsAt(side, b[0][flat], c, top)) {
					dripping++;
				}
			}
			if (dripping == 0 || to <= from) {
				return true;
			}
			if (dripping == to - from) {
				float[] corners = new float[12];
				for (int i = 0; i < 4; i++) {
					corners[3 * i] = quad.x(i);
					corners[3 * i + 1] = quad.y(i);
					corners[3 * i + 2] = quad.z(i);
				}
				this.whole.add(new Face(side, corners, quad.cullFace(), quad.material()));
				return true;
			}
			this.parts.getEmitter().copyFrom(quad).emit();
			this.anyParts = true;
			return false;
		}
	}

	/** A face of the model under a drip: its side, its corners (x, y, z in the face's own order), cull face and material. */
	private record Face(Direction side, float[] corners, @Nullable Direction cullFace, RenderMaterial material) {
	}

	/** A snowy state's model: the block's own (its faces under drips in pieces where need be), the snow logged in it and the drips. */
	static final class SnowyModel extends ForwardingBakedModel {
		private final Sprite snow;
		private final Sprite drip;
		private final RenderMaterial snowMaterial;
		/** The block's top as drawn, measured the first time it's drawn (each time for a mixed slab, whose halves vary). */
		@Nullable
		private volatile int[] heights;

		SnowyModel(BakedModel wrapped, Sprite snow, Sprite drip) {
			this.wrapped = wrapped;
			this.snow = snow;
			this.drip = drip;
			this.snowMaterial = snowMaterial();
		}

		@Override
		public boolean isVanillaAdapter() {
			return false;
		}

		@Override
		public void emitBlockQuads(BlockRenderView view, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
			int[] heights = this.heights;
			if (heights == null) {
				Measure measure = new Measure();
				context.pushTransform(measure);
				this.wrapped.emitBlockQuads(view, state, pos, randomSupplier, context);
				context.popTransform();
				heights = measure.heights;
				if (measure.any && !state.isOf(MixedSlabs.MIXED_SLAB)) {
					this.heights = heights;
				}
			}
			int layers = Math.min(ClientSnow.layers(pos), SnowCover.capacity(state));
			boolean snowAbove = SnowCover.isSnow(view.getBlockState(pos.up()));
			if (layers == 0 && !snowAbove) {
				this.wrapped.emitBlockQuads(view, state, pos, randomSupplier, context);
				return;
			}
			// Each column's floor and the snow's top on it (the floor itself where there's none), and the columns whose top has snow on it.
			int[] floors = new int[256];
			int[] tops = new int[256];
			boolean[] snowy = new boolean[256];
			for (int i = 0; i < 256; i++) {
				int height = heights[i];
				floors[i] = Math.max(0, height);
				tops[i] = layers > 0 && floors[i] < 16 ? Math.min(16, floors[i] + 2 * layers) : floors[i];
				snowy[i] = height >= 0 && (height < 16 ? layers > 0 : snowAbove);
			}
			Drips drips = new Drips(heights, snowy);
			context.pushTransform(drips);
			this.wrapped.emitBlockQuads(view, state, pos, randomSupplier, context);
			context.popTransform();
			QuadEmitter emitter = context.getEmitter();
			this.emitDrips(drips, emitter);
			if (layers > 0) {
				emitSnow(view, pos, floors, tops, emitter, this.snow, this.snowMaterial);
			}
		}

		private RenderMaterial dripMaterial(RenderMaterial under) {
			return renderer().materialFinder().copyFrom(under).blendMode(BlendMode.CUTOUT).find();
		}

		/** The drips: over each face with snow all along its top edge, and over each piece with snow along it of the faces drawn in pieces. */
		private void emitDrips(Drips drips, QuadEmitter emitter) {
			for (Face face : drips.whole) {
				this.dripOver(emitter, face.side(), face.corners(), face.cullFace(), this.dripMaterial(face.material()));
			}
			if (!drips.anyParts) {
				return;
			}
			drips.parts.build().forEach(face -> {
				Direction side = face.lightFace();
				float[][] b = bounds(face);
				int flat = side.getAxis().ordinal();
				int along = alongAxis(side);
				int top = Math.round(b[1][1]);
				int from = pixel(b[0][along]);
				int to = pixel(b[1][along]);
				int runFrom = from;
				boolean runDrips = drips.dripsAt(side, b[0][flat], from, top);
				for (int c = from + 1; c <= to; c++) {
					boolean drip = c < to && drips.dripsAt(side, b[0][flat], c, top);
					if (c == to || drip != runDrips) {
						float[] corners = piece(face, along, b[0][along], b[1][along], runFrom == from ? b[0][along] : runFrom, c == to ? b[1][along] : c, emitter);
						if (runDrips) {
							this.dripOver(emitter, side, corners, face.cullFace(), this.dripMaterial(face.material()));
						}
						runFrom = c;
						runDrips = drip;
					}
				}
			});
		}

		/**
		 * Draws the piece of a face between two points along it (pixels), its texture cut to match, and returns its
		 * corners (in the face's order).
		 */
		private static float[] piece(QuadView face, int along, float min, float max, float from, float to, QuadEmitter emitter) {
			emitter.copyFrom(face);
			float[] corners = new float[12];
			for (int i = 0; i < 4; i++) {
				float at = coordinate(face, i, along) * 16.0F;
				// This corner's partner across the face, at the same height.
				int partner = i;
				for (int j = 0; j < 4; j++) {
					if (j != i && Math.abs(face.y(j) - face.y(i)) < 1.0E-4F && Math.abs(coordinate(face, j, along) - coordinate(face, i, along)) > 1.0E-4F) {
						partner = j;
					}
				}
				boolean low = at - min < max - at;
				float moved = low ? from : to;
				float t = max > min ? (moved - min) / (max - min) : 0.0F;
				int atMin = low ? i : partner;
				int atMax = low ? partner : i;
				emitter.uv(i, MathHelper.lerp(t, face.u(atMin), face.u(atMax)), MathHelper.lerp(t, face.v(atMin), face.v(atMax)));
				float x = along == 0 ? moved / 16.0F : face.x(i);
				float z = along == 2 ? moved / 16.0F : face.z(i);
				emitter.pos(i, x, face.y(i), z);
				corners[3 * i] = x;
				corners[3 * i + 1] = face.y(i);
				corners[3 * i + 2] = z;
			}
			emitter.emit();
			return corners;
		}

		private static float coordinate(QuadView quad, int vertex, int axis) {
			return axis == 0 ? quad.x(vertex) : axis == 1 ? quad.y(vertex) : quad.z(vertex);
		}

		/**
		 * A drip over a face, with its corners: the drip texture's top edge along the face's top, laid out as vanilla lays
		 * faces out; on a short face (a fence rail), only the lower end of the drips, over its top half.
		 */
		private void dripOver(QuadEmitter emitter, Direction side, float[] corners, @Nullable Direction cullFace, RenderMaterial material) {
			float top = -Float.MAX_VALUE;
			float bottom = Float.MAX_VALUE;
			for (int i = 0; i < 4; i++) {
				top = Math.max(top, corners[3 * i + 1]);
				bottom = Math.min(bottom, corners[3 * i + 1]);
			}
			int high = Math.round((top - bottom) * 16.0F);
			int skipped = DRIP_ROWS - Math.min(DRIP_ROWS, (high + 1) / 2);
			float[] u = new float[4];
			float[] v = new float[4];
			for (int i = 0; i < 4; i++) {
				float x = corners[3 * i] * 16.0F;
				float z = corners[3 * i + 2] * 16.0F;
				u[i] = switch (side) {
					case NORTH -> 16.0F - x;
					case SOUTH -> x;
					case WEST -> z;
					default -> 16.0F - z;
				};
				v[i] = skipped + (top - corners[3 * i + 1]) * 16.0F;
				emitter.pos(i, corners[3 * i], corners[3 * i + 1], corners[3 * i + 2]);
			}
			finish(emitter, u, v, this.drip, material, side, cullFace);
		}
	}

	/**
	 * Snow over columns of a block's space (pixels: floors and tops, the floor itself where there's none): its tops (but
	 * under a full block above), and its sides where neither snow beside them nor an opaque neighbour covers them.
	 */
	private static void emitSnow(BlockRenderView view, BlockPos pos, int[] floors, int[] tops, QuadEmitter emitter, Sprite snow, RenderMaterial material) {
		boolean covered = view.getBlockState(pos.up()).isOpaqueFullCube(view, pos.up());
		boolean[] done = new boolean[256];
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				int i = x + 16 * z;
				if (done[i] || tops[i] <= floors[i]) {
					continue;
				}
				int top = tops[i];
				int x1 = x + 1;
				while (x1 < 16 && same(x1 + 16 * z, top, floors, tops, done)) {
					x1++;
				}
				int z1 = z + 1;
				while (z1 < 16 && rowSame(x, x1, z1, top, floors, tops, done)) {
					z1++;
				}
				for (int zz = z; zz < z1; zz++) {
					for (int xx = x; xx < x1; xx++) {
						done[xx + 16 * zz] = true;
					}
				}
				if (!(covered && top == 16)) {
					up(emitter, x, z, x1, z1, top, snow, material);
				}
			}
		}
		for (Direction side : SIDES) {
			emitSides(view, pos, side, floors, tops, emitter, snow, material);
		}
	}

	private static boolean same(int i, int top, int[] floors, int[] tops, boolean[] done) {
		return !done[i] && tops[i] > floors[i] && tops[i] == top;
	}

	private static boolean rowSame(int x0, int x1, int z, int top, int[] floors, int[] tops, boolean[] done) {
		for (int x = x0; x < x1; x++) {
			if (!same(x + 16 * z, top, floors, tops, done)) {
				return false;
			}
		}
		return true;
	}

	/** The snow's faces toward one side: what of each column's snow the snow in the column beside doesn't cover (nor, at the edge, an opaque neighbour). */
	private static void emitSides(BlockRenderView view, BlockPos pos, Direction side, int[] floors, int[] tops, QuadEmitter emitter, Sprite snow, RenderMaterial material) {
		boolean alongX = side.getAxis() == Direction.Axis.Z;
		for (int line = 0; line < 16; line++) {
			int runFrom = 0;
			int runBottom = -1;
			int runTop = -1;
			for (int along = 0; along <= 16; along++) {
				int bottom = -1;
				int top = -1;
				if (along < 16) {
					int x = alongX ? along : line;
					int z = alongX ? line : along;
					int i = x + 16 * z;
					if (tops[i] > floors[i]) {
						bottom = floors[i];
						top = tops[i];
						int nx = x + side.getOffsetX();
						int nz = z + side.getOffsetZ();
						if (nx >= 0 && nx <= 15 && nz >= 0 && nz <= 15) {
							int n = nx + 16 * nz;
							// Snow beside, from as low up, hides this side up to its own top.
							if (tops[n] > floors[n] && floors[n] <= bottom) {
								bottom = Math.max(bottom, tops[n]);
							}
						}
						if (bottom >= top) {
							bottom = top = -1;
						}
					}
				}
				if (bottom != runBottom || top != runTop) {
					if (runTop > runBottom) {
						int plane = side.getDirection() == Direction.AxisDirection.POSITIVE ? line + 1 : line;
						boolean edge = plane == 0 || plane == 16;
						if (!edge || !covered(view, pos, side, runFrom, along, runBottom, runTop)) {
							side(emitter, side, plane, runFrom, along, runBottom, runTop, snow, material);
						}
					}
					runFrom = along;
					runBottom = bottom;
					runTop = top;
				}
			}
		}
	}

	/** An upward face of snow at a height, over x0..x1 and z0..z1 (pixels), laid out as vanilla's (u east, v south). */
	private static void up(QuadEmitter emitter, int x0, int z0, int x1, int z1, int y, Sprite snow, RenderMaterial material) {
		int[][] corners = {{x0, z0}, {x0, z1}, {x1, z1}, {x1, z0}};
		float[] u = new float[4];
		float[] v = new float[4];
		for (int i = 0; i < 4; i++) {
			emitter.pos(i, corners[i][0] / 16.0F, y / 16.0F, corners[i][1] / 16.0F);
			u[i] = corners[i][0];
			v[i] = corners[i][1];
		}
		finish(emitter, u, v, snow, material, Direction.UP, null);
	}

	/** A face of snow toward a side, in the plane at plane pixels, from..to along it and bottom..top up it (the bottom of the texture at its bottom). */
	private static void side(QuadEmitter emitter, Direction side, int plane, int from, int to, int bottom, int top, Sprite snow, RenderMaterial material) {
		// Corners top-left, bottom-left, bottom-right, top-right as seen from outside, as vanilla's faces go.
		boolean leftIsLow = side == Direction.SOUTH || side == Direction.WEST;
		int left = leftIsLow ? from : to;
		int right = leftIsLow ? to : from;
		int[][] corners = {{left, top}, {left, bottom}, {right, bottom}, {right, top}};
		float[] u = new float[4];
		float[] v = new float[4];
		for (int i = 0; i < 4; i++) {
			int a = corners[i][0];
			int y = corners[i][1];
			if (side.getAxis() == Direction.Axis.Z) {
				emitter.pos(i, a / 16.0F, y / 16.0F, plane / 16.0F);
			} else {
				emitter.pos(i, plane / 16.0F, y / 16.0F, a / 16.0F);
			}
			u[i] = leftIsLow ? a : 16 - a;
			v[i] = 16 - (y - bottom);
		}
		finish(emitter, u, v, snow, material, side, null);
	}

	/** Texture coordinates (0 to 16) pulled in from the texture's edges as vanilla's are, and the rest of a quad. */
	private static void finish(QuadEmitter emitter, float[] u, float[] v, Sprite sprite, RenderMaterial material, Direction face, @Nullable Direction cullFace) {
		float shrink = sprite.getAnimationFrameDelta();
		float uMid = (u[0] + u[1] + u[2] + u[3]) / 4.0F;
		float vMid = (v[0] + v[1] + v[2] + v[3]) / 4.0F;
		for (int i = 0; i < 4; i++) {
			emitter.uv(i, MathHelper.lerp(shrink, u[i], uMid), MathHelper.lerp(shrink, v[i], vMid));
		}
		emitter.cullFace(cullFace);
		emitter.nominalFace(face);
		emitter.spriteBake(sprite, 0);
		emitter.color(-1, -1, -1, -1);
		emitter.colorIndex(-1);
		emitter.material(material);
		emitter.emit();
	}

	/** Whether an opaque neighbour on a side covers a face of the snow there (in pixels: along the side, and up it). */
	private static boolean covered(BlockRenderView view, BlockPos pos, Direction side, int from, int to, int bottom, int top) {
		if (top > 16) {
			return false;
		}
		BlockPos other = pos.offset(side);
		BlockState neighbor = view.getBlockState(other);
		if (!neighbor.isOpaque()) {
			return false;
		}
		VoxelShape face = neighbor.getCullingFace(view, other, side.getOpposite());
		VoxelShape mine = side.getAxis() == Direction.Axis.Z ? Block.createCuboidShape(from, bottom, 0, to, top, 16) : Block.createCuboidShape(0, bottom, from, 16, top, to);
		return !face.isEmpty() && !VoxelShapes.matchesAnywhere(mine, face, BooleanBiFunction.ONLY_FIRST);
	}

	/** Bakes the snow cover's model (snow's texture for its particles). */
	static final class CoverUnbaked implements UnbakedModel {
		@Override
		public Collection<Identifier> getModelDependencies() {
			return List.of();
		}

		@Override
		public void setParents(Function<Identifier, UnbakedModel> modelLoader) {
		}

		@Override
		public BakedModel bake(Baker baker, Function<SpriteIdentifier, Sprite> textureGetter, ModelBakeSettings rotationContainer) {
			return new CoverModel(textureGetter.apply(SNOW));
		}
	}

	/** A snow cover: its layers of snow over the parts of the block below it that reach the top, as that block is drawn. */
	static final class CoverModel implements BakedModel, FabricBakedModel {
		private final Sprite snow;
		private final RenderMaterial material;

		CoverModel(Sprite snow) {
			this.snow = snow;
			this.material = snowMaterial();
		}

		@Override
		public boolean isVanillaAdapter() {
			return false;
		}

		@Override
		public void emitBlockQuads(BlockRenderView view, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
			BlockState below = view.getBlockState(pos.down());
			if (!SnowCover.isHost(below)) {
				return;
			}
			int[] heights = coveredTop(view, below, pos.down(), randomSupplier, context);
			int depth = 2 * state.get(SnowBlock.LAYERS);
			int[] floors = new int[256];
			int[] tops = new int[256];
			for (int i = 0; i < 256; i++) {
				tops[i] = heights[i] >= 16 ? depth : 0;
			}
			emitSnow(view, pos, floors, tops, context.getEmitter(), this.snow, this.material);
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
			return this.snow;
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
