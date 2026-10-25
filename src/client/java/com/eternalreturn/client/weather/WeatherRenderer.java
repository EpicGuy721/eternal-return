package com.eternalreturn.client.weather;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.weather.StormField;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
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
 * where a storm covers, as strong as it covers, out to 32 blocks (16 on fast graphics) so the rain of a storm a short
 * way off can be seen falling; never above the clouds. The wind slants it (weather.windyRain), snow most; in a blizzard
 * the snow is thick, falls fast and drives sideways. Slanting, it stops where the ground upwind is in the way, so none
 * comes through blocks into a hole. Splashes and the sound of rain come only from where it rains.
 */
public final class WeatherRenderer {
	private static final Identifier RAIN = Identifier.ofVanilla("textures/environment/rain.png");
	private static final Identifier SNOW = Identifier.ofVanilla("textures/environment/snow.png");
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
		float blizzard = ClientWeather.blizzard(tickDelta);
		boolean windy = EternalReturnConfig.get().weather.windyRain;
		double windX = windy ? field.windX() : 0.0;
		double windZ = windy ? field.windZ() : 0.0;
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
		float time = ticks + tickDelta;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		Heights heights = HEIGHTS.around(world, ci, ck, far + 48);
		for (int z = ck - far; z <= ck + far; z++) {
			for (int x = ci - far; x <= ci + far; x++) {
				int dx = x - ci;
				int dz = z - ck;
				int distanceSq = dx * dx + dz * dz;
				if (distanceSq > far * far) {
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
				double distance = Math.sqrt(distanceSq);
				boolean close = distance <= near;
				// Columns further off stand taller, so their rain shows from a distance.
				int half = close ? near : (int) (near + (distance - near) * 0.6);
				int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
				int bottom = Math.max(cj - half, ground);
				int top = Math.max(cj + half, ground);
				if (!Float.isNaN(clouds)) {
					top = Math.min(top, MathHelper.floor(clouds));
				}
				if (bottom >= top) {
					continue;
				}
				int lightY = Math.max(ground, cj);
				Random random = Random.create(x * x * 3121 + x * 45238971 ^ z * z * 418711 + z * 13761);
				pos.set(x, bottom, z);
				Biome.Precipitation precipitation = biome.getPrecipitation(pos);
				if (precipitation == Biome.Precipitation.NONE) {
					continue;
				}
				// How far it is carried per block it falls: rain (about 9 blocks a second) a little, snow far more, a blizzard's
				// most; at most a block.
				double lean = precipitation == Biome.Precipitation.RAIN ? 2.5 : 6.0 * (1.0 + 1.5 * blizzard);
				double leanX = windX * lean;
				double leanZ = windZ * lean;
				double carried = Math.sqrt(leanX * leanX + leanZ * leanZ);
				if (carried > 1.0) {
					leanX /= carried;
					leanZ /= carried;
				}
				// Slanting in, it can't come through the ground upwind (into a hole, say): it starts above where that stands.
				bottom = heights.clear(x, z, ground, bottom, top, leanX, leanZ);
				if (bottom >= top) {
					continue;
				}
				// A sheet across the line of sight.
				double length = Math.sqrt(distanceSq);
				double sx = length < 0.5 ? 0.5 : -dz / length * 0.5;
				double sz = length < 0.5 ? 0.0 : dx / length * 0.5;
				float fade = close ? 1.0F - (float) (distance * distance) / (near * near) * 0.5F : 0.5F * (1.0F - (float) ((distance - near) / (far - near)));
				pos.set(x, lightY, z);
				int light = WorldRenderer.getLightmapCoordinates(world, pos);
				double baseX = x - cameraX + 0.5;
				double baseZ = z - cameraZ + 0.5;
				if (precipitation == Biome.Precipitation.RAIN) {
					if (texture != 0) {
						if (texture >= 0) {
							BufferRenderer.drawWithGlobalProgram(buffer.end());
						}
						texture = 0;
						RenderSystem.setShaderTexture(0, RAIN);
						buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_LIGHT);
					}
					int scroll = ticks & 131071;
					int offset = x * x * 3121 + x * 45238971 + z * z * 418711 + z * 13761 & 0xFF;
					float speed = 3.0F + random.nextFloat();
					float v = -(scroll + offset + tickDelta) / 32.0F * speed % 32.0F;
					float alpha = (0.5F + 0.5F * fade) * cover * (close ? 1.0F : 0.8F);
					quad(buffer, baseX, baseZ, sx, sz, bottom - cameraY, top - cameraY, ground - cameraY, leanX, leanZ, 0.0F, bottom * 0.25F + v,
							top * 0.25F + v, alpha, light);
				} else {
					if (texture != 1) {
						if (texture >= 0) {
							BufferRenderer.drawWithGlobalProgram(buffer.end());
						}
						texture = 1;
						RenderSystem.setShaderTexture(0, SNOW);
						buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_LIGHT);
					}
					// How far the snow has fallen and been driven sideways: added up tick by tick (ClientWeather), so a change in the
					// wind or the blizzard changes the pace from then on rather than jumping the snow.
					float v = -ClientWeather.snowFall(tickDelta) / 512.0F;
					float u = (float) (random.nextDouble() + time * 0.01 * random.nextGaussian()) + ClientWeather.snowDrive(tickDelta);
					float jitter = (float) (random.nextDouble() + time * random.nextGaussian() * 0.001);
					float alpha = Math.min(1.0F, (0.5F + 0.3F * fade) * cover * (close ? 1.0F : 0.8F) * (1.0F + 1.5F * blizzard));
					int sky = light >> 16 & 65535;
					int block = light & 65535;
					int snowLight = ((block * 3 + 240) / 4) | ((sky * 3 + 240) / 4) << 16;
					quad(buffer, baseX, baseZ, sx, sz, bottom - cameraY, top - cameraY, ground - cameraY, leanX, leanZ, u, bottom * 0.25F + v + jitter,
							top * 0.25F + v + jitter, alpha, snowLight);
				}
			}
		}
		if (texture >= 0) {
			BufferRenderer.drawWithGlobalProgram(buffer.end());
		}
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
		manager.disable();
	}

	/**
	 * One falling sheet from y0 to y1 (camera-relative) over a column whose ground is at floor, leaning with the wind
	 * (lean is how far it is carried per block it falls): where it lands is the column, so higher up it is that much
	 * further upwind. Fixed to the ground, it stays put however the camera moves.
	 */
	private static void quad(BufferBuilder buffer, double x, double z, double sx, double sz, double y0, double y1, double floor, double leanX,
			double leanZ, float u, float v0, float v1, float alpha, int light) {
		double topX = -leanX * (y1 - floor);
		double topZ = -leanZ * (y1 - floor);
		double bottomX = -leanX * (y0 - floor);
		double bottomZ = -leanZ * (y0 - floor);
		buffer.vertex((float) (x - sx + topX), (float) y1, (float) (z - sz + topZ)).texture(u, v0).color(1.0F, 1.0F, 1.0F, alpha).light(light);
		buffer.vertex((float) (x + sx + topX), (float) y1, (float) (z + sz + topZ)).texture(1.0F + u, v0).color(1.0F, 1.0F, 1.0F, alpha).light(light);
		buffer.vertex((float) (x + sx + bottomX), (float) y0, (float) (z + sz + bottomZ)).texture(1.0F + u, v1).color(1.0F, 1.0F, 1.0F, alpha).light(light);
		buffer.vertex((float) (x - sx + bottomX), (float) y0, (float) (z - sz + bottomZ)).texture(u, v1).color(1.0F, 1.0F, 1.0F, alpha).light(light);
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
		 * The bottom of a slanting sheet that lands on the column at x, z (its ground at floor): above the highest place
		 * its line up and upwind passes through the ground, so it never comes through blocks; top or more if it is
		 * blocked all the way up. Rain falling nearly straight is left alone.
		 */
		int clear(int x, int z, int floor, int bottom, int top, double leanX, double leanZ) {
			if (leanX * leanX + leanZ * leanZ < 0.0025) {
				return bottom;
			}
			for (int y = top; y > bottom; y--) {
				double above = y - floor;
				if (this.top(MathHelper.floor(x + 0.5 - leanX * above), MathHelper.floor(z + 0.5 - leanZ * above)) > y) {
					return y + 1;
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
