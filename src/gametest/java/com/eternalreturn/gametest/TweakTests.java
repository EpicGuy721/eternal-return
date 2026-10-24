package com.eternalreturn.gametest;

import com.eternalreturn.mixin.mobs.MobEntityAccessor;
import com.eternalreturn.mixin.survival.BeaconBlockEntityAccessor;
import com.eternalreturn.mobs.NewMobs;
import com.eternalreturn.mobs.ai.Lounging;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.PowderSnowBlock;
import net.minecraft.block.TurtleEggBlock;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.biome.BiomeKeys;

import java.util.List;

/**
 * Small tweaks: deep-water guardians; no natural soldiers or gimps; bone blocks smelt to calcite; eight echo shards
 * around a music disc copy it (and keep it); a pickaxe keeps a spawner; a feather punch does no damage; Reeling goes on
 * fishing rods; slimes bounce; squid ink blinds; Feather Falling boots tread lightly; vexes can't pass crying obsidian; the undead and illagers
 * fight; a drowned's trident comes back to it; a lantern that loses its hold falls and bursts into fire; beacons offer
 * Night Vision. And (own batch, it changes the weather) no rain above the clouds.
 */
public class TweakTests implements FabricGameTest {
	private static final String BATCH = "tweaks";
	/** 16 by 40 blocks of stone floor (at y 1 here: stand on y 2), 14 high. */
	private static final String ARENA = "eternalreturn_test:arena";

	private static RegistryEntry<Enchantment> enchantment(ServerWorld world, RegistryKey<Enchantment> key) {
		return world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(key).orElseThrow();
	}

