package com.eternalreturn.mobs;

import com.eternalreturn.EternalReturn;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

public final class MobTags {
	/** Can roll the baby variant on spawn. Only skeleton-family mobs and creepers support it. */
	public static final TagKey<EntityType<?>> BABY_VARIANTS = of("baby_variants");
	/** Use the boosted armor and armor-enchant chances. */
	public static final TagKey<EntityType<?>> ARMORED_SPAWNS = of("armored_spawns");
	/** Mobs a spider or cave spider jockey can be ridden by. */
	public static final TagKey<EntityType<?>> JOCKEY_RIDERS = of("jockey_riders");

	private MobTags() {
	}

	private static TagKey<EntityType<?>> of(String path) {
		return TagKey.of(RegistryKeys.ENTITY_TYPE, EternalReturn.id(path));
	}
}
