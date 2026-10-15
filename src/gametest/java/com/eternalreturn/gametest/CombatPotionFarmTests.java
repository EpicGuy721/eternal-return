package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.blocks.InvisibleSigns;
import com.eternalreturn.items.Potions2;
import com.eternalreturn.logic.Autoload;
import com.eternalreturn.survival.Growing;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.IllusionerEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.EggEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.BoneMealItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.recipe.BrewingRecipeRegistry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Older combat (tool damage, sweeping, thrown knockback, no elytra rockets), potions (stacks of 16, Haste, mixing),
 * the illusioner's potions, Cubism and Autoload, invisible signs, dyed shulkers, and farming (rain, bone meal,
 * Feather Falling on farmland, saplings that plant themselves).
 */
public class CombatPotionFarmTests implements FabricGameTest {
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

	private static RegistryEntry<Enchantment> enchantment(ServerWorld world, Identifier id) {
		return world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(id).orElseThrow();
	}

	private static void setField(Class<?> owner, Object target, String name, int value) throws ReflectiveOperationException {
		Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		field.setInt(target, value);
	}

	private static Optional<RecipeEntry<CraftingRecipe>> recipe(ServerWorld world, CraftingRecipeInput input) {
		return world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world);
	}

	private static ItemStack potion(Item kind, RegistryEntry<Potion> potion) {
		return PotionContentsComponent.createStack(kind, potion);
	}

	private static double toolDamage(Item item) {
		AttributeModifiersComponent modifiers = new ItemStack(item).getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
		for (AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
			if (entry.attribute().equals(EntityAttributes.GENERIC_ATTACK_DAMAGE) && entry.modifier().id().equals(Item.BASE_ATTACK_DAMAGE_MODIFIER_ID)) {
				return entry.modifier().value();
			}
		}
		return -1.0;
	}

	// ---------- combat ----------

	/** Pickaxes, axes and shovels hit as before 1.9 (2, 3 and 1, plus the material's bonus); swords are untouched. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void toolsHitAsTheyUsedTo(TestContext ctx) {
		ctx.assertTrue(toolDamage(Items.DIAMOND_PICKAXE) == 5.0, "diamond pickaxe +5, got " + toolDamage(Items.DIAMOND_PICKAXE));
		ctx.assertTrue(toolDamage(Items.IRON_AXE) == 5.0, "iron axe +5 (vanilla +8), got " + toolDamage(Items.IRON_AXE));
		ctx.assertTrue(toolDamage(Items.NETHERITE_AXE) == 7.0, "netherite axe +7, got " + toolDamage(Items.NETHERITE_AXE));
		ctx.assertTrue(toolDamage(Items.WOODEN_SHOVEL) == 1.0, "wooden shovel +1, got " + toolDamage(Items.WOODEN_SHOVEL));
		ctx.assertTrue(toolDamage(Items.STONE_PICKAXE) == 3.0, "stone pickaxe +3, got " + toolDamage(Items.STONE_PICKAXE));
		ctx.assertTrue(toolDamage(Items.DIAMOND_SWORD) == 6.0, "the diamond sword keeps +6, got " + toolDamage(Items.DIAMOND_SWORD));
		ctx.complete();
	}

	/** A fully charged sword swing on the ground sweeps the zombie beside the one it hits only with Sweeping Edge. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "sweeping")
	public void onlySweepingEdgeSweeps(TestContext ctx) throws ReflectiveOperationException {
		ServerWorld world = ctx.getWorld();
		floor(ctx, Blocks.STONE);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 1, 3));
		try {
			ItemStack enchanted = new ItemStack(Items.DIAMOND_SWORD);
			enchanted.addEnchantment(enchantment(world, Identifier.ofVanilla("sweeping_edge")), 3);
			ctx.assertTrue(!sweeps(ctx, player, new ItemStack(Items.DIAMOND_SWORD)), "a plain sword doesn't sweep");
			ctx.assertTrue(sweeps(ctx, player, enchanted), "a sword with Sweeping Edge does");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	private static boolean sweeps(TestContext ctx, ServerPlayerEntity player, ItemStack sword) throws ReflectiveOperationException {
		player.equipStack(EquipmentSlot.MAINHAND, sword);
		// A tick applies the sword's enchantments (Sweeping Edge's attribute), as when a real player picks it.
		player.playerTick();
		ZombieEntity hit = still(ctx, EntityType.ZOMBIE, new BlockPos(3, 1, 3));
		ZombieEntity beside = still(ctx, EntityType.ZOMBIE, new BlockPos(3, 1, 4));
		player.setOnGround(true);
		player.setSprinting(false);
		player.fallDistance = 0.0F;
		setField(LivingEntity.class, player, "lastAttackedTicks", 100);
		float before = beside.getHealth();
		player.attack(hit);
		boolean swept = beside.getHealth() < before;
		hit.discard();
		beside.discard();
		return swept;
	}

	/** Snowballs and eggs push what they hit, and a gliding player's firework rocket does nothing. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void thrownKnockbackAndNoRocketBoost(TestContext ctx) throws ReflectiveOperationException {
		ServerWorld world = ctx.getWorld();
		floor(ctx, Blocks.STONE);
		Method collide = ProjectileEntity.class.getDeclaredMethod("onCollision", HitResult.class);
		collide.setAccessible(true);
		for (EntityType<? extends ProjectileEntity> type : List.of(EntityType.SNOWBALL, EntityType.EGG)) {
			ZombieEntity zombie = still(ctx, EntityType.ZOMBIE, new BlockPos(4, 1, 4));
			ProjectileEntity thrown = type == EntityType.SNOWBALL ? new SnowballEntity(world, zombie.getX() - 2.0, zombie.getY() + 1.0, zombie.getZ())
					: new EggEntity(world, zombie.getX() - 2.0, zombie.getY() + 1.0, zombie.getZ());
			thrown.setVelocity(1.5, 0.0, 0.0);
			collide.invoke(thrown, new EntityHitResult(zombie));
			ctx.assertTrue(zombie.getVelocity().x > 0.3, type.getUntranslatedName() + " pushed the zombie away: " + zombie.getVelocity());
			zombie.discard();
		}
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(2, 1, 2));
		try {
			player.startFallFlying();
			ItemStack rockets = new ItemStack(Items.FIREWORK_ROCKET, 3);
			player.setStackInHand(Hand.MAIN_HAND, rockets);
			rockets.use(world, player, Hand.MAIN_HAND);
			ctx.assertTrue(player.isFallFlying(), "the player glides");
			ctx.assertTrue(rockets.getCount() == 3, "no rocket was used while gliding");
			ctx.assertTrue(world.getEntitiesByClass(FireworkRocketEntity.class, player.getBoundingBox().expand(4.0), e -> true).isEmpty(), "and none flew");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	// ---------- potions ----------

	/**
	 * Potions stack to 16; a diamond brews Haste (redstone and glowstone lengthen and strengthen it); potions of a kind
	 * mix in a crafting grid into one, keeping the stronger then longer of each effect, up to 8 effects, and leave
	 * their other bottles behind; the illusioner drops a potion of Blindness, Luck or Bad Luck.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void potionsStackBrewAndMix(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		for (Item item : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION)) {
			ctx.assertTrue(new ItemStack(item).getMaxCount() == 16, item + " stacks to 16");
		}
		BrewingRecipeRegistry brewing = world.getBrewingRecipeRegistry();
		ItemStack haste = brewing.craft(new ItemStack(Items.DIAMOND), potion(Items.POTION, Potions.AWKWARD));
		ctx.assertTrue(haste.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT).matches(Potions2.HASTE), "awkward + diamond = Haste: " + haste);
		ItemStack longer = brewing.craft(new ItemStack(Items.REDSTONE), haste);
		ctx.assertTrue(longer.get(DataComponentTypes.POTION_CONTENTS).matches(Potions2.LONG_HASTE), "+ redstone = long Haste");
		ItemStack stronger = brewing.craft(new ItemStack(Items.GLOWSTONE_DUST), haste);
		ctx.assertTrue(stronger.get(DataComponentTypes.POTION_CONTENTS).matches(Potions2.STRONG_HASTE), "+ glowstone = Haste II");

		CraftingRecipeInput two = CraftingRecipeInput.create(2, 1, List.of(potion(Items.POTION, Potions.SWIFTNESS), potion(Items.POTION, Potions.STRENGTH)));
		var match = recipe(world, two);
		ctx.assertTrue(match.isPresent(), "swiftness and strength mix");
		ItemStack mixed = match.get().value().craft(two, world.getRegistryManager());
		Set<RegistryEntry<StatusEffect>> effects = new HashSet<>();
		mixed.get(DataComponentTypes.POTION_CONTENTS).getEffects().forEach(e -> effects.add(e.getEffectType()));
		ctx.assertTrue(mixed.isOf(Items.POTION) && effects.equals(Set.of(StatusEffects.SPEED, StatusEffects.STRENGTH)), "into a potion of both: " + effects);
		var left = match.get().value().getRemainder(two);
		ctx.assertTrue(left.stream().filter(s -> s.isOf(Items.GLASS_BOTTLE)).count() == 1, "one bottle left over: " + left);

		CraftingRecipeInput same = CraftingRecipeInput.create(2, 1, List.of(potion(Items.SPLASH_POTION, Potions.SWIFTNESS),
				potion(Items.SPLASH_POTION, Potions.LONG_SWIFTNESS)));
		ItemStack longest = recipe(world, same).orElseThrow().value().craft(same, world.getRegistryManager());
		List<StatusEffectInstance> kept = toList(longest.get(DataComponentTypes.POTION_CONTENTS).getEffects());
		ctx.assertTrue(longest.isOf(Items.SPLASH_POTION) && kept.size() == 1 && kept.get(0).getDuration() == 9600, "two Swiftness keep the longer: " + kept);
		ctx.assertTrue(recipe(world, CraftingRecipeInput.create(2, 1, List.of(potion(Items.SPLASH_POTION, Potions.SWIFTNESS),
				potion(Items.POTION, Potions.STRENGTH)))).isEmpty(), "a splash potion doesn't mix with a drinking one");

		List<RegistryEntry<Potion>> nine = List.of(Potions.SWIFTNESS, Potions.STRENGTH, Potions.LEAPING, Potions.FIRE_RESISTANCE, Potions.WATER_BREATHING,
				Potions.INVISIBILITY, Potions.NIGHT_VISION, Potions.REGENERATION, Potions.SLOW_FALLING);
		List<ItemStack> grid = new ArrayList<>();
		nine.forEach(p -> grid.add(potion(Items.POTION, p)));
		ctx.assertTrue(recipe(world, CraftingRecipeInput.create(3, 3, grid)).isEmpty(), "nine effects are too many");
		grid.set(8, ItemStack.EMPTY);
		ctx.assertTrue(recipe(world, CraftingRecipeInput.create(3, 3, grid)).isPresent(), "eight mix");

		LootTable table = world.getServer().getReloadableRegistries().getLootTable(EntityType.ILLUSIONER.getLootTableId());
		IllusionerEntity illusioner = EntityType.ILLUSIONER.create(world);
		illusioner.refreshPositionAndAngles(ctx.getAbsolutePos(new BlockPos(2, 1, 2)), 0.0F, 0.0F);
		LootContextParameterSet params = new LootContextParameterSet.Builder(world).add(LootContextParameters.THIS_ENTITY, illusioner)
				.add(LootContextParameters.ORIGIN, illusioner.getPos()).add(LootContextParameters.DAMAGE_SOURCE, world.getDamageSources().generic())
				.build(LootContextTypes.ENTITY);
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 60; i++) {
			for (ItemStack drop : table.generateLoot(params)) {
				var contents = drop.get(DataComponentTypes.POTION_CONTENTS);
				ctx.assertTrue(drop.isOf(Items.POTION) && contents != null && contents.potion().isPresent(), "the illusioner drops a potion: " + drop);
				seen.add(contents.potion().get().getIdAsString());
			}
		}
		ctx.assertTrue(seen.equals(Set.of("eternalreturn:blindness", "minecraft:luck", "eternalreturn:bad_luck")), "Blindness, Luck and Bad Luck: " + seen);
		ctx.complete();
	}

	private static List<StatusEffectInstance> toList(Iterable<StatusEffectInstance> effects) {
		List<StatusEffectInstance> list = new ArrayList<>();
		effects.forEach(list::add);
		return list;
	}

	// ---------- enchantments ----------

	/**
	 * Cubism (swords, up to V) adds damage against slimes, magma cubes and shulkers only; Autoload (crossbows, I)
	 * loads a crossbow that isn't in hand, from the player's arrows.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void cubismAndAutoload(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, Blocks.STONE);
		RegistryEntry<Enchantment> cubism = enchantment(world, EternalReturn.id("cubism"));
		ctx.assertTrue(cubism.value().getMaxLevel() == 5, "Cubism goes to V");
		ctx.assertTrue(cubism.value().isSupportedItem(new ItemStack(Items.IRON_SWORD)) && !cubism.value().isSupportedItem(new ItemStack(Items.BOW)),
				"on swords");
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.addEnchantment(cubism, 5);
		DamageSource source = world.getDamageSources().generic();
		for (EntityType<? extends MobEntity> type : List.of(EntityType.SLIME, EntityType.MAGMA_CUBE, EntityType.SHULKER)) {
			MobEntity cube = still(ctx, type, new BlockPos(2, 1, 2));
			float damage = EnchantmentHelper.getDamage(world, sword, cube, source, 6.0F);
			ctx.assertTrue(damage > 6.0F, "Cubism V adds damage to a " + type.getUntranslatedName() + ": " + damage);
			cube.discard();
		}
		ZombieEntity zombie = still(ctx, EntityType.ZOMBIE, new BlockPos(2, 1, 2));
		ctx.assertTrue(EnchantmentHelper.getDamage(world, sword, zombie, source, 6.0F) == 6.0F, "but not to a zombie");
		zombie.discard();

		RegistryEntry<Enchantment> autoload = enchantment(world, Autoload.ID);
		ctx.assertTrue(autoload.value().getMaxLevel() == 1 && autoload.value().isSupportedItem(new ItemStack(Items.CROSSBOW)), "Autoload I, on crossbows");
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(4, 1, 4));
		try {
			var inventory = player.getInventory();
			inventory.selectedSlot = 0;
			ItemStack held = new ItemStack(Items.CROSSBOW);
			held.addEnchantment(autoload, 1);
			ItemStack plain = new ItemStack(Items.CROSSBOW);
			ItemStack stowed = new ItemStack(Items.CROSSBOW);
			stowed.addEnchantment(autoload, 1);
			inventory.setStack(0, held);
			inventory.setStack(2, plain);
			inventory.setStack(3, stowed);
			inventory.setStack(5, new ItemStack(Items.ARROW, 10));
			player.age = 25;
			Autoload.tick(player);
			ctx.assertTrue(CrossbowItem.isCharged(stowed), "the stowed Autoload crossbow loaded itself");
			ctx.assertTrue(!CrossbowItem.isCharged(held), "not the one in hand");
			ctx.assertTrue(!CrossbowItem.isCharged(plain), "nor one without Autoload");
			ctx.assertTrue(inventory.getStack(5).getCount() == 9, "using an arrow: " + inventory.getStack(5));
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	// ---------- signs and shulkers ----------

	/** Phantom membrane on a sign makes it invisible (only its text shows), kept when saved; dye colours a shulker. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void invisibleSignsAndDyedShulkers(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, Blocks.STONE);
		ctx.setBlockState(2, 1, 2, Blocks.OAK_SIGN);
		BlockPos sign = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(4, 1, 2));
		try {
			ItemStack membrane = new ItemStack(Items.PHANTOM_MEMBRANE, 2);
			player.setStackInHand(Hand.MAIN_HAND, membrane);
			BlockState state = world.getBlockState(sign);
			state.onUseWithItem(membrane, world, player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(sign), Direction.UP, sign, false));
			SignBlockEntity entity = (SignBlockEntity) world.getBlockEntity(sign);
			ctx.assertTrue(InvisibleSigns.isInvisible(entity), "the sign is invisible");
			ctx.assertTrue(membrane.getCount() == 1, "a membrane was used");
			NbtCompound saved = entity.createNbt(world.getRegistryManager());
			ctx.assertTrue(saved.getBoolean("eternalreturn_invisible"), "and stays so when saved: " + saved);

			ctx.setBlockState(5, 1, 5, Blocks.STONE);
			ShulkerEntity shulker = still(ctx, EntityType.SHULKER, new BlockPos(5, 2, 5));
			ItemStack dye = new ItemStack(Items.RED_DYE, 2);
			player.setStackInHand(Hand.MAIN_HAND, dye);
			shulker.interact(player, Hand.MAIN_HAND);
			ctx.assertTrue(shulker.getColor() == DyeColor.RED, "red dye makes a red shulker: " + shulker.getColor());
			ctx.assertTrue(dye.getCount() == 1, "using the dye");
			shulker.discard();
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	// ---------- farming ----------

	/** Bone meal grows sugar cane and cactus a block taller and nether wart a stage older. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void boneMealOnCaneCactusAndWart(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, Blocks.STONE);
		ctx.setBlockState(1, 0, 1, Blocks.SAND);
		ctx.setBlockState(1, 0, 2, Blocks.WATER);
		ctx.setBlockState(1, 1, 1, Blocks.SUGAR_CANE);
		ctx.setBlockState(5, 0, 1, Blocks.SAND);
		ctx.setBlockState(5, 1, 1, Blocks.CACTUS);
		ctx.setBlockState(3, 0, 5, Blocks.SOUL_SAND);
		ctx.setBlockState(3, 1, 5, Blocks.NETHER_WART);
		for (BlockPos rel : List.of(new BlockPos(1, 1, 1), new BlockPos(5, 1, 1), new BlockPos(3, 1, 5))) {
			ctx.assertTrue(BoneMealItem.useOnFertilizable(new ItemStack(Items.BONE_MEAL), world, ctx.getAbsolutePos(rel)), "bone meal takes on " + world.getBlockState(ctx.getAbsolutePos(rel)));
		}
		ctx.expectBlock(Blocks.SUGAR_CANE, new BlockPos(1, 2, 1));
		ctx.expectBlock(Blocks.CACTUS, new BlockPos(5, 2, 1));
		ctx.assertTrue(world.getBlockState(ctx.getAbsolutePos(new BlockPos(3, 1, 5))).get(NetherWartBlock.AGE) >= 1, "the wart grew");
		ctx.complete();
	}

	/** A falling player tramples farmland, but not in Feather Falling boots (players, as mobs need mobGriefing). */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void featherFallingSparesFarmland(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, Blocks.STONE);
		ctx.setBlockState(2, 0, 2, Blocks.FARMLAND);
		ctx.setBlockState(5, 0, 5, Blocks.FARMLAND);
		ServerPlayerEntity bare = FishingTests.survivalPlayer(ctx, new BlockPos(2, 1, 2));
		ServerPlayerEntity shod = FishingTests.survivalPlayer(ctx, new BlockPos(5, 1, 5));
		try {
			ItemStack boots = new ItemStack(Items.IRON_BOOTS);
			boots.addEnchantment(enchantment(world, Identifier.ofVanilla("feather_falling")), 1);
			shod.equipStack(EquipmentSlot.FEET, boots);
			BlockPos a = ctx.getAbsolutePos(new BlockPos(2, 0, 2));
			BlockPos b = ctx.getAbsolutePos(new BlockPos(5, 0, 5));
			world.getBlockState(a).getBlock().onLandedUpon(world, world.getBlockState(a), a, bare, 3.0F);
			world.getBlockState(b).getBlock().onLandedUpon(world, world.getBlockState(b), b, shod, 3.0F);
			ctx.expectBlock(Blocks.DIRT, new BlockPos(2, 0, 2));
			ctx.expectBlock(Blocks.FARMLAND, new BlockPos(5, 0, 5));
		} finally {
			FishingTests.disconnect(bare);
			FishingTests.disconnect(shod);
		}
		ctx.complete();
	}

	/** A sapling lying on grass near the end of its time plants itself where it lies. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
	public void saplingsPlantThemselves(TestContext ctx) throws ReflectiveOperationException {
		ServerWorld world = ctx.getWorld();
		floor(ctx, Blocks.GRASS_BLOCK);
		Vec3d at = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(3, 1, 3)));
		ItemEntity item = new ItemEntity(world, at.x, at.y + 0.2, at.z, new ItemStack(Items.OAK_SAPLING), 0.0, 0.0, 0.0);
		setField(ItemEntity.class, item, "itemAge", 5600);
		world.spawnEntity(item);
		ctx.waitAndRun(60, () -> {
			ctx.expectBlock(Blocks.OAK_SAPLING, new BlockPos(3, 1, 3));
			ctx.assertTrue(item.isRemoved(), "the item went into the ground");
			ctx.complete();
		});
	}

	/** In the rain a crop gets another growth tick for each of its own (survival.rainGrowthMultiplier 2); not when dry. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "rain_growth", skyAccess = true)
	public void rainSpeedsCrops(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, Blocks.STONE);
		ctx.setBlockState(3, 0, 3, Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, 7));
		BlockPos wheat = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
		Random random = Random.create(7);
		try {
			world.setWeather(0, 0, false, false);
			world.setRainGradient(0.0F);
			world.setBlockState(wheat, Blocks.WHEAT.getDefaultState());
			for (int i = 0; i < 200; i++) {
				Growing.rainTick(world.getBlockState(wheat), world, wheat, random);
			}
			ctx.assertTrue(world.getBlockState(wheat).get(CropBlock.AGE) == 0, "no extra growth in dry weather");
			world.setWeather(0, 6000, true, false);
			// Rain fades in over a few seconds; start it at full.
			world.setRainGradient(1.0F);
			ctx.assertTrue(world.hasRain(wheat), "it rains on the wheat");
			for (int i = 0; i < 200; i++) {
				Growing.rainTick(world.getBlockState(wheat), world, wheat, random);
			}
			ctx.assertTrue(world.getBlockState(wheat).get(CropBlock.AGE) > 0, "the rain made it grow: " + world.getBlockState(wheat));
		} finally {
			world.setWeather(0, 0, false, false);
			world.setRainGradient(0.0F);
		}
		ctx.complete();
	}
}
