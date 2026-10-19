package com.eternalreturn.gametest;

import com.eternalreturn.items.Gems;
import com.eternalreturn.logic.EnchantCaps;
import com.eternalreturn.mobs.NewMobs;
import com.eternalreturn.mobs.entity.BeholderEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

/** The beholder: a ghast's size, six times its health; lasers what it sees up close, spits fireballs otherwise; its drops. */
public class BeholderTests implements FabricGameTest {
	private static final String BATCH = "beholder";
	/** 16 by 40 blocks of stone floor, 14 high (a ghast-sized mob needs the room). */
	private static final String ARENA = "eternalreturn_test:arena";

	private static ZombieEntity target(TestContext ctx, BlockPos pos) {
		// A husk: it doesn't burn under the open sky, so only the beholder hurts it.
		ZombieEntity zombie = ctx.spawnEntity(EntityType.HUSK, pos);
		zombie.setAiDisabled(true);
		zombie.setInvulnerable(false);
		return zombie;
	}

	/** As big as a ghast, fire-proof, 60 health (a ghast's 10, six times over). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void sizeAndHealth(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BeholderEntity beholder = NewMobs.BEHOLDER.create(world);
		GhastEntity ghast = EntityType.GHAST.create(world);
		ctx.assertTrue(beholder.getMaxHealth() == 6.0F * ghast.getMaxHealth(), "six times a ghast's health: " + beholder.getMaxHealth());
		ctx.assertTrue(beholder.getWidth() == ghast.getWidth() && beholder.getHeight() == ghast.getHeight(), "a ghast's size");
		ctx.assertTrue(NewMobs.BEHOLDER.isFireImmune(), "fire-proof");
		ctx.assertTrue(beholder.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) > 0.0, "its beam has an attack behind it");
		ctx.complete();
	}

	/** Each kill: 5 to 8 rubies and one gold tool or armor piece, enchanted to its full capacity. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void drops(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BeholderEntity beholder = NewMobs.BEHOLDER.create(world);
		beholder.refreshPositionAndAngles(ctx.getAbsolutePos(new BlockPos(4, 2, 4)), 0.0F, 0.0F);
		LootTable table = world.getServer().getReloadableRegistries().getLootTable(NewMobs.BEHOLDER.getLootTableId());
		LootContextParameterSet params = new LootContextParameterSet.Builder(world).add(LootContextParameters.THIS_ENTITY, beholder)
				.add(LootContextParameters.ORIGIN, beholder.getPos()).add(LootContextParameters.DAMAGE_SOURCE, world.getDamageSources().generic())
				.build(LootContextTypes.ENTITY);
		List<String> gear = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			int rubies = 0;
			List<ItemStack> pieces = new ArrayList<>();
			for (ItemStack stack : table.generateLoot(params)) {
				if (stack.isOf(Gems.RUBY)) {
					rubies += stack.getCount();
				} else {
					pieces.add(stack);
				}
			}
			ctx.assertTrue(rubies >= 5 && rubies <= 8, "5 to 8 rubies, got " + rubies);
			ctx.assertTrue(pieces.size() == 1, "one piece of gear, got " + pieces);
			ItemStack piece = pieces.get(0);
			String id = Registries.ITEM.getId(piece.getItem()).getPath();
			ctx.assertTrue(id.startsWith("golden_"), "a gold piece, got " + id);
			ctx.assertTrue(EnchantCaps.remaining(piece) == 0, id + " enchanted to its full capacity: " + EnchantCaps.pointsUsed(piece) + " of "
					+ EnchantCaps.capFor(piece) + ", " + piece.getEnchantments());
			gear.add(id + " " + piece.getEnchantments().getEnchantmentEntries().stream()
					.map(e -> e.getKey().getIdAsString().replace("minecraft:", "") + " " + e.getIntValue()).toList());
		}
		com.eternalreturn.EternalReturn.LOGGER.info("[beholder] drops: {}", gear);
		ctx.complete();
	}

	/** It lasers a target it can see within 24 blocks; one behind a wall, or further off, it doesn't. */
	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true)
	public void choosesLaserOrFireball(TestContext ctx) {
		BeholderEntity beholder = ctx.spawnEntity(NewMobs.BEHOLDER, new BlockPos(8, 1, 4));
		beholder.setAiDisabled(true);
		ZombieEntity near = target(ctx, new BlockPos(8, 1, 12));
		ZombieEntity far = target(ctx, new BlockPos(8, 1, 37));
		ctx.assertTrue(beholder.lasers(near), "lasers a target it sees 8 blocks off");
		ctx.assertTrue(!beholder.lasers(far), "not one 33 blocks off");
		wall(ctx, 10);
		ctx.assertTrue(!beholder.lasers(near), "not one behind a wall");
		ctx.killAllEntities();
		ctx.complete();
	}

	/** In sight and in range: the beam locks on and, after its charge, hurts. */
	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true, tickLimit = 400)
	public void laserHurts(TestContext ctx) {
		BeholderEntity beholder = ctx.spawnEntity(NewMobs.BEHOLDER, new BlockPos(8, 1, 4));
		ZombieEntity zombie = target(ctx, new BlockPos(8, 1, 14));
		beholder.setTarget(zombie);
		float health = zombie.getHealth();
		boolean[] locked = {false};
		ctx.addInstantFinalTask(ctx::killAllEntities);
		ctx.runAtEveryTick(() -> locked[0] |= beholder.hasBeamTarget());
		ctx.runAtTick(BeholderEntity.WARMUP + 40, () -> {
			ctx.assertTrue(locked[0], "the beam locked on");
			ctx.assertTrue(zombie.getHealth() < health, "and hurt: " + zombie.getHealth() + " of " + health);
			ctx.assertTrue(fireballs(ctx, beholder).isEmpty(), "no fireballs at a target in sight");
			ctx.complete();
		});
	}

	/** Behind a wall: no beam, a fireball from its mouth instead. */
	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true, tickLimit = 400)
	public void fireballAtHiddenTarget(TestContext ctx) {
		BeholderEntity beholder = ctx.spawnEntity(NewMobs.BEHOLDER, new BlockPos(8, 1, 4));
		ZombieEntity zombie = target(ctx, new BlockPos(8, 1, 14));
		wall(ctx, 11);
		beholder.setTarget(zombie);
		boolean[] beam = {false};
		boolean[] fireball = {false};
		ctx.addInstantFinalTask(ctx::killAllEntities);
		ctx.runAtEveryTick(() -> {
			beam[0] |= beholder.hasBeamTarget();
			fireball[0] |= !fireballs(ctx, beholder).isEmpty();
		});
		ctx.runAtTick(80, () -> {
			ctx.assertTrue(fireball[0], "a fireball at the hidden target");
			ctx.assertTrue(!beam[0], "and no beam");
			ctx.complete();
		});
	}

	/** An obsidian wall across the arena at this z, as high as it is. */
	private static void wall(TestContext ctx, int z) {
		for (int x = 0; x < 16; x++) {
			for (int y = 1; y < 14; y++) {
				ctx.setBlockState(x, y, z, Blocks.OBSIDIAN);
			}
		}
	}

	private static List<FireballEntity> fireballs(TestContext ctx, BeholderEntity beholder) {
		return ctx.getWorld().getEntitiesByClass(FireballEntity.class, new Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(60), f -> f.getOwner() == beholder);
	}
}
