package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.items.Spears;
import com.eternalreturn.logic.EnchantCaps;
import com.eternalreturn.logic.SpearCombat;
import com.eternalreturn.mobs.ai.Lounging;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.recipe.input.SmithingRecipeInput;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Spears: recipes and enchantments, the jab, the charge, and mobs with spears. */
public class SpearTests implements FabricGameTest {
	private static final String BATCH = "spears";
	/** 16 by 40 blocks of stone floor (at y 1 here: stand on y 2), 14 high. */
	private static final String ARENA = "eternalreturn_test:arena";

	private static Enchantment enchantment(ServerWorld world, RegistryKey<Enchantment> key) {
		return world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getOrThrow(key);
	}

	/** A mob that won't move or think, facing south (+z). */
	private static <T extends ZombieEntity> T still(TestContext ctx, EntityType<T> type, BlockPos pos) {
		T mob = ctx.spawnEntity(type, pos);
		mob.setAiDisabled(true);
		mob.setYaw(0.0F);
		mob.setHeadYaw(0.0F);
		mob.setBodyYaw(0.0F);
		mob.setPitch(0.0F);
		return mob;
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void recipesAndEnchantments(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ItemStack stick = new ItemStack(Items.STICK);
		CraftingRecipeInput slant = CraftingRecipeInput.create(3, 3,
				List.of(ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.IRON_INGOT), ItemStack.EMPTY, stick, ItemStack.EMPTY, stick, ItemStack.EMPTY,
						ItemStack.EMPTY));
		ItemStack spear = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, slant, world)
				.map(recipe -> recipe.value().craft(slant, world.getRegistryManager())).orElse(ItemStack.EMPTY);
		ctx.assertTrue(spear.isOf(Spears.IRON), "an iron ingot over two sticks on a slant: " + spear);
		SmithingRecipeInput upgrade = new SmithingRecipeInput(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), new ItemStack(Spears.DIAMOND),
				new ItemStack(Items.NETHERITE_INGOT));
		ItemStack netherite = world.getRecipeManager().getFirstMatch(RecipeType.SMITHING, upgrade, world)
				.map(recipe -> recipe.value().craft(upgrade, world.getRegistryManager())).orElse(ItemStack.EMPTY);
		ctx.assertTrue(netherite.isOf(Spears.NETHERITE), "a diamond spear upgrades to netherite: " + netherite);
		ctx.assertTrue(spear.getMaxDamage() == 250 && new ItemStack(Spears.NETHERITE).getMaxDamage() == 2031, "durabilities");
		for (RegistryKey<Enchantment> key : List.of(Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS, Enchantments.FIRE_ASPECT,
				Enchantments.KNOCKBACK, Enchantments.LOOTING, Enchantments.UNBREAKING)) {
			ctx.assertTrue(enchantment(world, key).isSupportedItem(spear), key.getValue() + " goes on a spear");
		}
		for (RegistryKey<Enchantment> key : List.of(Enchantments.SHARPNESS, Enchantments.KNOCKBACK, Enchantments.LOOTING, Enchantments.FIRE_ASPECT)) {
			ctx.assertTrue(enchantment(world, key).isPrimaryItem(spear), key.getValue() + " is offered for a spear at the table");
		}
		ctx.assertTrue(!enchantment(world, Enchantments.SWEEPING_EDGE).isSupportedItem(spear), "Sweeping Edge doesn't");
		ctx.assertTrue(enchantment(world, Enchantments.KNOCKBACK).isSupportedItem(new ItemStack(Items.IRON_SWORD)), "swords keep theirs");
		ctx.assertTrue(EnchantCaps.capFor(spear) == EnchantCaps.capFor(new ItemStack(Items.IRON_SWORD)), "iron's enchant capacity");
		ctx.complete();
	}

	/** Husks 1, 3, 4 and 7 blocks ahead: a jab strikes the two between 2 and 4.5 blocks, and only when fully charged. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 100)
	public void jabsStrikeAlongTheLine(TestContext ctx) {
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 6));
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Spears.IRON));
		player.setYaw(0.0F);
		player.setPitch(0.0F);
		ZombieEntity near = still(ctx, EntityType.HUSK, new BlockPos(8, 2, 7));
		ZombieEntity three = still(ctx, EntityType.HUSK, new BlockPos(8, 2, 9));
		ZombieEntity four = still(ctx, EntityType.HUSK, new BlockPos(8, 2, 10));
		ZombieEntity far = still(ctx, EntityType.HUSK, new BlockPos(8, 2, 13));
		ctx.runAtEveryTick(player::playerTick);
		ctx.runAtTick(30, () -> {
			try {
				// Held items' attributes apply as the player ticks.
				ctx.assertTrue(player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) == 3.0, "an iron spear jabs for 3");
				SpearCombat.jab(player);
				ctx.assertTrue(three.getHealth() < three.getMaxHealth() && four.getHealth() < four.getMaxHealth(), "the husks 3 and 4 blocks off were struck");
				ctx.assertTrue(near.getHealth() == near.getMaxHealth(), "not the one too close");
				ctx.assertTrue(far.getHealth() == far.getMaxHealth(), "nor the one too far");
				ctx.assertTrue(player.getMainHandStack().getDamage() == 1, "one use of the spear");
				float health = three.getHealth();
				three.timeUntilRegen = 0;
				SpearCombat.jab(player);
				ctx.assertTrue(three.getHealth() == health, "no second jab before the attack recharges");
			} finally {
				FishingTests.disconnect(player);
			}
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/** A spear-carrying zombie moving at a husk: fast enough it strikes by its speed and unhorses a rider; too slow it doesn't. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void chargesStrikeBySpeed(TestContext ctx) {
		ItemStack spear = new ItemStack(Spears.IRON);
		int level = Spears.IRON.tier().delay() + 5;

		ZombieEntity runner = still(ctx, EntityType.ZOMBIE, new BlockPos(4, 2, 8));
		runner.equipStack(EquipmentSlot.MAINHAND, spear);
		ZombieEntity struck = still(ctx, EntityType.HUSK, new BlockPos(4, 2, 10));
		runner.setVelocity(0.0, 0.0, 0.35);
		SpearCombat.charge(runner, spear, level);
		float lost = struck.getMaxHealth() - struck.getHealth();
		ctx.assertTrue(lost > 4.0F, "7 blocks a second with an iron spear hurts (0.95 x 7, less armor): " + lost);

		ZombieEntity walker = still(ctx, EntityType.ZOMBIE, new BlockPos(8, 2, 8));
		ItemStack slowSpear = new ItemStack(Spears.IRON);
		walker.equipStack(EquipmentSlot.MAINHAND, slowSpear);
		ZombieEntity spared = still(ctx, EntityType.HUSK, new BlockPos(8, 2, 10));
		walker.setVelocity(0.0, 0.0, 0.1);
		SpearCombat.charge(walker, slowSpear, level);
		ctx.assertTrue(spared.getHealth() == spared.getMaxHealth(), "2 blocks a second doesn't");

		ZombieEntity lancer = still(ctx, EntityType.ZOMBIE, new BlockPos(12, 2, 8));
		ItemStack lance = new ItemStack(Spears.IRON);
		lancer.equipStack(EquipmentSlot.MAINHAND, lance);
		HorseEntity horse = ctx.spawnEntity(EntityType.HORSE, new BlockPos(12, 2, 10));
		horse.setAiDisabled(true);
		ZombieEntity rider = still(ctx, EntityType.HUSK, new BlockPos(12, 3, 10));
		rider.startRiding(horse, true);
		lancer.setVelocity(0.0, 0.0, 0.6);
		SpearCombat.charge(lancer, lance, level);
		ctx.assertTrue(!rider.hasVehicle(), "12 blocks a second, freshly level, throws a rider off");

		ctx.killAllEntities();
		ctx.complete();
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void spearsReachFurther(TestContext ctx) {
		ZombieEntity armed = still(ctx, EntityType.ZOMBIE, new BlockPos(4, 2, 8));
		armed.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Spears.IRON));
		ZombieEntity bare = still(ctx, EntityType.ZOMBIE, new BlockPos(10, 2, 8));
		ZombieEntity a = still(ctx, EntityType.HUSK, new BlockPos(4, 2, 10));
		ZombieEntity b = still(ctx, EntityType.HUSK, new BlockPos(10, 2, 10));
		ctx.assertTrue(armed.isInAttackRange(a), "a spear reaches 2 blocks");
		ctx.assertTrue(!bare.isInAttackRange(b), "a bare hand doesn't");
		ctx.killAllEntities();
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 60)
	public void piglinsCarryGoldenSpears(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		int piglinSpears = 0;
		int piglinSwords = 0;
		int zombifiedSpears = 0;
		int zombifiedSwords = 0;
		for (int i = 0; i < 60; i++) {
			PiglinEntity piglin = EntityType.PIGLIN.create(world);
			piglin.initialize(world, world.getLocalDifficulty(piglin.getBlockPos()), SpawnReason.NATURAL, null);
			piglinSpears += piglin.getMainHandStack().isOf(Spears.GOLDEN) ? 1 : 0;
			piglinSwords += piglin.getMainHandStack().isOf(Items.GOLDEN_SWORD) ? 1 : 0;
			piglin.discard();
			ZombifiedPiglinEntity zombified = EntityType.ZOMBIFIED_PIGLIN.create(world);
			zombified.initialize(world, world.getLocalDifficulty(zombified.getBlockPos()), SpawnReason.NATURAL, null);
			zombifiedSpears += zombified.getMainHandStack().isOf(Spears.GOLDEN) ? 1 : 0;
			zombifiedSwords += zombified.getMainHandStack().isOf(Items.GOLDEN_SWORD) ? 1 : 0;
			zombified.discard();
		}
		ctx.assertTrue(piglinSpears > 0 && piglinSwords > 0, "piglins: " + piglinSpears + " spears, " + piglinSwords + " swords");
		ctx.assertTrue(zombifiedSpears > 0 && zombifiedSwords > zombifiedSpears, "zombified piglins: " + zombifiedSpears + " spears, " + zombifiedSwords
				+ " swords");
		ctx.complete();
	}

	/** A zombie with a spear and a husk to hunt 8 blocks off: it lowers its spear and speeds up. */
	@GameTest(templateName = ARENA, batchId = "spear_charge", tickLimit = 200)
	public void zombiesCharge(TestContext ctx) {
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(8, 2, 4));
		zombie.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Spears.IRON));
		ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, new BlockPos(8, 2, 13));
		husk.setAiDisabled(true);
		Identifier charge = EternalReturn.id("spear_charge");
		ctx.runAtEveryTick(() -> {
			Lounging.stand(zombie);
			zombie.setTarget(husk);
			if (zombie.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).hasModifier(charge)) {
				ctx.killAllEntities();
				ctx.complete();
			}
		});
	}
}
