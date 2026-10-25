package com.eternalreturn.client.weather;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.weather.StormField;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;

/**
 * Rain and snow with storms (ClientWeather), in place of vanilla's (LocalWeatherRenderMixin): drawn column by column
 * where a storm covers, out to 32 blocks (16 on fast graphics) so the rain of a storm a short way off can be seen
 * falling; never above the clouds. Rain is as strong as the storm covers; snow is as many sheets of it, each one at full
 * strength, so a weak or fading storm has fewer flakes rather than fainter ones. Each sheet of snow falls at its own
 * pace, slants its own way and flutters, and a blizzard's is thick (more sheets), falls fast and drives sideways. The
 * flakes are fixed to the world's heights and each sheet turns smoothly to face the camera, so moving and jumping never
 * jolt them; the sheets fade out at their ends. The wind slants it all (weather.windyRain), snow most; slanting, it stops
 * where the ground upwind is in the way, so none comes through blocks into a hole. Splashes and the sound of rain come
 * only from where it rains.
 */
public final class WeatherRenderer {
	private static final Identifier RAIN = Identifier.ofVanilla("textures/environment/rain.png");
	private static final Identifier SNOW = Identifier.ofVanilla("textures/environment/snow.png");
	/** A sheet fades out over this many blocks at its ends. */
	private static final double FADE = 3.0;
	/** Sheets of snow in a column: one, and two more in a blizzard. */
	private static final int SNOW_LAYERS = 3;
	private static int rainSoundCounter;
	private static final Heights HEIGHTS = new Heights();

	private WeatherRenderer() {
	}

