package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.blocks.MixedSlabs;
import com.eternalreturn.blocks.Slabs;
import com.eternalreturn.blocks.SnowCover;
import com.eternalreturn.blocks.Snowlogging;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.WorldPresetTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Runs in both flat-world runs: with Moderner Beta (runGametest) and without it
 * (runGametestNoModernerBeta). The Eternal Return world preset must exist exactly when Moderner Beta
 * does, and the server must start either way.
 */
public class CompatTests implements FabricGameTest {
	private static final Identifier PRESET = EternalReturn.id("eternal_return");
	/** The light colours ColorLight (an optional client mod) reads for this mod's blocks when it's installed. */
	private static final String COLORLIGHT_COLOURS = "/assets/colorlight/block/eternalreturn.json";
	/** The moving light LambDynamicLights (an optional client mod) gives this mod's things when it's installed. */
	private static final String DYNAMIC_LIGHTS = "assets/eternalreturn/dynamiclights";
	/** What gives moving light: entity types and items, each named by one of the files. */
	private static final List<String> DYNAMIC_LIGHT_SOURCES = List.of("entity eternalreturn:glass_item_frame",
			"entity eternalreturn:glowing_glass_item_frame", "entity eternalreturn:laser_bolt", "entity eternalreturn:dynamite", "item eternalreturn:glowdine");

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "compat")
	public void eternalReturnPresetOnlyWithModernerBeta(TestContext ctx) {
		DynamicRegistryManager registries = ctx.getWorld().getRegistryManager();
		boolean modernerBeta = GameTestWorld.isModernerBetaLoaded();
		boolean worldPreset = registries.get(RegistryKeys.WORLD_PRESET).containsId(PRESET);
		boolean noiseSettings = registries.get(RegistryKeys.CHUNK_GENERATOR_SETTINGS).containsId(PRESET);
		boolean listed = registries.get(RegistryKeys.WORLD_PRESET).getEntry(PRESET)
				.map(entry -> entry.isIn(WorldPresetTags.NORMAL)).orElse(false);
		EternalReturn.LOGGER.info("[compat] moderner_beta loaded={} | world preset={} | noise settings={} | in world type list={}",
				modernerBeta, worldPreset, noiseSettings, listed);
		ctx.assertTrue(worldPreset == modernerBeta, "Eternal Return world preset present=" + worldPreset + " but Moderner Beta loaded=" + modernerBeta);
		ctx.assertTrue(noiseSettings == modernerBeta, "Eternal Return noise settings present=" + noiseSettings + " but Moderner Beta loaded=" + modernerBeta);
		ctx.assertTrue(listed == modernerBeta, "world type list entry present=" + listed + " but Moderner Beta loaded=" + modernerBeta);
		ctx.complete();
	}

	/**
	 * ColorLight picks a light colour for every block that gives light from its textures, and takes the colour from a
	 * resource file where one names the block. Every block of this mod that gives light is named in this mod's file
	 * (by id or by a * pattern, as ColorLight matches them), every entry names one of them, and every colour is a
	 * colour (red, green, blue 0 to 255), so a new light source can't be left to the texture guess unnoticed.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "compat")
	public void colorLightColoursCoverEveryLightSource(TestContext ctx) {
		JsonObject file;
		try (InputStream in = CompatTests.class.getResourceAsStream(COLORLIGHT_COLOURS)) {
			ctx.assertTrue(in != null, "the ColorLight colours file is in the jar");
			file = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
		List<Identifier> sources = new ArrayList<>();
		for (Block block : Registries.BLOCK) {
			Identifier id = Registries.BLOCK.getId(block);
			if (id.getNamespace().equals(EternalReturn.MOD_ID) && block.getStateManager().getStates().stream().anyMatch(state -> state.getLuminance() > 0)) {
				sources.add(id);
			}
		}
		Map<String, Pattern> entries = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : file.entrySet()) {
			if (entry.getKey().startsWith("_")) {
				continue;
			}
			JsonObject light = entry.getValue().getAsJsonObject();
			ctx.assertTrue(light.has("color") && light.getAsJsonArray("color").size() == 3, entry.getKey() + " gives a colour as [r, g, b]");
			light.getAsJsonArray("color").forEach(channel -> ctx.assertTrue(channel.getAsInt() >= 0 && channel.getAsInt() <= 255, entry.getKey() + ": channels 0 to 255"));
			entries.put(entry.getKey(), Pattern.compile(Pattern.quote(entry.getKey()).replace("*", "\\E.*\\Q")));
		}
		EternalReturn.LOGGER.info("[compat] {} light sources: {}", sources.size(), sources);
		for (Identifier source : sources) {
			ctx.assertTrue(entries.values().stream().anyMatch(pattern -> pattern.matcher(source.toString()).matches()), source + " has a ColorLight colour");
		}
		entries.forEach((key, pattern) -> ctx.assertTrue(sources.stream().anyMatch(source -> pattern.matcher(source.toString()).matches()),
				key + " names a block of this mod that gives light"));
		ctx.complete();
	}

	/**
	 * ColorLight spreads its light by each block's light blocking alone, asked with the world and position from its own
	 * thread (it doesn't look at shapes, as vanilla does for slabs). Asked that way, a slab on edge blocks what it blocks
	 * flat, a mixed slab blocks all light as a double slab does, a snowy slab, stairs, wall, fence or gate what it blocks
	 * bare, and a snow cover nothing; none gives light. Asked off the server thread with the server waiting, an answer
	 * that looked at the world would never come (the lighting-thread freeze the snow cover once had).
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "compat")
	public void lightBlockingAsColorLightReadsIt(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos origin = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
		Map<BlockPos, Integer> expected = new LinkedHashMap<>();
		int x = 0;
		for (Direction side : Direction.Type.HORIZONTAL) {
			BlockPos pos = origin.east(x++);
			world.setBlockState(pos, Slabs.onSide(Blocks.STONE_SLAB.getDefaultState(), side));
			expected.put(pos, Blocks.STONE_SLAB.getDefaultState().getOpacity(world, pos));
		}
		BlockPos mixed = origin.east(x++);
		world.setBlockState(mixed, MixedSlabs.MIXED_SLAB.getDefaultState().with(Slabs.AXIS, Direction.Axis.Z));
		((MixedSlabs.MixedSlabEntity) world.getBlockEntity(mixed)).setHalves(Slabs.normalized(Blocks.STONE_SLAB.getDefaultState()),
				Slabs.normalized(Blocks.OAK_SLAB.getDefaultState()));
		expected.put(mixed, 15);
		for (Block host : List.of(Blocks.STONE_SLAB, Blocks.STONE_STAIRS, Blocks.COBBLESTONE_WALL, Blocks.OAK_FENCE, Blocks.OAK_FENCE_GATE)) {
			BlockPos pos = origin.east(x++).south(2);
			world.setBlockState(pos, host.getDefaultState());
			Snowlogging.snowlog(world, pos, 2, Block.NOTIFY_ALL);
			ctx.assertTrue(SnowCover.marked(world.getBlockState(pos)), host + " is snowy");
			expected.put(pos, host.getDefaultState().getOpacity(world, pos));
		}
		BlockPos fence = origin.south(4);
		world.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState());
		world.setBlockState(fence.up(), Snowlogging.SNOW_COVER.getDefaultState());
		ctx.assertTrue(world.getBlockState(fence.up()).isOf(Snowlogging.SNOW_COVER), "a snow cover on the fence");
		expected.put(fence.up(), 0);

		Map<BlockPos, BlockState> states = new LinkedHashMap<>();
		expected.keySet().forEach(pos -> states.put(pos, world.getBlockState(pos)));
		Map<BlockPos, Integer> seen = CompletableFuture.supplyAsync(() -> {
			Map<BlockPos, Integer> opacity = new LinkedHashMap<>();
			states.forEach((pos, state) -> opacity.put(pos, state.getOpacity(world, pos)));
			return opacity;
		}).completeOnTimeout(null, 5, TimeUnit.SECONDS).join();
		ctx.assertTrue(seen != null, "light blocking asked off the server thread answers without the server");
		states.forEach((pos, state) -> {
			ctx.assertTrue(seen.get(pos).equals(expected.get(pos)), state + " blocks " + seen.get(pos) + " light, as ColorLight reads it; expected " + expected.get(pos));
			ctx.assertTrue(state.getLuminance() == 0, state + " gives no light");
		});
		ctx.complete();
	}

	/**
	 * LambDynamicLights reads light files for entities (dynamiclights/entity) and items (dynamiclights/item): each names
	 * entity types or items and a light of 0 to 15 (or a kind of light it works out itself, such as an item frame's from
	 * its item). Every file of this mod names only things that exist, gives a light in range, and the moving light
	 * sources this mod means to have are all named, so a renamed entity or item can't silently lose its light.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "compat")
	public void dynamicLightFilesNameThisModsThings(TestContext ctx) {
		Path root = FabricLoader.getInstance().getModContainer(EternalReturn.MOD_ID).orElseThrow().findPath(DYNAMIC_LIGHTS).orElse(null);
		ctx.assertTrue(root != null, "the LambDynamicLights files are in the jar");
		List<String> named = new ArrayList<>();
		try (Stream<Path> files = Files.walk(root)) {
			for (Path path : files.filter(file -> file.toString().endsWith(".json")).toList()) {
				String kind = root.relativize(path).getName(0).toString();
				ctx.assertTrue(kind.equals("entity") || kind.equals("item"), path + " is an entity or item light");
				JsonObject light;
				try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
					light = JsonParser.parseReader(in).getAsJsonObject();
				}
				JsonElement ids = light.getAsJsonObject("match").get(kind.equals("entity") ? "type" : "items");
				for (JsonElement id : ids.isJsonArray() ? ids.getAsJsonArray().asList() : List.of(ids)) {
					Identifier identifier = Identifier.of(id.getAsString());
					ctx.assertTrue(kind.equals("entity") ? Registries.ENTITY_TYPE.containsId(identifier) : Registries.ITEM.containsId(identifier),
							path.getFileName() + " names " + identifier + ", which exists");
					named.add(kind + " " + identifier);
				}
				JsonElement luminance = light.get("luminance");
				ctx.assertTrue(luminance.isJsonObject() ? luminance.getAsJsonObject().has("type")
						: luminance.getAsInt() >= 0 && luminance.getAsInt() <= 15, path.getFileName() + " gives a light of 0 to 15 or a kind of light");
			}
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
		EternalReturn.LOGGER.info("[compat] LambDynamicLights light for {}", named);
		DYNAMIC_LIGHT_SOURCES.forEach(source -> ctx.assertTrue(named.contains(source), source + " has a LambDynamicLights light"));
		ctx.complete();
	}

	/**
	 * A falling lantern (LanternFallMixin) is vanilla's falling block, which LambDynamicLights lights by the block it
	 * carries: the lantern it carries still gives a lantern's light (15, a soul lantern 10), so it lights its way down.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "compat", tickLimit = 60)
	public void fallingLanternsCarryTheirLight(TestContext ctx) {
		Map<Block, Integer> lanterns = Map.of(Blocks.LANTERN, 15, Blocks.SOUL_LANTERN, 10);
		int x = 1;
		for (Block lantern : lanterns.keySet()) {
			ctx.setBlockState(new BlockPos(x, 8, 1), Blocks.STONE.getDefaultState());
			ctx.setBlockState(new BlockPos(x, 7, 1), lantern.getDefaultState().with(LanternBlock.HANGING, true));
			x += 3;
		}
		ctx.runAtTick(2, () -> {
			ctx.setBlockState(new BlockPos(1, 8, 1), Blocks.AIR.getDefaultState());
			ctx.setBlockState(new BlockPos(4, 8, 1), Blocks.AIR.getDefaultState());
		});
		Map<Block, Integer> seen = new LinkedHashMap<>();
		ctx.runAtEveryTick(() -> ctx.getWorld().getEntitiesByClass(FallingBlockEntity.class, new Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(8.0),
				falling -> true).forEach(falling -> seen.put(falling.getBlockState().getBlock(), falling.getBlockState().getLuminance())));
		ctx.waitAndRun(40, () -> {
			lanterns.forEach((lantern, light) -> ctx.assertTrue(light.equals(seen.get(lantern)),
					"a falling " + lantern + " gives light " + seen.get(lantern) + "; expected " + light));
			ctx.killAllEntities();
			ctx.complete();
		});
	}
}
