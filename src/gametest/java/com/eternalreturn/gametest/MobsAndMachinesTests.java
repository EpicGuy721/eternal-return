package com.eternalreturn.gametest;

import com.eternalreturn.blocks.Frames;
import com.eternalreturn.blocks.Redstone;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.duck.JockeyMount;
import com.eternalreturn.mobs.entity.WebEntity;
import com.eternalreturn.survival.Farming;
import com.eternalreturn.survival.Food;
import com.eternalreturn.survival.Soulbound;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChainBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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
import net.minecraft.world.GameRules;

import java.util.List;

/**
 * Spider webs, eating and drinking, mining and hungry zombies, the newer jockeys, Soulbound, dispensers
 * placing blocks, the obsidian pressure plate, pistons moving chests and chains linking blocks, cut vines,
 * petals on water, pig litters, stunted babies, and glass and dyed item frames.
 */
public class MobsAndMachinesTests implements FabricGameTest {
	private static void floor(TestContext ctx, int size) {
		for (int x = -1; x <= size; x++) {
			for (int z = -1; z <= size; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static void withGriefing(TestContext ctx, Runnable body) {
		GameRules.BooleanRule rule = ctx.getWorld().getGameRules().get(GameRules.DO_MOB_GRIEFING);
		boolean saved = rule.get();
		rule.set(true, ctx.getWorld().getServer());
		try {
			body.run();
		} finally {
			rule.set(saved, ctx.getWorld().getServer());
		}
	}

	// ---------- mobs ----------

	/** A web that hits a mob leaves it standing in a cobweb. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void websTrapWhatTheyHit(TestContext ctx) {
		floor(ctx, 4);
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
		zombie.setAiDisabled(true);
		var spider = ctx.spawnEntity(EntityType.SPIDER, new BlockPos(0, 1, 0));
		spider.setAiDisabled(true);
		WebEntity web = new WebEntity(ctx.getWorld(), spider);
		Vec3d above = Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(2, 4, 2)));
		web.refreshPositionAndAngles(above.x, above.y, above.z, 0.0F, 0.0F);
		web.setVelocity(0.0, -1.0, 0.0);
		ctx.getWorld().spawnEntity(web);
		ctx.runAtTick(ctx.getTick() + 20, () -> {
			ctx.expectBlock(Blocks.COBWEB, new BlockPos(2, 1, 2));
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/** Snacks 0.8 s, meals 2.4 s, other food 1.6 s, drinks 1 s; getting hurt stops eating; raw beef poisons about 70% of the time. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void eatingAndDrinking(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 1, 1));
		try {
			ctx.assertTrue(new ItemStack(Items.SWEET_BERRIES).getMaxUseTime(player) == 16, "berries are a snack");
			ctx.assertTrue(new ItemStack(Items.COOKED_BEEF).getMaxUseTime(player) == 48, "steak is a meal");
			ctx.assertTrue(new ItemStack(Items.BREAD).getMaxUseTime(player) == 32, "bread is normal");
			ctx.assertTrue(new ItemStack(Items.POTION).getMaxUseTime(player) == 20 && new ItemStack(Items.MILK_BUCKET).getMaxUseTime(player) == 20
					&& new ItemStack(Items.HONEY_BOTTLE).getMaxUseTime(player) == 20, "drinks take a second");

			// A player who just joined can't be hurt for a few seconds: the eating check goes through Food directly.
			player.getHungerManager().setFoodLevel(10);
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.COOKED_BEEF));
			player.setCurrentHand(Hand.MAIN_HAND);
			ctx.assertTrue(player.isUsingItem(), "eating started");
			Food.interrupt(player);
			ctx.assertTrue(!player.isUsingItem(), "a hit stops eating");
			ZombieEntity eater = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
			eater.setAiDisabled(true);
			eater.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BREAD));
			eater.setCurrentHand(Hand.MAIN_HAND);
			eater.damage(world.getDamageSources().generic(), 1.0F);
			boolean stillEating = eater.isUsingItem();
			eater.discard();
			ctx.assertTrue(!stillEating, "damage that lands stops eating");

			int poisoned = 0;
			for (int i = 0; i < 200; i++) {
				player.clearStatusEffects();
				Food.ate(player, new ItemStack(Items.BEEF));
				poisoned += player.hasStatusEffect(StatusEffects.POISON) ? 1 : 0;
			}
			player.clearStatusEffects();
			Food.ate(player, new ItemStack(Items.COOKED_BEEF));
			ctx.assertTrue(poisoned > 115 && poisoned < 165 && !player.hasStatusEffect(StatusEffects.POISON), "raw beef poisoned " + poisoned + " of 200, steak never");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	/** A zombie with an iron pickaxe walled off from its target digs through the wall. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "mining_zombie", tickLimit = 600)
	public void zombiesDigToTheirTarget(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, 8);
		for (int x = -1; x <= 8; x++) {
			for (int y = 1; y <= 3; y++) {
				for (int z = -1; z <= 3; z++) {
					boolean inside = (x == 0 || x == 1 || x >= 5 && x <= 7) && y <= 2 && z >= 0 && z <= 2;
					ctx.setBlockState(x, y, z, inside ? Blocks.AIR : Blocks.STONE);
				}
			}
		}
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		zombie.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(6, 1, 1));
		GameRules.BooleanRule griefing = world.getGameRules().get(GameRules.DO_MOB_GRIEFING);
		boolean saved = griefing.get();
		griefing.set(true, world.getServer());
		boolean[] done = {false};
		ctx.runAtEveryTick(() -> {
			if (done[0]) {
				return;
			}
			zombie.setTarget(player);
			boolean dug = ctx.getBlockState(new BlockPos(2, 1, 1)).isAir() || ctx.getBlockState(new BlockPos(2, 2, 1)).isAir();
			if (dug) {
				done[0] = true;
				griefing.set(saved, world.getServer());
				zombie.discard();
				FishingTests.disconnect(player);
				ctx.complete();
			}
		});
	}

	/** An adult zombie goes after a cow. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "hunting_zombies", tickLimit = 600)
	public void zombiesHuntAnimals(TestContext ctx) {
		floor(ctx, 8);
		ZombieEntity hunter = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		hunter.setBaby(false);
		hunter.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		CowEntity cow = ctx.spawnEntity(EntityType.COW, new BlockPos(5, 1, 1));
		ctx.runAtEveryTick(() -> {
			if (hunter.getTarget() == cow) {
				hunter.discard();
				cow.discard();
				ctx.complete();
			}
		});
	}

	/** A hurt adult zombie walks to raw beef lying nearby, eats it and heals. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "eating_zombies", tickLimit = 600)
	public void zombiesEatRawMeat(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, 8);
		ZombieEntity hungry = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
		hungry.setBaby(false);
		// A helmet so it doesn't burn in the daylight before it gets there.
		hungry.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		hungry.setHealth(4.0F);
		Vec3d at = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(6, 1, 2))).add(0.4, 0.0, 0.4);
		var beef = new net.minecraft.entity.ItemEntity(world, at.x, at.y, at.z, new ItemStack(Items.BEEF));
		beef.setPickupDelayInfinite();
		world.spawnEntity(beef);
		// An animal left near by another test would have it hunting instead (its own test, zombiesHuntAnimals).
		EternalReturnConfig.MobTweaks cfg = EternalReturnConfig.get().mobs;
		boolean hunt = cfg.zombiesHuntAnimals;
		cfg.zombiesHuntAnimals = false;
		hungry.setTarget(null);
		ctx.runAtEveryTick(() -> {
			if (hungry.getHealth() > 4.0F && !beef.isAlive() && !cfg.zombiesHuntAnimals) {
				cfg.zombiesHuntAnimals = hunt;
				hungry.discard();
				ctx.complete();
			}
		});
	}

	/** With the chances at 1: baby zombies come riding a cow, pig or sheep they steer; phantoms and slimes come with a skeleton that doesn't steer. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "new_jockeys")
	public void newJockeys(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, 6);
		EternalReturnConfig.MobTweaks cfg = EternalReturnConfig.get().mobs;
		double baby = cfg.babyZombieAnimalJockeyChance;
		double phantom = cfg.phantomSkeletonChance;
		double slime = cfg.slimeSkeletonChance;
		try {
			cfg.babyZombieAnimalJockeyChance = 1.0;
			cfg.phantomSkeletonChance = 1.0;
			cfg.slimeSkeletonChance = 1.0;
			ZombieEntity zombie = EntityType.ZOMBIE.create(world);
			zombie.refreshPositionAndAngles(ctx.getAbsolutePos(new BlockPos(2, 1, 2)), 0.0F, 0.0F);
			zombie.initialize(world, world.getLocalDifficulty(zombie.getBlockPos()), SpawnReason.NATURAL, new ZombieEntity.ZombieData(true, false));
			Entity mount = zombie.getVehicle();
			ctx.assertTrue(mount instanceof CowEntity || mount instanceof PigEntity || mount instanceof net.minecraft.entity.passive.SheepEntity, "a baby zombie rides a farm animal: " + mount);
			ctx.assertTrue(((JockeyMount) mount).eternalreturn$isJockeyMount() && ((AnimalEntity) mount).getControllingPassenger() == zombie, "it steers its mount, which despawns with it");

			PhantomEntity phantomEntity = EntityType.PHANTOM.create(world);
			phantomEntity.refreshPositionAndAngles(ctx.getAbsolutePos(new BlockPos(2, 4, 2)), 0.0F, 0.0F);
			phantomEntity.initialize(world, world.getLocalDifficulty(phantomEntity.getBlockPos()), SpawnReason.NATURAL, null);
			ctx.assertTrue(phantomEntity.getFirstPassenger() instanceof SkeletonEntity && phantomEntity.getControllingPassenger() == null, "a phantom carries a skeleton and flies itself");

			SlimeEntity slimeEntity = EntityType.SLIME.create(world);
			slimeEntity.refreshPositionAndAngles(ctx.getAbsolutePos(new BlockPos(4, 1, 4)), 0.0F, 0.0F);
			slimeEntity.setSize(4, true);
			slimeEntity.initialize(world, world.getLocalDifficulty(slimeEntity.getBlockPos()), SpawnReason.NATURAL, null);
			ctx.assertTrue(slimeEntity.getSize() < 2 || slimeEntity.getFirstPassenger() instanceof SkeletonEntity, "a slime carries a skeleton: " + slimeEntity.getFirstPassenger());
			for (Entity entity : List.of(zombie, mount, phantomEntity, slimeEntity)) {
				entity.streamSelfAndPassengers().forEach(Entity::discard);
			}
		} finally {
			cfg.babyZombieAnimalJockeyChance = baby;
			cfg.phantomSkeletonChance = phantom;
			cfg.slimeSkeletonChance = slime;
		}
		ctx.complete();
	}

	/** A Soulbound sword stays in its slot through death; a plain stack is dropped. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "soulbound", tickLimit = 100)
	public void soulboundStaysThroughDeath(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, 4);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 1, 1));
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.addEnchantment(world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(Soulbound.ID).orElseThrow(), 1);
		player.getInventory().setStack(3, sword);
		player.getInventory().setStack(5, new ItemStack(Items.DIRT, 10));
		player.damage(world.getDamageSources().genericKill(), Float.MAX_VALUE);
		ServerPlayerEntity respawned = world.getServer().getPlayerManager().respawnPlayer(player, false, Entity.RemovalReason.KILLED);
		try {
			ctx.assertTrue(Soulbound.has(respawned.getInventory().getStack(3)), "the Soulbound sword is back in slot 3: " + respawned.getInventory().getStack(3));
			ctx.assertTrue(respawned.getInventory().count(Items.DIRT) == 0, "the dirt was dropped");
		} finally {
			FishingTests.disconnect(respawned);
			world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, new Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(8), e -> true).forEach(Entity::discard);
		}
		ctx.complete();
	}

	// ---------- redstone ----------

	/** A powered dispenser holding stone stairs places them in front, facing away from it. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void dispensersPlaceBlocks(TestContext ctx) {
		floor(ctx, 4);
		ctx.setBlockState(1, 1, 1, Blocks.DISPENSER.getDefaultState().with(DispenserBlock.FACING, Direction.EAST));
		((DispenserBlockEntity) ctx.getBlockEntity(new BlockPos(1, 1, 1))).setStack(0, new ItemStack(Items.STONE_STAIRS));
		ctx.setBlockState(0, 1, 1, Blocks.REDSTONE_BLOCK);
		ctx.runAtTick(ctx.getTick() + 10, () -> {
			BlockState placed = ctx.getBlockState(new BlockPos(2, 1, 1));
			ctx.assertTrue(placed.isOf(Blocks.STONE_STAIRS), "the dispenser placed the stairs: " + placed);
			ctx.complete();
		});
	}

	/** The obsidian pressure plate ignores a zombie and turns on for a player. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void obsidianPlateIsForPlayers(TestContext ctx) {
		floor(ctx, 4);
		ctx.setBlockState(1, 1, 1, Redstone.OBSIDIAN_PRESSURE_PLATE);
		ctx.setBlockState(3, 1, 3, Redstone.OBSIDIAN_PRESSURE_PLATE);
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		zombie.setAiDisabled(true);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(3, 1, 3));
		// A scripted player sends no movement, so it never touches blocks on its own: step on the plates by hand.
		ctx.getWorld().getBlockState(ctx.getAbsolutePos(new BlockPos(1, 1, 1))).onEntityCollision(ctx.getWorld(), ctx.getAbsolutePos(new BlockPos(1, 1, 1)), zombie);
		ctx.getWorld().getBlockState(ctx.getAbsolutePos(new BlockPos(3, 1, 3))).onEntityCollision(ctx.getWorld(), ctx.getAbsolutePos(new BlockPos(3, 1, 3)), player);
		ctx.runAtTick(ctx.getTick() + 2, () -> {
			boolean zombiePressed = ctx.getBlockState(new BlockPos(1, 1, 1)).get(net.minecraft.block.PressurePlateBlock.POWERED);
			boolean playerPressed = ctx.getBlockState(new BlockPos(3, 1, 3)).get(net.minecraft.block.PressurePlateBlock.POWERED);
			zombie.discard();
			FishingTests.disconnect(player);
			ctx.assertTrue(!zombiePressed && playerPressed, "zombie pressed " + zombiePressed + ", player pressed " + playerPressed);
			ctx.complete();
		});
	}

	/**
	 * A piston pushes a chest with its diamond inside; a chain standing on the pushed stone carries the
	 * planks on top of it along; a spawner stops a piston.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void pistonsMoveChestsAndChains(TestContext ctx) {
		floor(ctx, 8);
		ctx.setBlockState(1, 1, 1, Blocks.PISTON.getDefaultState().with(net.minecraft.block.PistonBlock.FACING, Direction.EAST));
		ctx.setBlockState(2, 1, 1, Blocks.CHEST);
		((ChestBlockEntity) ctx.getBlockEntity(new BlockPos(2, 1, 1))).setStack(0, new ItemStack(Items.DIAMOND, 3));
		ctx.setBlockState(1, 1, 4, Blocks.PISTON.getDefaultState().with(net.minecraft.block.PistonBlock.FACING, Direction.EAST));
		ctx.setBlockState(2, 1, 4, Blocks.STONE);
		ctx.setBlockState(2, 2, 4, Blocks.CHAIN.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Y));
		ctx.setBlockState(2, 3, 4, Blocks.OAK_PLANKS);
		ctx.setBlockState(1, 1, 7, Blocks.PISTON.getDefaultState().with(net.minecraft.block.PistonBlock.FACING, Direction.EAST));
		ctx.setBlockState(2, 1, 7, Blocks.SPAWNER);
		ctx.setBlockState(0, 1, 1, Blocks.REDSTONE_BLOCK);
		ctx.setBlockState(0, 1, 4, Blocks.REDSTONE_BLOCK);
		ctx.setBlockState(0, 1, 7, Blocks.REDSTONE_BLOCK);
		ctx.runAtTick(ctx.getTick() + 20, () -> {
			ctx.expectBlock(Blocks.CHEST, new BlockPos(3, 1, 1));
			ChestBlockEntity chest = (ChestBlockEntity) ctx.getBlockEntity(new BlockPos(3, 1, 1));
			ctx.assertTrue(chest.getStack(0).isOf(Items.DIAMOND) && chest.getStack(0).getCount() == 3, "the chest kept its diamonds: " + chest.getStack(0));
			ctx.expectBlock(Blocks.STONE, new BlockPos(3, 1, 4));
			ctx.expectBlock(Blocks.CHAIN, new BlockPos(3, 2, 4));
			ctx.expectBlock(Blocks.OAK_PLANKS, new BlockPos(3, 3, 4));
			ctx.expectBlock(Blocks.SPAWNER, new BlockPos(2, 1, 7));
			ctx.assertTrue(ctx.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class, new Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(10), e -> true).isEmpty(),
					"nothing spilled");
			ctx.complete();
		});
	}

	// ---------- plants and animals ----------

	/** Shears stop a vine growing: an uncut vine grows down, a cut one never does. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void cutVinesStopGrowing(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(5, 1, 3));
		try {
			for (int x : new int[]{1, 3}) {
				for (int y = 1; y <= 6; y++) {
					ctx.setBlockState(x, y, 0, Blocks.STONE);
				}
			}
			BlockState vine = Blocks.VINE.getDefaultState().with(VineBlock.NORTH, true);
			ctx.setBlockState(1, 6, 1, vine);
			ctx.setBlockState(3, 6, 1, vine);
			BlockPos cutPos = ctx.getAbsolutePos(new BlockPos(3, 6, 1));
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SHEARS));
			world.getBlockState(cutPos).onUseWithItem(player.getMainHandStack(), world, player, Hand.MAIN_HAND,
					new BlockHitResult(Vec3d.ofCenter(cutPos), Direction.SOUTH, cutPos, false));
			ctx.assertTrue(world.getBlockState(cutPos).get(Farming.CUT), "shears cut the vine");
			ctx.assertTrue(!Blocks.VINE.getDefaultState().get(Farming.CUT), "vines start uncut");
			Random random = Random.create(5);
			BlockPos grows = ctx.getAbsolutePos(new BlockPos(1, 6, 1));
			for (int i = 0; i < 400; i++) {
				world.getBlockState(grows).randomTick(world, grows, random);
				world.getBlockState(cutPos).randomTick(world, cutPos, random);
			}
			ctx.assertTrue(ctx.getBlockState(new BlockPos(1, 5, 1)).isOf(Blocks.VINE), "an uncut vine grows down");
			ctx.assertTrue(ctx.getBlockState(new BlockPos(3, 5, 1)).isAir(), "a cut vine doesn't");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	/** Pink petals stand on still water, stay there, and two side by side both stay. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void petalsFloat(TestContext ctx) {
		floor(ctx, 4);
		for (int x = 0; x <= 3; x++) {
			ctx.setBlockState(x, 1, 2, Blocks.WATER);
		}
		BlockPos above = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
		ctx.assertTrue(Blocks.PINK_PETALS.getDefaultState().canPlaceAt(ctx.getWorld(), above), "pink petals on water");
		ctx.assertTrue(!Blocks.POPPY.getDefaultState().canPlaceAt(ctx.getWorld(), above), "a poppy still can't");
		ctx.setBlockState(1, 2, 2, Blocks.PINK_PETALS);
		ctx.setBlockState(2, 2, 2, Blocks.PINK_PETALS);
		ctx.runAtTick(ctx.getTick() + 10, () -> {
			ctx.expectBlock(Blocks.PINK_PETALS, new BlockPos(1, 2, 2));
			ctx.expectBlock(Blocks.PINK_PETALS, new BlockPos(2, 2, 2));
			ctx.complete();
		});
	}

	/** Zombies from a spawner hold a pickaxe, even above ground. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "spawner_zombies", tickLimit = 1200)
	public void dungeonZombiesDig(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		world.setTimeOfDay(18000);
		for (int x = -2; x <= 10; x++) {
			for (int z = -2; z <= 10; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
				for (int y = 1; y <= 3; y++) {
					ctx.setBlockState(x, y, z, Blocks.AIR);
				}
				ctx.setBlockState(x, 4, z, Blocks.STONE);
			}
		}
		ctx.setBlockState(4, 1, 4, Blocks.SPAWNER);
		BlockPos spawner = ctx.getAbsolutePos(new BlockPos(4, 1, 4));
		((net.minecraft.block.entity.MobSpawnerBlockEntity) world.getBlockEntity(spawner)).setEntityType(EntityType.ZOMBIE, world.getRandom());
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(0, 1, 0));
		Box around = new Box(spawner).expand(8);
		boolean[] done = {false};
		ctx.runAtEveryTick(() -> {
			List<ZombieEntity> zombies = world.getEntitiesByClass(ZombieEntity.class, around, zombie -> true);
			if (done[0] || zombies.isEmpty()) {
				return;
			}
			done[0] = true;
			boolean armed = zombies.stream().allMatch(zombie -> zombie.getMainHandStack().isIn(net.minecraft.registry.tag.ItemTags.PICKAXES));
			zombies.forEach(Entity::discard);
			world.setBlockState(spawner, Blocks.AIR.getDefaultState());
			world.setTimeOfDay(6000);
			FishingTests.disconnect(player);
			ctx.assertTrue(armed, "every spawner zombie holds a pickaxe");
			ctx.complete();
		});
	}

	/** Two pigs have 2 or 3 piglets; after a golden carrot, 2 to 5. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void pigLitters(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, 8);
		int[] plain = litter(ctx, false, 20);
		int[] golden = litter(ctx, true, 40);
		ctx.assertTrue(plain[0] >= 2 && plain[1] <= 3, "plain litters " + plain[0] + " to " + plain[1]);
		ctx.assertTrue(golden[0] >= 2 && golden[1] == 5, "golden carrot litters " + golden[0] + " to " + golden[1]);
		ctx.complete();
	}

	private static int[] litter(TestContext ctx, boolean golden, int tries) {
		ServerWorld world = ctx.getWorld();
		int low = Integer.MAX_VALUE;
		int high = 0;
		Box around = new Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(12);
		for (int i = 0; i < tries; i++) {
			PigEntity a = ctx.spawnEntity(EntityType.PIG, new BlockPos(3, 1, 3));
			PigEntity b = ctx.spawnEntity(EntityType.PIG, new BlockPos(4, 1, 3));
			((Farming.Holder) a).eternalreturn$setGoldenFed(golden);
			a.breed(world, b);
			int babies = world.getEntitiesByClass(PigEntity.class, around, PigEntity::isBaby).size();
			low = Math.min(low, babies);
			high = Math.max(high, babies);
			world.getEntitiesByClass(PigEntity.class, around, pig -> true).forEach(Entity::discard);
			world.getEntitiesByClass(net.minecraft.entity.ExperienceOrbEntity.class, around, orb -> true).forEach(Entity::discard);
		}
		return new int[]{low, high};
	}

	/** A baby cow fed a poisonous potato (the chance at 1) is poisoned and never grows up. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "stunted")
	public void poisonousPotatoStunts(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, 4);
		double saved = EternalReturnConfig.get().survival.poisonousPotatoStuntChance;
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(0, 1, 0));
		CowEntity calf = ctx.spawnEntity(EntityType.COW, new BlockPos(2, 1, 2));
		try {
			EternalReturnConfig.get().survival.poisonousPotatoStuntChance = 1.0;
			calf.setBaby(true);
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.POISONOUS_POTATO, 2));
			calf.interact(player, Hand.MAIN_HAND);
			ctx.assertTrue(calf.hasStatusEffect(StatusEffects.POISON) && player.getMainHandStack().getCount() == 1, "the calf ate it and was poisoned");
			calf.setBreedingAge(0);
			calf.growUp(100000);
			ctx.assertTrue(calf.isBaby(), "a stunted calf never grows up");
		} finally {
			EternalReturnConfig.get().survival.poisonousPotatoStuntChance = saved;
			calf.discard();
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	// ---------- item frames ----------

	/**
	 * Item frames can be dyed in the crafting grid and keep the colour when placed and picked up; a glass
	 * frame holding an item hides its frame, and a click on it opens the chest it hangs on.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void glassAndDyedFrames(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		CraftingRecipeInput input = CraftingRecipeInput.create(2, 1, List.of(new ItemStack(Items.ITEM_FRAME), new ItemStack(Items.RED_DYE)));
		ItemStack dyed = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world).map(r -> r.value().craft(input, world.getRegistryManager()))
				.orElse(ItemStack.EMPTY);
		ctx.assertTrue(dyed.isOf(Items.ITEM_FRAME) && dyed.contains(DataComponentTypes.DYED_COLOR), "an item frame can be dyed: " + dyed);

		floor(ctx, 4);
		ctx.setBlockState(2, 1, 2, Blocks.CHEST);
		BlockPos chest = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
		Direction out = Direction.EAST;
		ItemFrameEntity plain = new ItemFrameEntity(world, chest.up(2).offset(out), out);
		Frames.colorFrom(plain, dyed);
		ctx.assertTrue(DyedColorComponent.getColor(plain.getPickBlockStack(), -1) == DyedColorComponent.getColor(dyed, -1), "the dyed frame drops a dyed frame");

		Frames.GlassItemFrameEntity glass = new Frames.GlassItemFrameEntity(Frames.GLASS_ITEM_FRAME, world, chest.offset(out), out);
		world.spawnEntity(glass);
		ctx.assertTrue(!glass.isInvisible(), "an empty glass frame shows");
		glass.setHeldItemStack(new ItemStack(Items.APPLE));
		ctx.assertTrue(glass.isInvisible() && glass.getPickBlockStack().isOf(Items.APPLE), "holding an item, the frame hides");
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(4, 1, 2));
		try {
			glass.interact(player, Hand.MAIN_HAND);
			ctx.assertTrue(player.currentScreenHandler != player.playerScreenHandler, "a click on the glass frame opened the chest behind it");
			ctx.assertTrue(glass.getRotation() == 0, "and didn't turn the apple");
		} finally {
			player.closeHandledScreen();
			FishingTests.disconnect(player);
			glass.discard();
		}
		ctx.complete();
	}
}
