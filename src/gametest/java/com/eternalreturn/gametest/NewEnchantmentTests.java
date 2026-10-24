package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.logic.EnchantCaps;
import com.eternalreturn.logic.GaleStep;
import com.eternalreturn.logic.LavaSwimming;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Telekinesis, Backstabber, Attraction, Gale Step, lava swimming with the highest Fire Protection, and horse armor that
 * wears out and takes enchantments.
 */
public class NewEnchantmentTests implements FabricGameTest {
	private static final String BATCH = "new_enchantments";
	/** 16 by 40 blocks of stone floor (at y 1 here: stand on y 2), 14 high. */
	private static final String ARENA = "eternalreturn_test:arena";

	private static RegistryEntry<Enchantment> enchantment(ServerWorld world, RegistryKey<Enchantment> key) {
		return world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(key).orElseThrow();
	}

	private static RegistryEntry<Enchantment> enchantment(ServerWorld world, String name) {
		return enchantment(world, RegistryKey.of(RegistryKeys.ENCHANTMENT, EternalReturn.id(name)));
	}

	private static ItemStack enchanted(Item item, RegistryEntry<Enchantment> enchantment, int level) {
		ItemStack stack = new ItemStack(item);
		stack.addEnchantment(enchantment, level);
		return stack;
	}

