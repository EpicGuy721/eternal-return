package com.eternalreturn.gametest;

import com.eternalreturn.items.Firearms;
import com.eternalreturn.mobs.NewMobs;
import com.eternalreturn.mobs.entity.SoldierEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/** The soldier: 20 health, a rifle in hand, bullets that hurt and wear armor, a slow reload; 8 to 16 bullets when killed. */
public class SoldierTests implements FabricGameTest {
	private static final String BATCH = "soldier";
	private static final String ARENA = "eternalreturn_test:arena";

	/** A husk (it doesn't burn under the open sky) that stands still. */
	private static ZombieEntity target(TestContext ctx, BlockPos pos) {
		ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, pos);
		husk.setAiDisabled(true);
		return husk;
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void healthAndRifle(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		SoldierEntity soldier = ctx.spawnEntity(NewMobs.SOLDIER, new BlockPos(2, 1, 2));
		soldier.initialize(world, world.getLocalDifficulty(soldier.getBlockPos()), SpawnReason.SPAWN_EGG, null);
		ctx.assertTrue(soldier.getMaxHealth() == 20.0F, "20 health: " + soldier.getMaxHealth());
		ctx.assertTrue(soldier.getEquippedStack(EquipmentSlot.MAINHAND).isOf(Firearms.RIFLE), "a rifle in hand");
		ctx.killAllEntities();
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void bulletsDropped(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		SoldierEntity soldier = NewMobs.SOLDIER.create(world);
		soldier.refreshPositionAndAngles(ctx.getAbsolutePos(new BlockPos(2, 1, 2)), 0.0F, 0.0F);
		LootTable table = world.getServer().getReloadableRegistries().getLootTable(NewMobs.SOLDIER.getLootTableId());
		LootContextParameterSet params = new LootContextParameterSet.Builder(world).add(LootContextParameters.THIS_ENTITY, soldier)
				.add(LootContextParameters.ORIGIN, soldier.getPos()).add(LootContextParameters.DAMAGE_SOURCE, world.getDamageSources().generic())
				.build(LootContextTypes.ENTITY);
		for (int i = 0; i < 30; i++) {
			int bullets = table.generateLoot(params).stream().filter(stack -> stack.isOf(Firearms.BULLET)).mapToInt(ItemStack::getCount).sum();
			ctx.assertTrue(bullets >= 8 && bullets <= 16, "8 to 16 bullets, got " + bullets);
		}
		ctx.complete();
	}

	/** A bullet: its damage (less what armor takes), and ARMOR_WEAR more wear on each armor piece than a hit would do. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void bulletHurtsAndWearsArmor(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		SoldierEntity soldier = ctx.spawnEntity(NewMobs.SOLDIER, new BlockPos(1, 1, 1));
		ZombieEntity bare = target(ctx, new BlockPos(5, 1, 1));
		ZombieEntity armored = target(ctx, new BlockPos(5, 1, 5));
		// Leather: no inherent Unbreaking to skip some of the wear.
		armored.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		armored.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
		float bareBefore = bare.getHealth();
		new Firearms.Bullet(world, soldier).strike(bare);
		float bareLost = bareBefore - bare.getHealth();
		new Firearms.Bullet(world, soldier).strike(armored);
		int helmetWear = armored.getEquippedStack(EquipmentSlot.HEAD).getDamage();
		int chestWear = armored.getEquippedStack(EquipmentSlot.CHEST).getDamage();
		ctx.assertTrue(bareLost >= 8.0F, "a bullet hits hard: " + bareLost);
		ctx.assertTrue(helmetWear >= Firearms.ARMOR_WEAR && chestWear >= Firearms.ARMOR_WEAR, "and wears armor: helmet " + helmetWear + ", chestplate " + chestWear);
		ctx.killAllEntities();
		ctx.complete();
	}

	/** In the open, at a target 12 blocks off: he shoots it, and it's hurt. */
	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true, tickLimit = 400)
	public void shootsTarget(TestContext ctx) {
		SoldierEntity soldier = ctx.spawnEntity(NewMobs.SOLDIER, new BlockPos(8, 1, 4));
		soldier.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Firearms.RIFLE));
		ZombieEntity husk = target(ctx, new BlockPos(8, 1, 16));
		soldier.setTarget(husk);
		float health = husk.getHealth();
		boolean[] aimed = {false};
		ctx.addInstantFinalTask(ctx::killAllEntities);
		ctx.runAtEveryTick(() -> aimed[0] |= soldier.isAiming());
		ctx.runAtTick(SoldierEntity.RELOAD + 80, () -> {
			ctx.assertTrue(aimed[0], "he raised his rifle");
			ctx.assertTrue(husk.getHealth() < health, "and shot the target: " + husk.getHealth() + " of " + health);
			ctx.complete();
		});
	}
}
