package com.eternalreturn.config;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.worldgen.caves.CaveTypes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plain JSON config stored at config/eternalreturn.json.
 * Numbers live here; which items belong to which group lives in item tags
 * (data/eternalreturn/tags/item/...), so modded gear can be slotted in with a datapack.
 */
public final class EternalReturnConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static EternalReturnConfig instance = new EternalReturnConfig();
	private static Compiled compiled = instance.compile();

	/** Enchantments whose vanilla max level is above 1 but below this get raised to it. */
	public boolean raiseMaxLevels = true;
	public int raisedMaxLevel = 5;

	/** Each level of a curse refunds this many points of enchant capacity. */
	public int pointsRefundedPerCurseLevel = 1;

	/** Item tag -> enchant capacity. First matching tag wins. Items in no tag are uncapped. */
	public Map<String, Integer> enchantCaps = defaultCaps();

	/** Item tag -> (enchantment -> level). These are free and never count toward capacity. */
	public Map<String, Map<String, Integer>> inherentEnchantments = defaultInherent();

	/** Enchantments that can never be rolled by tables, loot, mobs or villagers. */
	public List<String> disabledEnchantments = new ArrayList<>(List.of("minecraft:mending"));

	public BookshelfBias bookshelfBias = new BookshelfBias();
	public FortuneTweaks fortune = new FortuneTweaks();
	public MobTweaks mobs = new MobTweaks();
	public FishingTweaks fishing = new FishingTweaks();
	public VillagerTweaks villagers = new VillagerTweaks();
	public WorldgenTweaks worldgen = new WorldgenTweaks();

	public static final class BookshelfBias {
		public boolean enabled = true;
		/** Each level of a matching shelved book adds this much to the enchantment's weight multiplier (1.0 = +100%). */
		public double weightBonusPerBookLevel = 1.0;
		public double maxWeightMultiplier = 16.0;
		/** Each level of a matching shelved book adds this much enchanting power, for that enchantment only. */
		public int powerBonusPerBookLevel = 3;
		public int maxPowerBonus = 30;
		/** If true, shelved books can make the table offer enchantments it normally never offers (treasure ones). */
		public boolean booksCanUnlockNonTableEnchantments = false;
	}

	public static final class FortuneTweaks {
		public boolean hoesMultiplyCrops = true;
		public boolean shearsMultiplyWool = true;
	}

	/**
	 * Chances written as "base + perDifficulty" scale with clamped local difficulty, which runs
	 * from 0 (new areas, Easy) to 1 (long-inhabited areas on Hard).
	 */
	public static final class MobTweaks {
		/** Chance for a mob in eternalreturn:baby_variants (skeleton, stray, creeper) to spawn as a baby. */
		public double babyChance = 0.05;
		/** Extra movement speed for those babies. 0.5 = +50%, the same boost baby zombies get. */
		public double babySpeedBonus = 0.5;
		/** Baby creeper fuse length relative to an adult's. */
		public double babyCreeperFuseMultiplier = 0.5;
		/** Baby creeper explosion power relative to an adult's. */
		public double babyCreeperExplosionMultiplier = 0.35;

		/** Armor chance for eternalreturn:armored_spawns (zombies, skeletons). Vanilla is 0 + 0.15. */
		public double armorChanceBase = 0.25;
		public double armorChancePerDifficulty = 0.5;
		/** Chance for each armor piece those mobs spawn with to be enchanted. Vanilla is 0 + 0.5. */
		public double armorEnchantChanceBase = 0.2;
		public double armorEnchantChancePerDifficulty = 0.6;

		/** Natural creeper spawns need sky light at or below creeperMaxSkyLight (0 = no sky light reaches). */
		public boolean creepersOnlySpawnUnderground = true;
		public int creeperMaxSkyLight = 0;

		/** Vanilla spiders use 1%. */
		public double spiderJockeyChance = 0.01;
		public double caveSpiderJockeyChance = 0.02;
		/** Riders come from eternalreturn:jockey_riders, weighted by how often each spawns in the local biome. */
		public boolean biomeMatchedJockeyRiders = true;
		public double strayPolarBearJockeyChance = 0.05;

		public double zombieFishingRodChance = 0.05;
		/** Fraction of the distance to its target that a zombie's fishing rod yanks the hooked entity per tick. */
		public double zombieFishingRodPullStrength = 0.1;
		public double zombieEnderPearlChance = 0.01;
		public double skeletonSwordChance = 0.1;

		public boolean monstersBreakOutOfBoats = true;
	}

	public static final class FishingTweaks {
		/** Without bait in the inventory only junk and treasure bite. */
		public boolean fishRequireBait = true;
		/**
		 * Item -> bait stats. The first bait found in the off hand, main hand, then inventory order is
		 * used; lava baits only count in lava and the rest only in water.
		 */
		public Map<String, BaitStats> baits = defaultBaits();
		/** Chance for blocks in eternalreturn:drops_worms to drop a worm when a player mines them. */
		public double wormDropChance = 0.04;
		/** Anglerfish only bite where the water under the hook is at least this many blocks deep. */
		public int anglerfishMinWaterDepth = 10;
		/** Lava fishing works in ultrawarm dimensions (the Nether). True allows it in any lava. */
		public boolean lavaFishingOutsideNether = false;
	}

	public static final class BaitStats {
		/** Shortens the wait for a bite, like Lure (5 seconds per Lure level). */
		public double lureSeconds;
		/** Used instead of lureSeconds at night when above zero. */
		public double nightLureSeconds;
		public boolean lava;

		public BaitStats() {
		}

		BaitStats(double lureSeconds, double nightLureSeconds, boolean lava) {
			this.lureSeconds = lureSeconds;
			this.nightLureSeconds = nightLureSeconds;
			this.lava = lava;
		}
	}

	public static final class VillagerTweaks {
		/** Villagers stop buying items for emeralds. Fishermen still buy raw fish (eternalreturn:raw_fish). */
		public boolean onlyFishermenBuy = true;
		/** Multiplies the emeralds fishermen pay for every fish, vanilla ones included. Rounded, at least 1. */
		public double fishPriceMultiplier = 2.0;
		/**
		 * Extra fish the fisherman buys, on top of vanilla's cod, salmon, tropical fish and pufferfish.
		 * "emeralds" is before fishPriceMultiplier. Needs a restart.
		 */
		public Map<String, FishTrade> fishermanTrades = defaultFishTrades();
	}

	/** Overworld generation (see docs/worldgen.md). Changes need a restart, and only affect newly generated chunks. */
	public static final class WorldgenTweaks {
		/**
		 * Debug only: carves one straight 3x3 tunnel at y=20 along z=8, through every chunk on that
		 * row. It checks that this mod's carvers run under the active world generator.
		 */
		public boolean debugTunnel = false;
		/** The cave engine (com.eternalreturn.worldgen.caves). Only Eternal Return worlds are affected. */
		public CaveTweaks caves = new CaveTweaks();
	}

	/**
	 * Caves in Eternal Return worlds. Other worlds (vanilla, other Moderner Beta presets, flat) keep
	 * their normal carving whatever is set here.
	 */
	public static final class CaveTweaks {
		/** The new cave types below. Off: no new caves are carved. */
		public boolean enabled = true;
		/**
		 * Brings back the old caves and ravines (Moderner Beta's Beta-style caves and canyons, and any
		 * other carver not from this mod) in Eternal Return worlds. Off by default: the types below are
		 * the only caves there.
		 */
		public boolean oldCaves = false;
		/** Multiplies every type's weight: 2.0 doubles how many caves start, 0.5 halves it. */
		public double density = 1.0;
		/**
		 * Debug: when set to a type id (for example "ravine"), only that type is carved, at a high
		 * frequency, so examples are easy to find. Empty for normal generation.
		 */
		public String debugForceCaveType = "";
		/**
		 * Debug: writes the type and start x, y, z of every cave to eternalreturn-cave-starts.csv in the
		 * game folder as chunks generate, so real examples can be visited with /tp.
		 */
		public boolean debugLogCaveStarts = false;
		/** Per type: enabled, weight (caves starting per 100 chunks), start depth range, radius range. */
		public Map<String, CaveTypeSettings> types = CaveTypes.defaultSettings();

		/** Adds any type missing from an older config file, with its defaults. */
		void complete() {
			if (types == null) {
				types = new LinkedHashMap<>();
			}
			CaveTypes.defaultSettings().forEach(types::putIfAbsent);
			if (debugForceCaveType == null) {
				debugForceCaveType = "";
			}
		}
	}

	public static final class CaveTypeSettings {
		public boolean enabled = true;
		/** Caves of this type starting per 100 chunks, on average (before the density multiplier). */
		public double weight;
		/** A cave's start (its first point) is placed between these y levels. Tunnels may wander beyond. */
		public int minY;
		public int maxY;
		/** Radius range in blocks; what the radius means depends on the type (see docs/worldgen.md). */
		public double minRadius;
		public double maxRadius;

		public CaveTypeSettings() {
		}

		public CaveTypeSettings(double weight, int minY, int maxY, double minRadius, double maxRadius) {
			this.weight = weight;
			this.minY = minY;
			this.maxY = maxY;
			this.minRadius = minRadius;
			this.maxRadius = maxRadius;
		}
	}

	public static final class FishTrade {
		/** Villager level that can offer it, 1 (novice) to 5 (master). */
		public int level;
		/** Fish the player hands over. */
		public int count;
		public int emeralds;
		public int maxUses;
		public int experience;

		public FishTrade() {
		}

		FishTrade(int level, int count, int emeralds, int maxUses, int experience) {
			this.level = level;
			this.count = count;
			this.emeralds = emeralds;
			this.maxUses = maxUses;
			this.experience = experience;
		}
	}

	public static EternalReturnConfig get() {
		return instance;
	}

	public static Compiled rules() {
		return compiled;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(EternalReturn.MOD_ID + ".json");
		EternalReturnConfig loaded = null;

		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				loaded = GSON.fromJson(reader, EternalReturnConfig.class);
			} catch (IOException | JsonParseException e) {
				EternalReturn.LOGGER.error("Could not read {}, using defaults for this session (file left untouched)", path, e);
				loaded = new EternalReturnConfig();
				instance = loaded;
				compiled = loaded.compile();
				return;
			}
		}

		if (loaded == null) {
			loaded = new EternalReturnConfig();
		}
		if (loaded.worldgen == null) {
			loaded.worldgen = new WorldgenTweaks();
		}
		if (loaded.worldgen.caves == null) {
			loaded.worldgen.caves = new CaveTweaks();
		}
		loaded.worldgen.caves.complete();

		instance = loaded;
		compiled = loaded.compile();

		// Write back so newly added options appear in older config files.
		try (Writer writer = Files.newBufferedWriter(path)) {
			GSON.toJson(loaded, writer);
		} catch (IOException e) {
			EternalReturn.LOGGER.warn("Could not write {}", path, e);
		}
	}

	private Compiled compile() {
		List<CapRule> caps = new ArrayList<>();
		if (enchantCaps != null) {
			enchantCaps.forEach((tag, cap) -> {
				Identifier id = Identifier.tryParse(tag);
				if (id == null || cap == null) {
					EternalReturn.LOGGER.warn("Ignoring bad enchant cap entry '{}'", tag);
					return;
				}
				caps.add(new CapRule(TagKey.of(RegistryKeys.ITEM, id), cap));
			});
		}

		List<InherentRule> inherent = new ArrayList<>();
		if (inherentEnchantments != null) {
			inherentEnchantments.forEach((tag, levels) -> {
				Identifier tagId = Identifier.tryParse(tag);
				if (tagId == null || levels == null) {
					EternalReturn.LOGGER.warn("Ignoring bad inherent entry '{}'", tag);
					return;
				}
				Map<RegistryKey<Enchantment>, Integer> map = new LinkedHashMap<>();
				levels.forEach((enchantment, level) -> {
					Identifier enchantmentId = Identifier.tryParse(enchantment);
					if (enchantmentId == null || level == null || level <= 0) {
						EternalReturn.LOGGER.warn("Ignoring bad inherent enchantment '{}' in '{}'", enchantment, tag);
						return;
					}
					map.put(RegistryKey.of(RegistryKeys.ENCHANTMENT, enchantmentId), level);
				});
				inherent.add(new InherentRule(TagKey.of(RegistryKeys.ITEM, tagId), Map.copyOf(map)));
			});
		}

		Set<RegistryKey<Enchantment>> disabled = new HashSet<>();
		if (disabledEnchantments != null) {
			for (String s : disabledEnchantments) {
				Identifier id = Identifier.tryParse(s);
				if (id != null) {
					disabled.add(RegistryKey.of(RegistryKeys.ENCHANTMENT, id));
				}
			}
		}

		return new Compiled(List.copyOf(caps), List.copyOf(inherent), Set.copyOf(disabled));
	}

	public record CapRule(TagKey<Item> tag, int cap) {
	}

	public record InherentRule(TagKey<Item> tag, Map<RegistryKey<Enchantment>, Integer> levels) {
	}

	public record Compiled(List<CapRule> caps, List<InherentRule> inherent, Set<RegistryKey<Enchantment>> disabled) {
	}

	private static Map<String, Integer> defaultCaps() {
		Map<String, Integer> m = new LinkedHashMap<>();
		m.put("eternalreturn:enchant_cap/iron", 6);
		m.put("eternalreturn:enchant_cap/gold", 10);
		m.put("eternalreturn:enchant_cap/diamond", 7);
		m.put("eternalreturn:enchant_cap/netherite", 4);
		m.put("eternalreturn:enchant_cap/chainmail", 5);
		m.put("eternalreturn:enchant_cap/bows", 6);
		m.put("eternalreturn:enchant_cap/fishing_rods", 7);
		return m;
	}

	private static Map<String, BaitStats> defaultBaits() {
		Map<String, BaitStats> m = new LinkedHashMap<>();
		m.put("eternalreturn:worm", new BaitStats(5, 0, false));
		m.put("eternalreturn:chum", new BaitStats(10, 0, false));
		m.put("eternalreturn:glow_bait", new BaitStats(5, 15, false));
		m.put("minecraft:glistering_melon_slice", new BaitStats(5, 0, false));
		m.put("eternalreturn:infernal_bait", new BaitStats(5, 0, true));
		return m;
	}

	private static Map<String, FishTrade> defaultFishTrades() {
		Map<String, FishTrade> m = new LinkedHashMap<>();
		m.put("eternalreturn:perch", new FishTrade(1, 15, 1, 16, 2));
		m.put("eternalreturn:pike", new FishTrade(2, 6, 1, 16, 10));
		m.put("eternalreturn:catfish", new FishTrade(2, 6, 1, 16, 10));
		m.put("eternalreturn:magma_eel", new FishTrade(2, 10, 1, 16, 10));
		m.put("eternalreturn:tuna", new FishTrade(3, 5, 1, 16, 20));
		m.put("eternalreturn:icefish", new FishTrade(3, 6, 1, 16, 20));
		m.put("eternalreturn:ashfin", new FishTrade(3, 4, 1, 16, 20));
		m.put("eternalreturn:electric_eel", new FishTrade(4, 1, 2, 12, 30));
		m.put("eternalreturn:anglerfish", new FishTrade(4, 1, 3, 12, 30));
		m.put("eternalreturn:soulfin", new FishTrade(4, 1, 2, 12, 30));
		m.put("eternalreturn:koi", new FishTrade(5, 1, 5, 12, 30));
		return m;
	}

	private static Map<String, Map<String, Integer>> defaultInherent() {
		Map<String, Map<String, Integer>> m = new LinkedHashMap<>();
		m.put("eternalreturn:inherent/iron_gear", Map.of("minecraft:unbreaking", 1));
		m.put("eternalreturn:inherent/gold_tools", Map.of("minecraft:fortune", 1));
		m.put("eternalreturn:inherent/gold_armor", Map.of("minecraft:fire_protection", 1));
		m.put("eternalreturn:inherent/diamond_tools", Map.of("minecraft:efficiency", 1));
		m.put("eternalreturn:inherent/diamond_armor", Map.of("minecraft:protection", 1));
		m.put("eternalreturn:inherent/netherite_tools", Map.of("minecraft:unbreaking", 5));
		m.put("eternalreturn:inherent/netherite_armor", Map.of("minecraft:fire_protection", 5));
		m.put("eternalreturn:inherent/chainmail_armor", Map.of("minecraft:projectile_protection", 5));
		return m;
	}
}
