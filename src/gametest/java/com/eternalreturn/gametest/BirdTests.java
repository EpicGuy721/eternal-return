package com.eternalreturn.gametest;

import com.eternalreturn.birds.BirdEntity;
import com.eternalreturn.birds.BirdTable;
import com.eternalreturn.birds.BirdVariant;
import com.eternalreturn.birds.Birds;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.HashSet;
import java.util.Set;

/** Birds (stage 1): the table, the variant number, the presets' share of new birds, the entity's look, size and name. */
public class BirdTests implements FabricGameTest {
	private static final String BATCH = "birds";

	/** Every preset packs to a variant that unpacks to the same parts, and no two presets look the same. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void presetsPackAndDiffer(TestContext ctx) {
		Set<Integer> seen = new HashSet<>();
		for (BirdTable.Preset preset : BirdTable.PRESETS) {
			int v = preset.variant();
			int again = BirdVariant.pack(BirdVariant.shape(v), BirdVariant.base(v), BirdVariant.pattern(v, 0), BirdVariant.colour(v, 0),
					BirdVariant.pattern(v, 1), BirdVariant.colour(v, 1));
			ctx.assertTrue(again == v, preset.id() + " packs and unpacks to itself");
			ctx.assertTrue(seen.add(v), preset.id() + " looks like another preset");
			ctx.assertTrue(BirdTable.preset(v) != null, preset.id() + " is found by its variant");
		}
		ctx.assertTrue(BirdTable.PRESETS.size() >= 20, "the presets are loaded: " + BirdTable.PRESETS.size());
		ctx.complete();
	}

	/** About 90 percent of new birds are presets; the rest are valid random birds. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void newBirdsAreMostlyPresets(TestContext ctx) {
		Random random = Random.create(42);
		int presets = 0;
		int total = 4000;
		for (int i = 0; i < total; i++) {
			int v = BirdVariant.random(random);
			if (BirdTable.preset(v) != null) {
				presets++;
			}
			ctx.assertTrue(BirdVariant.shape(v) < BirdTable.SHAPES.size() && BirdVariant.base(v) < BirdTable.COLOURS.size(), "a valid bird: " + v);
			for (int layer = 0; layer < 2; layer++) {
				ctx.assertTrue(BirdVariant.pattern(v, layer) <= BirdTable.PATTERNS.size() && BirdVariant.colour(v, layer) < BirdTable.COLOURS.size(),
						"a valid pattern: " + v);
			}
		}
		double share = presets / (double) total;
		ctx.assertTrue(share > 0.86 && share < 0.94, "about 90 percent presets, got " + share);
		ctx.complete();
	}

	/** A bird keeps its look through saving, is sized by its shape (no bigger than a small slime), and is named after its preset. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void birdLookSizeAndName(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BirdEntity bird = ctx.spawnEntity(Birds.BIRD, new BlockPos(2, 1, 2));
		bird.initialize(world, world.getLocalDifficulty(bird.getBlockPos()), SpawnReason.SPAWN_EGG, null);
		for (BirdTable.Preset preset : BirdTable.PRESETS) {
			bird.setVariant(preset.variant());
			NbtCompound nbt = new NbtCompound();
			bird.writeCustomDataToNbt(nbt);
			BirdEntity copy = Birds.BIRD.create(world);
			copy.readCustomDataFromNbt(nbt);
			ctx.assertTrue(copy.getVariant() == preset.variant(), preset.id() + " keeps its look when saved");
			BirdTable.Shape shape = bird.getShape();
			ctx.assertTrue(Math.abs(bird.getWidth() - shape.width()) < 1e-4 && Math.abs(bird.getHeight() - shape.height()) < 1e-4,
					preset.id() + " is sized by its shape: " + bird.getWidth() + " x " + bird.getHeight());
			ctx.assertTrue(bird.getWidth() <= 0.6F && bird.getHeight() <= 0.6F, preset.id() + " is no bigger than a small slime");
			ctx.assertTrue(bird.getName().getString().equals(net.minecraft.text.Text.translatable("entity.eternalreturn.bird." + preset.id()).getString()),
					preset.id() + " is named after its preset: " + bird.getName().getString());
		}
		ctx.killAllEntities();
		ctx.complete();
	}

	private static final String ARENA = "eternalreturn_test:arena";

	/** A survival player 4 blocks off: the bird flies off, ending further away; one in creative it lets come close. */
	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true, tickLimit = 300)
	public void fliesOffFromPlayers(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BirdEntity wary = ctx.spawnEntity(Birds.BIRD, new BlockPos(4, 1, 20));
		wary.setVariant(BirdTable.PRESETS.get(0).variant());
		BirdEntity calm = ctx.spawnEntity(Birds.BIRD, new BlockPos(12, 1, 20));
		calm.setVariant(BirdTable.PRESETS.get(0).variant());
		net.minecraft.server.network.ServerPlayerEntity survival = FishingTests.survivalPlayer(ctx, new BlockPos(4, 1, 16));
		net.minecraft.server.network.ServerPlayerEntity creative = FishingTests.survivalPlayer(ctx, new BlockPos(12, 1, 16));
		creative.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
		double wary0 = wary.distanceTo(survival);
		double calm0 = calm.distanceTo(creative);
		ctx.addInstantFinalTask(() -> {
			FishingTests.disconnect(survival);
			FishingTests.disconnect(creative);
			ctx.killAllEntities();
		});
		ctx.runAtTick(120, () -> {
			ctx.assertTrue(wary.distanceTo(survival) > wary0 + 3.0, "it flew off: " + wary0 + " -> " + wary.distanceTo(survival));
			ctx.assertTrue(calm.distanceTo(creative) < calm0 + 6.0, "it lets a creative player near: " + calm0 + " -> " + calm.distanceTo(creative));
			ctx.complete();
		});
	}

	/** Dropped from 10 blocks up, it flutters down (slower than a fall) and lands unhurt. */
	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true, tickLimit = 300)
	public void fluttersDown(TestContext ctx) {
		BirdEntity bird = ctx.spawnEntity(Birds.BIRD, new BlockPos(8, 11, 20));
		bird.setVariant(BirdTable.PRESETS.get(0).variant());
		bird.setAiDisabled(true);
		double top = bird.getY();
		float health = bird.getHealth();
		ctx.addInstantFinalTask(ctx::killAllEntities);
		ctx.runAtTick(10, () -> ctx.assertTrue(top - bird.getY() < 2.5, "it flutters (a fall would drop about 3 blocks in 10 ticks): " + (top - bird.getY())));
		ctx.runAtTick(200, () -> {
			ctx.assertTrue(bird.isOnGround(), "it landed");
			ctx.assertTrue(bird.getHealth() == health, "unhurt: " + bird.getHealth());
			ctx.complete();
		});
	}

	private static BirdTable.Preset preset(String id) {
		return BirdTable.PRESETS.stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
	}

	/**
	 * A flock of songbirds startled by a player bursts up together (the alarm spreads), flies as a flock (close, never
	 * on top of each other) at a songbird's speed, while an owl nearby (solo) stays put.
	 */
	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true, tickLimit = 300)
	public void flocksTakeOffTogether(TestContext ctx) {
		java.util.List<BirdEntity> flock = new java.util.ArrayList<>();
		for (int i = 0; i < 6; i++) {
			BirdEntity bird = ctx.spawnEntity(Birds.BIRD, new BlockPos(6 + i % 3, 1, 20 + i / 3 * 2));
			bird.setVariant(preset("house_sparrow").variant());
			flock.add(bird);
		}
		BirdEntity owl = ctx.spawnEntity(Birds.BIRD, new BlockPos(11, 1, 21));
		owl.setVariant(preset("barn_owl").variant());
		net.minecraft.server.network.ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(7, 1, 16));
		double[] fastest = {0.0};
		ctx.addInstantFinalTask(() -> {
			FishingTests.disconnect(player);
			ctx.killAllEntities();
		});
		ctx.runAtEveryTick(() -> flock.forEach(bird -> fastest[0] = Math.max(fastest[0], bird.getVelocity().length())));
		ctx.runAtTick(30, () -> {
			long up = flock.stream().filter(bird -> bird.isFlocking() || !bird.isOnGround()).count();
			ctx.assertTrue(up >= 5, "the flock took off together: " + up + " of 6");
			ctx.assertTrue(!owl.isFlocking(), "the owl (solo) didn't join");
		});
		ctx.runAtTick(70, () -> {
			net.minecraft.util.math.Vec3d centre = flock.stream().map(BirdEntity::getPos).reduce(net.minecraft.util.math.Vec3d.ZERO, net.minecraft.util.math.Vec3d::add)
					.multiply(1.0 / flock.size());
			double spread = flock.stream().mapToDouble(bird -> bird.getPos().distanceTo(centre)).max().orElse(0);
			double closest = Double.MAX_VALUE;
			for (int a = 0; a < flock.size(); a++) {
				for (int b = a + 1; b < flock.size(); b++) {
					closest = Math.min(closest, flock.get(a).distanceTo(flock.get(b)));
				}
			}
			ctx.assertTrue(spread < 10.0, "the flock holds together: " + spread);
			ctx.assertTrue(closest > 0.3, "no two birds on top of each other: " + closest);
			double songbird = BirdTable.SHAPES.get(0).flightSpeed();
			ctx.assertTrue(fastest[0] > 0.8 * songbird, "they fly at a songbird's speed (" + songbird + "): " + fastest[0]);
			ctx.complete();
		});
	}

	/** In a snowy biome, wild birds are cold-habitat presets (about 90 percent) or random birds of those presets' shapes. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void wildBirdsFitTheirBiome(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		var snowy = world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.BIOME).getEntry(net.minecraft.world.biome.BiomeKeys.SNOWY_PLAINS).orElseThrow();
		java.util.Set<Integer> coldShapes = new java.util.HashSet<>();
		java.util.Set<Integer> coldPresets = new java.util.HashSet<>();
		for (BirdTable.Preset preset : BirdTable.PRESETS) {
			if (preset.habitats().stream().anyMatch(snowy::isIn)) {
				coldPresets.add(preset.variant());
				coldShapes.add(BirdVariant.shape(preset.variant()));
			}
		}
		ctx.assertTrue(coldPresets.contains(preset("snowy_owl").variant()) && !coldPresets.contains(preset("flamingo").variant()),
				"snowy owls live in the snow, flamingos don't");
		Random random = Random.create(7);
		int presets = 0;
		for (int i = 0; i < 2000; i++) {
			int v = BirdVariant.forBiome(snowy, random);
			BirdTable.Preset preset = BirdTable.preset(v);
			if (preset != null && coldPresets.contains(v)) {
				presets++;
			} else {
				ctx.assertTrue(coldShapes.contains(BirdVariant.shape(v)), "a random bird of a cold shape: " + BirdVariant.shapeOf(v).id());
			}
		}
		ctx.assertTrue(presets > 1700 && presets < 1880, "about 90 percent cold presets: " + presets);
		ctx.complete();
	}

	/** A wild sparrow brings flockmates of its kind (2 to 5); an owl comes alone. */
	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true)
	public void wildFlocksComeTogether(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BirdEntity sparrow = ctx.spawnEntity(Birds.BIRD, new BlockPos(8, 1, 8));
		sparrow.setVariant(preset("house_sparrow").variant());
		int mates = Birds.spawnFlockmates(world, sparrow);
		long alike = world.getEntitiesByClass(BirdEntity.class, sparrow.getBoundingBox().expand(5.0), b -> b != sparrow && b.getVariant() == sparrow.getVariant()).size();
		ctx.assertTrue(mates >= 1 && mates <= 5 && alike == mates, "a flock of its kind: " + mates + " spawned, " + alike + " nearby");
		BirdEntity owl = ctx.spawnEntity(Birds.BIRD, new BlockPos(8, 1, 30));
		owl.setVariant(preset("barn_owl").variant());
		ctx.assertTrue(Birds.spawnFlockmates(world, owl) == 0, "an owl comes alone");
		ctx.killAllEntities();
		ctx.complete();
	}

	/** New land is generated with its birds (spawned while the chunks generate) without trouble. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 400)
	public void newLandGetsBirds(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN).add(4000, 0, 4000);
		for (int dx = 0; dx < 4; dx++) {
			for (int dz = 0; dz < 4; dz++) {
				world.getChunk((origin.getX() >> 4) + dx, (origin.getZ() >> 4) + dz, net.minecraft.world.chunk.ChunkStatus.FULL, true);
			}
		}
		int birds = world.getEntitiesByClass(BirdEntity.class, new net.minecraft.util.math.Box(origin).expand(80, 400, 80), b -> true).size();
		com.eternalreturn.EternalReturn.LOGGER.info("[birds] birds on 16 new chunks: {}", birds);
		ctx.complete();
	}

	/** Seeds tame a songbird (sooner or later) but not an owl; the owner sits it down and stands it up; a pet doesn't fly off. */
	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true, tickLimit = 200)
	public void tamingSongbirds(TestContext ctx) {
		BirdEntity robin = ctx.spawnEntity(Birds.BIRD, new BlockPos(8, 1, 10));
		robin.setVariant(preset("american_robin").variant());
		robin.setAiDisabled(true);
		BirdEntity owl = ctx.spawnEntity(Birds.BIRD, new BlockPos(8, 1, 20));
		owl.setVariant(preset("barn_owl").variant());
		owl.setAiDisabled(true);
		net.minecraft.server.network.ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 1, 13));
		ctx.addInstantFinalTask(() -> {
			FishingTests.disconnect(player);
			ctx.killAllEntities();
		});
		// A second to land first: a bird in the air won't sit.
		ctx.runAtTick(20, () -> {
			for (int i = 0; i < 60 && !robin.isTamed(); i++) {
				player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new net.minecraft.item.ItemStack(net.minecraft.item.Items.WHEAT_SEEDS));
				robin.interactMob(player, net.minecraft.util.Hand.MAIN_HAND);
			}
			ctx.assertTrue(robin.isTamed() && robin.isOwner(player), "seeds tamed the robin");
			player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new net.minecraft.item.ItemStack(net.minecraft.item.Items.WHEAT_SEEDS, 5));
			owl.interactMob(player, net.minecraft.util.Hand.MAIN_HAND);
			ctx.assertTrue(!owl.isTamed() && player.getMainHandStack().getCount() == 5, "an owl won't take seeds");
			player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, net.minecraft.item.ItemStack.EMPTY);
			robin.interactMob(player, net.minecraft.util.Hand.MAIN_HAND);
			ctx.assertTrue(robin.isSitting(), "an empty hand sits it down");
			robin.interactMob(player, net.minecraft.util.Hand.MAIN_HAND);
			ctx.assertTrue(!robin.isSitting(), "and stands it up");
			robin.setAiDisabled(false);
		});
		ctx.runAtTick(80, () -> {
			ctx.assertTrue(!robin.isFlocking() && robin.distanceTo(player) < 8.0, "a pet stays near its owner instead of flying off: " + robin.distanceTo(player));
			ctx.complete();
		});
	}

	/** Birds drop 0 to 2 feathers. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void feathers(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BirdEntity bird = Birds.BIRD.create(world);
		bird.refreshPositionAndAngles(ctx.getAbsolutePos(new BlockPos(2, 1, 2)), 0.0F, 0.0F);
		var table = world.getServer().getReloadableRegistries().getLootTable(Birds.BIRD.getLootTableId());
		var params = new net.minecraft.loot.context.LootContextParameterSet.Builder(world).add(net.minecraft.loot.context.LootContextParameters.THIS_ENTITY, bird)
				.add(net.minecraft.loot.context.LootContextParameters.ORIGIN, bird.getPos())
				.add(net.minecraft.loot.context.LootContextParameters.DAMAGE_SOURCE, world.getDamageSources().generic())
				.build(net.minecraft.loot.context.LootContextTypes.ENTITY);
		int most = 0;
		for (int i = 0; i < 40; i++) {
			int feathers = table.generateLoot(params).stream().filter(stack -> stack.isOf(net.minecraft.item.Items.FEATHER)).mapToInt(net.minecraft.item.ItemStack::getCount).sum();
			ctx.assertTrue(feathers <= 2, "at most 2 feathers: " + feathers);
			most = Math.max(most, feathers);
		}
		ctx.assertTrue(most >= 1, "feathers drop");
		ctx.complete();
	}
}