	private static Item modItem(String name) {
		return Registries.ITEM.get(EternalReturn.id(name));
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void whatTheyGoOn(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		Enchantment telekinesis = enchantment(world, "telekinesis").value();
		Enchantment backstabber = enchantment(world, "backstabber").value();
		Enchantment attraction = enchantment(world, "attraction").value();
		Enchantment galeStep = enchantment(world, "gale_step").value();
		ctx.assertTrue(telekinesis.isSupportedItem(new ItemStack(Items.IRON_PICKAXE)) && telekinesis.isSupportedItem(new ItemStack(Items.IRON_SWORD))
				&& telekinesis.isSupportedItem(new ItemStack(Items.BOW)) && !telekinesis.isSupportedItem(new ItemStack(Items.IRON_BOOTS)),
				"Telekinesis on tools and weapons");
		ctx.assertTrue(backstabber.isSupportedItem(new ItemStack(Items.IRON_AXE)) && backstabber.isSupportedItem(new ItemStack(Items.MACE))
				&& backstabber.isSupportedItem(new ItemStack(Items.TRIDENT)) && !backstabber.isSupportedItem(new ItemStack(Items.IRON_PICKAXE)),
				"Backstabber on melee weapons");
		ctx.assertTrue(attraction.isSupportedItem(new ItemStack(Items.IRON_HELMET)) && attraction.isSupportedItem(new ItemStack(Items.IRON_BOOTS))
				&& !attraction.isSupportedItem(new ItemStack(Items.IRON_HORSE_ARMOR)), "Attraction on armor");
		ctx.assertTrue(galeStep.isSupportedItem(new ItemStack(Items.IRON_BOOTS)) && !galeStep.isSupportedItem(new ItemStack(Items.IRON_HELMET)),
				"Gale Step on boots");
		ctx.assertTrue(telekinesis.getMaxLevel() == 1 && backstabber.getMaxLevel() == 5 && attraction.getMaxLevel() == 5 && galeStep.getMaxLevel() == 1,
				"max levels");
		ctx.complete();
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void telekinesisBreaksIntoTheInventory(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 8));
		ItemStack pickaxe = enchanted(Items.IRON_PICKAXE, enchantment(world, "telekinesis"), 1);
		BlockPos a = ctx.getAbsolutePos(new BlockPos(4, 2, 12));
		BlockPos b = ctx.getAbsolutePos(new BlockPos(12, 2, 12));
		world.setBlockState(a, Blocks.STONE.getDefaultState());
		world.setBlockState(b, Blocks.STONE.getDefaultState());
		Block.dropStacks(world.getBlockState(a), world, a, null, player, pickaxe);
		Block.dropStacks(world.getBlockState(b), world, b, null, player, new ItemStack(Items.IRON_PICKAXE));
		ctx.assertTrue(player.getInventory().count(Items.COBBLESTONE) == 1, "the Telekinesis pickaxe's cobblestone is in the inventory");
		ctx.assertTrue(world.getEntitiesByClass(ItemEntity.class, new Box(a).expand(1.0), item -> true).isEmpty(), "and not on the ground");
		ctx.assertTrue(world.getEntitiesByClass(ItemEntity.class, new Box(b).expand(1.0), item -> item.getStack().isOf(Items.COBBLESTONE)).size() == 1,
				"the plain pickaxe's drops");
		FishingTests.disconnect(player);
		ctx.killAllEntities();
		ctx.complete();
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void telekinesisLoots(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 8));
		player.setStackInHand(Hand.MAIN_HAND, enchanted(Items.IRON_SWORD, enchantment(world, "telekinesis"), 1));
		ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, new BlockPos(8, 2, 10));
		husk.setAiDisabled(true);
		husk.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		husk.setEquipmentDropChance(EquipmentSlot.HEAD, 2.0F);
		husk.damage(world.getDamageSources().playerAttack(player), 1000.0F);
		ctx.assertTrue(player.getInventory().count(Items.DIAMOND_HELMET) == 1, "the husk's helmet went to the player");
		ctx.assertTrue(world.getEntitiesByClass(ItemEntity.class, new Box(husk.getBlockPos()).expand(3.0), item -> true).isEmpty(),
				"nothing dropped on the ground");
		FishingTests.disconnect(player);
		ctx.killAllEntities();
		ctx.complete();
	}

	/** A husk facing south: the same Backstabber V hit does half again as much from the north (behind it) as from the south. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void backstabberHitsHarderFromBehind(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, new BlockPos(8, 2, 10));
		husk.setAiDisabled(true);
		husk.setBodyYaw(0.0F);
		ServerPlayerEntity behind = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 8));
		ServerPlayerEntity front = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 12));
		ItemStack sword = enchanted(Items.IRON_SWORD, enchantment(world, "backstabber"), 5);
		float fromBehind = EnchantmentHelper.getDamage(world, sword, husk, world.getDamageSources().playerAttack(behind), 6.0F);
		float fromFront = EnchantmentHelper.getDamage(world, sword, husk, world.getDamageSources().playerAttack(front), 6.0F);
		ctx.assertTrue(Math.abs(fromFront - 6.0F) < 0.01F, "no bonus from the front: " + fromFront);
		ctx.assertTrue(Math.abs(fromBehind - 9.0F) < 0.01F, "50% more from behind: " + fromBehind);
		FishingTests.disconnect(behind);
		FishingTests.disconnect(front);
		ctx.killAllEntities();
		ctx.complete();
	}

	/** An item 3.5 blocks off: out of reach bare, picked up with Attraction II on the helmet and I on the boots. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void attractionReachesFurther(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 8));
		Vec3d at = player.getPos().add(3.5, 0.0, 0.0);
		ItemEntity item = new ItemEntity(world, at.x, at.y, at.z, new ItemStack(Items.EMERALD));
		item.setVelocity(Vec3d.ZERO);
		item.setPickupDelay(0);
		world.spawnEntity(item);
		com.eternalreturn.logic.Attraction.pickUp(player);
		ctx.assertTrue(!item.isRemoved(), "out of reach without Attraction");
		RegistryEntry<Enchantment> attraction = enchantment(world, "attraction");
		player.equipStack(EquipmentSlot.HEAD, enchanted(Items.IRON_HELMET, attraction, 2));
		player.equipStack(EquipmentSlot.FEET, enchanted(Items.IRON_BOOTS, attraction, 1));
		com.eternalreturn.logic.Attraction.pickUp(player);
		ctx.assertTrue(player.getInventory().count(Items.EMERALD) == 1, "picked up with three levels between two pieces");
		FishingTests.disconnect(player);
		ctx.killAllEntities();
		ctx.complete();
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void galeStepJumpsOnceInTheAir(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 6, 8));
		player.setOnGround(false);
		ctx.assertTrue(!GaleStep.canJump(player), "not without the boots");
		player.equipStack(EquipmentSlot.FEET, enchanted(Items.IRON_BOOTS, enchantment(world, "gale_step"), 1));
		ctx.assertTrue(GaleStep.canJump(player), "with them, in the air");
		player.fallDistance = 5.0F;
		GaleStep.jumped(player);
		ctx.assertTrue(player.fallDistance == 0.0F, "the jump forgives the fall");
		player.fallDistance = 5.0F;
		GaleStep.jumped(player);
		ctx.assertTrue(player.fallDistance == 5.0F, "only once before landing");
		player.setOnGround(true);
		ctx.runAtTick(3, () -> {
			player.setOnGround(false);
			GaleStep.jumped(player);
			ctx.assertTrue(player.fallDistance == 0.0F, "again after landing");
			FishingTests.disconnect(player);
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/** In lava with Fire Protection at its highest level on the chestplate: no burning, and sprinting swims. One level less burns. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void maxedFireProtectionSwimsInLava(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		RegistryEntry<Enchantment> fireProtection = enchantment(world, Enchantments.FIRE_PROTECTION);
		int max = fireProtection.value().getMaxLevel();
		for (BlockPos pos : BlockPos.iterate(2, 2, 6, 14, 4, 14)) {
			ctx.setBlockState(pos, Blocks.LAVA.getDefaultState());
		}
		ServerPlayerEntity swimmer = FishingTests.survivalPlayer(ctx, new BlockPos(5, 2, 10));
		swimmer.equipStack(EquipmentSlot.CHEST, enchanted(Items.IRON_CHESTPLATE, fireProtection, max));
		ServerPlayerEntity burner = FishingTests.survivalPlayer(ctx, new BlockPos(11, 2, 10));
		try {
			burner.equipStack(EquipmentSlot.CHEST, enchanted(Items.IRON_CHESTPLATE, fireProtection, max - 1));
			ctx.assertTrue(LavaSwimming.swims(swimmer) && !LavaSwimming.swims(burner), "only the highest level swims");
			float health = swimmer.getHealth();
			// Not in any fluid on its first tick (the test players aren't ticked by the server).
			for (int i = 0; i < 2; i++) {
				swimmer.baseTick();
				burner.baseTick();
			}
			ctx.assertTrue(swimmer.isInLava() && burner.isInLava(), "both in the lava");
			ctx.assertTrue(burner.isOnFire(), "one level less burns");
			ctx.assertTrue(!swimmer.isOnFire() && swimmer.getHealth() == health, "the swimmer neither burns nor is hurt");
			swimmer.setSprinting(true);
			swimmer.updateSwimming();
			ctx.assertTrue(swimmer.isSwimming(), "sprinting under lava swims");
		} finally {
			FishingTests.disconnect(swimmer);
			FishingTests.disconnect(burner);
		}
		ctx.killAllEntities();
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void horseArmorTakesEnchantments(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ItemStack iron = new ItemStack(Items.IRON_HORSE_ARMOR);
		ctx.assertTrue(iron.getMaxDamage() == new ItemStack(Items.IRON_CHESTPLATE).getMaxDamage(), "as durable as an iron chestplate: " + iron.getMaxDamage());
		ctx.assertTrue(new ItemStack(modItem("copper_horse_armor")).getMaxDamage() == new ItemStack(modItem("copper_chestplate")).getMaxDamage(),
				"copper too");
		ctx.assertTrue(iron.isEnchantable(), "enchantable at a table");
		for (RegistryKey<Enchantment> key : java.util.List.of(Enchantments.PROTECTION, Enchantments.FIRE_PROTECTION, Enchantments.BLAST_PROTECTION,
				Enchantments.PROJECTILE_PROTECTION, Enchantments.UNBREAKING, Enchantments.FROST_WALKER)) {
			ctx.assertTrue(enchantment(world, key).value().isSupportedItem(iron), key.getValue() + " goes on horse armor");
		}
		for (RegistryKey<Enchantment> key : java.util.List.of(Enchantments.THORNS, Enchantments.MENDING, Enchantments.FEATHER_FALLING)) {
			ctx.assertTrue(!enchantment(world, key).value().isSupportedItem(iron), key.getValue() + " doesn't");
		}
		ctx.assertTrue(enchantment(world, Enchantments.PROTECTION).value().isSupportedItem(new ItemStack(Items.IRON_CHESTPLATE)), "still on armor");
		ctx.assertTrue(EnchantCaps.capFor(iron) == EnchantCaps.capFor(new ItemStack(Items.IRON_CHESTPLATE)), "iron's capacity");
		ctx.assertTrue(EnchantCaps.capFor(new ItemStack(Items.DIAMOND_HORSE_ARMOR)) == EnchantCaps.capFor(new ItemStack(Items.DIAMOND_CHESTPLATE)),
				"diamond's capacity");
		ctx.complete();
	}

	/** A horse in Protection IV iron armor: the armor protects it, and wears as it's hurt. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void horseArmorProtectsAndWears(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		HorseEntity horse = ctx.spawnEntity(EntityType.HORSE, new BlockPos(8, 2, 10));
		horse.setAiDisabled(true);
		ItemStack armor = enchanted(Items.IRON_HORSE_ARMOR, enchantment(world, Enchantments.PROTECTION), 4);
		horse.equipStack(EquipmentSlot.BODY, armor);
		ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, new BlockPos(8, 2, 14));
		husk.setAiDisabled(true);
		DamageSource hit = world.getDamageSources().mobAttack(husk);
		ctx.assertTrue(EnchantmentHelper.getProtectionAmount(world, horse, hit) == 4.0F, "Protection IV counts on the horse: "
				+ EnchantmentHelper.getProtectionAmount(world, horse, hit));
		horse.damage(hit, 8.0F);
		ctx.assertTrue(horse.getEquippedStack(EquipmentSlot.BODY).getDamage() > 0, "the armor wore");
		ctx.killAllEntities();
		ctx.complete();
	}
}
