package com.eternalreturn.gametest;

import com.eternalreturn.items.LaserBlaster;
import com.eternalreturn.mobs.NewMobs;
import com.eternalreturn.mobs.entity.SandownEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

/**
 * The Sandown: 40 health, a laser blaster in hand (dropped one time in 100), the End only; neutral until hurt, then he
 * shoots; he teleports away from projectiles, falling blocks and anyone too close, and pulls a target out of his reach
 * in front of him. The blaster in a player's hands.
 */
public class SandownTests implements FabricGameTest {
	private static final String BATCH = "sandown";
	private static final String ARENA = "eternalreturn_test:arena";

	private static SandownEntity sandown(TestContext ctx, BlockPos pos) {
		ServerWorld world = ctx.getWorld();
		SandownEntity sandown = ctx.spawnEntity(NewMobs.SANDOWN, pos);
		sandown.initialize(world, world.getLocalDifficulty(sandown.getBlockPos()), SpawnReason.SPAWN_EGG, null);
		return sandown;
	}

	/** A husk (it doesn't burn under the open sky) that stands still. */
	private static ZombieEntity target(TestContext ctx, BlockPos pos) {
		ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, pos);
		husk.setAiDisabled(true);
		return husk;
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void healthBlasterAndEndSpawns(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		SandownEntity sandown = sandown(ctx, new BlockPos(2, 1, 2));
		ctx.assertTrue(sandown.getMaxHealth() == 40.0F, "40 health: " + sandown.getMaxHealth());
		ctx.assertTrue(sandown.getEquippedStack(EquipmentSlot.MAINHAND).isOf(LaserBlaster.BLASTER), "a laser blaster in hand");
		NbtCompound nbt = new NbtCompound();
		sandown.writeNbt(nbt);
		float drop = nbt.getList("HandDropChances", NbtElement.FLOAT_TYPE).getFloat(0);
		ctx.assertTrue(Math.abs(drop - 0.01F) < 1.0E-6F, "dropped one time in 100: " + drop);
		var biomes = world.getRegistryManager().get(RegistryKeys.BIOME);
		Biome highlands = biomes.get(BiomeKeys.END_HIGHLANDS);
		Biome plains = biomes.get(BiomeKeys.PLAINS);
		ctx.assertTrue(highlands.getSpawnSettings().getSpawnEntries(SpawnGroup.MONSTER).getEntries().stream().anyMatch(e -> e.type == NewMobs.SANDOWN),
				"spawns in the End");
		ctx.assertFalse(plains.getSpawnSettings().getSpawnEntries(SpawnGroup.MONSTER).getEntries().stream().anyMatch(e -> e.type == NewMobs.SANDOWN),
				"and not in the overworld");
		sandown.discard();
		ctx.complete();
	}

