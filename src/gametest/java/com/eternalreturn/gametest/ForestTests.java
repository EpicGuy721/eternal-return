package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.blocks.Woods;
import com.eternalreturn.items.WoodBoats;
import com.eternalreturn.mobs.ModEntities;
import com.eternalreturn.mobs.Vampires;
import com.eternalreturn.mobs.entity.BearEntity;
import com.eternalreturn.mobs.entity.VampireBatEntity;
import com.eternalreturn.mobs.entity.VampireEntity;
import com.eternalreturn.worldgen.biome.AutumnGrass;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SaplingBlock;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.ConfiguredFeature;

import java.util.List;

/** The autumnal forest: maple and pale oak, their trees, the biome and its grass, bears, and vampires. */
public class ForestTests implements FabricGameTest {
	private static void floor(TestContext ctx, Block block) {
		for (int x = -1; x <= 8; x++) {
			for (int z = -1; z <= 8; z++) {
				ctx.setBlockState(x, 0, z, block);
			}
		}
	}

	private static ItemStack craft(ServerWorld world, int width, int height, List<ItemStack> grid) {
		CraftingRecipeInput input = CraftingRecipeInput.create(width, height, grid);
		return world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world)
				.map(e -> ((CraftingRecipe) e.value()).craft(input, world.getRegistryManager())).orElse(ItemStack.EMPTY);
	}

	// ---------- woods ----------

	/**
	 * Each wood: logs make 4 planks, planks make stairs, slabs, doors and the rest, and burn; an axe strips a log;
	 * signs and hanging signs take text; the tags vanilla's woods are in hold these too; boats break into their own
	 * item.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void woodSetsWorkLikeVanillas(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, Blocks.STONE);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(6, 1, 6));
		try {
			for (Woods.Wood wood : Woods.all()) {
				String n = wood.name;
				ItemStack planks = craft(world, 1, 1, List.of(new ItemStack(wood.log)));
				ctx.assertTrue(planks.isOf(wood.planks.asItem()) && planks.getCount() == 4, n + " log makes 4 planks: " + planks);
				ItemStack p = new ItemStack(wood.planks);
				ItemStack door = craft(world, 2, 3, List.of(p, p, p, p, p, p));
				ctx.assertTrue(door.isOf(wood.door.asItem()) && door.getCount() == 3, n + " door: " + door);
				ItemStack slab = craft(world, 3, 1, List.of(p, p, p));
				ctx.assertTrue(slab.isOf(wood.slab.asItem()) && slab.getCount() == 6, n + " slab: " + slab);
				ItemStack boat = craft(world, 3, 2, List.of(p, ItemStack.EMPTY, p, p, p, p));
				ctx.assertTrue(boat.isOf(wood.boat), n + " boat: " + boat);
				ItemStack sticks = craft(world, 1, 2, List.of(p, p));
				ctx.assertTrue(sticks.isOf(Items.STICK), n + " planks make sticks: " + sticks);
				ItemStack chest = craft(world, 3, 3, List.of(p, p, p, p, ItemStack.EMPTY, p, p, p, p));
				ctx.assertTrue(chest.isOf(Items.CHEST), n + " planks make a chest: " + chest);
				ctx.assertTrue(p.isIn(ItemTags.PLANKS) && new ItemStack(wood.log).isIn(ItemTags.LOGS_THAT_BURN) && wood.log.getDefaultState().isIn(BlockTags.LOGS),
						n + " planks and logs are in their tags");
				ctx.assertTrue(wood.leaves.stream().allMatch(leaf -> leaf.getDefaultState().isIn(BlockTags.LEAVES)) && (wood.sapling == null || wood.sapling.getDefaultState().isIn(BlockTags.SAPLINGS)),
						n + " leaves and sapling tags");
				ctx.assertTrue(AbstractFurnaceBlockEntity.canUseAsFuel(p) && AbstractFurnaceBlockEntity.canUseAsFuel(new ItemStack(wood.boat)), n + " burns");
				ctx.assertTrue(BlockEntityType.SIGN.supports(wood.sign.getDefaultState()) && BlockEntityType.SIGN.supports(wood.wallSign.getDefaultState())
						&& BlockEntityType.HANGING_SIGN.supports(wood.hangingSign.getDefaultState()), n + " signs hold text");
				// An axe on a log.
				BlockPos log = new BlockPos(2, 1, 2);
				ctx.setBlockState(log, wood.log);
				ItemStack axe = new ItemStack(Items.IRON_AXE);
				player.setStackInHand(Hand.MAIN_HAND, axe);
				BlockPos abs = ctx.getAbsolutePos(log);
				axe.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(abs), Direction.UP, abs, false)));
				ctx.expectBlock(wood.strippedLog, log);
				// Boats.
				BoatEntity entity = WoodBoats.boat(n).create(world);
				BoatEntity chestBoat = WoodBoats.chestBoat(n).create(world);
				ctx.assertTrue(entity.asItem() == wood.boat && chestBoat.asItem() == wood.chestBoat, n + " boats break into their own items");
			}
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	/** A maple sapling grows a maple, a pale oak sapling a pale oak in one of its three colours, on grass. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "trees", skyAccess = true)
	public void saplingsGrowTheirTrees(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, Blocks.GRASS_BLOCK);
		for (Woods.Wood wood : List.of(Woods.MAPLE, Woods.PALE_OAK)) {
			BlockPos rel = wood == Woods.MAPLE ? new BlockPos(2, 1, 2) : new BlockPos(5, 1, 5);
			BlockPos abs = ctx.getAbsolutePos(rel);
			world.setBlockState(abs, wood.sapling.getDefaultState().with(SaplingBlock.STAGE, 1));
			Random random = Random.create(3);
			for (int tries = 0; tries < 10 && world.getBlockState(abs).isOf(wood.sapling); tries++) {
				((SaplingBlock) wood.sapling).generate(world, abs, world.getBlockState(abs).with(SaplingBlock.STAGE, 1), random);
			}
			ctx.assertTrue(world.getBlockState(abs).isOf(wood.log), wood.name + " sapling grew a trunk: " + world.getBlockState(abs));
			boolean leaves = false;
			for (BlockPos pos : BlockPos.iterate(abs.add(-4, 1, -4), abs.add(4, 12, 4))) {
				leaves |= wood.leaves.stream().anyMatch(leaf -> world.getBlockState(pos).isOf(leaf));
			}
			ctx.assertTrue(leaves, wood.name + " tree has its leaves");
		}
		ctx.complete();
	}

	// ---------- the biome ----------

	/**
	 * The autumnal forest is a forest whose grass is golden, with orange and red patches; its tree feature grows maples
	 * and pale oaks; vampires are among its monsters, bears among its creatures.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "trees", skyAccess = true)
	public void autumnalForest(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		var biomes = world.getRegistryManager().get(RegistryKeys.BIOME);
		RegistryKey<Biome> key = RegistryKey.of(RegistryKeys.BIOME, EternalReturn.id("autumnal_forest"));
		var autumn = biomes.getEntry(key).orElseThrow();
		ctx.assertTrue(autumn.isIn(BiomeTags.IS_FOREST) && autumn.isIn(BiomeTags.IS_OVERWORLD), "a forest, in the overworld");
		int gold = 0;
		int other = 0;
		for (int x = 0; x < 2000; x += 37) {
			for (int z = 0; z < 2000; z += 41) {
				int color = autumn.value().getGrassColorAt(x, z);
				int r = (color >> 16) & 0xFF;
				int g = (color >> 8) & 0xFF;
				int b = color & 0xFF;
				ctx.assertTrue(r > g && g > b, "warm colours only, got " + Integer.toHexString(color));
				if (color == AutumnGrass.GOLD) {
					gold++;
				} else {
					other++;
				}
			}
		}
		ctx.assertTrue(gold > other && other > 0, "mostly golden, some orange and red: " + gold + " gold, " + other + " other");
		var monsters = autumn.value().getSpawnSettings().getSpawnEntries(SpawnGroup.MONSTER).getEntries();
		ctx.assertTrue(monsters.stream().anyMatch(e -> e.type == ModEntities.VAMPIRE), "vampires spawn there");
		for (var biome : List.of(autumn, biomes.getEntry(RegistryKey.of(RegistryKeys.BIOME, net.minecraft.util.Identifier.ofVanilla("forest"))).orElseThrow())) {
			var creatures = biome.value().getSpawnSettings().getSpawnEntries(SpawnGroup.CREATURE).getEntries();
			for (EntityType<?> bear : List.of(ModEntities.BROWN_BEAR, ModEntities.BLACK_BEAR, ModEntities.SUN_BEAR)) {
				ctx.assertTrue(creatures.stream().anyMatch(e -> e.type == bear), bear.getUntranslatedName() + " in " + biome.getIdAsString());
			}
		}
		floor(ctx, Blocks.GRASS_BLOCK);
		ConfiguredFeature<?, ?> trees = world.getRegistryManager().get(RegistryKeys.CONFIGURED_FEATURE).get(EternalReturn.id("trees_autumnal_forest"));
		Random random = Random.create(11);
		int grown = 0;
		for (BlockPos rel : List.of(new BlockPos(2, 1, 2), new BlockPos(5, 1, 5))) {
			if (trees.generate(world, world.getChunkManager().getChunkGenerator(), random, ctx.getAbsolutePos(rel))) {
				grown++;
			}
		}
		ctx.assertTrue(grown > 0, "the forest's trees grow");
		ctx.complete();
	}

	// ---------- bears ----------

	/** Brown, black and sun bears are polar bears in all but coat, size and home; a cub is of its parents' kind. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void bears(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, Blocks.GRASS_BLOCK);
		for (EntityType<BearEntity> type : List.of(ModEntities.BROWN_BEAR, ModEntities.BLACK_BEAR, ModEntities.SUN_BEAR)) {
			BearEntity bear = ctx.spawnEntity(type, new BlockPos(3, 1, 3));
			bear.setAiDisabled(true);
			ctx.assertTrue(bear.getMaxHealth() == 30.0F, type.getUntranslatedName() + " has a polar bear's health");
			PassiveEntity cub = bear.createChild(world, bear);
			ctx.assertTrue(cub != null && cub.getType() == type, "a " + type.getUntranslatedName() + " cub");
			bear.discard();
		}
		ctx.assertTrue(ModEntities.SUN_BEAR.getWidth() < ModEntities.BLACK_BEAR.getWidth() && ModEntities.BLACK_BEAR.getWidth() < ModEntities.BROWN_BEAR.getWidth(),
				"sun bears are the smallest, brown bears the biggest");
		ctx.complete();
	}

	// ---------- vampires ----------

	/** A vampire dies leaving no experience, and only rubies (1 to 3). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "vampires_night", tickLimit = 60)
	public void vampiresDropNothing(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		world.setTimeOfDay(18000);
		floor(ctx, Blocks.STONE);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(6, 1, 6));
		VampireEntity vampire = ctx.spawnEntity(ModEntities.VAMPIRE, new BlockPos(3, 1, 3));
		vampire.setAiDisabled(true);
		vampire.damage(world.getDamageSources().playerAttack(player), 1000.0F);
		ctx.runAtTick(ctx.getTick() + 30, () -> {
			Box box = new Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(12.0);
			ctx.assertTrue(vampire.isDead(), "the vampire died");
			ctx.assertTrue(world.getEntitiesByClass(ItemEntity.class, box, e -> !e.getStack().isOf(com.eternalreturn.items.Gems.RUBY)).isEmpty(), "only rubies");
			ctx.assertTrue(world.getEntitiesByClass(ExperienceOrbEntity.class, box, e -> true).isEmpty(), "no experience");
			FishingTests.disconnect(player);
			ctx.complete();
		});
	}

	/** In daylight under the open sky a vampire catches fire. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "vampires_day", skyAccess = true, tickLimit = 200)
	public void vampiresBurnByDay(TestContext ctx) {
		ctx.getWorld().setTimeOfDay(6000);
		floor(ctx, Blocks.STONE);
		VampireEntity vampire = ctx.spawnEntity(ModEntities.VAMPIRE, new BlockPos(3, 1, 3));
		vampire.setAiDisabled(true);
		ctx.runAtEveryTick(() -> {
			if (vampire.isOnFire()) {
				ctx.killAllEntities();
				ctx.complete();
			}
		});
	}

	/**
	 * A vampire after a player calls up bats; a bat that reaches the player bites, poisoning them; a bitten villager
	 * turns into a vampire when its time is up, and vampires leave a bitten villager alone.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "vampires_night", tickLimit = 300)
	public void vampireBatsAndBites(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		world.setTimeOfDay(18000);
		floor(ctx, Blocks.STONE);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(6, 1, 6));
		player.onTeleportationDone();
		VampireEntity vampire = ctx.spawnEntity(ModEntities.VAMPIRE, new BlockPos(1, 1, 1));
		vampire.setTarget(player);
		VillagerEntity villager = ctx.spawnEntity(EntityType.VILLAGER, new BlockPos(1, 1, 6));
		villager.setAiDisabled(true);
		ctx.assertTrue(Vampires.biteable(villager), "an unbitten villager is fair game");
		ctx.assertTrue(Vampires.infect(villager) && !Vampires.biteable(villager), "bitten, it is left alone");
		ctx.assertTrue(!Vampires.infect(villager), "a second bite changes nothing");
		((Vampires.Bitten) villager).eternalreturn$setTurnTicks(20);
		boolean[] bats = {false};
		ctx.runAtEveryTick(() -> {
			if (vampire.getTarget() != player && vampire.isAlive()) {
				vampire.setTarget(player);
			}
			bats[0] |= !world.getEntitiesByClass(VampireBatEntity.class, vampire.getBoundingBox().expand(16.0),
					bat -> vampire.getUuid().equals(bat.owner())).isEmpty();
			boolean turned = villager.isRemoved() && !world.getEntitiesByClass(VampireEntity.class, new Box(ctx.getAbsolutePos(new BlockPos(1, 1, 6))).expand(3.0),
					v -> v != vampire).isEmpty();
			if (bats[0] && turned && player.hasStatusEffect(StatusEffects.POISON)) {
				FishingTests.disconnect(player);
				ctx.killAllEntities();
				ctx.complete();
			}
		});
	}

	/** Badly hurt with a player near, a vampire becomes a bat; with no player about, the bat turns back into it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "vampires_night", tickLimit = 260)
	public void vampiresFleeAsBats(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		world.setTimeOfDay(18000);
		floor(ctx, Blocks.STONE);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(5, 1, 5));
		VampireEntity vampire = ctx.spawnEntity(ModEntities.VAMPIRE, new BlockPos(3, 1, 3));
		vampire.setHealth(5.0F);
		vampire.setCustomName(net.minecraft.text.Text.literal("Vlad"));
		Box area = new Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(24.0);
		boolean[] fled = {false};
		ctx.runAtEveryTick(() -> {
			if (!fled[0]) {
				if (vampire.isRemoved() && world.getEntitiesByClass(VampireBatEntity.class, area, VampireBatEntity::isForm).size() == 1) {
					fled[0] = true;
					// The player leaves: the bat is safe.
					FishingTests.disconnect(player);
				}
				return;
			}
			List<VampireEntity> back = world.getEntitiesByClass(VampireEntity.class, area, v -> "Vlad".equals(v.getName().getString()));
			if (!back.isEmpty()) {
				ctx.assertTrue(back.get(0).getHealth() == 5.0F, "it came back with the health it had: " + back.get(0).getHealth());
				ctx.assertTrue(world.getEntitiesByClass(VampireBatEntity.class, area, VampireBatEntity::isForm).isEmpty(), "and the bat is gone");
				ctx.killAllEntities();
				ctx.complete();
			}
		});
	}
}
