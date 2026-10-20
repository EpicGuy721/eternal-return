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
}
