package com.eternalreturn.gametest;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.fishing.FishingItems;
import com.eternalreturn.fishing.HostileCatches;
import com.eternalreturn.fishing.Overfishing;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.mob.GuardianEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.SpawnSettings;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Oceans without monuments: guardians and elder guardians spawning in ocean biomes, monsters on the
 * hook (HostileCatches) and overfished spots (Overfishing). Each test that changes the config has a
 * batch of its own, so nothing else runs while the change is in place.
 */
public class OceanLifeTests implements FabricGameTest {
	// ---------- helpers ----------

	/** A 7 x 7 pool of water 5 deep (test-relative x and z 0 to 6, y 1 to 5) in stone, optionally roofed, in the given biome. */
	private static void pool(TestContext ctx, String biome, boolean roof) {
		for (int x = -1; x <= 7; x++) {
			for (int z = -1; z <= 7; z++) {
				boolean inside = x >= 0 && x <= 6 && z >= 0 && z <= 6;
				ctx.setBlockState(x, 0, z, Blocks.STONE);
				for (int y = 1; y <= 5; y++) {
					ctx.setBlockState(x, y, z, inside ? Blocks.WATER : Blocks.STONE);
				}
				ctx.setBlockState(x, 6, z, roof ? Blocks.STONE : Blocks.AIR);
			}
		}
		MinecraftServer server = ctx.getWorld().getServer();
		BlockPos a = ctx.getAbsolutePos(new BlockPos(-2, -1, -2));
		BlockPos b = ctx.getAbsolutePos(new BlockPos(8, 12, 8));
		server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(),
				"fillbiome " + a.getX() + " " + a.getY() + " " + a.getZ() + " " + b.getX() + " " + b.getY() + " " + b.getZ() + " " + biome);
	}

	/** A hook bobbing on the middle of the pool, owned by the player. */
	private static FishingBobberEntity hook(TestContext ctx, ServerPlayerEntity player) {
		FishingBobberEntity hook = new FishingBobberEntity(player, ctx.getWorld(), 0, 0);
		Vec3d at = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(3, 5, 3))).add(0.0, 0.9, 0.0);
		hook.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
		return hook;
	}

	private static void setField(Object target, String name, Object value) {
		try {
			Field f = FishingBobberEntity.class.getDeclaredField(name);
			f.setAccessible(true);
			f.set(target, value);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static int intField(Object target, String name) {
		try {
			Field f = FishingBobberEntity.class.getDeclaredField(name);
			f.setAccessible(true);
			return f.getInt(target);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	/** Items from a catch (building the pool can knock the test frame's start button off). */
	private static boolean caught(net.minecraft.entity.ItemEntity item) {
		return !item.getStack().isOf(Items.STONE_BUTTON);
	}

	private static List<MobEntity> mobsAround(TestContext ctx) {
		Box around = new Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(12);
		return ctx.getWorld().getEntitiesByClass(MobEntity.class, around, mob -> mob.isAlive());
	}

	private static void clearMobs(TestContext ctx) {
		mobsAround(ctx).forEach(Entity::discard);
	}

	private static int weight(ServerWorld world, RegistryKey<Biome> biome, EntityType<?> type) {
		Biome value = world.getRegistryManager().get(RegistryKeys.BIOME).get(biome);
		int total = 0;
		for (SpawnSettings.SpawnEntry entry : value.getSpawnSettings().getSpawnEntries(SpawnGroup.MONSTER).getEntries()) {
			if (entry.type == type) {
				total += entry.getWeight().getValue();
			}
		}
		return total;
	}

	// ---------- spawning ----------

	/** Every ocean lists guardians (weight 4) and elder guardians (weight 1) as monsters; land biomes don't. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void guardiansSpawnInEveryOcean(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		for (RegistryKey<Biome> ocean : List.of(BiomeKeys.OCEAN, BiomeKeys.DEEP_OCEAN, BiomeKeys.WARM_OCEAN, BiomeKeys.LUKEWARM_OCEAN,
				BiomeKeys.DEEP_LUKEWARM_OCEAN, BiomeKeys.COLD_OCEAN, BiomeKeys.DEEP_COLD_OCEAN, BiomeKeys.FROZEN_OCEAN, BiomeKeys.DEEP_FROZEN_OCEAN)) {
			ctx.assertTrue(weight(world, ocean, EntityType.GUARDIAN) == 4, ocean.getValue() + ": guardian weight " + weight(world, ocean, EntityType.GUARDIAN));
			ctx.assertTrue(weight(world, ocean, EntityType.ELDER_GUARDIAN) == 1, ocean.getValue() + ": elder guardian weight " + weight(world, ocean, EntityType.ELDER_GUARDIAN));
		}
		for (RegistryKey<Biome> land : List.of(BiomeKeys.PLAINS, BiomeKeys.RIVER, BiomeKeys.BEACH, BiomeKeys.SWAMP)) {
			ctx.assertTrue(weight(world, land, EntityType.GUARDIAN) == 0 && weight(world, land, EntityType.ELDER_GUARDIAN) == 0, land.getValue() + " spawns guardians");
		}
		ctx.complete();
	}

	/**
	 * Natural elder guardians: in a roofed ocean pool (so vanilla's open-sky rule doesn't get in the way),
	 * about one try in four passes; none with another elder guardian nearby. They aren't persistent, unlike
	 * the ones a monument would place.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "elder_spawn", tickLimit = 100)
	public void naturalElderGuardiansAreRareAndApart(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ctx.assertTrue(world.getDifficulty() != Difficulty.PEACEFUL, "the test world is on Peaceful");
		pool(ctx, "minecraft:ocean", true);
		BlockPos at = ctx.getAbsolutePos(new BlockPos(3, 2, 3));
		Random random = Random.create(42);
		// The test framework marks finished tests with beacons, which keep monsters from spawning around them.
		com.eternalreturn.survival.BeaconZones.clear(world);
		int alone = 0;
		int guardians = 0;
		for (int i = 0; i < 400; i++) {
			alone += SpawnRestriction.canSpawn(EntityType.ELDER_GUARDIAN, world, SpawnReason.NATURAL, at, random) ? 1 : 0;
			guardians += SpawnRestriction.canSpawn(EntityType.GUARDIAN, world, SpawnReason.NATURAL, at, random) ? 1 : 0;
		}
		ctx.assertTrue(alone > 60 && alone < 140, "about a quarter of natural elder guardian tries should pass, " + alone + " of 400");
		ctx.assertTrue(guardians == 400, "guardians under a roof always pass, " + guardians + " of 400");

		ElderGuardianEntity natural = EntityType.ELDER_GUARDIAN.create(world);
		natural.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
		natural.initialize(world, world.getLocalDifficulty(at), SpawnReason.NATURAL, null);
		world.spawnEntity(natural);
		int near = 0;
		com.eternalreturn.survival.BeaconZones.clear(world);
		for (int i = 0; i < 400; i++) {
			near += SpawnRestriction.canSpawn(EntityType.ELDER_GUARDIAN, world, SpawnReason.NATURAL, at, random) ? 1 : 0;
		}
		ctx.assertTrue(near == 0, near + " natural elder guardian tries passed next to another one");
		ctx.assertTrue(!natural.isPersistent(), "a natural elder guardian should despawn like other monsters");

		ElderGuardianEntity placed = EntityType.ELDER_GUARDIAN.create(world);
		placed.initialize(world, world.getLocalDifficulty(at), SpawnReason.STRUCTURE, null);
		ctx.assertTrue(placed.isPersistent(), "an elder guardian placed by a structure stays persistent");
		natural.discard();
		ctx.complete();
	}

	// ---------- monsters on the hook ----------

	/** Each hostile catch comes up at the hook and goes for the angler; the jockeys are a trident drowned on a guardian that swims by itself. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "hostile_catch", tickLimit = 100)
	public void hostileCatchesComeUpAndAttack(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		pool(ctx, "minecraft:ocean", false);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(-1, 6, 3));
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
		Map<String, EntityType<?>> expected = new LinkedHashMap<>();
		expected.put("drowned", EntityType.DROWNED);
		expected.put("guardian", EntityType.GUARDIAN);
		expected.put("elder_guardian", EntityType.ELDER_GUARDIAN);
		expected.put("drowned_guardian_jockey", EntityType.GUARDIAN);
		expected.put("drowned_elder_guardian_jockey", EntityType.ELDER_GUARDIAN);
		List<String> problems = new ArrayList<>();
		for (Map.Entry<String, EntityType<?>> entry : expected.entrySet()) {
			FishingBobberEntity hook = hook(ctx, player);
			MobEntity mob = HostileCatches.spawn(world, hook, player, entry.getKey());
			if (mob == null) {
				problems.add(entry.getKey() + ": nothing came up");
				continue;
			}
			if (mob.getType() != entry.getValue() || !mob.isAlive() || mob.getTarget() != player) {
				problems.add(entry.getKey() + ": " + mob.getType() + " alive " + mob.isAlive() + " target " + mob.getTarget());
			}
			if (mob.getY() > hook.getY() || mob.getY() < ctx.getAbsolutePos(BlockPos.ORIGIN).getY()) {
				problems.add(entry.getKey() + ": came up at y " + mob.getY() + ", hook at " + hook.getY());
			}
			if (mob instanceof ElderGuardianEntity && mob.isPersistent()) {
				problems.add(entry.getKey() + ": a fished-up elder guardian should despawn like other monsters");
			}
			boolean jockey = entry.getKey().endsWith("_jockey");
			Entity rider = mob.getFirstPassenger();
			if (jockey) {
				if (!(rider instanceof DrownedEntity drowned) || !drowned.getMainHandStack().isOf(Items.TRIDENT) || drowned.getTarget() != player) {
					problems.add(entry.getKey() + ": rider " + rider);
				} else if (mob.getControllingPassenger() != null) {
					problems.add(entry.getKey() + ": the drowned steers its " + mob.getType());
				}
			} else if (rider != null) {
				problems.add(entry.getKey() + ": has a rider " + rider);
			}
			clearMobs(ctx);
		}
		FishingTests.disconnect(player);
		ctx.assertTrue(problems.isEmpty(), String.join("; ", problems));
		ctx.complete();
	}

	/**
	 * Reeling in a bite with hostileCatchChance 1: a drowned comes up instead of items and a worm is
	 * used up. Ocean-only catches don't bite outside oceans, so there the bite is items as usual.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "hostile_catch_reel", tickLimit = 100)
	public void reelingInAMonster(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		EternalReturnConfig cfg = EternalReturnConfig.get();
		EternalReturnConfig.FishingTweaks saved = cfg.fishing;
		EternalReturnConfig.FishingTweaks always = new EternalReturnConfig.FishingTweaks();
		always.hostileCatchChance = 1.0;
		try {
			cfg.fishing = always;
			pool(ctx, "minecraft:plains", false);
			ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(-1, 6, 3));
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
			player.getInventory().main.set(9, new ItemStack(FishingItems.CHUM, 3));
			Box around = new Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(12);

			// Plains: only the drowned can bite here.
			for (int i = 0; i < 3; i++) {
				FishingBobberEntity hook = hook(ctx, player);
				setField(hook, "hookCountdown", 10);
				hook.use(player.getMainHandStack());
			}
			List<DrownedEntity> drowned = world.getEntitiesByClass(DrownedEntity.class, around, mob -> true);
			ctx.assertTrue(drowned.size() == 3 && drowned.stream().allMatch(mob -> mob.getTarget() == player), "three drowned should come up: " + drowned);
			ctx.assertTrue(world.getEntitiesByType(EntityType.ITEM, around, OceanLifeTests::caught).isEmpty(), "a monster catch brings up no items: " + world.getEntitiesByType(EntityType.ITEM, around, OceanLifeTests::caught));
			ctx.assertTrue(player.getInventory().main.get(9).isEmpty(), "each monster catch uses up a worm, left " + player.getInventory().main.get(9));
			clearMobs(ctx);

			// Only ocean catches allowed: outside an ocean the bite is items.
			always.hostileCatches = new LinkedHashMap<>(Map.of("guardian", always.hostileCatches.get("guardian")));
			FishingBobberEntity hook = hook(ctx, player);
			setField(hook, "hookCountdown", 10);
			hook.use(player.getMainHandStack());
			ctx.assertTrue(world.getEntitiesByClass(GuardianEntity.class, around, mob -> true).isEmpty(), "a guardian bit outside an ocean");
			ctx.assertTrue(!world.getEntitiesByType(EntityType.ITEM, around, OceanLifeTests::caught).isEmpty(), "outside an ocean the bite should be items");
			FishingTests.disconnect(player);
		} finally {
			cfg.fishing = saved;
			clearMobs(ctx);
		}
		ctx.complete();
	}

	// ---------- overfishing ----------

	/**
	 * Six catches at one spot (three free, then half as long again for each): the wait for a bite there
	 * is 2.5 times as long, through the real bobber's wait roll; 10 blocks away nothing changes. With a
	 * three-second recovery the spot is fresh again two seconds later.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "overfishing", tickLimit = 200)
	public void overfishedSpotsBiteSlower(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		pool(ctx, "minecraft:plains", false);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(-1, 6, 3));
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
		FishingBobberEntity hook = hook(ctx, player);
		Overfishing.clear(world);
		int[] fresh = rolls(hook, 200);
		for (int i = 0; i < 6; i++) {
			Overfishing.recordCatch(world, hook.getPos());
		}
		double slowdown = Overfishing.waitMultiplier(world, hook.getPos());
		double elsewhere = Overfishing.waitMultiplier(world, hook.getPos().add(10.0, 0.0, 0.0));
		int[] overfished = rolls(hook, 200);
		Overfishing.clear(world);
		ctx.assertTrue(Math.abs(slowdown - 2.5) < 0.01, "six catches should make bites 2.5 times slower, " + slowdown);
		ctx.assertTrue(elsewhere == 1.0, "10 blocks away should be fresh, " + elsewhere);
		ctx.assertTrue(fresh[0] >= 100 && fresh[1] <= 600, "fresh waits are vanilla's 100 to 600 ticks: " + fresh[0] + " to " + fresh[1]);
		ctx.assertTrue(overfished[0] >= 250 && overfished[1] <= 1500 && overfished[1] > 600, "overfished waits should be 250 to 1500 ticks: " + overfished[0] + " to " + overfished[1]);

		EternalReturnConfig cfg = EternalReturnConfig.get();
		EternalReturnConfig.Overfishing savedOverfishing = cfg.fishing.overfishing;
		EternalReturnConfig.Overfishing quick = new EternalReturnConfig.Overfishing();
		quick.recoveryMinutes = 3.0 / 60.0;
		cfg.fishing.overfishing = quick;
		for (int i = 0; i < 6; i++) {
			Overfishing.recordCatch(world, hook.getPos());
		}
		double justAfter = Overfishing.waitMultiplier(world, hook.getPos());
		ctx.runAtTick(ctx.getTick() + 40, () -> {
			double later = Overfishing.waitMultiplier(world, hook.getPos());
			cfg.fishing.overfishing = savedOverfishing;
			Overfishing.clear(world);
			FishingTests.disconnect(player);
			ctx.assertTrue(justAfter > 2.0, "just after six catches the spot is overfished, " + justAfter);
			ctx.assertTrue(later == 1.0, "two seconds later (three-second recovery) the spot should be fresh, " + later);
			ctx.complete();
		});
	}

	/** Lowest and highest of n fresh wait rolls from the bobber's own fishing logic, at its spot. */
	private static int[] rolls(FishingBobberEntity hook, int n) {
		try {
			Method tick = FishingBobberEntity.class.getDeclaredMethod("tickFishingLogic", BlockPos.class);
			tick.setAccessible(true);
			int low = Integer.MAX_VALUE;
			int high = Integer.MIN_VALUE;
			for (int i = 0; i < n; i++) {
				setField(hook, "hookCountdown", 0);
				setField(hook, "fishTravelCountdown", 0);
				setField(hook, "waitCountdown", 0);
				tick.invoke(hook, hook.getBlockPos());
				int wait = intField(hook, "waitCountdown");
				low = Math.min(low, wait);
				high = Math.max(high, wait);
			}
			return new int[]{low, high};
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}
}