	/** In a roofed pool 24 deep (so vanilla's open-sky rule doesn't get in the way): guardians at the bottom, not near the top. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void spawnRules(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		OceanLifeTests.pool(ctx, "minecraft:ocean", true, 24);
		com.eternalreturn.survival.BeaconZones.clear(world);
		BlockPos deep = ctx.getAbsolutePos(new BlockPos(3, 2, 3));
		BlockPos shallow = ctx.getAbsolutePos(new BlockPos(3, 20, 3));
		ctx.assertTrue(SpawnRestriction.canSpawn(EntityType.GUARDIAN, world, SpawnReason.NATURAL, deep, world.getRandom()), "guardians 23 blocks down");
		ctx.assertTrue(!SpawnRestriction.canSpawn(EntityType.GUARDIAN, world, SpawnReason.NATURAL, shallow, world.getRandom()),
				"but not 5 blocks down");
		var monsters = world.getRegistryManager().get(RegistryKeys.BIOME).getOrThrow(BiomeKeys.PLAINS).getSpawnSettings()
				.getSpawnEntries(net.minecraft.entity.SpawnGroup.MONSTER).getEntries();
		ctx.assertTrue(monsters.stream().noneMatch(entry -> entry.type == NewMobs.SOLDIER || entry.type == NewMobs.GIMP || entry.type == NewMobs.ATROPAL),
				"no natural soldiers, gimps or atropals");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void recipes(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		var calcite = world.getRecipeManager().getFirstMatch(RecipeType.SMELTING, new SingleStackRecipeInput(new ItemStack(Items.BONE_BLOCK)), world);
		ctx.assertTrue(calcite.isPresent() && calcite.get().value().getResult(world.getRegistryManager()).isOf(Items.CALCITE), "bone block smelts to calcite");
		ItemStack shard = new ItemStack(Items.ECHO_SHARD);
		CraftingRecipeInput input = CraftingRecipeInput.create(3, 3,
				List.of(shard, shard, shard, shard, new ItemStack(Items.MUSIC_DISC_CAT), shard, shard, shard, shard));
		var copy = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world);
		ItemStack result = copy.map(recipe -> recipe.value().craft(input, world.getRegistryManager())).orElse(ItemStack.EMPTY);
		ctx.assertTrue(result.isOf(Items.MUSIC_DISC_CAT), "a disc ringed by eight echo shards makes a copy of the disc: " + result);
		CraftingRecipeInput one = CraftingRecipeInput.create(2, 1, List.of(new ItemStack(Items.MUSIC_DISC_CAT), shard));
		ctx.assertTrue(world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, one, world).isEmpty(), "one shard isn't enough");
		var left = copy.get().value().getRemainder(input);
		ctx.assertTrue(left.stream().filter(stack -> stack.isOf(Items.MUSIC_DISC_CAT)).count() == 1, "and the disc stays: " + left);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void spawnerKeptWithAPickaxe(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos pos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
		BlockState spawner = Blocks.SPAWNER.getDefaultState();
		List<ItemStack> picked = Block.getDroppedStacks(spawner, world, pos, null, null, new ItemStack(Items.IRON_PICKAXE));
		List<ItemStack> punched = Block.getDroppedStacks(spawner, world, pos, null, null, ItemStack.EMPTY);
		ctx.assertTrue(picked.size() == 1 && picked.get(0).isOf(Items.SPAWNER), "a pickaxe keeps it: " + picked);
		ctx.assertTrue(punched.isEmpty(), "a bare hand doesn't: " + punched);
		ctx.complete();
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 100)
	public void featherPunchTickles(TestContext ctx) {
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 8));
		ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, new BlockPos(8, 2, 9));
		husk.setAiDisabled(true);
		float health = husk.getHealth();
		player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(Items.FEATHER));
		player.attack(husk);
		ctx.assertTrue(husk.getHealth() == health, "a feather does no damage: " + husk.getHealth());
		ctx.assertTrue(husk.hurtTime > 0, "but it still looks like a hit");
		husk.hurtTime = 0;
		husk.timeUntilRegen = 0;
		player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, ItemStack.EMPTY);
		player.resetLastAttackedTicks();
		player.attack(husk);
		ctx.assertTrue(husk.getHealth() < health, "a fist does");
		FishingTests.disconnect(player);
		ctx.killAllEntities();
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void reelingOnFishingRods(TestContext ctx) {
		var reeling = ctx.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT).get(com.eternalreturn.logic.NewEnchantments.REELING);
		ctx.assertTrue(reeling != null && reeling.isSupportedItem(new ItemStack(Items.FISHING_ROD)), "Reeling goes on a fishing rod");
		ctx.assertTrue(reeling.isSupportedItem(new ItemStack(Items.BOW)), "still on bows");
		ctx.complete();
	}

	/** Dropped from 10 blocks: unhurt, and back up off the floor. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 120)
	public void slimesBounce(TestContext ctx) {
		SlimeEntity slime = ctx.spawnEntity(EntityType.SLIME, new BlockPos(8, 12, 10));
		slime.setSize(2, true);
		((MobEntityAccessor) slime).eternalreturn$goalSelector().clear(goal -> true);
		float health = slime.getHealth();
		boolean[] landed = {false};
		boolean[] bounced = {false};
		ctx.runAtEveryTick(() -> {
			landed[0] |= slime.isOnGround();
			bounced[0] |= landed[0] && slime.getVelocity().y > 0.2;
		});
		ctx.runAtTick(100, () -> {
			ctx.assertTrue(slime.getHealth() == health, "no fall damage: " + slime.getHealth());
			ctx.assertTrue(bounced[0], "it bounced");
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void squidInkBlinds(TestContext ctx) {
		SquidEntity squid = ctx.spawnEntity(EntityType.SQUID, new BlockPos(8, 2, 10));
		ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, new BlockPos(8, 2, 12));
		husk.setAiDisabled(true);
		squid.damage(ctx.getWorld().getDamageSources().mobAttack(husk), 1.0F);
		ctx.assertTrue(husk.hasStatusEffect(StatusEffects.BLINDNESS), "the squid's attacker is blinded by its ink");
		ctx.killAllEntities();
		ctx.complete();
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void featherFallingTreadsLightly(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity light = FishingTests.survivalPlayer(ctx, new BlockPos(4, 2, 8));
		ItemStack boots = new ItemStack(Items.IRON_BOOTS);
		boots.addEnchantment(enchantment(world, Enchantments.FEATHER_FALLING), 1);
		light.equipStack(EquipmentSlot.FEET, boots);
		ServerPlayerEntity heavy = FishingTests.survivalPlayer(ctx, new BlockPos(12, 2, 8));
		BlockPos lightSoil = new BlockPos(4, 1, 8);
		BlockPos heavySoil = new BlockPos(12, 1, 8);
		ctx.setBlockState(lightSoil, Blocks.FARMLAND.getDefaultState());
		ctx.setBlockState(heavySoil, Blocks.FARMLAND.getDefaultState());
		Blocks.FARMLAND.onLandedUpon(world, Blocks.FARMLAND.getDefaultState(), ctx.getAbsolutePos(lightSoil), light, 3.0F);
		Blocks.FARMLAND.onLandedUpon(world, Blocks.FARMLAND.getDefaultState(), ctx.getAbsolutePos(heavySoil), heavy, 3.0F);
		ctx.expectBlock(Blocks.FARMLAND, lightSoil);
		ctx.expectBlock(Blocks.DIRT, heavySoil);
		ctx.assertTrue(PowderSnowBlock.canWalkOnPowderSnow(light) && !PowderSnowBlock.canWalkOnPowderSnow(heavy), "it walks on powder snow");
		BlockPos lightEgg = new BlockPos(4, 2, 12);
		BlockPos heavyEgg = new BlockPos(12, 2, 12);
		BlockState eggs = Blocks.TURTLE_EGG.getDefaultState().with(TurtleEggBlock.EGGS, 4);
		ctx.setBlockState(lightEgg, eggs);
		ctx.setBlockState(heavyEgg, eggs);
		for (int i = 0; i < 60; i++) {
			BlockState lightState = world.getBlockState(ctx.getAbsolutePos(lightEgg));
			if (lightState.isOf(Blocks.TURTLE_EGG)) {
				lightState.getBlock().onLandedUpon(world, lightState, ctx.getAbsolutePos(lightEgg), light, 1.0F);
			}
			BlockState heavyState = world.getBlockState(ctx.getAbsolutePos(heavyEgg));
			if (heavyState.isOf(Blocks.TURTLE_EGG)) {
				heavyState.getBlock().onLandedUpon(world, heavyState, ctx.getAbsolutePos(heavyEgg), heavy, 1.0F);
			}
		}
		ctx.assertTrue(world.getBlockState(ctx.getAbsolutePos(lightEgg)).isOf(Blocks.TURTLE_EGG)
				&& world.getBlockState(ctx.getAbsolutePos(lightEgg)).get(TurtleEggBlock.EGGS) == 4, "no eggs cracked under light feet");
		ctx.assertTrue(!world.getBlockState(ctx.getAbsolutePos(heavyEgg)).isOf(Blocks.TURTLE_EGG)
				|| world.getBlockState(ctx.getAbsolutePos(heavyEgg)).get(TurtleEggBlock.EGGS) < 4, "but some under plain boots");
		FishingTests.disconnect(light);
		FishingTests.disconnect(heavy);
		ctx.killAllEntities();
		ctx.complete();
	}

	/** A husk walled in crying obsidian: the vex after it can't get in. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 200)
	public void vexesCantPassCryingObsidian(TestContext ctx) {
		for (BlockPos pos : BlockPos.iterate(5, 2, 17, 11, 8, 23)) {
			boolean shell = pos.getX() == 5 || pos.getX() == 11 || pos.getY() == 2 || pos.getY() == 8 || pos.getZ() == 17 || pos.getZ() == 23;
			if (shell) {
				ctx.setBlockState(pos, Blocks.CRYING_OBSIDIAN.getDefaultState());
			}
		}
		ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, new BlockPos(8, 3, 20));
		husk.setAiDisabled(true);
		VexEntity vex = ctx.spawnEntity(EntityType.VEX, new BlockPos(8, 5, 10));
		Box inside = new Box(ctx.getAbsolutePos(new BlockPos(6, 3, 18))).union(new Box(ctx.getAbsolutePos(new BlockPos(10, 7, 22))));
		boolean[] gotIn = {false};
		ctx.runAtEveryTick(() -> {
			vex.setTarget(husk);
			gotIn[0] |= inside.intersects(vex.getBoundingBox().contract(0.1));
		});
		ctx.runAtTick(180, () -> {
			ctx.assertTrue(!gotIn[0], "the vex never got inside");
			ctx.assertTrue(husk.getHealth() == husk.getMaxHealth(), "nor hurt the husk");
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 200)
	public void undeadAndIllagersFight(TestContext ctx) {
		ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, new BlockPos(6, 2, 10));
		PillagerEntity pillager = ctx.spawnEntity(EntityType.PILLAGER, new BlockPos(10, 2, 14));
		ctx.runAtEveryTick(() -> {
			Lounging.stand(husk);
			if (husk.getTarget() == pillager && pillager.getTarget() == husk) {
				ctx.killAllEntities();
				ctx.complete();
			}
		});
	}

	/** A drowned throws its trident, gets it back, and throws it again (not taking to its fists instead). */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 800)
	public void drownedTridentsComeBack(TestContext ctx) {
		DrownedEntity drowned = ctx.spawnEntity(EntityType.DROWNED, new BlockPos(8, 2, 6));
		drowned.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
		ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, new BlockPos(8, 2, 16));
		husk.setAiDisabled(true);
		husk.setInvulnerable(true);
		boolean[] out = {false};
		int[] throwsBack = {0};
		ctx.runAtEveryTick(() -> {
			Lounging.stand(drowned);
			drowned.setTarget(husk);
			if (drowned.getMainHandStack().isEmpty()) {
				out[0] = true;
			} else if (out[0] && drowned.getMainHandStack().isOf(Items.TRIDENT)) {
				out[0] = false;
				if (++throwsBack[0] == 2) {
					ctx.killAllEntities();
					ctx.complete();
				}
			}
		});
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 100)
	public void lanternsFallAndBurn(TestContext ctx) {
		ctx.setBlockState(new BlockPos(8, 6, 10), Blocks.STONE.getDefaultState());
		ctx.setBlockState(new BlockPos(8, 5, 10), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
		ctx.runAtTick(5, () -> ctx.setBlockState(new BlockPos(8, 6, 10), Blocks.AIR.getDefaultState()));
		ctx.runAtEveryTick(() -> {
			if (ctx.getTick() > 6 && ctx.getWorld().getBlockState(ctx.getAbsolutePos(new BlockPos(8, 2, 10))).isOf(Blocks.FIRE)) {
				ctx.expectBlock(Blocks.AIR, new BlockPos(8, 5, 10));
				ctx.assertTrue(ctx.getWorld().getEntitiesByClass(ItemEntity.class, new Box(ctx.getAbsolutePos(new BlockPos(8, 2, 10))).expand(4.0),
						item -> item.getStack().isOf(Items.LANTERN)).isEmpty(), "no lantern item left");
				ctx.killAllEntities();
				ctx.complete();
			}
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void beaconsOfferNightVision(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ctx.assertTrue(BeaconBlockEntity.EFFECTS_BY_LEVEL.get(0).contains(StatusEffects.NIGHT_VISION), "on the first row");
		BlockPos pos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
		world.setBlockState(pos, Blocks.BEACON.getDefaultState());
		BeaconBlockEntity beacon = (BeaconBlockEntity) world.getBlockEntity(pos);
		NbtCompound nbt = new NbtCompound();
		nbt.putString("primary_effect", "minecraft:night_vision");
		beacon.read(nbt, world.getRegistryManager());
		RegistryEntry<?> primary = ((BeaconBlockEntityAccessor) beacon).eternalreturn$primary();
		ctx.assertTrue(StatusEffects.NIGHT_VISION.equals(primary), "a beacon accepts it: " + primary);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "rain_clouds", skyAccess = true)
	public void noRainAboveTheClouds(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN);
		com.eternalreturn.weather.Storm storm = Weathers.rain(world, net.minecraft.util.math.Vec3d.ofCenter(base), false);
		boolean below = world.hasRain(new BlockPos(base.getX(), 150, base.getZ()));
		boolean above = world.hasRain(new BlockPos(base.getX(), 200, base.getZ()));
		Weathers.dry(world, storm);
		ctx.assertTrue(below, "rain below the clouds");
		ctx.assertTrue(!above, "none above them");
		ctx.complete();
	}

	/**
	 * A snowy taiga floor already snowed on, then leaves come over it, as a tree from the next chunk does: after the
	 * tree, its top leaves have snow and the ground under them none, as under any tree; open ground keeps its snow.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "snowy_trees", skyAccess = true)
	public void borderTreesGetSnow(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos a = ctx.getAbsolutePos(new BlockPos(-1, -1, -1));
		BlockPos b = ctx.getAbsolutePos(new BlockPos(8, 12, 8));
		world.getServer().getCommandManager().executeWithPrefix(world.getServer().getCommandSource().withSilent(),
				"fillbiome " + a.getX() + " " + a.getY() + " " + a.getZ() + " " + b.getX() + " " + b.getY() + " " + b.getZ() + " minecraft:snowy_taiga");
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				ctx.setBlockState(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK.getDefaultState().with(net.minecraft.state.property.Properties.SNOWY, true));
				ctx.setBlockState(new BlockPos(x, 1, z), Blocks.SNOW.getDefaultState());
			}
		}
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				ctx.setBlockState(new BlockPos(x, 5, z), Blocks.SPRUCE_LEAVES.getDefaultState().with(net.minecraft.block.LeavesBlock.PERSISTENT, true));
			}
		}
		com.eternalreturn.worldgen.SnowyTrees.afterTree(world, ctx.getAbsolutePos(new BlockPos(2, 1, 2)));
		ctx.expectBlock(Blocks.SNOW, new BlockPos(2, 6, 2));
		ctx.expectBlock(Blocks.AIR, new BlockPos(2, 1, 2));
		ctx.assertTrue(!world.getBlockState(ctx.getAbsolutePos(new BlockPos(2, 0, 2))).get(net.minecraft.state.property.Properties.SNOWY),
				"the grass under the tree isn't snowy");
		ctx.expectBlock(Blocks.SNOW, new BlockPos(5, 1, 5));
		ctx.complete();
	}
}
