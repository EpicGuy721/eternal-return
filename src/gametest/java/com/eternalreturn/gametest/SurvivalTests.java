package com.eternalreturn.gametest;

import com.eternalreturn.blocks.CarpetLayers;
import com.eternalreturn.blocks.CutBlocks;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.fishing.Slayer;
import com.eternalreturn.survival.AnimalHunger;
import com.eternalreturn.survival.BeaconZones;
import com.eternalreturn.survival.BedMemory;
import com.eternalreturn.survival.SpawnerMobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.SlabType;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.SleepManager;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.TeleportTarget;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Beds and remembered beds, beacon areas, animal hunger, herd panic, blind mobs, spawner mobs' drops,
 * Growth and Slayer, wool and concrete stairs and slabs, and layered carpets. Tests that change the day
 * clock or the config have batches of their own.
 */
public class SurvivalTests implements FabricGameTest {
	private static void floor(TestContext ctx, int size, Block block) {
		for (int x = -1; x <= size; x++) {
			for (int z = -1; z <= size; z++) {
				ctx.setBlockState(x, 0, z, block);
			}
		}
	}

	private static void setInt(Object target, String name, int value) throws ReflectiveOperationException {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.setInt(target, value);
	}

	/** A bed facing north with its foot at rel and head one block north; returns the head (absolute). */
	private static BlockPos bed(TestContext ctx, BlockPos rel) {
		BlockState foot = Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.FOOT);
		ctx.setBlockState(rel, foot);
		ctx.setBlockState(rel.north(), foot.with(BedBlock.PART, BedPart.HEAD));
		return ctx.getAbsolutePos(rel.north());
	}

	private static RegistryEntry<Enchantment> enchantment(ServerWorld world, Identifier id) {
		return world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(id).orElseThrow();
	}

	// ---------- beds ----------

	/**
	 * However many players sleep, the night doesn't skip. Beds set the spawn point (by day too) and are
	 * remembered, newest first, three at most; when the newest is broken, respawning uses the one before.
	 * The list is saved and carried over to the respawned player.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "beds", tickLimit = 100)
	public void bedsSetSpawnAndAreRemembered(TestContext ctx) throws ReflectiveOperationException {
		ServerWorld world = ctx.getWorld();
		world.setTimeOfDay(6000);
		SleepManager sleep = new SleepManager();
		setInt(sleep, "total", 1);
		setInt(sleep, "sleeping", 1);
		ctx.assertTrue(!sleep.canSkipNight(100), "everyone asleep should not skip the night");

		floor(ctx, 12, Blocks.STONE);
		BlockPos[] beds = {bed(ctx, new BlockPos(1, 1, 3)), bed(ctx, new BlockPos(4, 1, 3)), bed(ctx, new BlockPos(7, 1, 3)), bed(ctx, new BlockPos(10, 1, 3))};
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 1, 4));
		try {
			for (BlockPos head : beds) {
				player.teleport(world, head.getX() + 0.5, head.getY(), head.getZ() + 2.5, 0.0F, 0.0F);
				player.trySleep(head);
				ctx.assertTrue(head.equals(player.getSpawnPointPosition()), "a bed sets the spawn point by day, got " + player.getSpawnPointPosition());
			}
			List<BedMemory.Bed> remembered = ((BedMemory.Holder) player).eternalreturn$beds();
			ctx.assertTrue(remembered.size() == 3 && remembered.get(0).pos().equals(beds[3]) && remembered.get(2).pos().equals(beds[1]),
					"the three newest beds, newest first: " + remembered);

			// Break the newest bed: respawning uses the one before it.
			ctx.setBlockState(beds[3].subtract(ctx.getAbsolutePos(BlockPos.ORIGIN)), Blocks.AIR);
			ctx.setBlockState(beds[3].south().subtract(ctx.getAbsolutePos(BlockPos.ORIGIN)), Blocks.AIR);
			TeleportTarget target = player.getRespawnTarget(false, TeleportTarget.NO_OP);
			ctx.assertTrue(beds[2].equals(player.getSpawnPointPosition()) && target.pos().isInRange(Vec3d.ofCenter(beds[2]), 3.0),
					"with the newest bed gone, the spawn should move to the one before: " + player.getSpawnPointPosition() + ", respawn at " + target.pos());

			net.minecraft.nbt.NbtCompound saved = new net.minecraft.nbt.NbtCompound();
			player.writeNbt(saved);
			ServerPlayerEntity copy = FishingTests.survivalPlayer(ctx, new BlockPos(1, 1, 6));
			copy.readNbt(saved);
			ctx.assertTrue(((BedMemory.Holder) copy).eternalreturn$beds().size() == 3, "remembered beds are saved");
			ServerPlayerEntity respawned = FishingTests.survivalPlayer(ctx, new BlockPos(2, 1, 6));
			respawned.copyFrom(player, false);
			ctx.assertTrue(((BedMemory.Holder) respawned).eternalreturn$beds().equals(remembered), "the respawned player keeps the list");
			FishingTests.disconnect(copy);
			FishingTests.disconnect(respawned);
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	// ---------- beacons ----------

	/**
	 * A tier-1 beacon on its iron pyramid covers 20 blocks each way: no natural zombie spawns there, and
	 * cows may spawn there but nowhere else. Removing the beacon removes its area.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "beacons", tickLimit = 300)
	public void beaconsShelterAndBreed(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		world.setTimeOfDay(6000);
		BeaconZones.clear(world);
		floor(ctx, 4, Blocks.STONE);
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				ctx.setBlockState(x, 1, z, Blocks.IRON_BLOCK);
			}
		}
		ctx.setBlockState(2, 2, 2, Blocks.BEACON);
		BlockPos beacon = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
		ctx.runAtTick(ctx.getTick() + 100, () -> {
			int level = BeaconZones.level(world, beacon);
			ctx.assertTrue(level == 1, "the beacon registered itself at level 1, got " + level);
			// The test framework marks every finished test with a beacon of its own: forget those for the checks
			// below (they register again on their next tick; these checks all run within this one).
			BeaconZones.clear(world);
			BeaconZones.update(world, beacon, level, true);
			ctx.assertTrue(BeaconZones.covers(world, beacon.add(15, 0, 15)), "15 blocks from a tier-1 beacon is covered");
			ctx.assertTrue(!BeaconZones.covers(world, beacon.add(25, 0, 0)), "25 blocks away is not");
			ctx.assertTrue(!BeaconZones.allowsNaturalSpawn(world, EntityType.ZOMBIE, beacon.add(5, 0, 5)), "no zombies near a beacon");
			ctx.assertTrue(BeaconZones.allowsNaturalSpawn(world, EntityType.ZOMBIE, beacon.add(40, 0, 0)), "zombies away from it");
			ctx.assertTrue(BeaconZones.allowsNaturalSpawn(world, EntityType.COW, beacon.add(5, 0, 5)), "cows may spawn near a beacon");
			ctx.assertTrue(!BeaconZones.allowsNaturalSpawn(world, EntityType.COW, beacon.add(40, 0, 0)), "but not away from one");
			// Through the real spawn rules: a cow on grass in daylight, inside and outside.
			BlockPos inside = beacon.add(6, -1, 0);
			BlockPos outside = beacon.add(30, -1, 0);
			for (BlockPos spot : List.of(inside, outside)) {
				world.setBlockState(spot.down(), Blocks.GRASS_BLOCK.getDefaultState());
				world.setBlockState(spot, Blocks.AIR.getDefaultState());
				world.setBlockState(spot.up(), Blocks.AIR.getDefaultState());
			}
			Random random = Random.create(1);
			ctx.assertTrue(SpawnRestriction.canSpawn(EntityType.COW, world, SpawnReason.NATURAL, inside, random), "a natural cow spawn inside the beacon's area");
			ctx.assertTrue(!SpawnRestriction.canSpawn(EntityType.COW, world, SpawnReason.NATURAL, outside, random), "no natural cow spawn outside it");
			ctx.assertTrue(SpawnRestriction.canSpawn(EntityType.COW, world, SpawnReason.CHUNK_GENERATION, outside, random), "new land still gets its animals");
			world.setBlockState(outside.down(), Blocks.AIR.getDefaultState());
			ctx.setBlockState(2, 2, 2, Blocks.AIR);
			ctx.assertTrue(BeaconZones.level(world, beacon) == 0, "a removed beacon covers nothing");
			ctx.complete();
		});
	}

	// ---------- animals ----------

	/**
	 * A cow 3.5 days unfed on bare stone loses health; a hungry cow on grass grazes it to dirt and is fed;
	 * a cow in love still eats wheat from a hungry player's hand; a horse at full health takes wheat when
	 * hungry. A fed cow doesn't graze.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "animal_hunger", tickLimit = 100)
	public void animalsGrazeOrStarve(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		world.setTimeOfDay(6000);
		EternalReturnConfig cfg = EternalReturnConfig.get();
		double grazeChance = cfg.survival.grazeChance;
		floor(ctx, 8, Blocks.STONE);
		ctx.setBlockState(5, 0, 1, Blocks.GRASS_BLOCK);
		CowEntity starving = ctx.spawnEntity(EntityType.COW, new BlockPos(1, 1, 1));
		CowEntity grazer = ctx.spawnEntity(EntityType.COW, new BlockPos(5, 1, 1));
		CowEntity loving = ctx.spawnEntity(EntityType.COW, new BlockPos(1, 1, 5));
		HorseEntity horse = ctx.spawnEntity(EntityType.HORSE, new BlockPos(5, 1, 5));
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(3, 1, 5));
		boolean griefing = world.getGameRules().getBoolean(net.minecraft.world.GameRules.DO_MOB_GRIEFING);
		try {
			world.getGameRules().get(net.minecraft.world.GameRules.DO_MOB_GRIEFING).set(true, world.getServer());
			cfg.survival.grazeChance = 1.0;
			for (var animal : List.of(starving, grazer, loving, horse)) {
				animal.setAiDisabled(true);
				AnimalHunger.tick(animal);
			}
			long now = world.getTimeOfDay();
			((AnimalHunger.Holder) starving).eternalreturn$setLastFed(now - (long) (3.5 * AnimalHunger.DAY));
			starving.age = 600;
			float before = starving.getHealth();
			AnimalHunger.tick(starving);
			ctx.assertTrue(starving.getHealth() == before - 1.0F, "a starving cow on stone loses a point of health: " + before + " -> " + starving.getHealth());

			((AnimalHunger.Holder) grazer).eternalreturn$setLastFed(now - AnimalHunger.DAY);
			grazer.age = 20;
			AnimalHunger.tick(grazer);
			ctx.assertTrue(AnimalHunger.daysUnfed(grazer) == 0.0 && ctx.getBlockState(new BlockPos(5, 0, 1)).isOf(Blocks.DIRT), "a hungry cow grazes the grass to dirt");
			ctx.setBlockState(5, 0, 1, Blocks.GRASS_BLOCK);
			AnimalHunger.tick(grazer);
			ctx.assertTrue(ctx.getBlockState(new BlockPos(5, 0, 1)).isOf(Blocks.GRASS_BLOCK), "a fed cow leaves the grass alone");

			loving.lovePlayer(null);
			((AnimalHunger.Holder) loving).eternalreturn$setLastFed(now - AnimalHunger.DAY);
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WHEAT, 3));
			loving.interact(player, Hand.MAIN_HAND);
			ctx.assertTrue(AnimalHunger.daysUnfed(loving) == 0.0 && player.getMainHandStack().getCount() == 2, "a hungry cow in love still eats wheat from the hand");

			// A tame horse at full health turns wheat down (an untamed one takes it to calm down); hungry, it eats.
			horse.setTame(true);
			((AnimalHunger.Holder) horse).eternalreturn$setLastFed(now - AnimalHunger.DAY);
			horse.interact(player, Hand.MAIN_HAND);
			ctx.assertTrue(AnimalHunger.daysUnfed(horse) == 0.0 && player.getMainHandStack().getCount() == 1, "a hungry horse at full health takes wheat");
		} finally {
			world.getGameRules().get(net.minecraft.world.GameRules.DO_MOB_GRIEFING).set(griefing, world.getServer());
			cfg.survival.grazeChance = grazeChance;
			FishingTests.disconnect(player);
			starving.discard();
			grazer.discard();
			loving.discard();
			horse.discard();
		}
		ctx.complete();
	}

	/**
	 * Hurting a cow sends the cows around it fleeing as if hurt too (panic comes from recent damage), and a
	 * few ticks later they are running; cows far off and pigs don't react.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "herd_panic", tickLimit = 200)
	public void herdsPanicTogether(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, 8, Blocks.GRASS_BLOCK);
		CowEntity hit = ctx.spawnEntity(EntityType.COW, new BlockPos(1, 1, 1));
		CowEntity near = ctx.spawnEntity(EntityType.COW, new BlockPos(5, 1, 5));
		PigEntity pig = ctx.spawnEntity(EntityType.PIG, new BlockPos(5, 1, 1));
		CowEntity far = ctx.spawnEntity(EntityType.COW, new BlockPos(30, 1, 1));
		ZombieEntity attacker = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(0, 1, 0));
		attacker.setAiDisabled(true);
		hit.damage(world.getDamageSources().mobAttack(attacker), 1.0F);
		boolean panicked = near.getRecentDamageSource() != null && near.getRecentDamageSource().isIn(DamageTypeTags.PANIC_CAUSES) && near.getAttacker() == attacker;
		boolean farCalm = far.getRecentDamageSource() == null && pig.getRecentDamageSource() == null;
		double nearHealth = near.getHealth();
		boolean[] fled = {false};
		ctx.runAtEveryTick(() -> fled[0] |= escaping(near));
		ctx.runAtTick(ctx.getTick() + 40, () -> {
			boolean running = fled[0];
			for (var entity : List.of(hit, near, pig, far, attacker)) {
				entity.discard();
			}
			ctx.assertTrue(panicked, "a cow next to a hurt cow should panic from the same damage");
			ctx.assertTrue(nearHealth == near.getMaxHealth(), "without being hurt itself");
			ctx.assertTrue(farCalm, "a cow 30 blocks away and a pig nearby don't react");
			ctx.assertTrue(running, "the panicking cow runs");
			ctx.complete();
		});
	}

	/** Whether the mob's panic goal (vanilla's EscapeDangerGoal) is running. */
	private static boolean escaping(net.minecraft.entity.mob.MobEntity mob) {
		try {
			java.lang.reflect.Field field = net.minecraft.entity.mob.MobEntity.class.getDeclaredField("goalSelector");
			field.setAccessible(true);
			net.minecraft.entity.ai.goal.GoalSelector goals = (net.minecraft.entity.ai.goal.GoalSelector) field.get(mob);
			return goals.getGoals().stream().anyMatch(goal -> goal.isRunning() && goal.getGoal() instanceof net.minecraft.entity.ai.goal.EscapeDangerGoal);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	/** A blinded zombie's follow range is 2 blocks; without blindness, its normal 35. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void blindMobsFollowOnlyClose(TestContext ctx) {
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		double normal = zombie.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE);
		zombie.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 200));
		double blind = zombie.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE);
		zombie.discard();
		ctx.assertTrue(normal == 35.0 && blind == 2.0, "follow range normal " + normal + ", blinded " + blind);
		ctx.complete();
	}

	/**
	 * A chicken spawner's chickens are marked and drop nothing when killed (an ordinary chicken drops its
	 * raw chicken).
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "spawner_drops", tickLimit = 1200)
	public void spawnerMobsDropNothing(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		world.setTimeOfDay(6000);
		// Spawners put mobs anywhere within 4 blocks: grass all round so every spot works for a chicken.
		for (int x = -2; x <= 10; x++) {
			for (int z = -2; z <= 10; z++) {
				ctx.setBlockState(x, 0, z, Blocks.GRASS_BLOCK);
			}
		}
		ctx.setBlockState(4, 1, 4, Blocks.SPAWNER);
		BlockPos spawnerPos = ctx.getAbsolutePos(new BlockPos(4, 1, 4));
		((MobSpawnerBlockEntity) world.getBlockEntity(spawnerPos)).setEntityType(EntityType.CHICKEN, world.getRandom());
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(0, 1, 0));
		Box around = new Box(spawnerPos).expand(8);
		boolean[] done = {false};
		ctx.runAtEveryTick(() -> {
			if (done[0] || world.getEntitiesByClass(ChickenEntity.class, around, chicken -> true).isEmpty()) {
				return;
			}
			done[0] = true;
			try {
				List<ChickenEntity> chickens = world.getEntitiesByClass(ChickenEntity.class, around, chicken -> true);
				ctx.assertTrue(!chickens.isEmpty(), "the spawner should have let out chickens");
				ctx.assertTrue(chickens.stream().allMatch(SpawnerMobs::dropsNothing), "every spawner chicken is marked");
				ChickenEntity spawned = chickens.get(0);
				ChickenEntity wild = ctx.spawnEntity(EntityType.CHICKEN, new BlockPos(6, 1, 6));
				world.setBlockState(spawnerPos, Blocks.AIR.getDefaultState());
				spawned.damage(world.getDamageSources().playerAttack(player), 100.0F);
				wild.damage(world.getDamageSources().playerAttack(player), 100.0F);
				List<ItemEntity> drops = world.getEntitiesByClass(ItemEntity.class, around.expand(4), item -> item.getStack().isOf(Items.CHICKEN));
				ctx.assertTrue(drops.size() == 1 && drops.get(0).squaredDistanceTo(wild) < 4.0, "only the wild chicken drops raw chicken: " + drops);
			} finally {
				world.getEntitiesByClass(ChickenEntity.class, around.expand(4), chicken -> true).forEach(ChickenEntity::discard);
				FishingTests.disconnect(player);
			}
			ctx.complete();
		});
	}

	// ---------- enchantments ----------

	/** Growth V on a helmet: +5 max health; on two pieces: +10. Slayer III: +15% on top of the 3% base. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void growthAndSlayer(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 1, 1));
		try {
			RegistryEntry<Enchantment> growth = enchantment(world, Identifier.of("eternalreturn", "growth"));
			ctx.assertTrue(growth.value().getMaxLevel() == 5, "Growth goes to V");
			ItemStack helmet = new ItemStack(Items.IRON_HELMET);
			helmet.addEnchantment(growth, 5);
			ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
			boots.addEnchantment(growth, 5);
			player.equipStack(EquipmentSlot.HEAD, helmet);
			player.playerTick();
			double one = player.getMaxHealth();
			player.equipStack(EquipmentSlot.FEET, boots);
			player.playerTick();
			double two = player.getMaxHealth();
			ctx.assertTrue(one == 25.0 && two == 30.0, "Growth V adds 5 max health per piece: " + one + ", " + two);

			RegistryEntry<Enchantment> slayer = enchantment(world, Slayer.KEY.getValue());
			ctx.assertTrue(slayer.value().getMaxLevel() == 5, "Slayer goes to V");
			ItemStack rod = new ItemStack(Items.FISHING_ROD);
			rod.addEnchantment(slayer, 3);
			double chance = Slayer.hostileCatchChance(rod, 0.03, world.getRandom());
			ctx.assertTrue(Math.abs(chance - 0.18) < 1.0E-6, "Slayer III: 3% + 15% = 18%, got " + chance);
			ctx.assertTrue(Slayer.hostileCatchChance(new ItemStack(Items.FISHING_ROD), 0.03, world.getRandom()) == 0.03F, "a plain rod keeps the base chance");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	// ---------- blocks ----------

	/**
	 * 32 stairs and 32 slabs: wool ones burn and count as wool, concrete ones need a pickaxe; each has its
	 * crafting recipe (and stonecutter recipes for concrete); a double slab drops two.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void woolAndConcreteStairsAndSlabs(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ctx.assertTrue(CutBlocks.ALL.size() == 32, "16 colors x wool and concrete, got " + CutBlocks.ALL.size());
		var recipes = world.getRecipeManager();
		for (CutBlocks.Variant variant : CutBlocks.ALL) {
			Identifier stairs = Registries.BLOCK.getId(variant.stairs());
			Identifier slab = Registries.BLOCK.getId(variant.slab());
			ctx.assertTrue(variant.stairs().getDefaultState().isIn(BlockTags.STAIRS) && variant.slab().getDefaultState().isIn(BlockTags.SLABS), stairs + " tags");
			ctx.assertTrue(recipes.get(stairs).isPresent() && recipes.get(slab).isPresent(), stairs + " and " + slab + " recipes");
			if (variant.wool()) {
				ctx.assertTrue(variant.stairs().getDefaultState().isIn(BlockTags.WOOL) && variant.stairs().getDefaultState().isBurnable(), stairs + " is wool and burns");
			} else {
				ctx.assertTrue(variant.slab().getDefaultState().isIn(BlockTags.PICKAXE_MINEABLE) && variant.slab().getDefaultState().isToolRequired(), slab + " needs a pickaxe");
				ctx.assertTrue(recipes.get(Identifier.of(slab.getNamespace(), slab.getPath() + "_from_" + Registries.BLOCK.getId(variant.base()).getPath() + "_stonecutting")).isPresent(),
						slab + " stonecutting");
			}
		}
		Block slab = CutBlocks.ALL.get(0).slab();
		BlockPos at = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
		BlockState doubleSlab = slab.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE);
		world.setBlockState(at, doubleSlab);
		int dropped = Block.getDroppedStacks(doubleSlab, world, at, null).stream().mapToInt(ItemStack::getCount).sum();
		world.setBlockState(at, Blocks.AIR.getDefaultState());
		ctx.assertTrue(dropped == 2, "a double slab drops two, got " + dropped);
		ctx.complete();
	}

	/**
	 * Placing a carpet on a carpet of the same kind adds a layer, up to 16 (a full block); each layer adds
	 * 1/16 of a block of height, and breaking drops one carpet per layer. A different carpet doesn't stack.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void carpetsStack(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, 3, Blocks.STONE);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(3, 1, 3));
		BlockPos at = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
		try {
			ctx.setBlockState(1, 1, 1, Blocks.WHITE_CARPET);
			ctx.assertTrue(world.getBlockState(at).get(CarpetLayers.LAYERS) == 1, "a placed carpet is one layer");
			for (int i = 0; i < 20; i++) {
				player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WHITE_CARPET));
				BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(at), Direction.UP, at, false);
				player.getMainHandStack().useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
			}
			BlockState full = world.getBlockState(at);
			ctx.assertTrue(full.isOf(Blocks.WHITE_CARPET) && full.get(CarpetLayers.LAYERS) == 16, "stacked to 16 layers, got " + full);
			ctx.assertTrue(full.getOutlineShape(world, at).getMax(Direction.Axis.Y) == 1.0, "16 layers is a full block high");
			BlockState five = Blocks.WHITE_CARPET.getDefaultState().with(CarpetLayers.LAYERS, 5);
			ctx.assertTrue(Math.abs(five.getOutlineShape(world, at).getMax(Direction.Axis.Y) - 5.0 / 16.0) < 1.0E-9, "5 layers is 5/16 high");
			int drops = Block.getDroppedStacks(five, world, at, null).stream().mapToInt(ItemStack::getCount).sum();
			ctx.assertTrue(drops == 5, "5 layers drop 5 carpets, got " + drops);

			ctx.setBlockState(2, 1, 1, Blocks.RED_CARPET);
			BlockPos red = at.east();
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WHITE_CARPET));
			player.getMainHandStack().useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(red), Direction.UP, red, false)));
			ctx.assertTrue(world.getBlockState(red).get(CarpetLayers.LAYERS) == 1, "white carpet doesn't stack on red");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}
}
