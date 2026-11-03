package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.logic.Homing;
import com.eternalreturn.logic.SwordBlocking;
import com.eternalreturn.mixin.mobs.MobEntityAccessor;
import com.eternalreturn.mobs.ModEntities;
import com.eternalreturn.mobs.NewMobs;
import com.eternalreturn.mobs.ai.Lounging;
import com.eternalreturn.mobs.entity.BearEntity;
import com.eternalreturn.mobs.entity.GrimlockEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * The combat rules of the owner's eleventh list: Defense and Swiftness (what they go on, what they do), no attacking
 * while blocking with a sword, Curse of Homing, grimlocks counting as undead (illagers go for them), and bears hunting
 * mobs that wear beehives. Nostalgic Tweaks (sword blocking) isn't in the test runs: SwordBlocking.testReduction stands
 * in for its 50%. The sword tests set it in their own batch.
 */
public class CombatRulesTests implements FabricGameTest {
	private static final String BATCH = "combat_rules";
	private static final String SWORD_BATCH = "sword_blocking";
	/** 16 by 40 blocks of stone floor (at y 1 here: stand on y 2), 14 high. */
	private static final String ARENA = "eternalreturn_test:arena";

	private static RegistryEntry<Enchantment> enchantment(ServerWorld world, String name) {
		return world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(RegistryKey.of(RegistryKeys.ENCHANTMENT, EternalReturn.id(name)))
				.orElseThrow();
	}

