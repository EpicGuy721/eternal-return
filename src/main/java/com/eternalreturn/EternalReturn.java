package com.eternalreturn;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.fishing.FishingItems;
import com.eternalreturn.fishing.FishingLoot;
import com.eternalreturn.fishing.Thriftiness;
import com.eternalreturn.fishing.Worms;
import com.eternalreturn.mobs.ModEntities;
import com.eternalreturn.villager.TradeRules;
import com.eternalreturn.worldgen.WorldgenFeatures;
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
		FishingItems.register();
		FishingLoot.register();
		Thriftiness.register();
		Worms.register();
		TradeRules.registerFishermanTrades();
		WorldgenFeatures.register();
		LOGGER.info("Eternal Return loaded: {} capped tiers, {} inherent rules",
				EternalReturnConfig.rules().caps().size(), EternalReturnConfig.rules().inherent().size());
	}
}
