package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.items.DragonRocket;
import com.eternalreturn.items.IceBall;
import com.eternalreturn.items.Materials;
import com.eternalreturn.items.Potions2;
import com.eternalreturn.logic.EnchantCaps;
import com.eternalreturn.logic.InherentEnchantments;
import com.eternalreturn.logic.Necromancy;
import com.eternalreturn.mobs.NewMobs;
import com.eternalreturn.mobs.Servants;
import com.eternalreturn.mobs.SpawnTweaks;
import com.eternalreturn.mobs.ZombieVillagerJobs;
import com.eternalreturn.mobs.entity.BoneSpiderEntity;
import com.eternalreturn.mobs.entity.FlowerCowEntity;
import com.eternalreturn.mobs.entity.FrostbiteEntity;
import com.eternalreturn.mobs.entity.ParchedEntity;
import com.eternalreturn.survival.ArmorWeight;
import com.eternalreturn.survival.Freezing;
import com.eternalreturn.villager.MasonTrades;
import com.eternalreturn.worldgen.Dungeons;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BedPart;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Copper and rose gold; the material rules; Necromancy; Freezing and ice balls; frostbites, the parched, bone
 * spiders, skeleton wolves, mooblooms and moolips; wither skeletons; phantom nights; removed natural spawns; dragon
 * rockets; beds in the Nether; death drops; Nether dungeon loot; Nether masons and mud; Soul Speed's removal.
 */
public class MaterialsMobsAndRulesTests implements FabricGameTest {
	private static void floor(TestContext ctx, Block block) {
		for (int x = -1; x <= 8; x++) {
			for (int z = -1; z <= 8; z++) {
				ctx.setBlockState(x, 0, z, block);
			}
		}
	}

	private static <T extends MobEntity> T still(TestContext ctx, EntityType<T> type, BlockPos pos) {
		T mob = ctx.spawnEntity(type, pos);
		mob.setAiDisabled(true);
		return mob;
	}