	/** A player in sight is let be; hurt by them, he aims at them and shoots. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 300)
	public void neutralUntilHurt(TestContext ctx) {
		SandownEntity sandown = sandown(ctx, new BlockPos(8, 1, 6));
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 1, 22));
		float health = player.getHealth();
		boolean[] aimed = {false};
		ctx.runAtEveryTick(() -> aimed[0] |= sandown.isAiming());
		ctx.runAtTick(60, () -> {
			try {
				ctx.assertTrue(sandown.getTarget() == null, "a player he hasn't been hurt by is let be: " + sandown.getTarget());
			} catch (RuntimeException e) {
				FishingTests.disconnect(player);
				throw e;
			}
			sandown.damage(ctx.getWorld().getDamageSources().playerAttack(player), 1.0F);
		});
		ctx.runAtTick(220, () -> {
			try {
				ctx.assertTrue(sandown.getTarget() == player, "hurt, he turns on the player: " + sandown.getTarget());
				ctx.assertTrue(aimed[0], "aims his blaster");
				ctx.assertTrue(player.getHealth() < health, "and shoots: " + player.getHealth() + " of " + health);
			} finally {
				FishingTests.disconnect(player);
				sandown.discard();
			}
			ctx.complete();
		});
	}

	/** An arrow coming at him finds him gone: he's teleported off, unhurt. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 100)
	public void dodgesArrows(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		SandownEntity sandown = sandown(ctx, new BlockPos(8, 1, 10));
		Vec3d from = sandown.getPos();
		Vec3d start = Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(8, 3, 22)));
		ArrowEntity arrow = new ArrowEntity(world, start.x, start.y, start.z, new ItemStack(Items.ARROW), null);
		Vec3d aim = new Vec3d(sandown.getX(), sandown.getBodyY(0.5), sandown.getZ()).subtract(start);
		arrow.setVelocity(aim.x, aim.y, aim.z, 2.5F, 0.0F);
		world.spawnEntity(arrow);
		ctx.runAtTick(40, () -> {
			ctx.assertTrue(sandown.getPos().distanceTo(from) > 5.0, "he teleported away: " + sandown.getPos().distanceTo(from) + " blocks");
			ctx.assertTrue(sandown.getHealth() == sandown.getMaxHealth(), "unhurt: " + sandown.getHealth());
			sandown.discard();
			arrow.discard();
			ctx.complete();
		});
	}

	/** An anvil falling on him: off he goes, unhurt. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 100)
	public void dodgesFallingBlocks(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		SandownEntity sandown = sandown(ctx, new BlockPos(8, 1, 10));
		Vec3d from = sandown.getPos();
		BlockPos above = ctx.getAbsolutePos(new BlockPos(8, 11, 10));
		world.setBlockState(above, Blocks.ANVIL.getDefaultState());
		FallingBlockEntity anvil = FallingBlockEntity.spawnFromBlock(world, above, Blocks.ANVIL.getDefaultState());
		ctx.runAtTick(40, () -> {
			ctx.assertTrue(sandown.getPos().distanceTo(from) > 4.0, "he teleported away: " + sandown.getPos().distanceTo(from) + " blocks");
			ctx.assertTrue(sandown.getHealth() == sandown.getMaxHealth(), "unhurt: " + sandown.getHealth());
			sandown.discard();
			anvil.discard();
			world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, new Box(above).expand(16.0), e -> true).forEach(net.minecraft.entity.Entity::discard);
			ctx.complete();
		});
	}

	/**
	 * A player who comes up close sees him teleport off; caught up with again straight away, he stays put (his teleport
	 * is cooling down), and once it's ready he's off again.
	 */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 300)
	public void teleportsAwayFromCloseCallers(TestContext ctx) {
		SandownEntity sandown = sandown(ctx, new BlockPos(8, 1, 12));
		Vec3d from = sandown.getPos();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 1, 14));
		Vec3d[] caught = new Vec3d[1];
		ctx.runAtTick(20, () -> {
			try {
				ctx.assertTrue(sandown.getPos().distanceTo(from) > 5.0, "he teleported away: " + sandown.getPos().distanceTo(from) + " blocks");
				ctx.assertTrue(sandown.distanceTo(player) > SandownEntity.TOO_CLOSE + 1.0, "out of the player's reach: " + sandown.distanceTo(player));
			} catch (RuntimeException e) {
				FishingTests.disconnect(player);
				sandown.discard();
				throw e;
			}
			player.teleport(ctx.getWorld(), sandown.getX() + 2.0, sandown.getY(), sandown.getZ(), 0.0F, 0.0F);
			caught[0] = sandown.getPos();
		});
		ctx.runAtTick(40, () -> {
			try {
				ctx.assertTrue(sandown.getPos().distanceTo(caught[0]) < 3.0, "caught up with during his cooldown, he stays: "
						+ sandown.getPos().distanceTo(caught[0]) + " blocks");
			} catch (RuntimeException e) {
				FishingTests.disconnect(player);
				sandown.discard();
				throw e;
			}
		});
		ctx.runAtTick(20 + SandownEntity.TELEPORT_COOLDOWN + 20, () -> {
			try {
				ctx.assertTrue(sandown.getPos().distanceTo(caught[0]) > 5.0, "and once it's up he's off again: "
						+ sandown.getPos().distanceTo(caught[0]) + " blocks");
			} finally {
				FishingTests.disconnect(player);
				sandown.discard();
			}
			ctx.complete();
		});
	}

	/** A target behind a wall, out of his reach: teleported in front of him, where he can see it, and shot. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 300)
	public void pullsTargetsInFrontOfHim(TestContext ctx) {
		for (int x = 0; x < 16; x++) {
			for (int y = 1; y < 13; y++) {
				ctx.setBlockState(new BlockPos(x, y, 20), Blocks.STONE);
			}
		}
		SandownEntity sandown = sandown(ctx, new BlockPos(8, 1, 6));
		ZombieEntity husk = target(ctx, new BlockPos(8, 1, 30));
		sandown.setTarget(husk);
		float health = husk.getHealth();
		boolean[] pulled = {false};
		ctx.runAtEveryTick(() -> pulled[0] |= husk.getZ() < ctx.getAbsolutePos(new BlockPos(8, 1, 20)).getZ() && sandown.canSee(husk));
		ctx.runAtTick(250, () -> {
			ctx.assertTrue(pulled[0], "the target was brought round the wall, in his sight");
			ctx.assertTrue(husk.getHealth() < health, "and shot: " + husk.getHealth() + " of " + health);
			sandown.discard();
			husk.discard();
			ctx.complete();
		});
	}

	/** In a player's hands: a shot, a bolt, a cooldown, and a little wear. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void blasterInPlayersHands(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(2, 1, 2));
		try {
			ItemStack blaster = new ItemStack(LaserBlaster.BLASTER);
			player.setStackInHand(Hand.MAIN_HAND, blaster);
			blaster.use(world, player, Hand.MAIN_HAND);
			ctx.assertTrue(blaster.getDamage() == 1, "worn by a shot: " + blaster.getDamage());
			ctx.assertTrue(player.getItemCooldownManager().isCoolingDown(LaserBlaster.BLASTER), "cooling down");
			var bolts = world.getEntitiesByClass(LaserBlaster.Bolt.class, player.getBoundingBox().expand(4.0), bolt -> bolt.getOwner() == player);
			ctx.assertTrue(bolts.size() == 1, "a bolt fired: " + bolts.size());
			bolts.forEach(net.minecraft.entity.Entity::discard);
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}
}
