package com.eternalreturn.gametest;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.duck.JockeyMount;
import com.eternalreturn.logic.InherentEnchantments;
import com.eternalreturn.mobs.Jockeys;
import com.eternalreturn.mobs.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.PrioritizedGoal;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.CaveSpiderEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.StrayEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.PolarBearEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Mob features: babies, spawn gear, jockeys, zombie rods and pearls, skeleton swords, boats. */
public class MobFeatureTests implements FabricGameTest {
	// ---------- helpers ----------

	private static void night(TestContext ctx) {
		ctx.getWorld().setTimeOfDay(18000);
	}

	private static void floor(TestContext ctx, int x0, int x1, int z0, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static void withConfig(Consumer<EternalReturnConfig.MobTweaks> change, Runnable body) {
		EternalReturnConfig cfg = EternalReturnConfig.get();
		EternalReturnConfig.MobTweaks saved = cfg.mobs;
		EternalReturnConfig.MobTweaks temp = new EternalReturnConfig.MobTweaks();
		change.accept(temp);
		cfg.mobs = temp;
		try {
			body.run();
		} finally {
			cfg.mobs = saved;
		}
	}

	/** Creates and initializes a mob the way natural spawning does, without adding it to the world. */
	private static <T extends MobEntity> T create(TestContext ctx, EntityType<T> type, BlockPos rel, SpawnReason reason) {
		ServerWorld world = ctx.getWorld();
		T mob = type.create(world);
		Vec3d p = Vec3d.ofBottomCenter(ctx.getAbsolutePos(rel));
		mob.refreshPositionAndAngles(p.x, p.y, p.z, 0.0F, 0.0F);
		mob.initialize(world, world.getLocalDifficulty(mob.getBlockPos()), reason, null);
		return mob;
	}

	private static void check(TestContext ctx, boolean condition, String message) {
		ctx.assertTrue(condition, message);
	}

	/** Re-checks every tick until the assertions pass, then completes; the tick limit fails it otherwise. */
	private static void eventually(TestContext ctx, Runnable assertions) {
		ctx.runAtEveryTick(() -> {
			try {
				assertions.run();
			} catch (net.minecraft.test.GameTestException e) {
				return;
			}
			ctx.complete();
		});
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 0.01;
	}

	private static GoalSelector goals(MobEntity mob) {
		try {
			Field f = MobEntity.class.getDeclaredField("goalSelector");
			f.setAccessible(true);
			return (GoalSelector) f.get(mob);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	// ---------- babies ----------

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void babySkeletonAndCreeper(TestContext ctx) {
		night(ctx);
		floor(ctx, 0, 7, 0, 7);
		SkeletonEntity skeleton = ctx.spawnEntity(EntityType.SKELETON, new BlockPos(1, 1, 1));
		skeleton.setBaby(true);
		check(ctx, skeleton.isBaby(), "skeleton should be a baby");
		check(ctx, near(skeleton.getHeight(), 1.99F * 0.5F), "baby skeleton height " + skeleton.getHeight());
		check(ctx, near(skeleton.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), 0.25 * 1.5),
				"baby skeleton speed " + skeleton.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));

		NbtCompound nbt = new NbtCompound();
		skeleton.writeNbt(nbt);
		check(ctx, nbt.getBoolean("IsBaby"), "IsBaby should be saved");
		SkeletonEntity loaded = EntityType.SKELETON.create(ctx.getWorld());
		loaded.readNbt(nbt);
		check(ctx, loaded.isBaby(), "IsBaby should load");

		StrayEntity stray = skeleton.convertTo(EntityType.STRAY, true);
		check(ctx, stray != null && stray.isBaby(), "baby skeleton should freeze into a baby stray");

		CreeperEntity creeper = ctx.spawnEntity(EntityType.CREEPER, new BlockPos(5, 1, 5));
		check(ctx, !creeper.isBaby() && near(creeper.getHeight(), 1.7F), "adult creeper height " + creeper.getHeight());
		creeper.setBaby(true);
		check(ctx, near(creeper.getHeight(), 0.85F), "baby creeper height " + creeper.getHeight());
		NbtCompound creeperNbt = new NbtCompound();
		creeper.writeNbt(creeperNbt);
		check(ctx, creeperNbt.getShort("Fuse") == 30, "saved fuse should stay the adult value, got " + creeperNbt.getShort("Fuse"));
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void babySpawnRollUsesTag(TestContext ctx) {
		withConfig(c -> c.babyChance = 1.0, () -> {
			check(ctx, create(ctx, EntityType.SKELETON, new BlockPos(1, 1, 1), SpawnReason.NATURAL).isBaby(), "skeleton baby roll");
			check(ctx, create(ctx, EntityType.STRAY, new BlockPos(1, 1, 1), SpawnReason.NATURAL).isBaby(), "stray baby roll");
			check(ctx, create(ctx, EntityType.CREEPER, new BlockPos(1, 1, 1), SpawnReason.NATURAL).isBaby(), "creeper baby roll");
			check(ctx, !create(ctx, EntityType.BOGGED, new BlockPos(1, 1, 1), SpawnReason.NATURAL).isBaby(), "bogged is not in the tag");
			check(ctx, !create(ctx, EntityType.WITHER_SKELETON, new BlockPos(1, 1, 1), SpawnReason.NATURAL).isBaby(), "wither skeleton is not in the tag");
		});
		withConfig(c -> c.babyChance = 0.0, () ->
				check(ctx, !create(ctx, EntityType.SKELETON, new BlockPos(1, 1, 1), SpawnReason.NATURAL).isBaby(), "chance 0 means no babies"));
		ctx.complete();
	}

	private static void creeperBlast(TestContext ctx, boolean baby, int minFuse, int maxFuse, boolean observerShouldSurvive) {
		night(ctx);
		ctx.getWorld().getGameRules().get(GameRules.DO_MOB_GRIEFING).set(false, ctx.getWorld().getServer());
		floor(ctx, 0, 7, 0, 7);
		CreeperEntity creeper = ctx.spawnMob(EntityType.CREEPER, new BlockPos(2, 1, 2));
		ZombieEntity observer = ctx.spawnMob(EntityType.ZOMBIE, new BlockPos(4, 1, 2));
		creeper.setBaby(baby);
		creeper.ignite();
		long start = ctx.getTick();
		ctx.runAtEveryTick(() -> {
			if (!creeper.isRemoved()) {
				return;
			}
			long fuse = ctx.getTick() - start;
			check(ctx, fuse >= minFuse && fuse <= maxFuse, (baby ? "baby" : "adult") + " creeper fused for " + fuse + " ticks");
			boolean survived = observer.isAlive();
			check(ctx, survived == observerShouldSurvive, (baby ? "baby" : "adult") + " blast: zombie 2 blocks away "
					+ (survived ? "survived with " + observer.getHealth() : "died"));
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "creeper_baby")
	public void babyCreeperFastSmallBlast(TestContext ctx) {
		creeperBlast(ctx, true, 13, 18, true);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "creeper_adult")
	public void adultCreeperUnchanged(TestContext ctx) {
		creeperBlast(ctx, false, 28, 33, false);
	}

	// ---------- spawn gear ----------

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void spawnGearRolls(TestContext ctx) {
		withConfig(c -> {
			c.zombieFishingRodChance = 1.0;
			c.zombieEnderPearlChance = 1.0;
			c.skeletonSwordChance = 1.0;
		}, () -> {
			ZombieEntity zombie = create(ctx, EntityType.ZOMBIE, new BlockPos(1, 1, 1), SpawnReason.NATURAL);
			ItemStack main = zombie.getMainHandStack();
			check(ctx, main.isOf(Items.FISHING_ROD) || main.isOf(Items.IRON_SWORD) || main.isOf(Items.IRON_SHOVEL), "zombie main hand " + main);
			check(ctx, zombie.getOffHandStack().isOf(Items.ENDER_PEARL), "zombie off hand " + zombie.getOffHandStack());
			SkeletonEntity skeleton = create(ctx, EntityType.SKELETON, new BlockPos(1, 1, 1), SpawnReason.NATURAL);
			check(ctx, skeleton.getMainHandStack().isOf(Items.BOW), "skeleton keeps its bow in the main hand");
			check(ctx, skeleton.getOffHandStack().isIn(ItemTags.SWORDS), "skeleton off hand " + skeleton.getOffHandStack());
		});
		withConfig(c -> {
			c.zombieFishingRodChance = 0.0;
			c.zombieEnderPearlChance = 0.0;
			c.skeletonSwordChance = 0.0;
		}, () -> {
			SkeletonEntity skeleton = create(ctx, EntityType.SKELETON, new BlockPos(1, 1, 1), SpawnReason.NATURAL);
			check(ctx, skeleton.getOffHandStack().isEmpty(), "no sword at chance 0");
		});
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void armorAndEnchantChances(TestContext ctx) {
		float clamped = ctx.getWorld().getLocalDifficulty(ctx.getAbsolutePos(BlockPos.ORIGIN)).getClampedLocalDifficulty();
		withConfig(c -> {
			c.armorChanceBase = 1.0;
			c.armorChancePerDifficulty = 0.0;
			c.armorEnchantChanceBase = 1.0;
			c.armorEnchantChancePerDifficulty = 0.0;
		}, () -> {
			for (EntityType<? extends MobEntity> type : java.util.List.of(EntityType.ZOMBIE, EntityType.HUSK, EntityType.SKELETON, EntityType.STRAY)) {
				MobEntity mob = create(ctx, type, new BlockPos(1, 1, 1), SpawnReason.NATURAL);
				ItemStack boots = mob.getEquippedStack(EquipmentSlot.FEET);
				check(ctx, !boots.isEmpty(), type.getUntranslatedName() + " should always get armor at chance 1");
				int total = EnchantmentHelper.getEnchantments(boots).getSize();
				int inherent = InherentEnchantments.inherentPortion(boots).size();
				check(ctx, total > inherent, type.getUntranslatedName() + " boots should be enchanted beyond inherent: " + boots.getEnchantments());
			}
			MobEntity creeper = create(ctx, EntityType.CREEPER, new BlockPos(1, 1, 1), SpawnReason.NATURAL);
			check(ctx, creeper.getEquippedStack(EquipmentSlot.FEET).isEmpty(), "creepers are not in armored_spawns");
		});
		if (clamped == 0.0F) {
			withConfig(c -> {
				c.armorChanceBase = 0.0;
				c.armorChancePerDifficulty = 0.0;
			}, () -> {
				MobEntity zombie = create(ctx, EntityType.ZOMBIE, new BlockPos(1, 1, 1), SpawnReason.NATURAL);
				check(ctx, zombie.getEquippedStack(EquipmentSlot.FEET).isEmpty(), "vanilla gives no armor at clamped difficulty 0");
			});
		}
		ctx.complete();
	}

	/** Waits for the lighting engine to light the new box, which can lag while many tests run at once. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 1200)
	public void creepersOnlySpawnUnderground(TestContext ctx) {
		night(ctx);
		// Sealed stone box: floor y0, walls, roof y3. Interior 3x2x3.
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				for (int y = 0; y <= 3; y++) {
					boolean shell = y == 0 || y == 3 || x == 0 || x == 4 || z == 0 || z == 4;
					ctx.setBlockState(x, y, z, shell ? Blocks.STONE : Blocks.AIR);
				}
			}
		}
		ctx.setBlockState(6, 0, 6, Blocks.STONE);
		BlockPos inside = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
		BlockPos open = ctx.getAbsolutePos(new BlockPos(6, 1, 6));
		ServerWorld world = ctx.getWorld();
		eventually(ctx, () -> {
			check(ctx, world.getLightLevel(net.minecraft.world.LightType.SKY, inside) == 0, "box interior should have no sky light yet");
			check(ctx, SpawnRestriction.canSpawn(EntityType.CREEPER, world, SpawnReason.NATURAL, inside, world.getRandom()), "creeper should spawn in the sealed box");
			for (int i = 0; i < 50; i++) {
				check(ctx, !SpawnRestriction.canSpawn(EntityType.CREEPER, world, SpawnReason.NATURAL, open, world.getRandom()), "creeper must not spawn under open sky");
			}
		});
	}

	// ---------- jockeys ----------

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void caveSpiderJockeys(TestContext ctx) {
		night(ctx);
		withConfig(c -> c.caveSpiderJockeyChance = 1.0, () -> {
			CaveSpiderEntity spider = create(ctx, EntityType.CAVE_SPIDER, new BlockPos(2, 1, 2), SpawnReason.SPAWNER);
			check(ctx, spider.getFirstPassenger() instanceof AbstractSkeletonEntity, "cave spider rider: " + spider.getFirstPassenger());
		});
		withConfig(c -> c.caveSpiderJockeyChance = 0.0, () -> {
			CaveSpiderEntity spider = create(ctx, EntityType.CAVE_SPIDER, new BlockPos(2, 1, 2), SpawnReason.SPAWNER);
			check(ctx, !spider.hasPassengers(), "no rider at chance 0");
		});
		withConfig(c -> c.spiderJockeyChance = 0.0, () -> {
			for (int i = 0; i < 300; i++) {
				SpiderEntity spider = create(ctx, EntityType.SPIDER, new BlockPos(2, 1, 2), SpawnReason.NATURAL);
				check(ctx, !spider.hasPassengers(), "vanilla 1% spider jockey roll should be replaced (chance 0 -> never)");
			}
		});
		ctx.complete();
	}

	private static Map<EntityType<?>, Integer> riders(TestContext ctx, String biome) {
		ServerWorld world = ctx.getWorld();
		MinecraftServer server = world.getServer();
		BlockPos a = ctx.getAbsolutePos(new BlockPos(-4, -4, -4));
		BlockPos b = ctx.getAbsolutePos(new BlockPos(11, 11, 11));
		server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(),
				"fillbiome " + a.getX() + " " + a.getY() + " " + a.getZ() + " " + b.getX() + " " + b.getY() + " " + b.getZ() + " " + biome);
		Map<EntityType<?>, Integer> counts = new HashMap<>();
		for (int i = 0; i < 40; i++) {
			SpiderEntity spider = EntityType.SPIDER.create(world);
			Vec3d p = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(3, 1, 3)));
			spider.refreshPositionAndAngles(p.x, p.y, p.z, 0.0F, 0.0F);
			Jockeys.addSpiderRider(spider, world, world.getLocalDifficulty(spider.getBlockPos()), SpawnReason.NATURAL);
			Entity rider = spider.getFirstPassenger();
			counts.merge(rider == null ? EntityType.PIG : rider.getType(), 1, Integer::sum);
		}
		return counts;
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void jockeyRidersMatchBiome(TestContext ctx) {
		night(ctx);
		Map<EntityType<?>, Integer> plains = riders(ctx, "minecraft:plains");
		check(ctx, plains.getOrDefault(EntityType.SKELETON, 0) == 40, "plains riders " + plains);
		Map<EntityType<?>, Integer> snowy = riders(ctx, "minecraft:snowy_plains");
		check(ctx, snowy.getOrDefault(EntityType.STRAY, 0) > 0, "snowy riders " + snowy);
		Map<EntityType<?>, Integer> desert = riders(ctx, "minecraft:desert");
		check(ctx, desert.getOrDefault(EntityType.HUSK, 0) > 0, "desert riders " + desert);
		check(ctx, desert.getOrDefault(EntityType.PIG, 0) == 0, "every spider got a rider " + desert);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void strayRidesPolarBear(TestContext ctx) {
		night(ctx);
		floor(ctx, 0, 7, 0, 7);
		withConfig(c -> c.strayPolarBearJockeyChance = 1.0, () -> {
			StrayEntity stray = create(ctx, EntityType.STRAY, new BlockPos(3, 1, 3), SpawnReason.NATURAL);
			ctx.getWorld().spawnEntityAndPassengers(stray);
			check(ctx, stray.getVehicle() instanceof PolarBearEntity, "stray vehicle: " + stray.getVehicle());
			PolarBearEntity bear = (PolarBearEntity) stray.getVehicle();
			check(ctx, !bear.isRemoved() && bear.getWorld() == ctx.getWorld(), "bear should be in the world");
			check(ctx, ((JockeyMount) bear).eternalreturn$isJockeyMount() && bear.canImmediatelyDespawn(10000.0), "bear should despawn like a hostile mob");
			check(ctx, bear.getControllingPassenger() == stray, "the stray should steer the bear");
			NbtCompound nbt = new NbtCompound();
			bear.writeNbt(nbt);
			PolarBearEntity reloaded = EntityType.POLAR_BEAR.create(ctx.getWorld());
			reloaded.readNbt(nbt);
			check(ctx, ((JockeyMount) reloaded).eternalreturn$isJockeyMount(), "mount flag should survive saving");
			PolarBearEntity wild = ctx.spawnEntity(EntityType.POLAR_BEAR, new BlockPos(6, 1, 6));
			check(ctx, !wild.canImmediatelyDespawn(10000.0), "ordinary polar bears still never despawn");
		});
		ctx.complete();
	}

	// ---------- behavior ----------

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "boat", tickLimit = 140)
	public void monstersBreakOutOfBoats(TestContext ctx) {
		night(ctx);
		floor(ctx, 0, 7, 0, 7);
		BoatEntity boat = ctx.spawnEntity(EntityType.BOAT, new BlockPos(2, 1, 2));
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
		check(ctx, zombie.startRiding(boat, true), "zombie should board");
		BoatEntity pigBoat = ctx.spawnEntity(EntityType.BOAT, new BlockPos(5, 1, 5));
		PigEntity pig = ctx.spawnEntity(EntityType.PIG, new BlockPos(5, 1, 5));
		check(ctx, pig.startRiding(pigBoat, true), "pig should board");
		eventually(ctx, () -> {
			check(ctx, boat.isRemoved() && !zombie.hasVehicle(), "zombie still in boat at tick " + ctx.getTick());
			check(ctx, !pigBoat.isRemoved() && pig.getVehicle() == pigBoat, "pig (not a monster) should stay in its boat");
			check(ctx, !ctx.getWorld().getEntitiesByType(EntityType.ITEM, new Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(8), e -> true).isEmpty(),
					"broken boat should drop");
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "sidearm", tickLimit = 240)
	public void skeletonDrawsSwordUpClose(TestContext ctx) {
		night(ctx);
		floor(ctx, 0, 2, 0, 14);
		SkeletonEntity skeleton = ctx.spawnEntity(EntityType.SKELETON, new BlockPos(1, 1, 1));
		skeleton.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.STONE_SWORD));
		PigEntity pig = ctx.spawnMob(EntityType.PIG, new BlockPos(1, 1, 3));
		pig.setInvulnerable(true);
		skeleton.setTarget(pig);
		int[] phase = {0};
		ctx.runAtEveryTick(() -> {
			skeleton.setTarget(pig);
			boolean sword = skeleton.getMainHandStack().isIn(ItemTags.SWORDS) && skeleton.getOffHandStack().isOf(Items.BOW);
			boolean bow = skeleton.getMainHandStack().isOf(Items.BOW) && skeleton.getOffHandStack().isIn(ItemTags.SWORDS);
			boolean meleeGoal = goals(skeleton).getGoals().stream().map(PrioritizedGoal::getGoal).anyMatch(g -> g instanceof MeleeAttackGoal);
			if (phase[0] == 0 && sword) {
				check(ctx, meleeGoal, "sword in hand but the melee goal is not installed");
				Vec3d far = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(1, 1, 13)));
				pig.refreshPositionAndAngles(far.x, far.y, far.z, 0.0F, 0.0F);
				phase[0] = 1;
			} else if (phase[0] == 1 && bow && skeleton.squaredDistanceTo(pig) > 49.0) {
				check(ctx, !meleeGoal, "bow back in hand but the melee goal is still installed");
				ctx.complete();
			}
		});
	}

	@GameTest(templateName = "eternalreturn_test:lane", batchId = "rod", tickLimit = 200)
	public void zombieReelsTargetIn(TestContext ctx) {
		night(ctx);
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
		zombie.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
		PigEntity pig = ctx.spawnMob(EntityType.PIG, new BlockPos(1, 2, 10));
		pig.setInvulnerable(true);
		double startZ = pig.getZ();
		boolean[] sawHook = {false};
		ctx.runAtEveryTick(() -> {
			zombie.setTarget(pig);
			int hooks = ctx.getWorld().getEntitiesByType(ModEntities.ZOMBIE_HOOK, zombie.getBoundingBox().expand(20), e -> true).size();
			if (hooks > 0) {
				sawHook[0] = true;
			}
			// The pig has no AI, and a zombie hit would knock it away (+z). Only the rod pulls it toward the zombie (-z).
			if (pig.getZ() < startZ - 1.0) {
				check(ctx, sawHook[0], "pig moved toward the zombie but no hook was ever cast");
				ctx.complete();
			}
		});
	}

	@GameTest(templateName = "eternalreturn_test:lane", batchId = "pearl", tickLimit = 200)
	public void zombieThrowsPearlTowardTarget(TestContext ctx) {
		night(ctx);
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
		zombie.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.ENDER_PEARL));
		PigEntity pig = ctx.spawnMob(EntityType.PIG, new BlockPos(1, 2, 26));
		pig.setInvulnerable(true);
		Vec3d[] last = {zombie.getPos()};
		ctx.runAtEveryTick(() -> {
			zombie.setTarget(pig);
			Vec3d now = zombie.getPos();
			double jump = now.distanceTo(last[0]);
			last[0] = now;
			if (jump > 3.0) {
				check(ctx, zombie.getOffHandStack().isEmpty(), "pearl should be used up");
				check(ctx, zombie.distanceTo(pig) < 6.0, "zombie landed " + zombie.distanceTo(pig) + " blocks from the pig");
				ctx.complete();
			}
		});
	}
}
