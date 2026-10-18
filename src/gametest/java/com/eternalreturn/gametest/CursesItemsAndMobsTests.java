package com.eternalreturn.gametest;

import com.eternalreturn.blocks.CopperRail;
import com.eternalreturn.items.AsylumPapers;
import com.eternalreturn.items.Dynamite;
import com.eternalreturn.items.Necronomicon;
import com.eternalreturn.items.PhilosophersStone;
import com.eternalreturn.logic.Curses;
import com.eternalreturn.mixin.mobs.CreeperEntityAccessor;
import com.eternalreturn.mobs.LargeMobs;
import com.eternalreturn.mobs.PillagerBoats;
import com.eternalreturn.mobs.Servants;
import com.eternalreturn.survival.ArmorWeight;
import com.eternalreturn.villager.WanderingTraders;
import com.eternalreturn.worldgen.Dungeons;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
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
import net.minecraft.village.TradeOffer;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.biome.BiomeKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * The five new curses, netherite crafted, gold armor lighter than iron, faster minecarts and copper rails,
 * dynamite, asylum papers and the wandering trader, the Necronomicon's servants, large mobs, more zombie
 * villagers and Nether witches, pillager boats, and dungeon styles.
 */
public class CursesItemsAndMobsTests implements FabricGameTest {
	private static void floor(TestContext ctx, int size) {
		for (int x = -1; x <= size; x++) {
			for (int z = -1; z <= size; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static RegistryEntry<Enchantment> enchantment(ServerWorld world, Identifier id) {
		return world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(id).orElseThrow();
	}

	private static ItemStack cursed(ServerWorld world, ItemStack stack, Identifier curse) {
		stack.addEnchantment(enchantment(world, curse), 1);
		return stack;
	}

	private static <T extends MobEntity> T still(TestContext ctx, EntityType<T> type, BlockPos pos) {
		T mob = ctx.spawnEntity(type, pos);
		mob.setAiDisabled(true);
		mob.setPersistent();
		return mob;
	}

	private static ItemStack craft(ServerWorld world, int width, int height, List<ItemStack> grid) {
		CraftingRecipeInput input = CraftingRecipeInput.create(width, height, grid);
		return world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world)
				.map(e -> ((CraftingRecipe) e.value()).craft(input, world.getRegistryManager())).orElse(ItemStack.EMPTY);
	}

	// ---------- curses ----------

	/** Visibility makes the wearer glow, Blindness blinds, Heaviness doubles weight; each fits its own gear, and not with its rival. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void wornCurses(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, 4);
		ZombieEntity wearer = still(ctx, EntityType.ZOMBIE, new BlockPos(2, 1, 2));
		wearer.equipStack(EquipmentSlot.CHEST, cursed(world, new ItemStack(Items.IRON_CHESTPLATE), Curses.VISIBILITY));
		wearer.equipStack(EquipmentSlot.HEAD, cursed(world, new ItemStack(Items.IRON_HELMET), Curses.BLINDNESS));
		Curses.tickWorn(wearer);
		ctx.assertTrue(wearer.hasStatusEffect(StatusEffects.GLOWING) && wearer.hasStatusEffect(StatusEffects.BLINDNESS), "cursed armor: glowing and blind");
		ZombieEntity plain = still(ctx, EntityType.ZOMBIE, new BlockPos(4, 1, 4));
		plain.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		Curses.tickWorn(plain);
		ctx.assertTrue(!plain.hasStatusEffect(StatusEffects.GLOWING), "plain armor: nothing");
		ctx.killAllEntities();

		double heavy = ArmorWeight.weight(cursed(world, new ItemStack(Items.IRON_CHESTPLATE), Curses.HEAVINESS));
		double normal = ArmorWeight.weight(new ItemStack(Items.IRON_CHESTPLATE));
		ctx.assertTrue(normal > 0.0 && Math.abs(heavy - 2.0 * normal) < 1e-9, "heaviness doubles the weight: " + heavy + " vs " + normal);
		ctx.assertTrue(ArmorWeight.weight(new ItemStack(Items.GOLDEN_CHESTPLATE)) < normal, "gold armor is lighter than iron");
		ctx.assertTrue(!Enchantment.canBeCombined(enchantment(world, Curses.HEAVINESS), enchantment(world, ArmorWeight.WEIGHTLESS)), "heaviness vs weightless");
		ctx.assertTrue(!Enchantment.canBeCombined(enchantment(world, Curses.FRAILNESS), enchantment(world, Identifier.ofVanilla("unbreaking"))), "frailness vs unbreaking");
		ctx.assertTrue(enchantment(world, Curses.VISIBILITY).value().isSupportedItem(new ItemStack(Items.IRON_CHESTPLATE))
				&& !enchantment(world, Curses.VISIBILITY).value().isSupportedItem(new ItemStack(Items.IRON_HELMET)), "visibility: chestplates");
		ctx.assertTrue(enchantment(world, Curses.BLINDNESS).value().isSupportedItem(new ItemStack(Items.IRON_HELMET)), "blindness: helmets");
		ctx.assertTrue(enchantment(world, Curses.SLIPPERINESS).value().isSupportedItem(new ItemStack(Items.IRON_PICKAXE))
				&& enchantment(world, Curses.SLIPPERINESS).value().isSupportedItem(new ItemStack(Items.IRON_SWORD))
				&& !enchantment(world, Curses.SLIPPERINESS).value().isSupportedItem(new ItemStack(Items.IRON_CHESTPLATE)), "slipperiness: tools and weapons");
		var curseTag = net.minecraft.registry.tag.EnchantmentTags.CURSE;
		for (Identifier curse : List.of(Curses.VISIBILITY, Curses.BLINDNESS, Curses.HEAVINESS, Curses.SLIPPERINESS, Curses.FRAILNESS)) {
			ctx.assertTrue(enchantment(world, curse).isIn(curseTag), curse + " is a curse");
		}
		ctx.complete();
	}

	/** Slipperiness drops a tool about 1 use in 200; Frailness doubles about half of the damage an item takes. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "slippery")
	public void usedCurses(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, 4);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(2, 1, 2));
		try {
			int slips = 0;
			for (int i = 0; i < 4000; i++) {
				player.setStackInHand(Hand.MAIN_HAND, cursed(world, new ItemStack(Items.IRON_PICKAXE), Curses.SLIPPERINESS));
				if (Curses.slip(player, Hand.MAIN_HAND)) {
					slips++;
					ctx.assertTrue(player.getMainHandStack().isEmpty(), "a slipped tool leaves the hand");
				}
			}
			ctx.assertTrue(slips >= 6 && slips <= 40, "slipped " + slips + " times in 4000 uses (about 20)");
			int dropped = world.getEntitiesByClass(ItemEntity.class, new Box(player.getBlockPos()).expand(8), e -> e.getStack().isOf(Items.IRON_PICKAXE)).size();
			ctx.assertTrue(dropped == slips, "each slip drops the tool: " + dropped);
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
			ctx.assertTrue(!Curses.slip(player, Hand.MAIN_HAND), "an uncursed tool never slips");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.killAllEntities();
		int frail = 0;
		int plain = 0;
		for (int i = 0; i < 400; i++) {
			ItemStack sword = cursed(world, new ItemStack(Items.IRON_SWORD), Curses.FRAILNESS);
			sword.damage(1, world, null, item -> {
			});
			frail += sword.getDamage();
			ItemStack other = new ItemStack(Items.IRON_SWORD);
			other.damage(1, world, null, item -> {
			});
			plain += other.getDamage();
		}
		ctx.assertTrue(plain == 400 && frail > 520 && frail < 680, "frail swords took " + frail + " damage from 400 hits (about 600), plain " + plain);
		ctx.complete();
	}

	// ---------- gear, rails, items ----------

	/** Netherite gear is crafted from netherite ingots like diamond gear, and no longer upgraded from diamond. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void netheriteIsCrafted(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ItemStack n = new ItemStack(Items.NETHERITE_INGOT);
		ItemStack none = ItemStack.EMPTY;
		ItemStack chest = craft(world, 3, 3, List.of(n, none, n, n, n, n, n, n, n));
		ctx.assertTrue(chest.isOf(Items.NETHERITE_CHESTPLATE), "8 netherite ingots make a chestplate: " + chest);
		ItemStack stick = new ItemStack(Items.STICK);
		ItemStack sword = craft(world, 1, 3, List.of(n, n, stick));
		ctx.assertTrue(sword.isOf(Items.NETHERITE_SWORD), "netherite sword: " + sword);
		for (String piece : List.of("sword", "pickaxe", "axe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots")) {
			ctx.assertTrue(world.getRecipeManager().get(Identifier.ofVanilla("netherite_" + piece + "_smithing")).isEmpty(), "no smithing upgrade for " + piece);
		}
		ctx.complete();
	}

	/** Minecarts take 3 of vanilla's steps a tick; copper rails are crafted from copper and carry a cart like any rail. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void fasterMinecartsAndCopperRails(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ItemStack c = new ItemStack(Items.COPPER_INGOT);
		ItemStack rails = craft(world, 3, 3, List.of(c, ItemStack.EMPTY, c, c, new ItemStack(Items.STICK), c, c, ItemStack.EMPTY, c));
		ctx.assertTrue(rails.isOf(CopperRail.ITEM) && rails.getCount() == 16, "6 copper and a stick make 16 copper rails: " + rails);
		ctx.assertTrue(CopperRail.BLOCK.getDefaultState().isIn(BlockTags.RAILS), "copper rails are rails");
		floor(ctx, 7);
		for (int x = 0; x <= 7; x++) {
			ctx.setBlockState(new BlockPos(x, 1, 3), CopperRail.BLOCK.getDefaultState()
					.with(net.minecraft.block.RailBlock.SHAPE, net.minecraft.block.enums.RailShape.EAST_WEST));
		}
		AbstractMinecartEntity cart = ctx.spawnEntity(EntityType.MINECART, new BlockPos(0, 1, 3));
		ctx.assertTrue(com.eternalreturn.blocks.Minecarts.steps() == 3, "3 steps a tick, each up to vanilla's 0.4 blocks");
		Vec3d east = ctx.getAbsolutePos(new BlockPos(1, 0, 0)).subtract(ctx.getAbsolutePos(BlockPos.ORIGIN)).toCenterPos().subtract(0.5, 0.5, 0.5);
		cart.setVelocity(east.multiply(0.6));
		double start = cart.getPos().dotProduct(east);
		ctx.runAtTick(ctx.getTick() + 8, () -> {
			double moved = cart.getPos().dotProduct(east) - start;
			ctx.assertTrue(moved > 2.0, "the cart ran " + moved + " blocks along copper rail");
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/** A stick of dynamite goes off 2 seconds after it is lit, a little weaker than TNT, breaking blocks. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "dynamite", tickLimit = 100)
	public void dynamiteGoesOffAfterItsFuse(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, 7);
		for (int x = 1; x <= 5; x++) {
			for (int z = 1; z <= 5; z++) {
				ctx.setBlockState(x, 1, z, Blocks.DIRT);
			}
		}
		Vec3d at = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(3, 2, 3)));
		Dynamite.Lit lit = new Dynamite.Lit(world, at.x, at.y, at.z);
		world.spawnEntity(lit);
		ctx.assertTrue(craft(world, 2, 2, List.of(new ItemStack(Items.GUNPOWDER), new ItemStack(Items.GUNPOWDER), new ItemStack(Items.PAPER),
				new ItemStack(Items.STRING))).isOf(Dynamite.ITEM), "dynamite from 2 gunpowder, paper and string");
		ctx.runAtTick(ctx.getTick() + 30, () -> ctx.assertTrue(lit.isAlive(), "still lit after 1.5 seconds"));
		ctx.runAtTick(ctx.getTick() + 45, () -> {
			ctx.assertTrue(lit.isRemoved(), "gone off by 2.25 seconds");
			int gone = 0;
			for (int x = 1; x <= 5; x++) {
				for (int z = 1; z <= 5; z++) {
					gone += ctx.getBlockState(new BlockPos(x, 1, z)).isAir() ? 1 : 0;
				}
			}
			ctx.assertTrue(gone >= 4, "the blast broke " + gone + " dirt blocks");
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/** Asylum papers bring an adult villager with no job; a wandering trader always sells them, and never what the world can't give. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "traders", tickLimit = 40)
	public void asylumPapersAndTraders(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		floor(ctx, 5);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(0, 1, 0));
		try {
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(AsylumPapers.ITEM, 2));
			BlockPos ground = ctx.getAbsolutePos(new BlockPos(3, 0, 3));
			AsylumPapers.ITEM.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(ground), Direction.UP, ground, false)));
			List<VillagerEntity> villagers = world.getEntitiesByClass(VillagerEntity.class, new Box(ground).expand(4), v -> true);
			ctx.assertTrue(villagers.size() == 1 && !villagers.get(0).isBaby()
					&& villagers.get(0).getVillagerData().getProfession() == VillagerProfession.NONE, "a jobless adult villager arrived: " + villagers);
			ctx.assertTrue(player.getMainHandStack().getCount() == 1, "the papers are used up");
			ctx.assertTrue(!WanderingTraders.nearVillage(player), "one villager isn't a village");
			still(ctx, EntityType.VILLAGER, new BlockPos(4, 1, 4));
			still(ctx, EntityType.VILLAGER, new BlockPos(5, 1, 2));
			ctx.assertTrue(WanderingTraders.nearVillage(player), "three villagers are");
		} finally {
			FishingTests.disconnect(player);
		}
		List<String> wrong = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			WanderingTraderEntity trader = EntityType.WANDERING_TRADER.create(world);
			boolean papers = false;
			for (TradeOffer offer : trader.getOffers()) {
				if (offer.getSellItem().isIn(WanderingTraders.NOT_SOLD)) {
					wrong.add(offer.getSellItem().toString());
				}
				papers |= offer.getSellItem().isOf(AsylumPapers.ITEM) && offer.getOriginalFirstBuyItem().isOf(Items.EMERALD)
						&& offer.getOriginalFirstBuyItem().getCount() == 16;
			}
			ctx.assertTrue(papers, "a trader sells asylum papers for 16 emeralds");
		}
		ctx.assertTrue(wrong.isEmpty(), "traders sold " + wrong);
		ctx.killAllEntities();
		ctx.complete();
	}

	// ---------- the Necronomicon ----------

	/**
	 * Creative-only; raises up to 3 servants that leave
	 * their master alone, go for what their master hits, survive saving and loading, and drop nothing.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "servants", tickLimit = 200)
	public void necronomiconServants(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ItemStack s = new ItemStack(Items.WITHER_SKELETON_SKULL);
		ItemStack book = craft(world, 3, 3, List.of(s, s, s, s, new ItemStack(PhilosophersStone.ITEM), s, s, new ItemStack(Items.BOOK), s));
		ctx.assertTrue(book.isEmpty(), "the Necronomicon is creative-only, not crafted: " + book);
		book = new ItemStack(Necronomicon.ITEM);
		ctx.assertTrue(book.getMaxDamage() == 666, "666 uses");
		floor(ctx, 7);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(0, 1, 0));
		player.setStackInHand(Hand.MAIN_HAND, book);
		BlockPos ground = ctx.getAbsolutePos(new BlockPos(4, 0, 4));
		BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(ground), Direction.UP, ground, false);
		for (int i = 0; i < 4; i++) {
			player.getItemCooldownManager().remove(Necronomicon.ITEM);
			Necronomicon.ITEM.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
		}
		List<MobEntity> servants = world.getEntitiesByClass(MobEntity.class, new Box(ground).expand(8), Servants::isServant);
		ctx.assertTrue(servants.size() == 3 && Servants.count(player) == 3, "3 servants at most: " + servants);
		ctx.assertTrue(player.getMainHandStack().getDamage() == 3, "3 uses, 3 damage: " + player.getMainHandStack().getDamage());
		ctx.assertTrue(servants.stream().allMatch(MobEntity::isPersistent), "servants stay");
		MobEntity first = servants.get(0);
		NbtCompound saved = new NbtCompound();
		first.writeNbt(saved);
		MobEntity copy = (MobEntity) first.getType().create(world);
		copy.readNbt(saved);
		ctx.assertTrue(Servants.isServant(copy), "a loaded servant keeps its master");

		CowEntity cow = still(ctx, EntityType.COW, new BlockPos(7, 1, 7));
		// Hard to kill, or a wither skeleton servant may have finished it off before the check (an invulnerable one can't be targeted at all).
		cow.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1000.0);
		cow.setHealth(1000.0F);
		player.onAttacking(cow);
		ctx.runAtTick(ctx.getTick() + 40, () -> {
			for (MobEntity servant : servants) {
				ctx.assertTrue(servant.getTarget() != player, servant.getType() + " turned on its master");
			}
			boolean helping = servants.stream().anyMatch(servant -> servant.getTarget() == cow);
			ctx.assertTrue(helping, "servants go for what their master hits: " + servants.stream().map(m -> String.valueOf(m.getTarget())).toList());
			MobEntity victim = servants.get(0);
			BlockPos where = victim.getBlockPos();
			victim.damage(world.getDamageSources().generic(), 1000.0F);
			ctx.runAtTick(ctx.getTick() + 2, () -> {
				Box around = new Box(where).expand(3);
				int items = world.getEntitiesByClass(ItemEntity.class, around, e -> true).size();
				int orbs = world.getEntitiesByClass(ExperienceOrbEntity.class, around, e -> true).size();
				FishingTests.disconnect(player);
				ctx.killAllEntities();
				ctx.assertTrue(items == 0 && orbs == 0, "a dead servant leaves nothing: " + items + " items, " + orbs + " orbs");
				ctx.complete();
			});
		});
	}

	// ---------- mobs ----------

	/** A large zombie is twice the size, holds nothing, and leaves villagers alone; a large creeper's fuse and blast are doubled. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "large_mobs", tickLimit = 200)
	public void largeMobs(TestContext ctx) {
		floor(ctx, 7);
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		zombie.setBaby(false);
		zombie.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		double height = zombie.getHeight();
		ctx.assertTrue(LargeMobs.makeLarge(zombie), "room to grow");
		ctx.assertTrue(LargeMobs.isLarge(zombie) && Math.abs(zombie.getHeight() - 2.0 * height) < 0.01, "twice the height: " + zombie.getHeight());
		ctx.assertTrue(zombie.getMainHandStack().isEmpty() && zombie.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.LEATHER_HELMET), "no sword, helmet kept");
		ctx.assertTrue(zombie.getAttributeInstance(EntityAttributes.GENERIC_SCALE).getBaseValue() == 2.0, "the scale is saved with it");
		CreeperEntity creeper = still(ctx, EntityType.CREEPER, new BlockPos(6, 1, 6));
		LargeMobs.makeLarge(creeper);
		CreeperEntityAccessor accessor = (CreeperEntityAccessor) creeper;
		ctx.assertTrue(accessor.eternalreturn$getFuseTime() == 60 && accessor.eternalreturn$getExplosionRadius() == 6, "creeper fuse "
				+ accessor.eternalreturn$getFuseTime() + ", blast " + accessor.eternalreturn$getExplosionRadius());
		creeper.discard();
		VillagerEntity villager = still(ctx, EntityType.VILLAGER, new BlockPos(6, 1, 1));
		ctx.runAtEveryTick(() -> ctx.assertTrue(zombie.getTarget() != villager, "a large zombie went for a villager"));
		ctx.runAtTick(ctx.getTick() + 120, () -> {
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/** Zombie villagers are 15% of zombies (weights in a biome's spawn list); witches spawn in the Nether. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void spawnLists(TestContext ctx) {
		var biomes = ctx.getWorld().getRegistryManager().get(RegistryKeys.BIOME);
		var plains = biomes.getOrThrow(BiomeKeys.PLAINS).getSpawnSettings().getSpawnEntries(SpawnGroup.MONSTER).getEntries();
		int zombies = plains.stream().filter(e -> e.type == EntityType.ZOMBIE).mapToInt(e -> e.getWeight().getValue()).sum();
		int villagers = plains.stream().filter(e -> e.type == EntityType.ZOMBIE_VILLAGER).mapToInt(e -> e.getWeight().getValue()).sum();
		double share = villagers / (double) (zombies + villagers);
		ctx.assertTrue(Math.abs(share - 0.15) < 0.02, "zombie villagers are " + share + " of plains zombies");
		for (var key : List.of(BiomeKeys.NETHER_WASTES, BiomeKeys.SOUL_SAND_VALLEY, BiomeKeys.CRIMSON_FOREST)) {
			ctx.assertTrue(biomes.getOrThrow(key).getSpawnSettings().getSpawnEntries(SpawnGroup.MONSTER).getEntries().stream()
					.anyMatch(e -> e.type == EntityType.WITCH), "witches in " + key.getValue());
		}
		ctx.complete();
	}

	/** A boat of two pillagers steers toward a player, they don't smash it, and an empty one goes. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "pillager_boats", tickLimit = 400)
	public void pillagerBoats(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
				ctx.setBlockState(x, 1, z, Blocks.WATER);
			}
		}
		ctx.setBlockState(0, 1, 0, Blocks.STONE);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(0, 2, 0));
		BoatEntity boat = PillagerBoats.spawnAt(world, ctx.getAbsolutePos(new BlockPos(7, 2, 7)));
		List<PillagerEntity> crew = boat.getPassengerList().stream().filter(e -> e instanceof PillagerEntity).map(e -> (PillagerEntity) e).toList();
		ctx.assertTrue(crew.size() == 2 && PillagerBoats.isPillagerBoat(boat), "two pillagers in a pillager boat");
		double start = boat.squaredDistanceTo(player);
		ctx.runAtTick(ctx.getTick() + 100, () -> {
			double now = boat.squaredDistanceTo(player);
			ctx.assertTrue(boat.isAlive(), "the pillagers didn't break their boat");
			ctx.assertTrue(Math.sqrt(now) < Math.sqrt(start) - 1.5, "the boat came closer: " + Math.sqrt(start) + " -> " + Math.sqrt(now));
			FishingTests.disconnect(player);
			crew.forEach(PillagerEntity::discard);
		});
		ctx.runAtTick(ctx.getTick() + 230, () -> {
			ctx.assertTrue(boat.isRemoved(), "an empty pillager boat goes");
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	// ---------- dungeons ----------

	/** Stone-brick, brick and Nether dungeons are built of their blocks; Nether spawners raise wither skeletons, blazes or witches. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "dungeon_styles", tickLimit = 40)
	public void dungeonStyles(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		List<String> problems = new ArrayList<>();
		int n = 0;
		for (Dungeons.Style style : Dungeons.Style.values()) {
			// Well above the test area, so nothing else is in the way: a solid block with a tunnel in, as in a cave wall.
			BlockPos center = ctx.getAbsolutePos(new BlockPos(3, 30 + 12 * n++, 3));
			for (BlockPos pos : BlockPos.iterate(center.add(-6, -2, -6), center.add(6, 6, 6))) {
				world.setBlockState(pos, (style.nether ? Blocks.NETHERRACK : Blocks.STONE).getDefaultState(), 2);
			}
			for (int x = 1; x <= 6; x++) {
				world.setBlockState(center.add(x, 0, 0), Blocks.AIR.getDefaultState(), 2);
				world.setBlockState(center.add(x, 1, 0), Blocks.AIR.getDefaultState(), 2);
			}
			boolean built = Dungeons.build(world, world.getChunkManager().getChunkGenerator(), Random.create(42L + n), center, style);
			if (!built) {
				problems.add(style + " not built");
				continue;
			}
			if (!world.getBlockState(center.add(0, -1, 0)).isOf(style.floor.getBlock()) && !world.getBlockState(center.add(0, -1, 0)).isOf(style.wall.getBlock())) {
				problems.add(style + " floor " + world.getBlockState(center.add(0, -1, 0)));
			}
			if (!world.getBlockState(center.add(-4, 1, 0)).isOf(style.wall.getBlock()) && !world.getBlockState(center.add(-3, 1, 0)).isOf(style.wall.getBlock())) {
				problems.add(style + " wall " + world.getBlockState(center.add(-3, 1, 0)));
			}
			if (world.getBlockEntity(center) instanceof MobSpawnerBlockEntity spawner) {
				String mob = spawner.createNbt(world.getRegistryManager()).getCompound("SpawnData").getCompound("entity").getString("id");
				boolean netherMob = Dungeons.netherSpawnerMobs().stream().anyMatch(type -> net.minecraft.registry.Registries.ENTITY_TYPE.getId(type).toString().equals(mob));
				if (netherMob != style.nether) {
					problems.add(style + " spawner " + mob);
				}
			} else {
				problems.add(style + " has no spawner");
			}
			for (BlockPos pos : BlockPos.iterate(center.add(-6, -2, -6), center.add(6, 6, 6))) {
				world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
			}
		}
		world.getEntitiesByClass(ItemEntity.class, new Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(100), e -> true).forEach(ItemEntity::discard);
		ctx.assertTrue(problems.isEmpty(), String.join("; ", problems));
		ctx.complete();
	}
}
