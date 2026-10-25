package com.eternalreturn.gametest;

import com.eternalreturn.weather.LocalWeather;
import com.eternalreturn.weather.Storm;
import com.eternalreturn.weather.StormField;
import com.eternalreturn.weather.WeatherPayload;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.WeatherCheckLootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Optional;

/** Local weather (LocalWeather): storms that rain only under themselves, drift, and come and go with /weather. */
public class WeatherTests implements FabricGameTest {
	private static final String BATCH = "weather";

	/** A biome over test-relative x and z from..to (inside the test's own area), from the bottom of the world up. */
	private static void fillBiome(TestContext ctx, int from, int to, String biome) {
		MinecraftServer server = ctx.getWorld().getServer();
		int bottom = ctx.getWorld().getBottomY();
		// Up past sea level too, where the storms judge the country.
		int top = Math.max(bottom + 30, ctx.getWorld().getSeaLevel() + 8);
		BlockPos a = ctx.getAbsolutePos(new BlockPos(from, 0, from));
		BlockPos b = ctx.getAbsolutePos(new BlockPos(to, 0, to));
		server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(), "fillbiome " + Math.min(a.getX(), b.getX()) + " " + bottom + " "
				+ Math.min(a.getZ(), b.getZ()) + " " + Math.max(a.getX(), b.getX()) + " " + top + " " + Math.max(a.getZ(), b.getZ()) + " " + biome);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, skyAccess = true)
	public void stormsRainOnlyUnderThemselves(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos here = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
		Storm rain = Weathers.rain(world, Vec3d.ofCenter(here), false);
		try {
			ctx.assertTrue(!world.isRaining(), "no rain everywhere at once");
			ctx.assertTrue(world.hasRain(here), "rain under the storm");
			ctx.assertTrue(!world.hasRain(here.add(400, 0, 0)), "none 400 blocks off");
			ctx.assertTrue(!LocalWeather.thunderAt(world, here), "a plain storm doesn't thunder");
			ctx.assertTrue(LocalWeather.field(world).darkness(here.getX() + 64 + 100, here.getZ()) > 0.0F
					&& !LocalWeather.rainingAt(world, here.add(64 + 100, 0, 0)), "the sky darkens a way beyond its rain");
		} finally {
			Weathers.dry(world, rain);
		}
		Storm thunder = Weathers.rain(world, Vec3d.ofCenter(here), true);
		try {
			ctx.assertTrue(LocalWeather.thunderAt(world, here), "a thunderstorm thunders");
		} finally {
			Weathers.dry(world, thunder);
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void stormsDriftAndAge(TestContext ctx) {
		StormField field = new StormField();
		field.windAngle = 0.0F;
		field.windSpeed = 0.1F;
		Storm storm = new Storm(1, 0.0, 0.0, 200.0F, false, 42L, 0, 6000, 0.0F, 1.0F);
		field.storms.add(storm);
		field.drift(100);
		ctx.assertTrue(Math.abs(storm.x - 10.0) < 0.01 && Math.abs(storm.z) < 0.01, "carried east by the wind: " + storm.x + ", " + storm.z);
		storm.age(Storm.GROW / 2);
		ctx.assertTrue(storm.intensity > 0.4F && storm.intensity < 0.6F, "building up: " + storm.intensity);
		storm.age(Storm.GROW);
		ctx.assertTrue(storm.intensity > 0.99F, "at its height: " + storm.intensity);
		storm.climate = 0.0F;
		storm.age(0);
		ctx.assertTrue(storm.intensity < 0.4F, "weak over dry country: " + storm.intensity);
		storm.age(6000);
		ctx.assertTrue(storm.over() && storm.intensity == 0.0F, "over at the end of its life");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void wetCountryStorms(TestContext ctx) {
		var biomes = ctx.getWorld().getRegistryManager().get(net.minecraft.registry.RegistryKeys.BIOME);
		float jungle = LocalWeather.climate(biomes.entryOf(net.minecraft.world.biome.BiomeKeys.JUNGLE));
		float swamp = LocalWeather.climate(biomes.entryOf(net.minecraft.world.biome.BiomeKeys.SWAMP));
		float ocean = LocalWeather.climate(biomes.entryOf(net.minecraft.world.biome.BiomeKeys.OCEAN));
		float plains = LocalWeather.climate(biomes.entryOf(net.minecraft.world.biome.BiomeKeys.PLAINS));
		float desert = LocalWeather.climate(biomes.entryOf(net.minecraft.world.biome.BiomeKeys.DESERT));
		float badlands = LocalWeather.climate(biomes.entryOf(net.minecraft.world.biome.BiomeKeys.BADLANDS));
		ctx.assertTrue(jungle == 1.0F && swamp == 1.0F && ocean == 1.0F, "wet: jungle " + jungle + ", swamp " + swamp + ", ocean " + ocean);
		ctx.assertTrue(plains > desert && desert < 0.1F && badlands < 0.1F, "plains " + plains + ", desert " + desert + ", badlands " + badlands);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, skyAccess = true)
	public void thunderstormsOverSnowAreBlizzards(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		// In the middle, so the game's blurring of biome edges still lands in the snow.
		BlockPos snow = ctx.getAbsolutePos(new BlockPos(4, 1, 4));
		fillBiome(ctx, 0, 7, "minecraft:snowy_plains");
		Storm storm = Weathers.rain(world, Vec3d.ofCenter(snow), true);
		try {
			ctx.assertTrue(LocalWeather.blizzardAt(world, snow), "a blizzard over the snowy plains");
		} finally {
			Weathers.dry(world, storm);
		}
		Storm plain = Weathers.rain(world, Vec3d.ofCenter(snow), false);
		try {
			ctx.assertTrue(!LocalWeather.blizzardAt(world, snow), "plain snow without thunder");
		} finally {
			Weathers.dry(world, plain);
		}
		ctx.complete();
	}

	/** The weather check fish and Channeling use: rain or thunder where it is asked about. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, skyAccess = true)
	public void weatherChecksAskTheSpot(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos here = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
		LootCondition thundering = WeatherCheckLootCondition.create().thundering(true).build();
		Storm storm = Weathers.rain(world, Vec3d.ofCenter(here), true);
		try {
			ctx.assertTrue(thundering.test(context(world, Vec3d.ofCenter(here))), "thunder under the storm");
			ctx.assertTrue(!thundering.test(context(world, Vec3d.ofCenter(here.add(500, 0, 0)))), "none far off");
		} finally {
			Weathers.dry(world, storm);
		}
		ctx.complete();
	}

	private static LootContext context(ServerWorld world, Vec3d at) {
		return new LootContext.Builder(new LootContextParameterSet.Builder(world).add(LootContextParameters.ORIGIN, at).build(LootContextTypes.COMMAND))
				.build(Optional.empty());
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void stormsAreSavedAndSent(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		LocalWeather weather = new LocalWeather();
		weather.field.windAngle = 1.25F;
		weather.field.windSpeed = 0.12F;
		Storm storm = weather.add(100.0, -200.0, 180.0F, true, 500, 9000, 0.9F, world.random);
		NbtCompound nbt = weather.writeNbt(new NbtCompound(), world.getRegistryManager());
		LocalWeather loaded = LocalWeather.fromNbt(nbt, world.getRegistryManager());
		Storm back = loaded.field.storms.get(0);
		ctx.assertTrue(back.x == 100.0 && back.z == -200.0 && back.radius == 180.0F && back.thunder && back.age == 500 && back.life == 9000
				&& loaded.field.windAngle == 1.25F && loaded.field.windSpeed == 0.12F, "saved and loaded");
		RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), world.getRegistryManager());
		WeatherPayload.CODEC.encode(buf, new WeatherPayload(true, 1.25F, 0.12F, List.of(storm)));
		WeatherPayload sent = WeatherPayload.CODEC.decode(buf);
		Storm received = sent.storms().get(0);
		ctx.assertTrue(sent.active() && received.x == 100.0 && received.radius == 180.0F && received.thunder && received.seed == storm.seed
				&& received.intensity == storm.intensity, "sent to players");
		ctx.assertTrue(Math.abs(received.cover(100.0, -200.0, StormField.RAIN_EDGE) - storm.cover(100.0, -200.0, StormField.RAIN_EDGE)) < 1.0E-6,
				"and their games see the same rain");
		ctx.complete();
	}

	/** /weather makes a storm where it is run and clears them all; setWeather (other code) makes them over the players. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "weather_command", skyAccess = true, tickLimit = 60)
	public void weatherCommandMakesStorms(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		MinecraftServer server = world.getServer();
		BlockPos here = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
		server.getCommandManager().executeWithPrefix(server.getCommandSource().withWorld(world).withPosition(Vec3d.ofCenter(here)).withSilent(),
				"weather thunder 600");
		ctx.assertTrue(LocalWeather.thunderAt(world, here) && world.hasRain(here), "/weather thunder: a thunderstorm here");
		server.getCommandManager().executeWithPrefix(server.getCommandSource().withWorld(world).withSilent(), "weather clear 600");
		ctx.assertTrue(LocalWeather.get(world).field.storms.isEmpty() && !world.hasRain(here), "/weather clear: none left");
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 1, 1));
		try {
			world.setWeather(0, 600, true, false);
			ctx.assertTrue(world.hasRain(player.getBlockPos()), "setWeather: rain over the player");
			world.setWeather(600, 0, false, false);
			ctx.assertTrue(!world.hasRain(player.getBlockPos()), "and clear again");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	/** Rain thins out from a storm's core through its edge, rather than stopping at a line. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void stormsThinOutAtTheEdge(TestContext ctx) {
		Storm storm = new Storm(1, 0.0, 0.0, 200.0F, false, 0L, Storm.GROW, 99999, 1.0F, 1.0F);
		float last = 2.0F;
		StringBuilder seen = new StringBuilder();
		for (int distance = 0; distance <= 400; distance += 20) {
			float cover = storm.cover(distance, 0.0, StormField.RAIN_EDGE);
			seen.append(' ').append(distance).append(':').append(String.format("%.2f", cover));
			ctx.assertTrue(cover <= last, "never heavier further out:" + seen);
			last = cover;
		}
		ctx.assertTrue(storm.cover(0.0, 0.0, StormField.RAIN_EDGE) == 1.0F && storm.cover(400.0, 0.0, StormField.RAIN_EDGE) == 0.0F,
				"full in the middle, none far off");
		int thinning = 0;
		for (int distance = 0; distance <= 400; distance += 20) {
			float cover = storm.cover(distance, 0.0, StormField.RAIN_EDGE);
			thinning += cover > 0.05F && cover < 0.95F ? 1 : 0;
		}
		ctx.assertTrue(thinning >= 5, "thins out over 100 blocks or more:" + seen);
		ctx.complete();
	}

	/** /eternalreturn weather lists the storms near the player, in words (every one of them in the language file). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, skyAccess = true)
	public void weatherCommandListsStorms(TestContext ctx) throws Exception {
		ServerWorld world = ctx.getWorld();
		BlockPos here = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
		Storm storm = Weathers.rain(world, Vec3d.ofCenter(here), true);
		List<net.minecraft.text.Text> said = new java.util.ArrayList<>();
		net.minecraft.server.command.CommandOutput output = new net.minecraft.server.command.CommandOutput() {
			@Override
			public void sendMessage(net.minecraft.text.Text message) {
				said.add(message);
			}

			@Override
			public boolean shouldReceiveFeedback() {
				return true;
			}

			@Override
			public boolean shouldTrackOutput() {
				return true;
			}

			@Override
			public boolean shouldBroadcastConsoleToOps() {
				return false;
			}
		};
		try {
			net.minecraft.server.command.ServerCommandSource source = new net.minecraft.server.command.ServerCommandSource(output, Vec3d.ofCenter(here),
					net.minecraft.util.math.Vec2f.ZERO, world, 4, "test", net.minecraft.text.Text.literal("test"), world.getServer(), null);
			int found = world.getServer().getCommandManager().getDispatcher().execute("eternalreturn weather", source);
			ctx.assertTrue(found >= 1, "found the storm overhead: " + found);
			java.util.Set<String> keys = new java.util.HashSet<>();
			for (net.minecraft.text.Text text : said) {
				collectKeys(text, keys);
			}
			ctx.assertTrue(keys.contains("commands.eternalreturn.weather.thunderstorm") && keys.contains("commands.eternalreturn.weather.here"),
					"told of the thunderstorm over the player: " + keys);
			com.google.gson.JsonObject lang;
			try (var in = WeatherTests.class.getResourceAsStream("/assets/eternalreturn/lang/en_us.json")) {
				lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
			}
			List<String> missing = keys.stream().filter(key -> key.startsWith("commands.eternalreturn.") && !lang.has(key)).toList();
			ctx.assertTrue(missing.isEmpty(), "words missing from the language file: " + missing);
		} finally {
			Weathers.dry(world, storm);
		}
		ctx.complete();
	}

	private static void collectKeys(net.minecraft.text.Text text, java.util.Set<String> keys) {
		if (text.getContent() instanceof net.minecraft.text.TranslatableTextContent translatable) {
			keys.add(translatable.getKey());
			for (Object arg : translatable.getArgs()) {
				if (arg instanceof net.minecraft.text.Text inner) {
					collectKeys(inner, keys);
				}
			}
		}
		for (net.minecraft.text.Text sibling : text.getSiblings()) {
			collectKeys(sibling, keys);
		}
	}
}
