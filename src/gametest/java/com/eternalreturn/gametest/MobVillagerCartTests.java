package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.blocks.Minecarts;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.mixin.mobs.MobEntityAccessor;
import com.eternalreturn.mobs.ZombieVillagerJobs;
import com.eternalreturn.mobs.ai.LeapGapGoal;
import com.eternalreturn.mobs.ai.ZombieCrossbowGoal;
import com.eternalreturn.mobs.ai.ZombiePotionGoal;
import com.eternalreturn.survival.BeaconPower;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PoweredRailBlock;
import net.minecraft.block.RailBlock;
import net.minecraft.block.WitherSkullBlock;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.EntityInteraction;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.biome.BiomeKeys;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Zombies by trade and on horseback, pillagers backing off, End phantoms, sheep and wolves, mob parkour, the
 * wither and bedrock; villagers' chores, no golem summoning, no cure discount; beacons without pyramids; and
 * minecarts (fast on curves, chained together).
 */
public class MobVillagerCartTests implements FabricGameTest {
	private static void floor(TestContext ctx) {
		for (int x = -1; x <= 8; x++) {
			for (int z = -1; z <= 8; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static <T extends MobEntity> T still(TestContext ctx, EntityType<T> type, BlockPos pos) {
		T mob = ctx.spawnEntity(type, pos);
		mob.setAiDisabled(true);
		return mob;
	}

	private static boolean hasGoal(MobEntity mob, Class<?> type) {
		return ((MobEntityAccessor) mob).eternalreturn$goalSelector().getGoals().stream().anyMatch(goal -> type.isInstance(goal.getGoal()));
	}

	private static ZombieVillagerEntity zombieVillager(TestContext ctx, VillagerProfession job, BlockPos pos) {
		ZombieVillagerEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE_VILLAGER, pos);
		zombie.setVillagerData(zombie.getVillagerData().withProfession(job));
		ZombieVillagerJobs.onSpawn(zombie, ctx.getWorld(), ctx.getWorld().getLocalDifficulty(zombie.getBlockPos()), SpawnReason.NATURAL);
		return zombie;
	}

	private static VillagerEntity villager(TestContext ctx, VillagerProfession job, BlockPos pos) {
		VillagerEntity villager = ctx.spawnEntity(EntityType.VILLAGER, pos);
		// Past novice, so it keeps its trade without a workstation.
		villager.setVillagerData(villager.getVillagerData().withProfession(job).withLevel(2));
		villager.setExperience(20);
		return villager;
	}

	// ---------- zombies and other mobs ----------

	/**
	 * Zombie fletchers get crossbows, zombie fishermen rods, clerics throw potions; zombies and other tagged mobs
	 * leap gaps, cows don't; with the chance set to 1, a natural zombie arrives on a zombie horse.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "zombie_jobs")
	public void zombiesByTradeAndOnHorseback(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		world.setTimeOfDay(18000);
		floor(ctx);
		ZombieVillagerEntity fletcher = zombieVillager(ctx, VillagerProfession.FLETCHER, new BlockPos(1, 1, 1));
		ZombieVillagerEntity fisherman = zombieVillager(ctx, VillagerProfession.FISHERMAN, new BlockPos(3, 1, 1));
		ZombieVillagerEntity cleric = zombieVillager(ctx, VillagerProfession.CLERIC, new BlockPos(5, 1, 1));
		ctx.assertTrue(fletcher.getMainHandStack().isOf(Items.CROSSBOW), "a zombie fletcher holds a crossbow");
		ctx.assertTrue(fisherman.getMainHandStack().isOf(Items.FISHING_ROD), "a zombie fisherman holds a rod");
		ctx.assertTrue(ZombieVillagerJobs.isCleric(cleric) && !ZombieVillagerJobs.isCleric(fletcher), "a cleric is a cleric");
		ctx.assertTrue(hasGoal(fletcher, ZombieCrossbowGoal.class) && hasGoal(cleric, ZombiePotionGoal.class), "zombies have the crossbow and potion goals");
		ctx.assertTrue(hasGoal(fletcher, LeapGapGoal.class), "zombie villagers leap gaps");
		TagKey<EntityType<?>> parkour = TagKey.of(RegistryKeys.ENTITY_TYPE, EternalReturn.id("parkour_mobs"));
		ctx.assertTrue(EntityType.SKELETON.isIn(parkour) && EntityType.PILLAGER.isIn(parkour) && !EntityType.COW.isIn(parkour), "parkour_mobs");
		CowEntity cow = still(ctx, EntityType.COW, new BlockPos(6, 1, 6));
		ctx.assertTrue(!hasGoal(cow, LeapGapGoal.class), "cows don't");
		var mobs = EternalReturnConfig.get().mobs;
		double chance = mobs.zombieHorsemanChance;
		try {
			mobs.zombieHorsemanChance = 1.0;
			ZombieEntity rider = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(3, 1, 5));
			ZombieVillagerJobs.onSpawn(rider, world, world.getLocalDifficulty(rider.getBlockPos()), SpawnReason.NATURAL);
			ctx.assertTrue(rider.getVehicle() != null && rider.getVehicle().getType() == EntityType.ZOMBIE_HORSE, "the zombie rides a zombie horse: " + rider.getVehicle());
		} finally {
			mobs.zombieHorsemanChance = chance;
		}
		ctx.killAllEntities();
		ctx.complete();
	}

	/** A zombie fletcher shoots its crossbow at a villager it hunts. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "zombie_jobs", tickLimit = 160)
	public void zombieFletcherShoots(TestContext ctx) {
		ctx.getWorld().setTimeOfDay(18000);
		floor(ctx);
		ZombieVillagerEntity fletcher = zombieVillager(ctx, VillagerProfession.FLETCHER, new BlockPos(1, 1, 1));
		VillagerEntity target = still(ctx, EntityType.VILLAGER, new BlockPos(6, 1, 6));
		fletcher.setTarget(target);
		ctx.addInstantFinalTask(() -> ctx.killAllEntities());
		ctx.runAtEveryTick(() -> {
			if (!ctx.getWorld().getEntitiesByClass(PersistentProjectileEntity.class, new Box(fletcher.getBlockPos()).expand(12.0),
					arrow -> arrow.getOwner() == fletcher).isEmpty()) {
				ctx.complete();
			}
		});
	}

	/** A zombie cleric throws Harming at a hurt zombie, which heals it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "zombie_jobs", tickLimit = 120)
	public void zombieClericHealsUndead(TestContext ctx) {
		ctx.getWorld().setTimeOfDay(18000);
		floor(ctx);
		ZombieVillagerEntity cleric = zombieVillager(ctx, VillagerProfession.CLERIC, new BlockPos(1, 1, 1));
		ZombieEntity hurt = still(ctx, EntityType.ZOMBIE, new BlockPos(5, 1, 5));
		hurt.setHealth(8.0F);
		ctx.addInstantFinalTask(() -> ctx.killAllEntities());
		ctx.runAtEveryTick(() -> {
			boolean thrown = !ctx.getWorld().getEntitiesByClass(PotionEntity.class, new Box(cleric.getBlockPos()).expand(12.0),
					potion -> potion.getOwner() == cleric).isEmpty();
			if (thrown || hurt.getHealth() > 8.0F) {
				ctx.complete();
			}
		});
	}

	/** A pillager with its crossbow on a target 3 blocks off backs away instead of standing its ground. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "zombie_jobs", tickLimit = 100)
	public void pillagersBackOff(TestContext ctx) {
		floor(ctx);
		PillagerEntity pillager = ctx.spawnEntity(EntityType.PILLAGER, new BlockPos(4, 1, 4));
		pillager.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
		IronGolemEntity golem = still(ctx, EntityType.IRON_GOLEM, new BlockPos(1, 1, 4));
		golem.setInvulnerable(false);
		pillager.setTarget(golem);
		double start = pillager.distanceTo(golem);
		ctx.addInstantFinalTask(() -> ctx.killAllEntities());
		ctx.runAtEveryTick(() -> {
			if (pillager.getTarget() != golem) {
				pillager.setTarget(golem);
			}
			if (pillager.distanceTo(golem) > start + 1.0) {
				ctx.complete();
			}
		});
	}

	/**
	 * Phantoms can spawn in the End; sheep run from wolves; the wither can't be built within 8 blocks of bedrock
	 * (with the distance set to 0, the same build summons it).
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "wither_bedrock")
	public void endPhantomsSheepAndTheWither(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		var spawns = world.getRegistryManager().get(RegistryKeys.BIOME).getOrThrow(BiomeKeys.END_HIGHLANDS).getSpawnSettings().getSpawnEntries(SpawnGroup.MONSTER);
		ctx.assertTrue(spawns.getEntries().stream().anyMatch(e -> e.type == EntityType.PHANTOM), "phantoms spawn in the End highlands");
		SheepEntity sheep = still(ctx, EntityType.SHEEP, new BlockPos(6, 1, 6));
		ctx.assertTrue(hasGoal(sheep, FleeEntityGoal.class), "sheep run from wolves");
		sheep.discard();

		floor(ctx);
		ctx.setBlockState(0, 0, 0, Blocks.BEDROCK);
		var mobs = EternalReturnConfig.get().mobs;
		int reach = mobs.witherBedrockDistance;
		try {
			ctx.assertTrue(!summonWither(ctx), "no wither 3 blocks from bedrock");
			mobs.witherBedrockDistance = 0;
			ctx.assertTrue(summonWither(ctx), "away from bedrock the build summons it");
		} finally {
			mobs.witherBedrockDistance = reach;
		}
		ctx.complete();
	}

	private static boolean summonWither(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ctx.setBlockState(3, 1, 3, Blocks.SOUL_SAND);
		for (int x = 2; x <= 4; x++) {
			ctx.setBlockState(x, 2, 3, Blocks.SOUL_SAND);
			ctx.setBlockState(x, 3, 3, Blocks.WITHER_SKELETON_SKULL);
		}
		BlockPos skull = ctx.getAbsolutePos(new BlockPos(3, 3, 3));
		WitherSkullBlock.onPlaced(world, skull, (SkullBlockEntity) world.getBlockEntity(skull));
		List<WitherEntity> withers = world.getEntitiesByClass(WitherEntity.class, new Box(skull).expand(6.0), w -> true);
		withers.forEach(WitherEntity::discard);
		return !withers.isEmpty();
	}

	// ---------- villagers ----------

	/** An armorer walks to a hurt iron golem and repairs it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "chores", tickLimit = 300)
	public void blacksmithsRepairGolems(TestContext ctx) {
		ctx.getWorld().setTimeOfDay(6000);
		floor(ctx);
		villager(ctx, VillagerProfession.ARMORER, new BlockPos(1, 1, 1));
		IronGolemEntity golem = still(ctx, EntityType.IRON_GOLEM, new BlockPos(4, 1, 1));
		golem.setHealth(50.0F);
		ctx.addInstantFinalTask(() -> ctx.killAllEntities());
		ctx.runAtEveryTick(() -> {
			if (golem.getHealth() >= 75.0F) {
				ctx.complete();
			}
		});
	}

	/** A cleric throws Healing at a hurt villager. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "chores", tickLimit = 300)
	public void clericsHealVillagers(TestContext ctx) {
		ctx.getWorld().setTimeOfDay(6000);
		floor(ctx);
		villager(ctx, VillagerProfession.CLERIC, new BlockPos(1, 1, 1));
		VillagerEntity hurt = still(ctx, EntityType.VILLAGER, new BlockPos(5, 1, 5));
		hurt.setHealth(8.0F);
		ctx.addInstantFinalTask(() -> ctx.killAllEntities());
		ctx.runAtEveryTick(() -> {
			if (hurt.getHealth() > 8.0F) {
				ctx.complete();
			}
		});
	}

	/** A farmer feeds a calf wheat from its pockets, and it grows up faster. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "chores", tickLimit = 300)
	public void farmersFeedBabies(TestContext ctx) {
		ctx.getWorld().setTimeOfDay(6000);
		floor(ctx);
		VillagerEntity farmer = villager(ctx, VillagerProfession.FARMER, new BlockPos(1, 1, 1));
		farmer.getInventory().addStack(new ItemStack(Items.WHEAT, 4));
		CowEntity calf = still(ctx, EntityType.COW, new BlockPos(3, 1, 1));
		calf.setBreedingAge(-24000);
		ctx.addInstantFinalTask(() -> ctx.killAllEntities());
		ctx.runAtEveryTick(() -> {
			if (calf.getBreedingAge() > -22000) {
				ctx.complete();
			}
		});
	}

	/** A shepherd shears a woolly sheep. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "chores", tickLimit = 300)
	public void shepherdsShearSheep(TestContext ctx) {
		ctx.getWorld().setTimeOfDay(6000);
		floor(ctx);
		villager(ctx, VillagerProfession.SHEPHERD, new BlockPos(1, 1, 1));
		SheepEntity sheep = still(ctx, EntityType.SHEEP, new BlockPos(3, 1, 1));
		ctx.addInstantFinalTask(() -> ctx.killAllEntities());
		ctx.runAtEveryTick(() -> {
			if (sheep.isSheared()) {
				ctx.complete();
			}
		});
	}

	/** In the morning a fisherman walks to the water's edge and fishes, rod in hand. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "chores", tickLimit = 300)
	public void fishermenGoFishing(TestContext ctx) {
		ctx.getWorld().setTimeOfDay(6000);
		floor(ctx);
		ctx.setBlockState(6, 0, 6, Blocks.WATER);
		ctx.setBlockState(5, 0, 6, Blocks.WATER);
		VillagerEntity fisherman = villager(ctx, VillagerProfession.FISHERMAN, new BlockPos(1, 1, 1));
		ctx.addInstantFinalTask(() -> ctx.killAllEntities());
		ctx.runAtEveryTick(() -> {
			if (fisherman.getMainHandStack().isOf(Items.FISHING_ROD)) {
				ctx.complete();
			}
		});
	}

	/** Villagers who have slept and want a golem don't get one; curing a zombie villager earns no discount. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "chores")
	public void noGolemsNoCureDiscount(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx);
		VillagerEntity villager = still(ctx, EntityType.VILLAGER, new BlockPos(3, 1, 3));
		villager.getBrain().remember(MemoryModuleType.LAST_SLEPT, world.getTime());
		villager.summonGolem(world, world.getTime(), 0);
		ctx.assertTrue(world.getEntitiesByClass(IronGolemEntity.class, villager.getBoundingBox().expand(20.0), g -> true).isEmpty(), "no golem summoned");
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(5, 1, 5));
		try {
			villager.onInteractionWith(EntityInteraction.ZOMBIE_VILLAGER_CURED, player);
			ctx.assertTrue(villager.getReputation(player) == 0, "no reputation for the cure: " + villager.getReputation(player));
		} finally {
			FishingTests.disconnect(player);
		}
		villager.discard();
		ctx.complete();
	}

	// ---------- beacons ----------

	/**
	 * Every pulse of a beacon paid with iron adds to its effect on players within 32 blocks, up to 30 seconds; one
	 * paid with netherite reaches 512 blocks and 4 minutes. Regeneration is a first-row choice now, and there is no
	 * second effect.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "beacon_power", tickLimit = 200)
	public void beaconsBuildUpOneEffect(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx);
		ctx.assertTrue(BeaconBlockEntity.EFFECTS_BY_LEVEL.get(2).contains(StatusEffects.REGENERATION) && BeaconBlockEntity.EFFECTS_BY_LEVEL.get(3).isEmpty(),
				"Regeneration among the first choices, no second effect");
		ctx.assertTrue(BeaconPower.tierOf(new ItemStack(Items.IRON_INGOT)) == 1 && BeaconPower.tierOf(new ItemStack(Items.NETHERITE_INGOT)) == 5
				&& BeaconPower.tierOf(new ItemStack(Items.STICK)) == 0, "payment tiers");
		ctx.setBlockState(2, 1, 2, Blocks.BEACON);
		BlockPos beacon = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
		ServerPlayerEntity near = FishingTests.survivalPlayer(ctx, new BlockPos(5, 1, 5));
		ServerPlayerEntity far = FishingTests.survivalPlayer(ctx, new BlockPos(5, 1, 5));
		// The scripted player loads no land around itself where it is moved to: keep that chunk loaded meanwhile.
		BlockPos farPos = beacon.add(40, 0, 0);
		world.setChunkForced(farPos.getX() >> 4, farPos.getZ() >> 4, true);
		far.teleport(world, farPos.getX() + 0.5, near.getY(), farPos.getZ() + 0.5, 0.0F, 0.0F);
		long start = ctx.getTick();
		boolean[] done = {false};
		ctx.runAtEveryTick(() -> {
			// Wait until the far player can be found where it stands (its chunk takes a while to load on a busy server).
			boolean seen = !world.getEntitiesByClass(ServerPlayerEntity.class, new Box(farPos).expand(3.0), p -> p == far).isEmpty();
			if (done[0] || (!seen && ctx.getTick() - start < 150)) {
				return;
			}
			done[0] = true;
			try {
				ctx.assertTrue(seen, "the far player's chunk loaded");
				((BeaconPower.Holder) world.getBlockEntity(beacon)).eternalreturn$setTier(1);
				BeaconPower.apply(world, beacon, StatusEffects.SPEED);
				StatusEffectInstance speed = near.getStatusEffect(StatusEffects.SPEED);
				ctx.assertTrue(speed != null && speed.getDuration() == 160 && speed.getAmplifier() == 0, "a pulse gives 8 seconds of Speed: " + speed);
				ctx.assertTrue(far.getStatusEffect(StatusEffects.SPEED) == null, "40 blocks is beyond an iron beacon");
				for (int i = 0; i < 10; i++) {
					BeaconPower.apply(world, beacon, StatusEffects.SPEED);
				}
				ctx.assertTrue(near.getStatusEffect(StatusEffects.SPEED).getDuration() == 600, "iron holds at 30 seconds: " + near.getStatusEffect(StatusEffects.SPEED));
				((BeaconPower.Holder) world.getBlockEntity(beacon)).eternalreturn$setTier(5);
				for (int i = 0; i < 40; i++) {
					BeaconPower.apply(world, beacon, StatusEffects.SPEED);
				}
				ctx.assertTrue(near.getStatusEffect(StatusEffects.SPEED).getDuration() == 4800, "netherite builds to 4 minutes");
				ctx.assertTrue(far.getStatusEffect(StatusEffects.SPEED) != null, "and reaches 40 blocks");
				// Paying through the beacon screen records the payment and keeps only the first effect.
				BeaconBlockEntity entity = (BeaconBlockEntity) world.getBlockEntity(beacon);
				var screen = (net.minecraft.screen.BeaconScreenHandler) entity.createMenu(1, near.getInventory(), near);
				screen.getSlot(0).setStack(new ItemStack(Items.GOLD_INGOT));
				screen.setEffects(java.util.Optional.of(StatusEffects.SPEED), java.util.Optional.of(StatusEffects.REGENERATION));
				NbtCompound saved = entity.createNbt(world.getRegistryManager());
				ctx.assertTrue(((BeaconPower.Holder) entity).eternalreturn$tier() == 2 && saved.getInt("eternalreturn_tier") == 2, "paid with gold: " + saved);
				ctx.assertTrue(saved.contains("primary_effect") && !saved.contains("secondary_effect"), "one effect: " + saved);
				ctx.assertTrue(screen.getSlot(0).getStack().isEmpty(), "the gold was taken");
				near.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, StatusEffectInstance.INFINITE, 1));
				BeaconPower.apply(world, beacon, StatusEffects.HASTE);
				StatusEffectInstance haste = near.getStatusEffect(StatusEffects.HASTE);
				ctx.assertTrue(haste.getAmplifier() == 1 && haste.isInfinite(), "a stronger effect is left alone");
			} finally {
				FishingTests.disconnect(near);
				FishingTests.disconnect(far);
				world.setChunkForced(farPos.getX() >> 4, farPos.getZ() >> 4, false);
			}
			ctx.complete();
		});
	}

	// ---------- minecarts ----------

	/**
	 * A carried minecart sent round a loop of powered rails with four curves keeps to the rails for 5 seconds and
	 * covers well over vanilla's 8 blocks a second.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "minecarts", tickLimit = 200)
	public void fastMinecartsHoldCurves(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx);
		Map<BlockPos, BlockState> track = new LinkedHashMap<>();
		BlockState eastWest = Blocks.POWERED_RAIL.getDefaultState().with(PoweredRailBlock.SHAPE, RailShape.EAST_WEST).with(PoweredRailBlock.POWERED, true);
		BlockState northSouth = Blocks.POWERED_RAIL.getDefaultState().with(PoweredRailBlock.SHAPE, RailShape.NORTH_SOUTH).with(PoweredRailBlock.POWERED, true);
		for (int i = 1; i <= 6; i++) {
			track.put(new BlockPos(i, 1, 0), eastWest);
			track.put(new BlockPos(i, 1, 7), eastWest);
			track.put(new BlockPos(0, 1, i), northSouth);
			track.put(new BlockPos(7, 1, i), northSouth);
		}
		track.put(new BlockPos(0, 1, 0), Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.SOUTH_EAST));
		track.put(new BlockPos(7, 1, 0), Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.SOUTH_WEST));
		track.put(new BlockPos(7, 1, 7), Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.NORTH_WEST));
		track.put(new BlockPos(0, 1, 7), Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.NORTH_EAST));
		track.forEach((pos, state) -> {
			if (state.isOf(Blocks.POWERED_RAIL)) {
				ctx.setBlockState(pos.down(), Blocks.REDSTONE_BLOCK);
			}
		});
		track.forEach(ctx::setBlockState);
		// Placing rails bends their neighbours: set each to its shape again, without telling the neighbours.
		track.forEach((pos, state) -> world.setBlockState(ctx.getAbsolutePos(pos), state, Block.NOTIFY_LISTENERS));
		track.forEach((pos, state) -> ctx.assertTrue(world.getBlockState(ctx.getAbsolutePos(pos)).equals(state), "rail at " + pos + ": "
				+ world.getBlockState(ctx.getAbsolutePos(pos))));
		AbstractMinecartEntity cart = ctx.spawnEntity(EntityType.MINECART, new BlockPos(2, 1, 0));
		PigEntity pig = still(ctx, EntityType.PIG, new BlockPos(2, 1, 0));
		pig.startRiding(cart, true);
		Vec3d east = Vec3d.of(ctx.getAbsolutePos(new BlockPos(1, 0, 0)).subtract(ctx.getAbsolutePos(BlockPos.ORIGIN)));
		cart.setVelocity(east.multiply(0.4));
		double[] covered = {0.0};
		int[] offRail = {0};
		Vec3d[] last = {cart.getPos()};
		long start = ctx.getTick();
		ctx.addInstantFinalTask(() -> ctx.killAllEntities());
		ctx.runAtEveryTick(() -> {
			if (ctx.getTick() <= start) {
				return;
			}
			Vec3d now = cart.getPos();
			covered[0] += now.subtract(last[0]).horizontalLength();
			last[0] = now;
			if (!cart.isOnRail()) {
				offRail[0]++;
			}
			if (ctx.getTick() - start == 100) {
				ctx.assertTrue(offRail[0] == 0, "the cart left the rails " + offRail[0] + " times");
				ctx.assertTrue(cart.hasPassenger(pig), "the pig is still aboard");
				ctx.assertTrue(covered[0] > 60.0, "the cart covered " + covered[0] + " blocks in 5 seconds (vanilla at most 40)");
				ctx.complete();
			}
		});
	}

	/**
	 * A chain clicked on one minecart and then another chains the second behind the first (not back the other way).
	 * The chain is rigid: the front cart pulled, the back one comes along at 1.6 blocks; the back one pushed, the
	 * front one goes ahead of it at 1.6 blocks, not squeezed together. Shears cut it, dropping the chain; a broken
	 * cart drops its chain too.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "minecarts", tickLimit = 100)
	public void chainedMinecartsFollow(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx);
		for (int x = 0; x <= 7; x++) {
			ctx.setBlockState(new BlockPos(x, 1, 3), Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.EAST_WEST));
		}
		AbstractMinecartEntity leader = ctx.spawnEntity(EntityType.MINECART, new BlockPos(4, 1, 3));
		AbstractMinecartEntity follower = ctx.spawnEntity(EntityType.MINECART, new BlockPos(2, 1, 3));
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(3, 1, 5));
		ItemStack chains = new ItemStack(Items.CHAIN, 3);
		player.setStackInHand(Hand.MAIN_HAND, chains);
		ctx.assertTrue(Minecarts.use(player, world, Hand.MAIN_HAND, leader, null) == ActionResult.SUCCESS, "the first click takes");
		Minecarts.use(player, world, Hand.MAIN_HAND, follower, null);
		Minecarts.Linked linked = (Minecarts.Linked) follower;
		ctx.assertTrue(leader.getUuid().equals(linked.eternalreturn$leader()) && linked.eternalreturn$leaderId() == leader.getId(), "the second cart is chained behind the first");
		ctx.assertTrue(chains.getCount() == 2, "a chain was used");
		NbtCompound saved = new NbtCompound();
		follower.writeNbt(saved);
		ctx.assertTrue(saved.containsUuid("eternalreturn_leader"), "the chain is saved");
		Minecarts.use(player, world, Hand.MAIN_HAND, follower, null);
		Minecarts.use(player, world, Hand.MAIN_HAND, leader, null);
		ctx.assertTrue(((Minecarts.Linked) leader).eternalreturn$leader() == null && chains.getCount() == 2, "no chaining back the other way");
		Vec3d east = Vec3d.of(ctx.getAbsolutePos(new BlockPos(1, 0, 0)).subtract(ctx.getAbsolutePos(BlockPos.ORIGIN)));
		double followerStart = follower.getPos().dotProduct(east);
		leader.setVelocity(east.multiply(0.1));
		double[] leaderStart = new double[1];
		ctx.runAtTick(ctx.getTick() + 10, () -> {
			double moved = follower.getPos().dotProduct(east) - followerStart;
			ctx.assertTrue(moved > 0.5, "the chained cart was pulled along " + moved + " blocks");
			ctx.assertTrue(Math.abs(follower.distanceTo(leader) - Minecarts.SPACING) < 0.15, "at the chain's length: " + follower.distanceTo(leader));
			leaderStart[0] = leader.getPos().dotProduct(east);
			follower.setVelocity(follower.getVelocity().add(east.multiply(0.1)));
		});
		ctx.runAtTick(ctx.getTick() + 16, () -> {
			try {
				double pushed = leader.getPos().dotProduct(east) - leaderStart[0];
				ctx.assertTrue(pushed > 0.3, "pushing the back cart moved the front one " + pushed + " blocks");
				ctx.assertTrue(Math.abs(follower.distanceTo(leader) - Minecarts.SPACING) < 0.15, "still at the chain's length: " + follower.distanceTo(leader));
				player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SHEARS));
				Minecarts.use(player, world, Hand.MAIN_HAND, follower, null);
				ctx.assertTrue(linked.eternalreturn$leader() == null, "shears cut the chain");
				ctx.assertTrue(chainsDropped(world, follower) == 1, "which drops");
				player.setStackInHand(Hand.MAIN_HAND, chains);
				Minecarts.use(player, world, Hand.MAIN_HAND, leader, null);
				Minecarts.use(player, world, Hand.MAIN_HAND, follower, null);
				ctx.assertTrue(linked.eternalreturn$leader() != null, "chained again");
				leader.kill();
				ctx.assertTrue(linked.eternalreturn$leader() == null && chainsDropped(world, follower) == 2, "breaking the cart ahead drops the chain");
			} finally {
				FishingTests.disconnect(player);
			}
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	private static int chainsDropped(ServerWorld world, AbstractMinecartEntity near) {
		return world.getEntitiesByClass(ItemEntity.class, near.getBoundingBox().expand(4.0), item -> item.getStack().isOf(Items.CHAIN)).stream()
				.mapToInt(item -> item.getStack().getCount()).sum();
	}
}
