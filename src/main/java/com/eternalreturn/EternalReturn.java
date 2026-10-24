package com.eternalreturn;

import com.eternalreturn.blocks.CutBlocks;
import com.eternalreturn.blocks.Frames;
import com.eternalreturn.items.PhilosophersStone;
import com.eternalreturn.mobs.WitherSkeletons;
import com.eternalreturn.blocks.Redstone;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.fishing.FishingItems;
import com.eternalreturn.fishing.FishingLoot;
import com.eternalreturn.fishing.Slayer;
import com.eternalreturn.fishing.Thriftiness;
import com.eternalreturn.mobs.ModEntities;
import com.eternalreturn.mobs.Blazes;
import com.eternalreturn.mobs.OceanSpawns;
import com.eternalreturn.mobs.Slimes;
import com.eternalreturn.scrolls.ScrollLoot;
import com.eternalreturn.scrolls.Scrolls;
import com.eternalreturn.villager.VillagerRegions;
import com.eternalreturn.worldgen.WorldgenFeatures;
import com.eternalreturn.worldgen.ores.OreVariants;
import com.eternalreturn.worldgen.stone.BiomeStoneCondition;
import com.eternalreturn.worldgen.stone.HardenedBlocks;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EternalReturn implements ModInitializer {
	public static final String MOD_ID = "eternalreturn";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		EternalReturnConfig.load();
		ModEntities.register();
		com.eternalreturn.items.Gems.register();
		com.eternalreturn.worldgen.ores.OreBonuses.register();
		OceanSpawns.register();
		FishingItems.register();
		FishingLoot.register();
		Thriftiness.register();
		Slayer.register();
		CutBlocks.register();
		Redstone.register();
		Frames.register();
		PhilosophersStone.register();
		WitherSkeletons.register();
		Scrolls.register();
		ScrollLoot.register();
		Slimes.register();
		Blazes.register();
		VillagerRegions.register();
		com.eternalreturn.survival.Meals.register();
		com.eternalreturn.logic.OldCombat.register();
		com.eternalreturn.items.Potions2.register();
		com.eternalreturn.items.DiscCopyRecipe.register();
		com.eternalreturn.mobs.SpawnTweaks.register();
		com.eternalreturn.logic.Curses.register();
		com.eternalreturn.blocks.CopperRail.register();
		com.eternalreturn.blocks.Woods.register();
		com.eternalreturn.mobs.ForestMobs.register();
		com.eternalreturn.items.Materials.register();
		com.eternalreturn.items.HorseArmor.register();
		com.eternalreturn.logic.GaleStep.register();
		com.eternalreturn.items.DragonRocket.register();
		com.eternalreturn.logic.Necromancy.register();
		com.eternalreturn.items.IceBall.register();
		com.eternalreturn.items.Shuriken.register();
		com.eternalreturn.items.BlockTools.register();
		com.eternalreturn.items.Spears.register();
		com.eternalreturn.logic.BlockLocks.register();
		com.eternalreturn.items.Firearms.register();
		com.eternalreturn.mobs.NewMobs.register();
		com.eternalreturn.birds.Birds.register();
		com.eternalreturn.blocks.Minecarts.register();
		com.eternalreturn.items.Dynamite.register();
		com.eternalreturn.items.AsylumPapers.register();
		com.eternalreturn.items.Necronomicon.register();
		com.eternalreturn.mobs.PillagerBoats.register();
		WorldgenFeatures.register();
		HardenedBlocks.register();
		OreVariants.register();
		BiomeStoneCondition.register();
		LOGGER.info("Eternal Return loaded: {} capped tiers, {} inherent rules",
				EternalReturnConfig.rules().caps().size(), EternalReturnConfig.rules().inherent().size());
	}
}