	private static ItemStack craft(ServerWorld world, int width, int height, List<ItemStack> grid) {
		CraftingRecipeInput input = CraftingRecipeInput.create(width, height, grid);
		return world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world)
				.map(e -> ((CraftingRecipe) e.value()).craft(input, world.getRegistryManager())).orElse(ItemStack.EMPTY);
	}

	private static RegistryEntry<Enchantment> enchantment(ServerWorld world, Identifier id) {
		return world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(id).orElseThrow();
	}

	private static int level(ServerWorld world, ItemStack stack, String id) {
		return EnchantmentHelper.getLevel(enchantment(world, Identifier.of(id)), stack);
	}

	// ---------- materials ----------

	/**
	 * Rose gold ingots (4 gold and 4 copper make 8); copper and rose gold tools mine what stone does, 190 uses, rose gold
	 * as fast as gold; the inherent enchantments and capacities of the table; copper armor weighs as iron, rose gold as gold.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void copperAndRoseGold(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ItemStack g = new ItemStack(Items.GOLD_INGOT);
		ItemStack c = new ItemStack(Items.COPPER_INGOT);
		ItemStack ingots = craft(world, 3, 3, List.of(g, c, g, c, ItemStack.EMPTY, c, g, c, g));
		ctx.assertTrue(ingots.isOf(Materials.ROSEGOLD_INGOT) && ingots.getCount() == 8, "4 gold and 4 copper make 8 rose gold: " + ingots);
		ItemStack r = new ItemStack(Materials.ROSEGOLD_INGOT);
		ItemStack stick = new ItemStack(Items.STICK);
		ItemStack pick = craft(world, 3, 3, List.of(r, r, r, ItemStack.EMPTY, stick, ItemStack.EMPTY, ItemStack.EMPTY, stick, ItemStack.EMPTY));
		ctx.assertTrue(pick.isOf(Materials.ROSEGOLD.get(2)), "a rose gold pickaxe: " + pick);
		ItemStack copperPick = new ItemStack(Materials.COPPER.get(2));
		ctx.assertTrue(copperPick.isSuitableFor(Blocks.IRON_ORE.getDefaultState()) && !copperPick.isSuitableFor(Blocks.DIAMOND_ORE.getDefaultState()),
				"copper mines iron ore, not diamond ore");
		ctx.assertTrue(copperPick.getMaxDamage() == 190 && pick.getMaxDamage() == 190, "190 uses");
		ctx.assertTrue(pick.getMiningSpeedMultiplier(Blocks.STONE.getDefaultState()) > copperPick.getMiningSpeedMultiplier(Blocks.STONE.getDefaultState()),
				"rose gold digs faster than copper");
		ctx.assertTrue(new ItemStack(Materials.COPPER.get(6)).getMaxDamage() == 176, "a copper chestplate lasts 176");
		// Inherent enchantments, as the table says.
		record Rule(ItemStack stack, String enchantment, int level) {
		}
		for (Rule rule : List.of(new Rule(new ItemStack(Items.GOLDEN_PICKAXE), "minecraft:silk_touch", 1),
				new Rule(new ItemStack(Items.GOLDEN_HOE), "minecraft:fortune", 1),
				new Rule(new ItemStack(Materials.ROSEGOLD.get(2)), "minecraft:silk_touch", 1),
				new Rule(new ItemStack(Materials.ROSEGOLD.get(4)), "minecraft:fortune", 1),
				new Rule(new ItemStack(Materials.ROSEGOLD.get(6)), "minecraft:fire_protection", 1),
				new Rule(new ItemStack(Items.CHAINMAIL_CHESTPLATE), "minecraft:projectile_protection", 1),
				new Rule(new ItemStack(Items.DIAMOND_CHESTPLATE), "minecraft:blast_protection", 1),
				new Rule(new ItemStack(Items.NETHERITE_CHESTPLATE), "minecraft:unbreaking", 5),
				new Rule(new ItemStack(Items.IRON_SWORD), "minecraft:unbreaking", 1))) {
			InherentEnchantments.apply(rule.stack(), world.getRegistryManager());
			ctx.assertTrue(level(world, rule.stack(), rule.enchantment()) == rule.level(),
					rule.stack().getItem() + " has " + rule.enchantment() + " " + rule.level() + ": " + rule.stack().getEnchantments());
		}
		ItemStack copperSword = new ItemStack(Materials.COPPER.get(0));
		ctx.assertTrue(!InherentEnchantments.apply(copperSword, world.getRegistryManager()) && copperSword.getEnchantments().isEmpty(), "copper has none");
		ctx.assertTrue(EnchantCaps.capFor(copperSword) == 8 && EnchantCaps.capFor(new ItemStack(Materials.ROSEGOLD.get(0))) == 8
				&& EnchantCaps.capFor(new ItemStack(Items.GOLDEN_SWORD)) == 10, "copper and rose gold capacity 8, gold 10");
		ctx.assertTrue(ArmorWeight.weight(new ItemStack(Materials.COPPER.get(6))) == ArmorWeight.weight(new ItemStack(Items.IRON_CHESTPLATE))
				&& ArmorWeight.weight(new ItemStack(Materials.ROSEGOLD.get(6))) == ArmorWeight.weight(new ItemStack(Items.GOLDEN_CHESTPLATE)),
				"copper weighs as iron, rose gold as gold");
		// The damage enchantments go together now; Soul Speed is gone.
		var sharp = enchantment(world, Identifier.ofVanilla("sharpness"));
		ctx.assertTrue(Enchantment.canBeCombined(sharp, enchantment(world, Identifier.ofVanilla("smite")))
				&& Enchantment.canBeCombined(sharp, enchantment(world, EternalReturn.id("cubism")))
				&& Enchantment.canBeCombined(enchantment(world, Identifier.ofVanilla("bane_of_arthropods")), enchantment(world, Identifier.ofVanilla("smite"))),
				"Sharpness, Smite, Bane of Arthropods and Cubism combine");
		ctx.assertTrue(!enchantment(world, Identifier.ofVanilla("soul_speed")).value().isSupportedItem(new ItemStack(Items.DIAMOND_BOOTS)), "no Soul Speed");
		var bartering = world.getServer().getReloadableRegistries().getLootTable(RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.ofVanilla("gameplay/piglin_bartering")));
		ctx.assertTrue(bartering != net.minecraft.loot.LootTable.EMPTY, "bartering loads");
		ctx.complete();
	}

	// ---------- Necromancy ----------

	/** A Necromancy I sword stores the zombie it kills (only one at level I); sneaking and using it raises it as a servant. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void necromancy(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, Blocks.STONE);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 1, 1));
		try {
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			sword.addEnchantment(enchantment(world, Necromancy.ID), 1);
			player.setStackInHand(Hand.MAIN_HAND, sword);
			for (int i = 0; i < 2; i++) {
				ZombieEntity zombie = still(ctx, EntityType.ZOMBIE, new BlockPos(4, 1, 4));
				zombie.damage(world.getDamageSources().playerAttack(player), 1000.0F);
			}
			WitherSkeletonEntity skeleton = still(ctx, EntityType.WITHER_SKELETON, new BlockPos(5, 1, 5));
			skeleton.damage(world.getDamageSources().playerAttack(player), 1000.0F);
			ctx.assertTrue(Necromancy.stored(sword).equals(List.of(EntityType.ZOMBIE)), "one zombie stored at level I: " + Necromancy.stored(sword));
			ctx.assertTrue(Necromancy.tooltip(sword).get(0).getString().contains("Zombie"), "the tooltip names it: " + Necromancy.tooltip(sword));
			player.setSneaking(true);
			UseItemCallback.EVENT.invoker().interact(player, world, Hand.MAIN_HAND);
			ctx.assertTrue(Necromancy.stored(sword).isEmpty(), "raising it empties the sword");
			List<ZombieEntity> servants = world.getEntitiesByClass(ZombieEntity.class, player.getBoundingBox().expand(8.0), Servants::isServant);
			ctx.assertTrue(servants.size() == 1 && Servants.master(servants.get(0)) == player, "a zombie servant of the player's");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.killAllEntities();
		ctx.complete();
	}

	// ---------- freezing and ice balls ----------

	/**
	 * Freezing freezes over 7 seconds to half speed, then hurts 1.5 every 2 seconds; leather boots keep it off;
	 * ice balls hurt by their speed; a potion of Freezing brews from an awkward potion and an ice ball.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 260)
	public void freezingAndIceBalls(TestContext ctx) throws ReflectiveOperationException {
		ServerWorld world = ctx.getWorld();
		floor(ctx, Blocks.STONE);
		CowEntity cow = still(ctx, EntityType.COW, new BlockPos(1, 1, 1));
		CowEntity booted = still(ctx, EntityType.COW, new BlockPos(6, 1, 6));
		ZombieEntity zombie = still(ctx, EntityType.ZOMBIE, new BlockPos(1, 1, 6));
		zombie.equipStack(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
		for (var mob : List.of(cow, booted, zombie)) {
			mob.addStatusEffect(new StatusEffectInstance(Freezing.EFFECT, 1000));
		}
		float before = cow.getHealth();
		ItemStack brewed = world.getBrewingRecipeRegistry().craft(new ItemStack(IceBall.ITEM),
				PotionContentsComponent.createStack(Items.POTION, Potions.AWKWARD));
		ctx.assertTrue(brewed.get(DataComponentTypes.POTION_CONTENTS).matches(Potions2.FREEZING), "awkward + ice ball = Freezing: " + brewed);
		ctx.assertTrue(craft(world, 1, 1, List.of(new ItemStack(Items.ICE))).isOf(IceBall.ITEM), "ice makes ice balls");
		// An ice ball: 3 damage at a throw's speed, 1 when slow.
		Method collide = ProjectileEntity.class.getDeclaredMethod("onCollision", HitResult.class);
		collide.setAccessible(true);
		CowEntity target = still(ctx, EntityType.COW, new BlockPos(4, 1, 4));
		IceBall.Ball fast = new IceBall.Ball(IceBall.ENTITY, world);
		fast.setVelocity(1.5, 0.0, 0.0);
		ctx.assertTrue(fast.damage() == 3.0F, "1.5 blocks a tick does 3: " + fast.damage());
		fast.setPosition(target.getX() - 1.0, target.getEyeY(), target.getZ());
		float full = target.getHealth();
		collide.invoke(fast, new EntityHitResult(target));
		ctx.assertTrue(full - target.getHealth() == 3.0F, "the ball took 3: " + (full - target.getHealth()));
		IceBall.Ball slow = new IceBall.Ball(IceBall.ENTITY, world);
		slow.setVelocity(0.4, 0.0, 0.0);
		ctx.assertTrue(slow.damage() == 1.0F, "a slow ball does 1");
		ctx.runAtTick(ctx.getTick() + 220, () -> {
			ctx.assertTrue(cow.isFrozen(), "the cow froze through: " + cow.getFrozenTicks());
			var speed = cow.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
			ctx.assertTrue(Math.abs(speed.getValue() - speed.getBaseValue() * 0.5) < 0.01, "at half speed: " + speed.getValue() + " of " + speed.getBaseValue());
			float lost = before - cow.getHealth();
			ctx.assertTrue(lost >= 1.5F && lost % 1.5F == 0.0F, "and hurt in steps of 1.5: " + lost);
			ctx.assertTrue(zombie.getFrozenTicks() == 0, "leather boots keep it off");
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	// ---------- new mobs ----------

	/**
	 * A frostbite (24 health) gives Freezing with its hits; the parched doesn't burn by day and its arrows weaken; a
	 * bone spider (64 health) withers; skeleton wolves are a Nether wolf variant; mooblooms and moolips give stew.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "new_mobs_day", skyAccess = true, tickLimit = 100)
	public void newMobs(TestContext ctx) throws ReflectiveOperationException {
		ServerWorld world = ctx.getWorld();
		world.setTimeOfDay(6000);
		floor(ctx, Blocks.GRASS_BLOCK);
		FrostbiteEntity frostbite = still(ctx, NewMobs.FROSTBITE, new BlockPos(1, 1, 1));
		CowEntity cow = still(ctx, EntityType.COW, new BlockPos(2, 1, 1));
		ctx.assertTrue(frostbite.getMaxHealth() == 24.0F, "a frostbite has 24 health");
		frostbite.tryAttack(cow);
		ctx.assertTrue(cow.hasStatusEffect(Freezing.EFFECT), "its hit freezes");
		BoneSpiderEntity spider = still(ctx, NewMobs.BONE_SPIDER, new BlockPos(5, 1, 1));
		CowEntity bitten = still(ctx, EntityType.COW, new BlockPos(6, 1, 2));
		ctx.assertTrue(spider.getMaxHealth() == 64.0F, "a bone spider has 64 health");
		var difficulty = world.getDifficulty();
		// Easy too: the wither is there on every difficulty.
		world.getServer().setDifficulty(net.minecraft.world.Difficulty.EASY, true);
		try {
			spider.tryAttack(bitten);
		} finally {
			world.getServer().setDifficulty(difficulty, true);
		}
		ctx.assertTrue(bitten.hasStatusEffect(StatusEffects.WITHER), "its bite withers");
		ParchedEntity parched = still(ctx, NewMobs.PARCHED, new BlockPos(3, 1, 5));
		Method arrow = net.minecraft.entity.mob.AbstractSkeletonEntity.class.getDeclaredMethod("createArrowProjectile", ItemStack.class, float.class, ItemStack.class);
		arrow.setAccessible(true);
		PersistentProjectileEntity shot = (PersistentProjectileEntity) arrow.invoke(parched, new ItemStack(Items.ARROW), 1.0F, new ItemStack(Items.BOW));
		ctx.assertTrue(shot instanceof net.minecraft.entity.projectile.ArrowEntity && shot.getItemStack().getOrDefault(DataComponentTypes.POTION_CONTENTS,
				PotionContentsComponent.DEFAULT).getEffects().iterator().hasNext(), "the parched's arrows carry an effect");
		var variants = world.getRegistryManager().get(RegistryKeys.WOLF_VARIANT);
		var skeleton = variants.getEntry(EternalReturn.id("skeleton")).orElseThrow();
		ctx.assertTrue(skeleton.value().getBiomes().contains(world.getRegistryManager().get(RegistryKeys.BIOME).getEntry(BiomeKeys.NETHER_WASTES).orElseThrow()),
				"skeleton wolves belong to the Nether");
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(6, 1, 6));
		try {
			for (var type : List.of(NewMobs.MOOBLOOM, NewMobs.MOOLIP)) {
				FlowerCowEntity flowerCow = still(ctx, type, new BlockPos(4, 1, 6));
				player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BOWL));
				flowerCow.interactMob(player, Hand.MAIN_HAND);
				ItemStack stew = player.getMainHandStack();
				var effects = stew.get(DataComponentTypes.SUSPICIOUS_STEW_EFFECTS);
				ctx.assertTrue(stew.isOf(Items.SUSPICIOUS_STEW) && effects != null && !effects.effects().isEmpty(), type.getUntranslatedName() + " gives stew");
				var effect = effects.effects().get(0).effect();
				ctx.assertTrue(type == NewMobs.MOOBLOOM ? effect == StatusEffects.SATURATION
						: effect == StatusEffects.FIRE_RESISTANCE || effect == StatusEffects.WEAKNESS, "with the right effect: " + effect);
				player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BUCKET));
				flowerCow.interactMob(player, Hand.MAIN_HAND);
				ctx.assertTrue(player.getMainHandStack().isOf(Items.MILK_BUCKET), "and milk");
				flowerCow.discard();
			}
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.runAtTick(ctx.getTick() + 60, () -> {
			ctx.assertTrue(!parched.isOnFire(), "the parched doesn't burn in the sun");
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/**
	 * Skeleton wolves: a wolf may spawn on netherrack in the Nether, in the dark; one made there is a skeleton wolf,
	 * which fire and lava can't hurt; they are a fair share of the Nether's animals.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "nether_wolves")
	public void skeletonWolves(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerWorld nether = world.getServer().getWorld(World.NETHER);
		BlockPos pos = new BlockPos(8, 70, 8);
		nether.setChunkForced(0, 0, true);
		try {
			for (BlockPos p : BlockPos.iterate(pos.add(-2, -1, -2), pos.add(2, -1, 2))) {
				nether.setBlockState(p, Blocks.NETHERRACK.getDefaultState());
			}
			for (BlockPos p : BlockPos.iterate(pos.add(-2, 0, -2), pos.add(2, 2, 2))) {
				nether.setBlockState(p, Blocks.AIR.getDefaultState());
			}
			ctx.assertTrue(net.minecraft.entity.SpawnRestriction.canSpawn(EntityType.WOLF, nether, SpawnReason.NATURAL, pos, nether.getRandom()),
					"a wolf may spawn on netherrack in the Nether");
			var wolf = EntityType.WOLF.create(nether);
			wolf.refreshPositionAndAngles(pos, 0.0F, 0.0F);
			wolf.initialize(nether, nether.getLocalDifficulty(pos), SpawnReason.NATURAL, null);
			ctx.assertTrue(wolf.getVariant().matchesKey(RegistryKey.of(RegistryKeys.WOLF_VARIANT, EternalReturn.id("skeleton"))),
					"a wolf of the Nether is a skeleton wolf: " + wolf.getVariant().getIdAsString());
			nether.spawnEntity(wolf);
			float health = wolf.getHealth();
			wolf.damage(nether.getDamageSources().inFire(), 4.0F);
			wolf.damage(nether.getDamageSources().lava(), 4.0F);
			ctx.assertTrue(wolf.isFireImmune() && wolf.getHealth() == health, "fire and lava don't hurt it");
			wolf.discard();
		} finally {
			nether.setChunkForced(0, 0, false);
		}
		var creatures = world.getRegistryManager().get(RegistryKeys.BIOME).get(BiomeKeys.NETHER_WASTES).getSpawnSettings().getSpawnEntries(SpawnGroup.CREATURE);
		int wolves = creatures.getEntries().stream().filter(e -> e.type == EntityType.WOLF).mapToInt(e -> e.getWeight().getValue()).sum();
		int all = creatures.getEntries().stream().mapToInt(e -> e.getWeight().getValue()).sum();
		ctx.assertTrue(wolves * 5 >= all, "wolves are at least a fifth of the Nether's animal spawns: " + wolves + " of " + all);
		ctx.complete();
	}

	/** Wither skeletons have 80 health and shoot wither skulls at a target 8 or more blocks off. */
	/** In the arena (floor at y 1): the target far enough off (8 to 32 blocks) for a skull before the skeleton closes in. */
	@GameTest(templateName = "eternalreturn_test:arena", tickLimit = 200)
	public void witherSkeletons(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		WitherSkeletonEntity skeleton = ctx.spawnEntity(EntityType.WITHER_SKELETON, new BlockPos(8, 2, 2));
		ctx.assertTrue(skeleton.getMaxHealth() == 80.0F && skeleton.getHealth() == 80.0F, "80 health: " + skeleton.getMaxHealth());
		VillagerEntity target = still(ctx, EntityType.VILLAGER, new BlockPos(8, 2, 30));
		ctx.runAtEveryTick(() -> {
			skeleton.setTarget(target);
			if (!world.getEntitiesByClass(WitherSkullEntity.class, new Box(skeleton.getBlockPos()).expand(16.0), skull -> skull.getOwner() == skeleton).isEmpty()) {
				ctx.killAllEntities();
				ctx.complete();
			}
		});
	}

	// ---------- world rules ----------

	/**
	 * Phantom nights are about one in 13; removed mobs aren't in spawn lists; frostbites and the parched replace most
	 * zombies and skeletons where they live; a zombie that spawns out of the sky's sight leaves torches alone.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, skyAccess = true, tickLimit = 60)
	public void spawnRules(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		long time = world.getTimeOfDay();
		int nights = 0;
		try {
			for (int day = 0; day < 1300; day++) {
				world.setTimeOfDay(day * 24000L + 18000L);
				if (SpawnTweaks.isPhantomNight(world)) {
					nights++;
				}
			}
		} finally {
			world.setTimeOfDay(time);
		}
		ctx.assertTrue(nights > 60 && nights < 140, "about one night in 13 has phantoms: " + nights + " of 1300");
		var biomes = world.getRegistryManager().get(RegistryKeys.BIOME);
		for (var key : List.of(BiomeKeys.WARM_OCEAN, BiomeKeys.LUSH_CAVES, BiomeKeys.MEADOW, BiomeKeys.SWAMP, BiomeKeys.BEACH, BiomeKeys.SAVANNA,
				BiomeKeys.DESERT, BiomeKeys.SNOWY_SLOPES, BiomeKeys.DRIPSTONE_CAVES)) {
			Biome biome = biomes.get(key);
			for (SpawnGroup group : SpawnGroup.values()) {
				for (var entry : biome.getSpawnSettings().getSpawnEntries(group).getEntries()) {
					ctx.assertTrue(!List.of(EntityType.ALLAY, EntityType.ARMADILLO, EntityType.AXOLOTL, EntityType.CAMEL, EntityType.FROG, EntityType.GLOW_SQUID,
							EntityType.SNIFFER, EntityType.TADPOLE, EntityType.TURTLE, EntityType.GOAT).contains(entry.type),
							entry.type.getUntranslatedName() + " still spawns in " + key.getValue());
				}
			}
		}
		var snowy = biomes.get(BiomeKeys.SNOWY_PLAINS).getSpawnSettings().getSpawnEntries(SpawnGroup.MONSTER).getEntries();
		ctx.assertTrue(snowy.stream().anyMatch(e -> e.type == NewMobs.FROSTBITE && e.getWeight().getValue() == 76), "frostbites in snowy plains");
		var desert = biomes.get(BiomeKeys.DESERT).getSpawnSettings().getSpawnEntries(SpawnGroup.MONSTER).getEntries();
		ctx.assertTrue(desert.stream().anyMatch(e -> e.type == NewMobs.PARCHED), "the parched in deserts");
		var nether = biomes.get(BiomeKeys.NETHER_WASTES).getSpawnSettings();
		ctx.assertTrue(nether.getSpawnEntries(SpawnGroup.MONSTER).getEntries().stream().anyMatch(e -> e.type == NewMobs.BONE_SPIDER)
				&& nether.getSpawnEntries(SpawnGroup.CREATURE).getEntries().stream().anyMatch(e -> e.type == EntityType.WOLF), "bone spiders and wolves in the Nether");
		floor(ctx, Blocks.STONE);
		ctx.setBlockState(3, 3, 3, Blocks.STONE);
		// Once the light has settled under the new roof.
		ctx.runAtTick(ctx.getTick() + 20, () -> {
			ZombieEntity covered = still(ctx, EntityType.ZOMBIE, new BlockPos(3, 1, 3));
			ZombieVillagerJobs.onSpawn(covered, world, world.getLocalDifficulty(covered.getBlockPos()), SpawnReason.NATURAL);
			ZombieEntity open = still(ctx, EntityType.ZOMBIE, new BlockPos(6, 1, 6));
			ZombieVillagerJobs.onSpawn(open, world, world.getLocalDifficulty(open.getBlockPos()), SpawnReason.NATURAL);
			ctx.assertTrue(covered.getCommandTags().contains(ZombieVillagerJobs.UNDERGROUND), "a zombie spawned under cover is marked to leave lights");
			ctx.assertTrue(!open.getCommandTags().contains(ZombieVillagerJobs.UNDERGROUND), "one under the sky isn't");
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/**
	 * Dragon rockets (8 rockets around a phantom membrane make 8) boost a gliding player; beds in the Nether don't
	 * explode; a dead player's drops never despawn; Nether dungeons have their own loot; Nether masons sell Nether
	 * blocks and dripstone sales become mud.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void itemsAndBlocks(TestContext ctx) throws ReflectiveOperationException {
		ServerWorld world = ctx.getWorld();
		floor(ctx, Blocks.STONE);
		ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
		ItemStack made = craft(world, 3, 3, List.of(rocket, rocket, rocket, rocket, new ItemStack(Items.PHANTOM_MEMBRANE), rocket, rocket, rocket, rocket));
		ctx.assertTrue(made.isOf(DragonRocket.ITEM) && made.getCount() == 8, "8 dragon rockets: " + made);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(2, 1, 2));
		try {
			ItemStack rockets = new ItemStack(DragonRocket.ITEM, 4);
			player.setStackInHand(Hand.MAIN_HAND, rockets);
			rockets.use(world, player, Hand.MAIN_HAND);
			ctx.assertTrue(rockets.getCount() == 4, "not gliding: nothing");
			player.startFallFlying();
			rockets.use(world, player, Hand.MAIN_HAND);
			ctx.assertTrue(rockets.getCount() == 3 && !world.getEntitiesByClass(FireworkRocketEntity.class, player.getBoundingBox().expand(4.0), e -> true).isEmpty(),
					"gliding: a boost");
			// Death drops.
			player.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 3));
			player.getInventory().dropAll();
			ItemEntity dropped = world.getEntitiesByClass(ItemEntity.class, player.getBoundingBox().expand(4.0), e -> e.getStack().isOf(Items.DIAMOND)).get(0);
			Field age = ItemEntity.class.getDeclaredField("itemAge");
			age.setAccessible(true);
			ctx.assertTrue(age.getInt(dropped) < -30000, "a dead player's drops never despawn");
			// A bed in the Nether.
			ServerWorld nether = world.getServer().getWorld(World.NETHER);
			BlockPos bed = new BlockPos(0, 64, 0);
			nether.setChunkForced(0, 0, true);
			try {
				nether.setBlockState(bed.down(), Blocks.NETHERRACK.getDefaultState());
				nether.setBlockState(bed, Blocks.RED_BED.getDefaultState().with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.NORTH));
				BlockState state = nether.getBlockState(bed);
				state.onUse(nether, player, new net.minecraft.util.hit.BlockHitResult(bed.toCenterPos(), Direction.UP, bed, false));
				ctx.assertTrue(nether.getBlockState(bed).isOf(Blocks.RED_BED), "the bed is still there, no explosion");
				nether.setBlockState(bed, Blocks.AIR.getDefaultState());
				// A Nether mason.
				VillagerEntity mason = EntityType.VILLAGER.create(nether);
				mason.setVillagerData(mason.getVillagerData().withProfession(VillagerProfession.MASON).withLevel(1));
				TradeOfferList offers = mason.getOffers();
				ctx.assertTrue(offers.size() == 2 && offers.stream().allMatch(o -> List.of(Items.NETHER_BRICKS, Items.BLACKSTONE, Items.NETHERRACK)
						.contains(o.getSellItem().getItem())), "a Nether mason sells Nether blocks: " + offers.stream().map(o -> o.getSellItem().toString()).toList());
			} finally {
				nether.setChunkForced(0, 0, false);
			}
		} finally {
			FishingTests.disconnect(player);
		}
		TradeOfferList dripstone = new TradeOfferList();
		dripstone.add(new TradeOffer(new TradedItem(Items.EMERALD, 1), new ItemStack(Items.DRIPSTONE_BLOCK, 4), 16, 10, 0.05F));
		MasonTrades.mudForDripstone(dripstone);
		ctx.assertTrue(dripstone.get(0).getSellItem().isOf(Items.MUD) && dripstone.get(0).getSellItem().getCount() == 4, "mud, not dripstone");
		var loot = world.getServer().getReloadableRegistries().getLootTable(Dungeons.NETHER_LOOT);
		ctx.assertTrue(loot != net.minecraft.loot.LootTable.EMPTY, "the Nether dungeon loot table loads");
		ctx.killAllEntities();
		ctx.complete();
	}
}
