package com.eternalreturn.weather;

import com.eternalreturn.config.EternalReturnConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameRules;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Local weather (weather.localWeather), in the overworld: instead of the same weather everywhere at once, rain and
 * thunder come as storms (Storm) a few hundred blocks across that form near players, drift with the wind and break up.
 * <ul>
 * <li>Storms form upwind of players, so they come over, keeping about weather.stormsNearPlayers within 1,600 blocks of
 * each in the wettest country (jungles, swamps, the sea, taigas), fewer in drier, and seldom over deserts, savannas
 * and badlands, where they also weaken and die sooner. About a quarter are thunderstorms (weather.thunderstormChance),
 * more over wet country.</li>
 * <li>The wind turns slowly and changes strength (1 to 3 blocks a second); storms move with it, and the clouds, and it
 * slants the rain (client WeatherRenderer).</li>
 * <li>Everything that asks whether it rains at a spot asks the storms there: mobs burning and putting out fires,
 * crops, farmland, cauldrons, fishing, bees, fire, snow piling up, slimes in the rain, endermen, Channeling
 * (WeatherMixins). Lightning strikes only inside thunderstorms, and more often there than vanilla's everywhere.</li>
 * <li>A thunderstorm over snow is a blizzard (weather.blizzards): no lightning, snow piling up four times as fast, and
 * thick driving snow and fog for those in it.</li>
 * <li>Players' games are told of the storms near them each second (WeatherPayload): the sky darkens as one comes near,
 * rain can be seen falling a short way off, and it rains on them only inside one.</li>
 * <li>/weather rain and /weather thunder make a storm where they are run, /weather clear clears them all and keeps the
 * sky clear for its time; /eternalreturn weather lists the storms near the player. With doWeatherCycle off, storms and
 * wind stand still.</li>
 * </ul>
 * Vanilla's own weather is kept off in the overworld meanwhile; other dimensions are vanilla's.
 */
public final class LocalWeather extends PersistentState {
	public static final String ID = "eternalreturn_weather";
	private static final PersistentState.Type<LocalWeather> TYPE = new PersistentState.Type<>(LocalWeather::new, LocalWeather::fromNbt, null);
	/** Storms are counted and made within this far of players. */
	public static final double NEAR = 1600.0;
	/** Storms further than this from every player go. */
	private static final double FORGET = 4000.0;
	/** Each player's game is told of the storms within this far. */
	private static final double SEND = 2600.0;

	/** The storms a player's game knows of (set by the client's ClientWeather), or null where it doesn't. */
	public static Function<World, StormField> clientField = world -> null;

	public final StormField field = new StormField();
	private int nextId;
	/** No storms form before this game time (after /weather clear). */
	private long calmUntil;
	private float windTarget = 0.08F;

	public LocalWeather() {
	}

	public static boolean enabled() {
		return EternalReturnConfig.get().weather.localWeather;
	}

	public static LocalWeather get(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(TYPE, ID);
	}

	/** The storms over this world, or null where the weather is vanilla's. */
	@Nullable
	public static StormField field(World world) {
		if (world.getRegistryKey() != World.OVERWORLD) {
			return null;
		}
		if (world instanceof ServerWorld server) {
			return enabled() ? get(server).field : null;
		}
		return clientField.apply(world);
	}

	/** Whether a storm rains (or snows) here; where the weather is vanilla's, whether it rains at all. */
	public static boolean rainingAt(World world, BlockPos pos) {
		StormField field = field(world);
		return field == null ? world.isRaining() : field.raining(pos.getX() + 0.5, pos.getZ() + 0.5);
	}

	/** Whether a thunderstorm is here; where the weather is vanilla's, whether it thunders at all. */
	public static boolean thunderAt(World world, BlockPos pos) {
		StormField field = field(world);
		return field == null ? world.isThundering() : field.thundering(pos.getX() + 0.5, pos.getZ() + 0.5);
	}

	/** For code that first asks whether it rains anywhere and then checks the spot: with storms, it may be raining somewhere. */
	public static boolean mayRain(World world) {
		return field(world) != null || world.isRaining();
	}

	/** Whether a blizzard blows here: a thunderstorm where it snows. */
	public static boolean blizzardAt(World world, BlockPos pos) {
		StormField field = field(world);
		return field != null && EternalReturnConfig.get().weather.blizzards && field.thundering(pos.getX() + 0.5, pos.getZ() + 0.5)
				&& world.getBiome(pos).value().getPrecipitation(pos) == Biome.Precipitation.SNOW;
	}