	private static ItemStack enchanted(Item item, RegistryEntry<Enchantment> enchantment, int level) {
		ItemStack stack = new ItemStack(item);
		stack.addEnchantment(enchantment, level);
		return stack;
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void whatTheyGoOn(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		RegistryEntry<Enchantment> defense = enchantment(world, "defense");
		RegistryEntry<Enchantment> swiftness = enchantment(world, "swiftness");
		RegistryEntry<Enchantment> homing = enchantment(world, "homing_curse");
		ctx.assertTrue(defense.value().isSupportedItem(new ItemStack(Items.IRON_SWORD)) && !defense.value().isSupportedItem(new ItemStack(Items.IRON_AXE))
				&& defense.value().getMaxLevel() == 5, "Defense: swords, up to V");
		ctx.assertTrue(swiftness.value().isSupportedItem(new ItemStack(Items.IRON_SWORD)) && swiftness.value().isSupportedItem(new ItemStack(Items.IRON_AXE))
				&& swiftness.value().isSupportedItem(new ItemStack(Items.TRIDENT)) && swiftness.value().isSupportedItem(new ItemStack(Items.MACE))
				&& !swiftness.value().isSupportedItem(new ItemStack(Items.IRON_PICKAXE)) && swiftness.value().getMaxLevel() == 5,
				"Swiftness: melee weapons, up to V");
		ctx.assertTrue(homing.value().isSupportedItem(new ItemStack(Items.IRON_HELMET)) && homing.value().isSupportedItem(new ItemStack(Items.IRON_BOOTS))
				&& !homing.value().isSupportedItem(new ItemStack(Items.IRON_SWORD)) && homing.value().getMaxLevel() == 1 && homing.isIn(EnchantmentTags.CURSE)
				&& !homing.isIn(EnchantmentTags.IN_ENCHANTING_TABLE), "Curse of Homing: armor, I only, a curse, not from the table");
		ctx.assertTrue(defense.isIn(EnchantmentTags.IN_ENCHANTING_TABLE) && swiftness.isIn(EnchantmentTags.IN_ENCHANTING_TABLE), "Defense and Swiftness in the table");
		ctx.complete();
	}

	/** Swiftness adds 10% attack speed a level, as Haste does. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void swiftnessSpeedsAttacks(TestContext ctx) {
		RegistryEntry<Enchantment> swiftness = enchantment(ctx.getWorld(), "swiftness");
		for (int level = 1; level <= 5; level++) {
			double[] bonus = {0.0};
			EnchantmentHelper.applyAttributeModifiers(enchanted(Items.IRON_SWORD, swiftness, level), EquipmentSlot.MAINHAND, (attribute, modifier) -> {
				if (attribute.matches(EntityAttributes.GENERIC_ATTACK_SPEED) && modifier.operation() == EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
					bonus[0] += modifier.value();
				}
			});
			ctx.assertTrue(Math.abs(bonus[0] - 0.1 * level) < 1.0E-6, "Swiftness " + level + ": +" + bonus[0] * 100 + "% attack speed");
		}
		ctx.complete();
	}

	/** Defense V takes the blocked share from 50% to 75%; never past 100%. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = SWORD_BATCH)
	public void defenseFactor(TestContext ctx) {
		ctx.assertTrue(Math.abs(SwordBlocking.defenseFactor(0.5F, 5) - 0.5F) < 1.0E-6, "50% + 25%: half of what's left");
		ctx.assertTrue(Math.abs(SwordBlocking.defenseFactor(0.5F, 1) - 0.9F) < 1.0E-6, "50% + 5%");
		ctx.assertTrue(SwordBlocking.defenseFactor(0.9F, 5) == 0.0F, "never past 100%");
		ctx.assertTrue(SwordBlocking.defenseFactor(-1.0F, 5) == 1.0F, "no Nostalgic Tweaks: nothing");
		ctx.complete();
	}

	/** Blocking with a Defense V sword (players just joined can't be hurt but by the void: its damage) takes half the damage a plain blocking sword lets through (Nostalgic Tweaks takes its 50% on top). */
	@GameTest(templateName = ARENA, batchId = SWORD_BATCH, tickLimit = 40)
	public void defenseBlocksMore(TestContext ctx) {
		SwordBlocking.testReduction = 0.5F;
		try {
			ServerPlayerEntity plain = FishingTests.survivalPlayer(ctx, new BlockPos(4, 2, 8));
			ServerPlayerEntity defended = FishingTests.survivalPlayer(ctx, new BlockPos(12, 2, 8));
			plain.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			defended.setStackInHand(Hand.MAIN_HAND, enchanted(Items.IRON_SWORD, enchantment(ctx.getWorld(), "defense"), 5));
			ctx.assertTrue(SwordBlocking.defend(defended, 10.0F) == 10.0F, "not blocking: the whole hit");
			plain.setCurrentHand(Hand.MAIN_HAND);
			defended.setCurrentHand(Hand.MAIN_HAND);
			ctx.assertTrue(SwordBlocking.isBlocking(defended), "raising the sword is blocking");
			float before = plain.getHealth();
			plain.damage(ctx.getWorld().getDamageSources().outOfWorld(), 8.0F);
			float plainTaken = before - plain.getHealth();
			before = defended.getHealth();
			defended.damage(ctx.getWorld().getDamageSources().outOfWorld(), 8.0F);
			float defendedTaken = before - defended.getHealth();
			ctx.assertTrue(plainTaken > 0.0F && Math.abs(defendedTaken - plainTaken * 0.5F) < 0.01F,
					"Defense V halves what gets through: " + plainTaken + " plain, " + defendedTaken + " with Defense");
			FishingTests.disconnect(plain);
			FishingTests.disconnect(defended);
		} finally {
			SwordBlocking.testReduction = null;
		}
		ctx.complete();
	}

	@GameTest(templateName = ARENA, batchId = SWORD_BATCH, tickLimit = 40)
	public void noAttackWhileBlocking(TestContext ctx) {
		SwordBlocking.testReduction = 0.5F;
		try {
			ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 8));
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(8, 2, 9));
			((MobEntityAccessor) zombie).eternalreturn$goalSelector().clear(goal -> true);
			player.setCurrentHand(Hand.MAIN_HAND);
			float health = zombie.getHealth();
			player.attack(zombie);
			ctx.assertTrue(zombie.getHealth() == health, "a hit while blocking does nothing");
			player.stopUsingItem();
			player.resetLastAttackedTicks();
			player.attack(zombie);
			ctx.assertTrue(zombie.getHealth() < health, "once the sword is lowered it hits");
			FishingTests.disconnect(player);
		} finally {
			SwordBlocking.testReduction = null;
		}
		ctx.killAllEntities();
		ctx.complete();
	}

	/** Two cursed pieces pull projectiles within 2 blocks; not the wearer's own, not from farther. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 40)
	public void homingPullsProjectiles(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 20));
		RegistryEntry<Enchantment> homing = enchantment(world, "homing_curse");
		ctx.assertTrue(Homing.radius(player) == 0, "no curse, no pull");
		player.equipStack(EquipmentSlot.FEET, enchanted(Items.IRON_BOOTS, homing, 1));
		player.equipStack(EquipmentSlot.LEGS, enchanted(Items.IRON_LEGGINGS, homing, 1));
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		ctx.assertTrue(Homing.radius(player) == 2, "a block per cursed piece: " + Homing.radius(player));
		Vec3d centre = player.getBoundingBox().getCenter();
		// Flying past along x, 1.5 blocks to the side (within 2 of the player's body), and one 3.5 blocks off.
		ArrowEntity near = new ArrowEntity(EntityType.ARROW, world);
		near.setPosition(centre.add(-1.0, 0.0, 1.5));
		near.setVelocity(1.5, 0.0, 0.0);
		world.spawnEntity(near);
		ArrowEntity far = new ArrowEntity(EntityType.ARROW, world);
		far.setPosition(centre.add(-1.0, 0.0, 3.6));
		far.setVelocity(1.5, 0.0, 0.0);
		world.spawnEntity(far);
		ArrowEntity own = new ArrowEntity(EntityType.ARROW, world);
		own.setOwner(player);
		own.setPosition(centre.add(-1.0, 0.0, -1.5));
		own.setVelocity(1.5, 0.0, 0.0);
		world.spawnEntity(own);
		ctx.assertTrue(Homing.target(near) == player, "the near arrow homes in");
		ctx.assertTrue(Homing.target(far) == null, "the far one doesn't");
		ctx.assertTrue(Homing.target(own) == null, "the player's own arrow doesn't");
		Homing.steer(near);
		Vec3d toward = centre.subtract(near.getPos()).normalize();
		ctx.assertTrue(near.getVelocity().normalize().dotProduct(toward) > 0.99 && Math.abs(near.getVelocity().length() - 1.5) < 1.0E-3,
				"it turns straight for the player at the same speed: " + near.getVelocity());
		FishingTests.disconnect(player);
		ctx.killAllEntities();
		ctx.complete();
	}

	/** The grimlock is undead now, so a vindicator goes for it (UndeadIllagerFeudMixin). */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 200)
	public void illagersHuntGrimlocks(TestContext ctx) {
		ctx.assertTrue(NewMobs.GRIMLOCK.isIn(EntityTypeTags.UNDEAD), "grimlocks are undead");
		GrimlockEntity grimlock = ctx.spawnEntity(NewMobs.GRIMLOCK, new BlockPos(8, 2, 14));
		((MobEntityAccessor) grimlock).eternalreturn$goalSelector().clear(goal -> true);
		VindicatorEntity vindicator = ctx.spawnEntity(EntityType.VINDICATOR, new BlockPos(8, 2, 6));
		ctx.runAtEveryTick(() -> {
			Lounging.stand(vindicator);
			Lounging.stand(grimlock);
		});
		ctx.runAtTick(190, () -> ctx.throwGameTestException("the vindicator never went for the grimlock: " + vindicator.getTarget()));
		ctx.runAtEveryTick(() -> {
			if (vindicator.getTarget() == grimlock) {
				ctx.killAllEntities();
				ctx.complete();
			}
		});
	}

	/** A brown bear goes for a zombie wearing a beehive (and not for one without). */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 200)
	public void bearsHuntBeehiveHeads(TestContext ctx) {
		ZombieEntity hived = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(5, 2, 14));
		hived.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.BEEHIVE));
		((MobEntityAccessor) hived).eternalreturn$goalSelector().clear(goal -> true);
		((MobEntityAccessor) hived).eternalreturn$targetSelector().clear(goal -> true);
		BearEntity bear = ctx.spawnEntity(ModEntities.BROWN_BEAR, new BlockPos(5, 2, 6));
		ZombieEntity bare = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(12, 2, 14));
		((MobEntityAccessor) bare).eternalreturn$goalSelector().clear(goal -> true);
		((MobEntityAccessor) bare).eternalreturn$targetSelector().clear(goal -> true);
		BearEntity other = ctx.spawnEntity(ModEntities.BROWN_BEAR, new BlockPos(12, 2, 6));
		ctx.assertTrue(BearEntity.wearsBeehive(hived) && !BearEntity.wearsBeehive(bare), "which wears a hive");
		ctx.runAtEveryTick(() -> {
			Lounging.stand(hived);
			Lounging.stand(bare);
			ctx.assertTrue(other.getTarget() != bare, "a bear leaves a zombie without a hive alone");
			if (bear.getTarget() == hived) {
				ctx.killAllEntities();
				ctx.complete();
			}
		});
		ctx.runAtTick(190, () -> ctx.throwGameTestException("the bear never went for the zombie with the beehive: " + bear.getTarget()));
	}
}
