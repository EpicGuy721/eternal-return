package com.eternalreturn.worldgen.stone;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.mojang.serialization.MapCodec;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.surfacebuilder.MaterialRules;

/**
 * Surface-rule condition eternalreturn:biome_stone_enabled: true while worldgen.biomeStone is on. The
 * Eternal Return noise settings wrap their biome stone rules in it (tools/worldgen/build_preset.py writes
 * them from the biome_stone table in tools/worldgen/knobs.json), so the setting can switch biome stone off.
 * Only those noise settings use it, so no other world is touched.
 */
public enum BiomeStoneCondition implements MaterialRules.MaterialCondition {
	INSTANCE;

	static final CodecHolder<BiomeStoneCondition> CODEC = CodecHolder.of(MapCodec.unit(INSTANCE));

	public static void register() {
		Registry.register(Registries.MATERIAL_CONDITION, EternalReturn.id("biome_stone_enabled"), CODEC.codec());
	}

	@Override
	public CodecHolder<? extends MaterialRules.MaterialCondition> codec() {
		return CODEC;
	}

	@Override
	public MaterialRules.BooleanSupplier apply(MaterialRules.MaterialRuleContext context) {
		boolean enabled = EternalReturnConfig.get().worldgen.biomeStone;
		if (enabled) {
			BiomeStoneSurface.markChunk();
		}
		return () -> enabled;
	}
}
