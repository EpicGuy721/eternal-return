package com.eternalreturn.client.weather;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The snow texture thinned out, so less snow can fall as fewer flakes spread evenly, each as bright (WeatherRenderer):
 * level k of LEVELS keeps k of LEVELS shares of its flakes, each flake (a patch of touching pixels) given its share at
 * random, and each level keeps every flake the level below it does. Made from the snow texture the resource packs give,
 * again after each reload.
 */
final class SnowTextures {
	static final int LEVELS = 16;
	private static final Identifier SNOW = Identifier.ofVanilla("textures/environment/snow.png");
	private static Identifier[] levels;

	private SnowTextures() {
	}

	static void register() {
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return EternalReturn.id("thinned_snow");
			}

			@Override
			public void reload(ResourceManager manager) {
				SnowTextures.clear();
			}
		});
	}

	/** The texture with level (1 to LEVELS) shares of the flakes. */
	static Identifier get(int level) {
		if (levels == null) {
			build();
		}
		return levels[level - 1];
	}

	private static void clear() {
		if (levels != null) {
			TextureManager textures = MinecraftClient.getInstance().getTextureManager();
			for (Identifier id : levels) {
				textures.destroyTexture(id);
			}
			levels = null;
		}
	}

	private static void build() {
		levels = new Identifier[LEVELS];
		NativeImage snow;
		try (InputStream in = MinecraftClient.getInstance().getResourceManager().getResourceOrThrow(SNOW).getInputStream()) {
			snow = NativeImage.read(NativeImage.Format.RGBA, in);
		} catch (IOException e) {
			EternalReturn.LOGGER.warn("Couldn't read the snow texture to thin it out", e);
			Arrays.fill(levels, SNOW);
			return;
		}
		int width = snow.getWidth();
		int height = snow.getHeight();
		int[] flakeOf = flakes(snow, width, height);
		int count = Arrays.stream(flakeOf).max().orElse(-1) + 1;
		// Shares dealt out evenly in a shuffled order, so each level adds as many flakes as the next.
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			order.add(i);
		}
		Random random = Random.create(20260);
		for (int i = count - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			order.set(i, order.set(j, order.get(i)));
		}
		float[] share = new float[count];
		for (int i = 0; i < count; i++) {
			share[order.get(i)] = (i + 0.5F) / count;
		}
		TextureManager textures = MinecraftClient.getInstance().getTextureManager();
		for (int level = 1; level <= LEVELS; level++) {
			NativeImage thinned = new NativeImage(width, height, false);
			thinned.copyFrom(snow);
			float keep = level / (float) LEVELS;
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					int flake = flakeOf[y * width + x];
					if (flake >= 0 && share[flake] >= keep) {
						thinned.setColor(x, y, 0);
					}
				}
			}
			Identifier id = EternalReturn.id("snow_" + level);
			textures.registerTexture(id, new NativeImageBackedTexture(thinned));
			levels[level - 1] = id;
		}
		snow.close();
	}

	/** Which flake each pixel is part of (-1 for none): touching pixels, across the edges too, as the texture repeats. */
	private static int[] flakes(NativeImage snow, int width, int height) {
		int[] flakeOf = new int[width * height];
		Arrays.fill(flakeOf, -1);
		int count = 0;
		int[] stack = new int[width * height];
		for (int start = 0; start < flakeOf.length; start++) {
			if (flakeOf[start] >= 0 || opacity(snow, start % width, start / width) == 0) {
				continue;
			}
			int size = 0;
			stack[size++] = start;
			flakeOf[start] = count;
			while (size > 0) {
				int at = stack[--size];
				int x = at % width;
				int y = at / width;
				for (int dy = -1; dy <= 1; dy++) {
					for (int dx = -1; dx <= 1; dx++) {
						int nx = Math.floorMod(x + dx, width);
						int ny = Math.floorMod(y + dy, height);
						int next = ny * width + nx;
						if (flakeOf[next] < 0 && opacity(snow, nx, ny) != 0) {
							flakeOf[next] = count;
							stack[size++] = next;
						}
					}
				}
			}
			count++;
		}
		return flakeOf;
	}

	private static int opacity(NativeImage snow, int x, int y) {
		return snow.getColor(x, y) >>> 24;
	}
}