	/** How rainy the country is here, 0 (dry) to 1 (wet). */
	public static float climate(World world, double x, double z) {
		return climate(world.getBiome(BlockPos.ofFloored(x, world.getSeaLevel(), z)));
	}

	/** How rainy a biome is: wet (jungles, swamps, the sea, rivers, taigas) 1, dry 0.3, rainless (deserts) 0.05, the rest 0.6. */
	public static float climate(RegistryEntry<Biome> biome) {
		if (!biome.value().hasPrecipitation()) {
			return 0.05F;
		}
		if (biome.isIn(ConventionalBiomeTags.IS_DRY_OVERWORLD)) {
			return 0.3F;
		}
		if (biome.isIn(ConventionalBiomeTags.IS_WET_OVERWORLD) || biome.isIn(BiomeTags.IS_OCEAN) || biome.isIn(BiomeTags.IS_RIVER)
				|| biome.isIn(BiomeTags.IS_TAIGA) || biome.isIn(BiomeTags.IS_JUNGLE)) {
			return 1.0F;
		}
		return 0.6F;
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(WeatherPayload.ID, WeatherPayload.CODEC);
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			if (world.getRegistryKey() == World.OVERWORLD && enabled()) {
				get(world).tick(world);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player));
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> send(player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> send(newPlayer));
		WeatherCommands.register();
	}

	private void tick(ServerWorld world) {
		// Vanilla's weather stays off: it would rain everywhere at once.
		if (world.getRainGradient(1.0F) > 0.0F || world.getThunderGradient(1.0F) > 0.0F || world.getLevelProperties().isRaining()) {
			world.resetWeather();
			world.setRainGradient(0.0F);
			world.setThunderGradient(0.0F);
		}
		long time = world.getTime();
		if (world.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE)) {
			this.turnWind(world.random, time);
			this.step(world, 1);
			if (time % 200 == 0) {
				this.spawn(world);
			}
			if (time % 100 == 0) {
				this.markDirty();
			}
		}
		if (time % 20 == 0) {
			for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
				send(player);
			}
		}
	}

	/** Storms move with the wind and age, and those over or far from everyone go. */
	public void step(ServerWorld world, int ticks) {
		this.field.drift(ticks);
		long time = world.getTime();
		for (Storm storm : this.field.storms) {
			if ((time + storm.id) % 20 == 0) {
				storm.country = climate(world, storm.x, storm.z);
			}
			// Crossing into other country, a storm strengthens or weakens over several seconds rather than by jumps.
			storm.climate += MathHelper.clamp(storm.country - storm.climate, -0.005F * ticks, 0.005F * ticks);
			// Over dry country a storm wears out at twice the pace.
			storm.age(storm.climate < 0.35F ? 2 * ticks : ticks);
		}
		List<ServerPlayerEntity> players = world.getPlayers();
		this.field.storms.removeIf(storm -> storm.over()
				|| players.stream().noneMatch(player -> player.squaredDistanceTo(storm.x, player.getY(), storm.z) < FORGET * FORGET));
	}

	/** The wind wanders round, and every couple of minutes sets out for a new strength. */
	private void turnWind(Random random, long time) {
		this.field.windAngle = MathHelper.wrapDegrees((float) Math.toDegrees(this.field.windAngle) + (random.nextFloat() - 0.5F) * 0.3F)
				* MathHelper.RADIANS_PER_DEGREE;
		if (time % 2400 == 0) {
			this.windTarget = 0.05F + random.nextFloat() * 0.1F;
		}
		this.field.windSpeed += MathHelper.clamp(this.windTarget - this.field.windSpeed, -0.0002F, 0.0002F);
	}

	/** New storms near players who have fewer than their country gives them, upwind of them so they come over. */
	private void spawn(ServerWorld world) {
		if (world.getTime() < this.calmUntil) {
			return;
		}
		EternalReturnConfig.WeatherTweaks cfg = EternalReturnConfig.get().weather;
		Random random = world.random;
		for (ServerPlayerEntity player : world.getPlayers()) {
			double px = player.getX();
			double pz = player.getZ();
			long near = this.field.storms.stream().filter(storm -> MathHelper.square(storm.x - px) + MathHelper.square(storm.z - pz) < NEAR * NEAR).count();
			float around = (climate(world, px, pz) + climate(world, px + 800, pz) + climate(world, px - 800, pz) + climate(world, px, pz + 800)
					+ climate(world, px, pz - 800)) / 5.0F;
			if (near >= cfg.stormsNearPlayers * (0.15 + 0.85 * around) || random.nextFloat() > 0.35F) {
				continue;
			}
			double angle = this.field.windAngle + Math.PI + (random.nextDouble() - 0.5) * 1.6;
			double distance = 500.0 + random.nextDouble() * 900.0;
			double x = px + Math.cos(angle) * distance;
			double z = pz + Math.sin(angle) * distance;
			float here = climate(world, x, z);
			if (random.nextFloat() > 0.2F + here) {
				continue;
			}
			float radius = 110.0F + 260.0F * (float) Math.pow(random.nextDouble(), 1.6);
			boolean thunder = random.nextDouble() < cfg.thunderstormChance * (0.5 + here);
			Storm storm = this.add(x, z, radius, thunder, 0, 6000 + random.nextInt(12000), 0.7F + 0.3F * random.nextFloat(), random);
			storm.climate = here;
			storm.country = here;
		}
	}

	public Storm add(double x, double z, float radius, boolean thunder, int age, int life, float peak, Random random) {
		Storm storm = new Storm(this.nextId++, x, z, radius, thunder, random.nextLong(), age, life, 0.0F, peak);
		storm.age(0);
		this.field.storms.add(storm);
		this.markDirty();
		return storm;
	}

	/** /weather rain or thunder: a storm here, raining within a few seconds, for the time given. */
	public Storm storm(ServerWorld world, Vec3d at, boolean thunder, int duration) {
		this.calmUntil = 0;
		Storm storm = this.add(at.x, at.z, 260.0F, thunder, Storm.GROW - 100, Storm.GROW - 100 + Math.max(duration, Storm.GROW * 2), 1.0F, world.random);
		storm.climate = 1.0F;
		storm.age(0);
		for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
			send(player);
		}
		return storm;
	}

	/** /weather clear: every storm goes, and none form for the time given. */
	public void clear(ServerWorld world, int duration) {
		this.field.storms.clear();
		this.calmUntil = world.getTime() + duration;
		this.markDirty();
		for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
			send(player);
		}
	}

	/** Tells a player's game the storms near them (none, and vanilla's weather, outside the overworld). */
	public static void send(ServerPlayerEntity player) {
		if (!ServerPlayNetworking.canSend(player, WeatherPayload.ID)) {
			return;
		}
		ServerWorld world = player.getServerWorld();
		if (world.getRegistryKey() != World.OVERWORLD || !enabled()) {
			ServerPlayNetworking.send(player, new WeatherPayload(false, 0.0F, 0.0F, List.of()));
			return;
		}
		LocalWeather weather = get(world);
		List<Storm> near = new ArrayList<>();
		for (Storm storm : weather.field.storms) {
			if (player.squaredDistanceTo(storm.x, player.getY(), storm.z) < SEND * SEND) {
				near.add(storm);
			}
		}
		float wind = world.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE) ? weather.field.windSpeed : 0.0F;
		ServerPlayNetworking.send(player, new WeatherPayload(true, weather.field.windAngle, wind, near));
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		NbtList storms = new NbtList();
		for (Storm storm : this.field.storms) {
			storms.add(storm.toNbt());
		}
		nbt.put("storms", storms);
		nbt.putInt("next_id", this.nextId);
		nbt.putLong("calm_until", this.calmUntil);
		nbt.putFloat("wind_angle", this.field.windAngle);
		nbt.putFloat("wind_speed", this.field.windSpeed);
		nbt.putFloat("wind_target", this.windTarget);
		return nbt;
	}

	public static LocalWeather fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		LocalWeather weather = new LocalWeather();
		for (NbtElement element : nbt.getList("storms", NbtElement.COMPOUND_TYPE)) {
			weather.field.storms.add(Storm.fromNbt((NbtCompound) element));
		}
		weather.nextId = nbt.getInt("next_id");
		weather.calmUntil = nbt.getLong("calm_until");
		weather.field.windAngle = nbt.getFloat("wind_angle");
		weather.field.windSpeed = nbt.contains("wind_speed") ? nbt.getFloat("wind_speed") : 0.08F;
		weather.windTarget = nbt.contains("wind_target") ? nbt.getFloat("wind_target") : 0.08F;
		return weather;
	}
}
