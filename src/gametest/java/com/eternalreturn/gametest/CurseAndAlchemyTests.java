package com.eternalreturn.gametest;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.gametest.mixin.RangedWeaponInvoker;
import com.eternalreturn.items.PhilosophersStone;
import com.eternalreturn.logic.NewEnchantments;
import com.eternalreturn.mobs.HeadGear;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.biome.BiomeKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * Beehive and TNT heads, zombies breaking lights, glowing seen through walls, wither skeletons in the
 * Nether, Launch, Reeling, Spread of Ailments, Curse of Ascension, what endermen carry, and the
 * philosopher's stone.
 */
public class CurseAndAlchemyTests implements FabricGameTest {
	private static void floor(TestContext ctx, int size) {
		for (int x = -1; x <= size; x++) {
			for (int z = -1; z <= size; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static ItemStack enchanted(ServerWorld world, ItemStack stack, Identifier enchantment, int level) {
		stack.addEnchantment(world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(enchantment).orElseThrow(), level);
		return stack;
	}

	private static <T extends MobEntity> T still(TestContext ctx, EntityType<T> type, BlockPos pos) {
		T mob = ctx.spawnEntity(type, pos);
		mob.setAiDisabled(true);
		mob.setPersistent();
		return mob;
	}

	// ---------- heads ----------

	/** Zombies and skeletons of every kind spawn wearing TNT at its chance; beehives go with birch forests and cherry groves. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "head_gear")
	public void headGearOnSpawn(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		EternalReturnConfig.MobTweaks cfg = EternalReturnConfig.get().mobs;
		double tnt = cfg.tntHeadChance;
		cfg.tntHeadChance = 1.0;
		List<MobEntity> mobs = new ArrayList<>();
		try {
			for (EntityType<? extends MobEntity> type : List.of(EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED, EntityType.ZOMBIE_VILLAGER,
					EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED, EntityType.WITHER_SKELETON)) {
				mobs.add(type.spawn(world, ctx.getAbsolutePos(new BlockPos(1, 1, 1)), SpawnReason.NATURAL));
			}
		} finally {
			cfg.tntHeadChance = tnt;
		}
		int withTnt = 0;
		for (MobEntity mob : mobs) {
			ItemStack head = mob.getEquippedStack(EquipmentSlot.HEAD);
			// A mob can already have a helmet from its armour roll; then its head is left alone.
			ctx.assertTrue(head.isOf(Items.TNT) || !head.isEmpty() && !head.isOf(Items.BEEHIVE), mob.getType() + " wears " + head);
			withTnt += head.isOf(Items.TNT) ? 1 : 0;
			mob.discard();
		}
		ctx.assertTrue(withTnt >= 5, "only " + withTnt + " of 8 wore TNT");
		var biomes = world.getRegistryManager().get(RegistryKeys.BIOME);
		ctx.assertTrue(biomes.entryOf(BiomeKeys.BIRCH_FOREST).isIn(HeadGear.BEEHIVE_MOB_BIOMES) && biomes.entryOf(BiomeKeys.CHERRY_GROVE).isIn(HeadGear.BEEHIVE_MOB_BIOMES)
				&& !biomes.entryOf(BiomeKeys.PLAINS).isIn(HeadGear.BEEHIVE_MOB_BIOMES), "beehive biomes");
		ctx.complete();
	}

	/** A zombie in a beehive, killed by a player, lets out 3 bees angry at that player; the hive isn't dropped. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "beehive_head")
	public void beehiveReleasesBees(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, 4);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(0, 1, 0));
		try {
			ZombieEntity zombie = still(ctx, EntityType.ZOMBIE, new BlockPos(3, 1, 3));
			zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.BEEHIVE));
			zombie.damage(world.getDamageSources().playerAttack(player), 1000.0F);
			Box box = new Box(ctx.getAbsolutePos(new BlockPos(-2, 0, -2))).stretch(9, 6, 9);
			List<BeeEntity> bees = world.getEntitiesByClass(BeeEntity.class, box, bee -> true);
			boolean hive = !world.getEntitiesByClass(ItemEntity.class, box, item -> item.getStack().isOf(Items.BEEHIVE)).isEmpty();
			boolean angry = bees.stream().allMatch(bee -> player.getUuid().equals(bee.getAngryAt()) && bee.hasAngerTime());
			bees.forEach(BeeEntity::discard);
			ctx.assertTrue(bees.size() == 3, bees.size() + " bees came out");
			ctx.assertTrue(angry, "the bees are angry at the player");
			ctx.assertTrue(!hive, "the beehive isn't dropped");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	/** A skeleton wearing TNT explodes when it dies (in water here, so the explosion hurts but breaks nothing). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tnt_head", tickLimit = 60)
	public void tntHeadExplodes(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		for (int x = -1; x <= 5; x++) {
			for (int z = -1; z <= 5; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
				for (int y = 1; y <= 3; y++) {
					boolean wall = x == -1 || x == 5 || z == -1 || z == 5;
					ctx.setBlockState(x, y, z, wall ? Blocks.STONE : Blocks.WATER);
				}
			}
		}
		SkeletonEntity skeleton = still(ctx, EntityType.SKELETON, new BlockPos(2, 1, 2));
		skeleton.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.TNT));
		ZombieEntity bystander = still(ctx, EntityType.ZOMBIE, new BlockPos(2, 1, 4));
		bystander.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		skeleton.damage(world.getDamageSources().generic(), 1000.0F);
		ctx.assertTrue(!bystander.isAlive() || bystander.getHealth() < bystander.getMaxHealth(), "the explosion hurt the zombie next to it");
		Box box = new Box(ctx.getAbsolutePos(new BlockPos(-2, 0, -2))).stretch(9, 6, 9);
		ctx.assertTrue(world.getEntitiesByClass(ItemEntity.class, box, item -> item.getStack().isOf(Items.TNT)).isEmpty(), "the TNT isn't dropped");
		ctx.killAllEntities();
		ctx.complete();
	}

	// ---------- sight and spawns ----------

	/** A mob can't see a pig behind a wall, until the pig glows. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "glowing_sight")
	public void glowingSeenThroughWalls(TestContext ctx) {
		floor(ctx, 5);
		for (int y = 1; y <= 3; y++) {
			for (int z = -1; z <= 3; z++) {
				ctx.setBlockState(2, y, z, Blocks.STONE);
			}
		}
		ZombieEntity zombie = still(ctx, EntityType.ZOMBIE, new BlockPos(0, 1, 1));
		PigEntity pig = still(ctx, EntityType.PIG, new BlockPos(4, 1, 1));
		boolean before = zombie.canSee(pig);
		pig.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 200));
		boolean after = zombie.canSee(pig);
		ctx.killAllEntities();
		ctx.assertTrue(!before, "the wall hides the pig");
		ctx.assertTrue(after, "a glowing pig is seen through the wall");
		ctx.complete();
	}

	/** Wither skeletons are in every Nether biome's monster list, and always drop their skull. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "wither_skulls")
	public void witherSkeletonsAndSkulls(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		var biomes = world.getRegistryManager().get(RegistryKeys.BIOME);
		for (var key : List.of(BiomeKeys.NETHER_WASTES, BiomeKeys.CRIMSON_FOREST, BiomeKeys.WARPED_FOREST, BiomeKeys.SOUL_SAND_VALLEY, BiomeKeys.BASALT_DELTAS)) {
			boolean listed = biomes.getOrThrow(key).getSpawnSettings().getSpawnEntries(SpawnGroup.MONSTER).getEntries().stream()
					.anyMatch(entry -> entry.type == EntityType.WITHER_SKELETON);
			ctx.assertTrue(listed, "wither skeletons spawn in " + key.getValue());
		}
		ctx.assertTrue(biomes.getOrThrow(BiomeKeys.PLAINS).getSpawnSettings().getSpawnEntries(SpawnGroup.MONSTER).getEntries().stream()
				.noneMatch(entry -> entry.type == EntityType.WITHER_SKELETON), "not in the Overworld");
		floor(ctx, 5);
		for (int i = 0; i < 4; i++) {
			WitherSkeletonEntity skeleton = still(ctx, EntityType.WITHER_SKELETON, new BlockPos(1 + i, 1, 2));
			skeleton.damage(world.getDamageSources().generic(), 1000.0F);
		}
		Box box = new Box(ctx.getAbsolutePos(new BlockPos(-2, 0, -2))).stretch(9, 6, 9);
		int skulls = world.getEntitiesByClass(ItemEntity.class, box, item -> item.getStack().isOf(Items.WITHER_SKELETON_SKULL)).stream()
				.mapToInt(item -> item.getStack().getCount()).sum();
		ctx.killAllEntities();
		ctx.assertTrue(skulls == 4, skulls + " skulls from 4 wither skeletons");
		ctx.complete();
	}

	/** Endermen only pick up cactus, pumpkins and melons. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void endermenCarryLittle(TestContext ctx) {
		ctx.assertTrue(Blocks.CACTUS.getDefaultState().isIn(BlockTags.ENDERMAN_HOLDABLE) && Blocks.PUMPKIN.getDefaultState().isIn(BlockTags.ENDERMAN_HOLDABLE)
				&& Blocks.MELON.getDefaultState().isIn(BlockTags.ENDERMAN_HOLDABLE), "cactus, pumpkin, melon");
		ctx.assertTrue(!Blocks.GRASS_BLOCK.getDefaultState().isIn(BlockTags.ENDERMAN_HOLDABLE) && !Blocks.DIRT.getDefaultState().isIn(BlockTags.ENDERMAN_HOLDABLE)
				&& !Blocks.TNT.getDefaultState().isIn(BlockTags.ENDERMAN_HOLDABLE), "nothing else");
		ctx.complete();
	}

	// ---------- zombies and lights ----------

	/** A zombie walks to a torch and knocks it off; without a tool it leaves glowstone alone. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "light_zombies", tickLimit = 600)
	public void zombiesBreakTorches(TestContext ctx) {
		floor(ctx, 7);
		GameRules.BooleanRule rule = ctx.getWorld().getGameRules().get(GameRules.DO_MOB_GRIEFING);
		boolean saved = rule.get();
		rule.set(true, ctx.getWorld().getServer());
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(0, 1, 0));
		zombie.setBaby(false);
		zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		zombie.setPersistent();
		// The goal can be ticked once more after its light is gone (the selector checks shouldContinue every other tick).
		new com.eternalreturn.mobs.ai.BreakLightsGoal(zombie).tick();
		ctx.setBlockState(new BlockPos(5, 1, 5), Blocks.TORCH);
		ctx.setBlockState(new BlockPos(0, 1, 5), Blocks.GLOWSTONE);
		boolean[] done = {false};
		ctx.runAtEveryTick(() -> {
			if (!done[0] && ctx.getBlockState(new BlockPos(5, 1, 5)).isAir()) {
				done[0] = true;
				// Let the zombie tick on a while after the light is gone (it once crashed there).
				ctx.runAtTick(ctx.getTick() + 10, () -> {
					rule.set(saved, ctx.getWorld().getServer());
					ctx.assertTrue(zombie.isAlive(), "the zombie carried on");
					ctx.killAllEntities();
					ctx.expectBlock(Blocks.GLOWSTONE, new BlockPos(0, 1, 5));
					ctx.complete();
				});
			}
		});
	}

	/** A zombie with a pickaxe also digs out a lantern. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "light_zombies", tickLimit = 600)
	public void toolZombiesBreakLanterns(TestContext ctx) {
		floor(ctx, 7);
		GameRules.BooleanRule rule = ctx.getWorld().getGameRules().get(GameRules.DO_MOB_GRIEFING);
		boolean saved = rule.get();
		rule.set(true, ctx.getWorld().getServer());
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(0, 1, 0));
		zombie.setBaby(false);
		zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		zombie.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
		zombie.setPersistent();
		ctx.setBlockState(new BlockPos(5, 1, 4), Blocks.LANTERN);
		boolean[] done = {false};
		ctx.runAtEveryTick(() -> {
			if (!done[0] && ctx.getBlockState(new BlockPos(5, 1, 4)).isAir()) {
				done[0] = true;
				// Let the zombie tick on a while after the light is gone (it once crashed there).
				ctx.runAtTick(ctx.getTick() + 10, () -> {
					rule.set(saved, ctx.getWorld().getServer());
					ctx.assertTrue(zombie.isAlive(), "the zombie carried on");
					ctx.killAllEntities();
					ctx.complete();
				});
			}
		});
	}

	// ---------- enchantments ----------

	/** A Launch V hit throws the target straight up. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "launch")
	public void launchThrowsUp(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, 4);
		ZombieEntity zombie = still(ctx, EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		zombie.equipStack(EquipmentSlot.MAINHAND, enchanted(world, new ItemStack(Items.IRON_SWORD), NewEnchantments.LAUNCH, 5));
		PigEntity pig = still(ctx, EntityType.PIG, new BlockPos(2, 1, 1));
		boolean hit = zombie.tryAttack(pig);
		Vec3d velocity = pig.getVelocity();
		ctx.killAllEntities();
		ctx.assertTrue(hit, "the hit landed");
		ctx.assertTrue(velocity.y > 0.9 && Math.abs(velocity.x) < 0.01 && Math.abs(velocity.z) < 0.01, "thrown straight up: " + velocity);
		ctx.complete();
	}

	/** An arrow from a Reeling V bow pulls a pig 7 blocks away up to the shooter. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "reeling", tickLimit = 100)
	public void reelingPullsIn(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, 7);
		SkeletonEntity skeleton = still(ctx, EntityType.SKELETON, new BlockPos(0, 1, 2));
		skeleton.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		// Mobs with no AI aren't moved by knockback, so this pig keeps its AI.
		PigEntity pig = ctx.spawnEntity(EntityType.PIG, new BlockPos(7, 1, 2));
		ItemStack bow = enchanted(world, new ItemStack(Items.BOW), NewEnchantments.REELING, 5);
		ArrowEntity arrow = new ArrowEntity(world, skeleton, new ItemStack(Items.ARROW), bow);
		arrow.setPosition(skeleton.getX() + 0.8, skeleton.getEyeY() - 0.4, skeleton.getZ());
		Vec3d aim = pig.getPos().add(0.0, pig.getHeight() / 2.0, 0.0).subtract(arrow.getPos());
		arrow.setVelocity(aim.x, aim.y, aim.z, 3.0F, 0.0F);
		world.spawnEntity(arrow);
		// The pull, as the hit leaves it (about 0.8 blocks a tick toward the shooter at this range), and the closest the
		// pig comes (once hurt it panics and runs off again).
		double[] pull = {0.0};
		double[] closest = {Double.MAX_VALUE};
		ctx.runAtEveryTick(() -> {
			pull[0] = Math.max(pull[0], -pig.getVelocity().x);
			closest[0] = Math.min(closest[0], Math.sqrt(pig.squaredDistanceTo(skeleton.getX(), pig.getY(), skeleton.getZ())));
		});
		ctx.runAtTick(ctx.getTick() + 40, () -> {
			boolean hurt = pig.getHealth() < pig.getMaxHealth();
			ctx.killAllEntities();
			ctx.assertTrue(hurt, "the arrow hit the pig");
			ctx.assertTrue(pull[0] > 0.6, "pulled toward the shooter at " + pull[0] + " blocks a tick");
			ctx.assertTrue(closest[0] < 4.5, "the pig was pulled in to " + closest[0] + " blocks");
			ctx.complete();
		});
	}

	/** With Spread of Ailments, the shooter's effects ride on its arrows, from a mob's bow or a crossbow, and pass to what they hit. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "spread_of_ailments", tickLimit = 100)
	public void spreadOfAilments(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, 7);
		SkeletonEntity skeleton = still(ctx, EntityType.SKELETON, new BlockPos(0, 1, 2));
		skeleton.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		skeleton.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200));
		ItemStack bow = enchanted(world, new ItemStack(Items.BOW), NewEnchantments.SPREAD_OF_AILMENTS, 1);
		ItemStack crossbow = enchanted(world, new ItemStack(Items.CROSSBOW), NewEnchantments.SPREAD_OF_AILMENTS, 1);
		PersistentProjectileEntity fromBow = ProjectileUtil.createArrowProjectile(skeleton, new ItemStack(Items.ARROW), 1.0F, bow);
		ProjectileEntity fromCrossbow = ((RangedWeaponInvoker) Items.CROSSBOW).eternalreturn$createArrowEntity(world, skeleton, crossbow, new ItemStack(Items.ARROW), false);
		PersistentProjectileEntity plain = ProjectileUtil.createArrowProjectile(skeleton, new ItemStack(Items.ARROW), 1.0F, new ItemStack(Items.BOW));
		ctx.assertTrue(((ArrowEntity) fromBow).getColor() != -1 && ((ArrowEntity) fromCrossbow).getColor() != -1, "the arrows carry the effect");
		ctx.assertTrue(((ArrowEntity) plain).getColor() == -1, "a plain bow's arrow doesn't");
		PigEntity pig = still(ctx, EntityType.PIG, new BlockPos(6, 1, 2));
		fromBow.setPosition(skeleton.getX() + 0.8, skeleton.getEyeY() - 0.4, skeleton.getZ());
		Vec3d aim = pig.getPos().add(0.0, pig.getHeight() / 2.0, 0.0).subtract(fromBow.getPos());
		fromBow.setVelocity(aim.x, aim.y, aim.z, 3.0F, 0.0F);
		world.spawnEntity(fromBow);
		ctx.runAtTick(ctx.getTick() + 40, () -> {
			StatusEffectInstance speed = pig.getStatusEffect(StatusEffects.SPEED);
			ctx.killAllEntities();
			// The skeleton had 200 ticks of Speed; the pig gets what was left when the arrow was made, less the flight.
			ctx.assertTrue(speed != null && speed.getDuration() > 120 && speed.getDuration() <= 200, "the pig has the skeleton's Speed: " + speed);
			ctx.complete();
		});
	}

	/** A dropped item with Curse of Ascension drifts up; a plain one falls. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "ascension_curse", tickLimit = 100)
	public void ascensionCurseRises(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, 4);
		Vec3d at = Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(1, 2, 1)));
		ItemEntity cursed = new ItemEntity(world, at.x, at.y, at.z, enchanted(world, new ItemStack(Items.IRON_SWORD), NewEnchantments.ASCENSION_CURSE, 1), 0.0, 0.0, 0.0);
		ItemEntity plain = new ItemEntity(world, at.x + 2.0, at.y, at.z, new ItemStack(Items.IRON_SWORD), 0.0, 0.0, 0.0);
		world.spawnEntity(cursed);
		world.spawnEntity(plain);
		ctx.runAtTick(ctx.getTick() + 40, () -> {
			double rose = cursed.getY() - at.y;
			double fell = at.y - plain.getY();
			ctx.killAllEntities();
			ctx.assertTrue(rose > 0.7 && rose < 1.3, "the cursed sword rose " + rose + " blocks in 2 seconds");
			ctx.assertTrue(fell > 0.5, "the plain sword fell");
			ctx.complete();
		});
	}

	// ---------- the philosopher's stone ----------

	/** Crafted from a diamond, 4 lapis and 4 amethyst; 777 uses; turns glass to sand and back, keeps a log's axis, concrete to powder and back, and wears down. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void philosophersStoneOnBlocks(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ItemStack a = new ItemStack(Items.AMETHYST_SHARD), l = new ItemStack(Items.LAPIS_LAZULI), d = new ItemStack(Items.DIAMOND);
		CraftingRecipeInput input = CraftingRecipeInput.create(3, 3, List.of(a, l, a, l, d, l, a, l, a));
		ItemStack crafted = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world).map(r -> r.value().craft(input, world.getRegistryManager()))
				.orElse(ItemStack.EMPTY);
		ctx.assertTrue(crafted.isOf(PhilosophersStone.ITEM) && crafted.getMaxDamage() == 777, "crafted: " + crafted);

		floor(ctx, 4);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(0, 1, 0));
		try {
			player.setStackInHand(Hand.MAIN_HAND, crafted);
			BlockPos glass = new BlockPos(2, 1, 2);
			ctx.setBlockState(glass, Blocks.GLASS);
			use(ctx, player, glass);
			ctx.expectBlock(Blocks.SAND, glass);
			use(ctx, player, glass);
			ctx.expectBlock(Blocks.GLASS, glass);
			BlockPos log = new BlockPos(3, 1, 2);
			ctx.setBlockState(log, Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.X));
			use(ctx, player, log);
			ctx.expectBlockProperty(log, PillarBlock.AXIS, Direction.Axis.X);
			ctx.expectBlock(Blocks.SPRUCE_LOG, log);
			BlockPos stone = new BlockPos(1, 1, 3);
			ctx.setBlockState(stone, Blocks.DIAMOND_BLOCK);
			use(ctx, player, stone);
			ctx.expectBlock(Blocks.DIAMOND_BLOCK, stone);
			BlockPos concrete = new BlockPos(1, 1, 1);
			ctx.setBlockState(concrete, Blocks.RED_CONCRETE);
			use(ctx, player, concrete);
			ctx.expectBlock(Blocks.RED_CONCRETE_POWDER, concrete);
			use(ctx, player, concrete);
			ctx.expectBlock(Blocks.RED_CONCRETE, concrete);
			// Powder made beside water sets again at once: nothing happens, and the stone isn't worn.
			ctx.setBlockState(new BlockPos(0, 1, 1), Blocks.WATER);
			use(ctx, player, concrete);
			ctx.expectBlock(Blocks.RED_CONCRETE, concrete);
			BlockPos cherry = new BlockPos(3, 1, 3);
			ctx.setBlockState(cherry, Blocks.CHERRY_LOG);
			use(ctx, player, cherry);
			ctx.expectBlock(Blocks.OAK_LOG, cherry);
			ctx.assertTrue(PhilosophersStone.transmute(Blocks.DARK_OAK_PLANKS) == Blocks.CHERRY_PLANKS && PhilosophersStone.transmute(Blocks.CHERRY_PLANKS) == Blocks.OAK_PLANKS
					&& PhilosophersStone.transmute(Blocks.MANGROVE_LOG) == null && PhilosophersStone.transmute(Blocks.MYCELIUM) == null
					&& PhilosophersStone.transmute(Blocks.NETHER_BRICKS) == Blocks.RED_NETHER_BRICKS,
					"no mangrove or bamboo wood, no moss; red nether bricks (wild nether wart)");
			ctx.assertTrue(player.getMainHandStack().getDamage() == 6, "6 uses, 6 damage: " + player.getMainHandStack().getDamage());
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	private static void use(TestContext ctx, ServerPlayerEntity player, BlockPos rel) {
		BlockPos abs = ctx.getAbsolutePos(rel);
		PhilosophersStone.ITEM.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(abs), Direction.UP, abs, false)));
	}

	/** A pig becomes a zombified piglin and back; a sheep's wool changes colour; a creeper is charged; a right-clicked villager becomes a witch. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void philosophersStoneOnCreatures(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, 5);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(0, 1, 0));
		try {
			ItemStack stone = new ItemStack(PhilosophersStone.ITEM);
			player.setStackInHand(Hand.MAIN_HAND, stone);
			PigEntity pig = still(ctx, EntityType.PIG, new BlockPos(2, 1, 2));
			PhilosophersStone.ITEM.useOnEntity(stone, player, pig, Hand.MAIN_HAND);
			Box box = new Box(ctx.getAbsolutePos(new BlockPos(-1, 0, -1))).stretch(7, 4, 7);
			List<ZombifiedPiglinEntity> piglins = world.getEntitiesByClass(ZombifiedPiglinEntity.class, box, e -> e.isAlive());
			ctx.assertTrue(pig.isRemoved() && piglins.size() == 1, "the pig became a zombified piglin");
			PhilosophersStone.ITEM.useOnEntity(stone, player, piglins.get(0), Hand.MAIN_HAND);
			ctx.assertTrue(world.getEntitiesByClass(PigEntity.class, box, e -> e.isAlive()).size() == 1, "and back into a pig");

			SheepEntity sheep = still(ctx, EntityType.SHEEP, new BlockPos(4, 1, 4));
			sheep.setColor(DyeColor.WHITE);
			PhilosophersStone.ITEM.useOnEntity(stone, player, sheep, Hand.MAIN_HAND);
			ctx.assertTrue(sheep.getColor() != DyeColor.WHITE, "the sheep's wool changed colour");

			CreeperEntity creeper = still(ctx, EntityType.CREEPER, new BlockPos(4, 1, 1));
			PhilosophersStone.ITEM.useOnEntity(stone, player, creeper, Hand.MAIN_HAND);
			ctx.assertTrue(creeper.shouldRenderOverlay(), "the creeper is charged");

			// Through a real right-click, the stone goes before the villager's trades.
			var villager = still(ctx, EntityType.VILLAGER, new BlockPos(1, 1, 4));
			player.interact(villager, Hand.MAIN_HAND);
			ctx.assertTrue(villager.isRemoved() && world.getEntitiesByClass(net.minecraft.entity.mob.WitchEntity.class, box, e -> e.isAlive()).size() == 1,
					"right-clicking a villager made a witch");
			ctx.assertTrue(!PhilosophersStone.canTransmute(still(ctx, EntityType.CHICKEN, new BlockPos(0, 1, 4))), "chickens are left alone");

			// A horse: a real right-click changes its coat or its markings (not both), instead of mounting it.
			net.minecraft.entity.passive.HorseEntity horse = still(ctx, EntityType.HORSE, new BlockPos(3, 1, 0));
			var color = horse.getVariant();
			var marking = horse.getMarking();
			player.interact(horse, Hand.MAIN_HAND);
			boolean newColor = horse.getVariant() != color, newMarking = horse.getMarking() != marking;
			ctx.assertTrue(newColor != newMarking, "coat " + color + " -> " + horse.getVariant() + ", markings " + marking + " -> " + horse.getMarking());
			ctx.assertTrue(!player.hasVehicle(), "not mounted");
			ctx.assertTrue(stone.getDamage() == 6, "6 uses, 6 damage: " + stone.getDamage());
		} finally {
			FishingTests.disconnect(player);
			ctx.killAllEntities();
		}
		ctx.complete();
	}
}
