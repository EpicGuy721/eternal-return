package com.eternalreturn.gametest;

import com.eternalreturn.mobs.NewMobs;
import com.eternalreturn.mobs.entity.AtropalEntity;
import com.eternalreturn.mobs.entity.GimpEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * The gimp and its atropal: they spawn together; the gimp (20 health, a zombie's punch) climbs walls and is freed
 * (neutral) when its atropal (10 health) dies; the atropal turns projectiles aside, keeps away and beams its target.
 * Their drops.
 */
public class GimpTests implements FabricGameTest {
	private static final String BATCH = "gimp";
	/** 16 by 40 blocks of stone floor, 14 high. */
	private static final String ARENA = "eternalreturn_test:arena";

	/** A gimp spawned as by its egg, with its atropal. */
	private static GimpEntity pair(TestContext ctx, BlockPos pos) {
		ServerWorld world = ctx.getWorld();
		GimpEntity gimp = ctx.spawnEntity(NewMobs.GIMP, pos);
		gimp.initialize(world, world.getLocalDifficulty(gimp.getBlockPos()), SpawnReason.SPAWN_EGG, null);
		return gimp;
	}

	private static ZombieEntity husk(TestContext ctx, BlockPos pos) {
		ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, pos);
		husk.setAiDisabled(true);
		return husk;
	}

	@GameTest(templateName = ARENA, batchId = BATCH)
	public void spawnsWithItsAtropal(TestContext ctx) {
		GimpEntity gimp = pair(ctx, new BlockPos(8, 1, 8));
		AtropalEntity atropal = gimp.getAtropal();
		ctx.assertTrue(atropal != null, "an atropal came with it");
		ctx.assertTrue(atropal.getGimp() == gimp, "bound to it");
		ctx.assertTrue(atropal.squaredDistanceTo(gimp) < 9.0, "just above it: " + atropal.distanceTo(gimp));
		ctx.assertTrue(gimp.getMaxHealth() == 20.0F && atropal.getMaxHealth() == 10.0F, "20 and 10 health: " + gimp.getMaxHealth() + ", " + atropal.getMaxHealth());
		ZombieEntity zombie = EntityType.ZOMBIE.create(ctx.getWorld());
		ctx.assertTrue(gimp.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) == zombie.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE),
				"a zombie's punch");
		ctx.assertFalse(gimp.isFreed(), "not free while its atropal lives");
		ctx.killAllEntities();
		ctx.complete();
	}

	@GameTest(templateName = ARENA, batchId = BATCH)
	public void freedWhenItsAtropalDies(TestContext ctx) {
		GimpEntity gimp = pair(ctx, new BlockPos(8, 1, 8));
		gimp.getAtropal().kill();
		ctx.assertTrue(gimp.isFreed(), "freed the moment its atropal died");
		ctx.killAllEntities();
		ctx.complete();
	}

	/** An arrow shot straight at it turns aside, and it isn't hurt. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 100)
	public void turnsProjectilesAside(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		AtropalEntity atropal = ctx.spawnEntity(NewMobs.ATROPAL, new BlockPos(8, 2, 12));
		atropal.setAiDisabled(true);
		Vec3d from = Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(8, 2, 6)));
		ArrowEntity arrow = new ArrowEntity(world, from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
		Vec3d at = atropal.getPos().add(0.0, atropal.getHeight() * 0.5, 0.0).subtract(from);
		arrow.setVelocity(at.x, at.y, at.z, 1.5F, 0.0F);
		world.spawnEntity(arrow);
		ctx.addInstantFinalTask(ctx::killAllEntities);
		ctx.runAtTick(40, () -> {
			ctx.assertTrue(atropal.getHealth() == atropal.getMaxHealth(), "unhurt: " + atropal.getHealth());
			ctx.assertTrue(arrow.isRemoved() || arrow.getZ() < atropal.getZ(), "the arrow turned back");
			ctx.complete();
		});
	}

	/** A target in sight 10 blocks off: the beam locks on and hits it. */
	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true, tickLimit = 300)
	public void beamsItsTarget(TestContext ctx) {
		AtropalEntity atropal = ctx.spawnEntity(NewMobs.ATROPAL, new BlockPos(8, 2, 6));
		ZombieEntity husk = husk(ctx, new BlockPos(8, 1, 16));
		float health = husk.getHealth();
		boolean[] locked = {false};
		ctx.addInstantFinalTask(ctx::killAllEntities);
		ctx.runAtEveryTick(() -> {
			atropal.setTarget(husk);
			locked[0] |= atropal.hasBeamTarget();
		});
		ctx.runAtTick(AtropalEntity.BEAM_WARMUP + 60, () -> {
			ctx.assertTrue(locked[0], "the beam locked on");
			ctx.assertTrue(husk.getHealth() < health, "and hit: " + husk.getHealth() + " of " + health);
			ctx.complete();
		});
	}

	/** Its target close by: it flies off from it. */
	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true, tickLimit = 200)
	public void keepsAway(TestContext ctx) {
		AtropalEntity atropal = ctx.spawnEntity(NewMobs.ATROPAL, new BlockPos(8, 2, 20));
		ZombieEntity husk = husk(ctx, new BlockPos(8, 1, 22));
		double start = atropal.distanceTo(husk);
		ctx.addInstantFinalTask(ctx::killAllEntities);
		ctx.runAtEveryTick(() -> atropal.setTarget(husk));
		ctx.runAtTick(100, () -> {
			ctx.assertTrue(atropal.distanceTo(husk) > AtropalEntity.FLEE_DISTANCE - 1.0, "it got away: " + start + " to " + atropal.distanceTo(husk));
			ctx.complete();
		});
	}

	/** A wall 5 high across the arena, its target on top: it climbs up to it. */
	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true, tickLimit = 400)
	public void climbsWalls(TestContext ctx) {
		for (int x = 0; x < 16; x++) {
			for (int y = 1; y <= 5; y++) {
				ctx.setBlockState(new BlockPos(x, y, 20), Blocks.STONE.getDefaultState());
			}
		}
		ZombieEntity husk = husk(ctx, new BlockPos(8, 6, 20));
		GimpEntity gimp = ctx.spawnEntity(NewMobs.GIMP, new BlockPos(8, 1, 16));
		double floor = gimp.getY();
		boolean[] climbed = {false};
		ctx.addInstantFinalTask(ctx::killAllEntities);
		ctx.runAtEveryTick(() -> {
			gimp.setTarget(husk);
			climbed[0] |= gimp.isClimbing();
		});
		ctx.waitAndRun(300, () -> {
			ctx.assertTrue(climbed[0], "it climbed");
			ctx.assertTrue(gimp.getY() >= floor + 4.0 || husk.getHealth() < husk.getMaxHealth(), "up the wall to its target: rose " + (gimp.getY() - floor));
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void drops(TestContext ctx) {
		List<ItemStack> gimp = loot(ctx, NewMobs.GIMP, 60);
		List<ItemStack> atropal = loot(ctx, NewMobs.ATROPAL, 60);
		ctx.assertTrue(has(gimp, Items.LEATHER) && has(gimp, Items.CHAIN), "the gimp: leather and chains");
		ctx.assertTrue(gimp.stream().allMatch(stack -> stack.isOf(Items.LEATHER) || stack.isOf(Items.CHAIN)), "and nothing else");
		ctx.assertTrue(has(atropal, Items.ROTTEN_FLESH) && atropal.stream().allMatch(stack -> stack.isOf(Items.ROTTEN_FLESH)), "the atropal: rotten flesh");
		ctx.complete();
	}

	private static boolean has(List<ItemStack> stacks, Item item) {
		return stacks.stream().anyMatch(stack -> stack.isOf(item));
	}

	private static List<ItemStack> loot(TestContext ctx, EntityType<?> type, int kills) {
		ServerWorld world = ctx.getWorld();
		var entity = type.create(world);
		entity.refreshPositionAndAngles(ctx.getAbsolutePos(new BlockPos(2, 1, 2)), 0.0F, 0.0F);
		LootTable table = world.getServer().getReloadableRegistries().getLootTable(type.getLootTableId());
		LootContextParameterSet params = new LootContextParameterSet.Builder(world).add(LootContextParameters.THIS_ENTITY, entity)
				.add(LootContextParameters.ORIGIN, entity.getPos()).add(LootContextParameters.DAMAGE_SOURCE, world.getDamageSources().generic())
				.build(LootContextTypes.ENTITY);
		List<ItemStack> all = new java.util.ArrayList<>();
		for (int i = 0; i < kills; i++) {
			table.generateLoot(params).stream().filter(stack -> !stack.isEmpty()).forEach(all::add);
		}
		return all;
	}

	/** A player nearby: the gimp whose atropal lives goes for them, the one whose atropal is dead leaves them be. */
	@GameTest(templateName = ARENA, batchId = "gimp_neutral", tickLimit = 200)
	public void neutralOnceFreed(TestContext ctx) {
		GimpEntity bound = pair(ctx, new BlockPos(4, 1, 12));
		GimpEntity freed = pair(ctx, new BlockPos(12, 1, 12));
		freed.getAtropal().kill();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 1, 16));
		ctx.addInstantFinalTask(() -> {
			FishingTests.disconnect(player);
			ctx.killAllEntities();
		});
		ctx.runAtTick(100, () -> {
			ctx.assertTrue(bound.getTarget() == player, "the bound gimp hunts the player");
			ctx.assertTrue(freed.getTarget() == null, "the freed one doesn't: " + freed.getTarget());
			ctx.complete();
		});
	}
}