	public static void render(LightmapTextureManager manager, float tickDelta, double cameraX, double cameraY, double cameraZ, int ticks) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = client.world;
		StormField field = ClientWeather.field();
		if (world == null || field.storms.isEmpty()) {
			return;
		}
		boolean fancy = MinecraftClient.isFancyGraphicsOrBetter();
		int near = fancy ? 10 : 5;
		int far = fancy ? 32 : 16;
		float clouds = world.getDimensionEffects().getCloudsHeight();
		double ceiling = Float.isNaN(clouds) ? Double.MAX_VALUE : clouds;
		float blizzard = ClientWeather.blizzard(tickDelta);
		boolean windy = EternalReturnConfig.get().weather.windyRain;
		double windX = windy ? ClientWeather.windX(tickDelta) : 0.0;
		double windZ = windy ? ClientWeather.windZ(tickDelta) : 0.0;
		double fall = ClientWeather.snowFall(tickDelta);
		int ci = MathHelper.floor(cameraX);
		int cj = MathHelper.floor(cameraY);
		int ck = MathHelper.floor(cameraZ);
		manager.enable();
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder buffer = null;
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(MinecraftClient.isFabulousGraphicsOrBetter());
		RenderSystem.setShader(GameRenderer::getParticleProgram);
		int texture = -1;
		double time = ticks + tickDelta;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		Heights heights = HEIGHTS.around(world, ci, ck, far + 48);
		for (int z = ck - far; z <= ck + far; z++) {
			for (int x = ci - far; x <= ci + far; x++) {
				int dx = x - ci;
				int dz = z - ck;
				if (dx * dx + dz * dz > far * far) {
					continue;
				}
				float cover = field.rain(x + 0.5, z + 0.5);
				if (cover < 0.02F) {
					continue;
				}
				pos.set(x, cameraY, z);
				Biome biome = world.getBiome(pos).value();
				if (!biome.hasPrecipitation()) {
					continue;
				}
				// From the camera itself (not its block), so nothing jumps as it crosses from one block to the next.
				double offsetX = x + 0.5 - cameraX;
				double offsetZ = z + 0.5 - cameraZ;
				double distance = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
				// Strongest by the camera, half as strong at near, gone at far (fading out over the last few blocks).
				double fade = distance <= near ? 1.0 - distance * distance / (near * near) * 0.5 : 0.5 * (1.0 - (distance - near) / (far - near));
				if (fade <= 0.0) {
					continue;
				}
				double edge = Math.min(1.0, fade * 4.0);
				// Columns further off stand taller, so their rain shows from a distance.
				double half = distance <= near ? near : near + (distance - near) * 0.6;
				int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
				boolean hanging = cameraY - half > ground;
				double bottom = Math.max(cameraY - half, ground);
				double top = Math.min(Math.max(cameraY + half, ground), ceiling);
				if (bottom >= top) {
					continue;
				}
				pos.set(x, MathHelper.floor(bottom), z);
				Biome.Precipitation precipitation = biome.getPrecipitation(pos);
				if (precipitation == Biome.Precipitation.NONE) {
					continue;
				}
				// A sheet across the line of sight, turning smoothly as the camera moves.
				double sx = distance < 0.01 ? 0.5 : -offsetZ / distance * 0.5;
				double sz = distance < 0.01 ? 0.0 : offsetX / distance * 0.5;
				pos.set(x, Math.max(ground, cj), z);
				int light = WorldRenderer.getLightmapCoordinates(world, pos);
				if (precipitation == Biome.Precipitation.RAIN) {
					if (texture != 0) {
						if (texture >= 0) {
							draw(buffer);
						}
						texture = 0;
						RenderSystem.setShaderTexture(0, RAIN);
						buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_LIGHT);
					}
					// Carried a little per block it falls (rain falls about 9 blocks a second).
					double leanX = windX * 2.5;
					double leanZ = windZ * 2.5;
					// Slanting in, it can't come through the ground upwind (into a hole, say): it starts above where that stands.
					double from = heights.clear(x + 0.5, z + 0.5, ground, bottom, top, leanX, leanZ);
					if (from >= top) {
						continue;
					}
					Random random = Random.create(x * x * 3121 + x * 45238971 ^ z * z * 418711 + z * 13761);
					int scroll = ticks & 131071;
					int offset = x * x * 3121 + x * 45238971 + z * z * 418711 + z * 13761 & 0xFF;
					float speed = 3.0F + random.nextFloat();
					float v = -(scroll + offset + tickDelta) / 32.0F * speed % 32.0F;
					float alpha = (float) ((0.5 + 0.5 * fade) * edge) * cover;
					sheet(buffer, x + 0.5 - cameraX, z + 0.5 - cameraZ, sx, sz, from - cameraY, top - cameraY, hanging && from == bottom, ground - cameraY,
							leanX, leanZ, 0.0F, 1.0F, v, cameraY, 0.25, alpha, light);
				} else {
					if (texture != 1) {
						if (texture >= 0) {
							draw(buffer);
						}
						texture = 1;
						RenderSystem.setShaderTexture(0, SNOW);
						buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_LIGHT);
					}
					int sky = light >> 16 & 65535;
					int block = light & 65535;
					int snowLight = ((block * 3 + 240) / 4) | ((sky * 3 + 240) / 4) << 16;
					float alpha = (float) ((0.5 + 0.3 * fade) * edge);
					// How much snow there is: a weak or fading storm has fewer sheets, a blizzard more.
					float thick = Math.min(1.0F, cover * 1.25F);
					for (int layer = 0; layer < SNOW_LAYERS; layer++) {
						long seed = seed(x, z, layer);
						float density = layer == 0 ? thick : thick * blizzard;
						// Each sheet shows above its own share of the storm's strength, widening in quickly from a sliver.
						float shown = MathHelper.clamp((density - 0.875F * unit(seed, 0)) * 8.0F, 0.0F, 1.0F);
						if (shown <= 0.0F) {
							continue;
						}
						// Each its own: where in the column it hangs, how it slants, how big its flakes are, how fast they fall.
						double sheetX = x + 0.5 + (unit(seed, 1) - 0.5) * 0.7;
						double sheetZ = z + 0.5 + (unit(seed, 2) - 0.5) * 0.7;
						double lean = 6.0 * (1.0 + 1.5 * blizzard) * (0.8 + 0.4 * unit(seed, 3));
						double leanX = windX * lean;
						double leanZ = windZ * lean;
						double carried = Math.sqrt(leanX * leanX + leanZ * leanZ);
						if (carried > 1.0) {
							leanX /= carried;
							leanZ /= carried;
						}
						double size = 0.8 + 0.45 * unit(seed, 4);
						double speed = 0.5 + unit(seed, 5);
						double sheetTop = Math.min(top + 3.0 * unit(seed, 6), ceiling);
						double from = heights.clear(sheetX, sheetZ, ground, bottom, sheetTop, leanX, leanZ);
						if (from >= sheetTop) {
							continue;
						}
						// It falls at its own pace (the fall is added up tick by tick, so a change of pace never jumps it) and drifts
						// and flutters sideways, less in a blizzard's driving wind.
						double v = unit(seed, 7) - fall * speed / 512.0;
						double u = unit(seed, 8) + time * 0.012 * (unit(seed, 9) - 0.5)
								+ 0.12 * (1.0 - 0.8 * blizzard) * Math.sin(time * (0.05 + 0.06 * unit(seed, 10)) + unit(seed, 11) * MathHelper.TAU);
						u -= Math.floor(u);
						sheet(buffer, sheetX - cameraX, sheetZ - cameraZ, sx * shown, sz * shown, from - cameraY, sheetTop - cameraY,
								hanging && from == bottom, ground - cameraY, leanX, leanZ, (float) u, (float) (u + shown * size), (float) (v - Math.floor(v)), cameraY,
								0.25 * size, alpha, snowLight);
					}
				}
			}
		}
		if (texture >= 0) {
			draw(buffer);
		}
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
		manager.disable();
	}

	/**
	 * One falling sheet from y0 to y1 (camera-relative), fading out over its top few blocks and its bottom few where it
	 * hangs in the air. Its texture is fixed to the world's heights (scale texture rows a block, phase v), so where the sheet
	 * starts and stops never moves the flakes on it.
	 */
	private static void sheet(BufferBuilder buffer, double x, double z, double sx, double sz, double y0, double y1, boolean hanging, double floor,
			double leanX, double leanZ, float u0, float u1, float v, double cameraY, double scale, float alpha, int light) {
		double low = hanging ? Math.min(y0 + FADE, (y0 + y1) / 2.0) : y0;
		double high = Math.max(y1 - FADE, low);
		if (low > y0) {
			quad(buffer, x, z, sx, sz, y0, low, floor, leanX, leanZ, u0, u1, v, cameraY, scale, 0.0F, alpha, light);
		}
		if (high > low) {
			quad(buffer, x, z, sx, sz, low, high, floor, leanX, leanZ, u0, u1, v, cameraY, scale, alpha, alpha, light);
		}
		quad(buffer, x, z, sx, sz, high, y1, floor, leanX, leanZ, u0, u1, v, cameraY, scale, alpha, 0.0F, light);
	}

	/**
	 * One piece of a sheet from y0 to y1 over a column whose ground is at floor, leaning with the wind (lean is how far it
	 * is carried per block it falls): where it lands is the column, so higher up it is that much further upwind. Fixed to
	 * the ground, it stays put however the camera moves.
	 */
	private static void quad(BufferBuilder buffer, double x, double z, double sx, double sz, double y0, double y1, double floor, double leanX,
			double leanZ, float u0, float u1, float v, double cameraY, double scale, float alpha0, float alpha1, int light) {
		double topX = -leanX * (y1 - floor);
		double topZ = -leanZ * (y1 - floor);
		double bottomX = -leanX * (y0 - floor);
		double bottomZ = -leanZ * (y0 - floor);
		// Texture rows run down the world: higher up, smaller v.
		float v1 = (float) (v - (y1 + cameraY) * scale);
		float v0 = (float) (v - (y0 + cameraY) * scale);
		buffer.vertex((float) (x - sx + topX), (float) y1, (float) (z - sz + topZ)).texture(u0, v1).color(1.0F, 1.0F, 1.0F, alpha1).light(light);
		buffer.vertex((float) (x + sx + topX), (float) y1, (float) (z + sz + topZ)).texture(u1, v1).color(1.0F, 1.0F, 1.0F, alpha1).light(light);
		buffer.vertex((float) (x + sx + bottomX), (float) y0, (float) (z + sz + bottomZ)).texture(u1, v0).color(1.0F, 1.0F, 1.0F, alpha0).light(light);
		buffer.vertex((float) (x - sx + bottomX), (float) y0, (float) (z - sz + bottomZ)).texture(u0, v0).color(1.0F, 1.0F, 1.0F, alpha0).light(light);
	}

	/** Draws what is in the buffer, if anything (every sheet since the last texture may have been left out). */
	private static void draw(BufferBuilder buffer) {
		BuiltBuffer built = buffer.endNullable();
		if (built != null) {
			BufferRenderer.drawWithGlobalProgram(built);
		}
	}

	/** A column's sheet's own numbers, the same every frame. */
	private static long seed(int x, int z, int layer) {
		return HashCommon.murmurHash3(MathHelper.hashCode(x, layer, z));
	}

	/** One of a sheet's numbers, 0 to 1. */
	private static float unit(long seed, int which) {
		return (HashCommon.murmurHash3(seed + which * 0x9E3779B97F4A7C15L) >>> 40) * 0x1.0p-24F;
	}

	/** The height of the ground around the camera, looked up once a frame as the slanting rain needs it. */
	private static final class Heights {
		private ClientWorld world;
		private int x0;
		private int z0;
		private int size;
		private int[] tops = new int[0];

		/** Ready for this frame: centred on the camera, nothing looked up yet. */
		Heights around(ClientWorld world, int cx, int cz, int reach) {
			this.world = world;
			this.x0 = cx - reach;
			this.z0 = cz - reach;
			this.size = 2 * reach + 1;
			if (this.tops.length != this.size * this.size) {
				this.tops = new int[this.size * this.size];
			}
			java.util.Arrays.fill(this.tops, Integer.MIN_VALUE);
			return this;
		}

		/** The first open block above the ground here. */
		int top(int x, int z) {
			int i = x - this.x0;
			int k = z - this.z0;
			if (i < 0 || k < 0 || i >= this.size || k >= this.size) {
				return this.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
			}
			int index = k * this.size + i;
			if (this.tops[index] == Integer.MIN_VALUE) {
				this.tops[index] = this.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
			}
			return this.tops[index];
		}

		/**
		 * The bottom of a slanting sheet whose foot is at x, z (its column's ground at floor): above the highest place its
		 * line up and upwind passes through the ground, so it never comes through blocks; top or more if it is blocked all
		 * the way up. Rain falling nearly straight is left alone.
		 */
		double clear(double x, double z, int floor, double bottom, double top, double leanX, double leanZ) {
			if (leanX * leanX + leanZ * leanZ < 0.0025) {
				return bottom;
			}
			for (int y = MathHelper.floor(top); y > bottom; y--) {
				double above = y - floor;
				if (this.top(MathHelper.floor(x - leanX * above), MathHelper.floor(z - leanZ * above)) > y) {
					return Math.max(bottom, y + 1);
				}
			}
			return bottom;
		}
	}

	/** Splashes and the sound of rain, as vanilla's but only where a storm rains. */
	public static void splash(Camera camera, int ticks) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = client.world;
		StormField field = ClientWeather.field();
		if (world == null || field.storms.isEmpty()) {
			return;
		}
		Random random = Random.create(ticks * 312987231L);
		BlockPos at = BlockPos.ofFloored(camera.getPos());
		BlockPos splashed = null;
		ParticlesMode mode = client.options.getParticles().getValue();
		int tries = (MinecraftClient.isFancyGraphicsOrBetter() ? 100 : 50) / (mode == ParticlesMode.DECREASED ? 2 : 1);
		for (int i = 0; i < tries; i++) {
			int x = random.nextInt(21) - 10;
			int z = random.nextInt(21) - 10;
			BlockPos column = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, at.add(x, 0, z));
			if (random.nextFloat() > field.rain(column.getX() + 0.5, column.getZ() + 0.5)) {
				continue;
			}
			if (column.getY() <= world.getBottomY() || column.getY() > at.getY() + 10 || column.getY() < at.getY() - 10) {
				continue;
			}
			if (world.getBiome(column).value().getPrecipitation(column) != Biome.Precipitation.RAIN) {
				continue;
			}
			splashed = column.down();
			if (mode == ParticlesMode.MINIMAL) {
				break;
			}
			double px = random.nextDouble();
			double pz = random.nextDouble();
			BlockState state = world.getBlockState(splashed);
			FluidState fluid = world.getFluidState(splashed);
			VoxelShape shape = state.getCollisionShape(world, splashed);
			double height = Math.max(shape.getEndingCoord(Direction.Axis.Y, px, pz), fluid.getHeight(world, splashed));
			ParticleEffect particle = !fluid.isIn(FluidTags.LAVA) && !state.isOf(Blocks.MAGMA_BLOCK) && !CampfireBlock.isLitCampfire(state)
					? ParticleTypes.RAIN : ParticleTypes.SMOKE;
			world.addParticle(particle, splashed.getX() + px, splashed.getY() + height, splashed.getZ() + pz, 0.0, 0.0, 0.0);
		}
		if (splashed != null && random.nextInt(3) < rainSoundCounter++) {
			rainSoundCounter = 0;
			if (splashed.getY() > at.getY() + 1 && world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, at).getY() > MathHelper.floor(at.getY())) {
				world.playSoundAtBlockCenter(splashed, SoundEvents.WEATHER_RAIN_ABOVE, SoundCategory.WEATHER, 0.1F, 0.5F, false);
			} else {
				world.playSoundAtBlockCenter(splashed, SoundEvents.WEATHER_RAIN, SoundCategory.WEATHER, 0.2F, 1.0F, false);
			}
		}
	}
}
