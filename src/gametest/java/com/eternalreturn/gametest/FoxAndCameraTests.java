package com.eternalreturn.gametest;

import com.eternalreturn.logic.EyesInside;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import java.util.Map;

public class FoxAndCameraTests implements FabricGameTest {
	/**
	 * A fox is white where its biome is in minecraft:spawns_snow_foxes. This mod's biome variants used to copy that tag
	 * from their base biome, so the warm redwood forest (from a snowy taiga) spawned white foxes and the snowy taiga
	 * peaks red ones. Now every one of this mod's biomes that spawns foxes is in the tag exactly where snow lies (below
	 * 0.15 with precipitation), as build_variants.py writes it.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "fox_colours")
	public void foxColoursFollowTheBiomesSnow(TestContext ctx) {
		Registry<Biome> biomes = ctx.getWorld().getRegistryManager().get(RegistryKeys.BIOME);
		int checked = 0;
		for (Map.Entry<net.minecraft.registry.RegistryKey<Biome>, Biome> entry : biomes.getEntrySet()) {
			Identifier id = entry.getKey().getValue();
			Biome biome = entry.getValue();
			if (!id.getNamespace().equals("eternalreturn")
					|| biome.getSpawnSettings().getSpawnEntries(SpawnGroup.CREATURE).getEntries().stream().noneMatch(e -> e.type == EntityType.FOX)) {
				continue;
			}
			RegistryEntry<Biome> holder = biomes.getEntry(entry.getKey()).orElseThrow();
			boolean snowy = biome.hasPrecipitation() && biome.getTemperature() < 0.15F;
			ctx.assertTrue(holder.isIn(BiomeTags.SPAWNS_SNOW_FOXES) == snowy,
					id + " (" + biome.getTemperature() + ") " + (snowy ? "is snowy but spawns red foxes" : "has no snow but spawns white foxes"));
			checked++;
		}
		if (FabricLoader.getInstance().isModLoaded("moderner_beta")) {
			ctx.assertTrue(checked >= 5, "the fox biomes are checked (" + checked + ")");
			RegistryEntry<Biome> redwoods = biomes.getEntry(Identifier.of("eternalreturn", "redwood_forest")).orElseThrow();
			ctx.assertFalse(redwoods.isIn(BiomeTags.SPAWNS_SNOW_FOXES), "the redwood forest spawns red foxes");
			RegistryEntry<Biome> peak = biomes.getEntry(Identifier.of("eternalreturn", "old_growth_spruce_taiga_peak")).orElseThrow();
			ctx.assertTrue(peak.isIn(BiomeTags.SPAWNS_SNOW_FOXES), "the snowy old growth spruce taiga peak spawns white foxes");
		}
		ctx.complete();
	}

	/**
	 * Nostalgic Tweaks' "Old Creative Crouching" lets a flying player crouch (a hitbox 1.5 tall) with their eyes at
	 * standing height (1.62): flying up under a block put the camera inside it. A crouching player's eyes now always
	 * sit inside their hitbox (EyesInside), and vanilla's sizes, and that mod's old sneaking eyes (1.41), are unchanged.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "eyes_inside")
	public void crouchingEyesStayInsideTheHitbox(TestContext ctx) {
		EntityDimensions oldCreative = EyesInside.fit(new EntityDimensions(0.6F, 1.5F, 1.62F, null, false));
		ctx.assertTrue(oldCreative.height() >= 1.62F + 0.05F, "eyes at 1.62 get a hitbox above them (" + oldCreative.height() + ")");
		ctx.assertTrue(Math.abs(oldCreative.height() - 1.8F) < 0.001F && oldCreative.eyeHeight() == 1.62F,
				"with the standing proportion: 1.8 tall, eyes still at 1.62 (" + oldCreative + ")");
		EntityDimensions oldSneaking = new EntityDimensions(0.6F, 1.5F, 1.41F, null, false);
		ctx.assertTrue(EyesInside.fit(oldSneaking) == oldSneaking, "old sneaking's eyes (1.41) keep the 1.5 hitbox");
		PlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 2, 1));
		EntityDimensions crouching = player.getDimensions(EntityPose.CROUCHING);
		ctx.assertTrue(Math.abs(crouching.height() - 1.5F) < 0.001F && Math.abs(crouching.eyeHeight() - 1.27F) < 0.001F,
				"vanilla's crouching size is unchanged (" + crouching + ")");
		ctx.complete();
	}
}
