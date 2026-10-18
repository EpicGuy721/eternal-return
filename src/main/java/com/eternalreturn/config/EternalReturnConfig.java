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
	public List<String> disabledEnchantments = new ArrayList<>(List.of("minecraft:mending", "minecraft:soul_speed"));

	/**
	 * Which defaults the material rules (enchantCaps, inherentEnchantments, disabledEnchantments, armor weights) were
	 * written with: version 1 added copper and rose gold, gave gold tools Silk Touch (hoes Fortune), chainmail
	 * Projectile Protection I, diamond armor Blast Protection I, netherite armor Unbreaking V, and removed Soul Speed.
	 * Don't edit.
	 */
	public int materialsVersion;

	public BookshelfBias bookshelfBias = new BookshelfBias();
	public FortuneTweaks fortune = new FortuneTweaks();
	public MobTweaks mobs = new MobTweaks();
	public FishingTweaks fishing = new FishingTweaks();
	public VillagerTweaks villagers = new VillagerTweaks();
	public WorldgenTweaks worldgen = new WorldgenTweaks();
	public SurvivalTweaks survival = new SurvivalTweaks();
	public RedstoneTweaks redstone = new RedstoneTweaks();
	public ExtraEnchantmentTweaks enchantmentsExtra = new ExtraEnchantmentTweaks();

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

		/**
		 * Guardians and elder guardians spawn in ocean biomes (biome tag eternalreturn:ocean_waters) like other
		 * monsters, with these weights in the monster spawn list (drowned have 95 there, the whole list
		 * about 515). Like vanilla's, they mostly spawn where the sky is hidden; under open sky only one
		 * try in 20 passes. Read when the world loads.
		 */
		public int guardianSpawnWeight = 4;
		public int elderGuardianSpawnWeight = 1;
		/** On top of its weight, a natural elder guardian spawn goes ahead only this often... */
		public double elderGuardianSpawnChance = 0.25;
		/** ...and only with no other elder guardian within this many blocks. */
		public int elderGuardianSpacing = 96;

		/** Slimes: besides vanilla's sizes 1, 2 and 4, a spawning slime is size 8 this often, and size 16 this often. */
		public double slimeSize8Chance = 0.08;
		public double slimeSize16Chance = 0.02;
		/** Slimes also spawn at night in any overworld biome while it rains (on top of swamps and slime chunks). */
		public boolean slimesInNightRain = true;
		/** Fish (cod, salmon, tropical fish, pufferfish) and squids spawn as babies this often (eternalreturn:baby_variants). */
		public double waterBabyChance = 0.15;

		/** Spiders fling a web at their target about this often (seconds); 0 turns it off. */
		public int spiderWebCooldownSeconds = 6;
		/** Zombies spawning below y 0 hold a pickaxe or shovel this often, and dig toward targets they can't reach. */
		public double miningZombieChance = 0.15;
		/** Zombies from monster spawners (dungeons) always hold a pickaxe, at any depth. */
		public boolean spawnerZombiesDig = true;
		/** Adult zombies hunt farm animals (entity tag eternalreturn:zombie_prey). */
		public boolean zombiesHuntAnimals = true;
		/** Baby zombies spawn riding a cow, pig or sheep this often. */
		public double babyZombieAnimalJockeyChance = 0.05;
		/** Phantoms spawn carrying a skeleton this often. */
		public double phantomSkeletonChance = 0.1;
		/** Slimes of size 2 and up spawn with a skeleton on top this often. */
		public double slimeSkeletonChance = 0.05;
		/** Zombies and skeletons in birch forests and cherry groves wear a beehive this often (3 angry bees when it dies). */
		public double beehiveHeadChance = 0.1;
		/** Zombies and skeletons anywhere wear TNT this often (it explodes when they die). */
		public double tntHeadChance = 0.001;
		/** Zombies break torches near them, and with a pickaxe or shovel other lights and beds. */
		public boolean zombiesBreakLights = true;
		/** Wither skeletons' weight in every Nether biome's monster list (0: fortresses only). */
		public int netherWitherSkeletonWeight = 2;
		/** Share of zombie villagers among a biome's zombies and zombie villagers (vanilla 0.05). Read when the world loads. */
		public double zombieVillagerShare = 0.15;
		/** Witches' weight in every Nether biome's monster list (0: none). Read when the world loads. */
		public int netherWitchWeight = 2;
		/** Chance, checked once a minute for each player in an ocean biome, that a boat of two pillagers turns up. */
		public double pillagerBoatChance = 0.15;
		/** Chance an adult zombie spawning naturally rides a zombie horse. */
		public double zombieHorsemanChance = 0.02;
		/** Pillagers back away from a target within 5 blocks. */
		public boolean pillagersBackOff = true;
		/** Phantoms' weight in the End's monster lists (0: none). Read when the world loads. */
		public int endPhantomWeight = 2;
		/** Sheep run from wild wolves within 16 blocks. */
		public boolean sheepFleeWolves = true;
		/** Humanoid monsters jump gaps to reach their target. */
		public boolean mobParkour = true;
		/** No wither can be summoned within this many blocks of bedrock (0: anywhere). */
		public int witherBedrockDistance = 8;
		/** Weight of each of the brown, black and sun bears among creatures in forest biomes (0: none). Needs a restart. */
		public int bearSpawnWeight = 3;
		/** Seconds a villager bitten by a vampire or its bat takes to turn into a vampire. */
		public int vampireTurnSeconds = 30;
		/** Seconds of Poison a vampire's bat gives a player it bites. */
		public int vampireBatPoisonSeconds = 5;
		/** Wither skeletons' health (vanilla 20). */
		public double witherSkeletonHealth = 80.0;
		/** Wither skeletons shoot wither skulls at targets 8 to 32 blocks away. */
		public boolean witherSkeletonSkulls = true;
		/** Phantoms come on one night in this many, whether or not anyone slept (0: vanilla's insomnia instead). */
		public int phantomNightChance = 13;
		/**
		 * Mobs that no longer spawn naturally (spawn eggs, commands, breeding and structures still make them). Read
		 * when the world loads.
		 */
		public List<String> noNaturalSpawns = new ArrayList<>(List.of("minecraft:allay", "minecraft:armadillo", "minecraft:axolotl",
				"minecraft:camel", "minecraft:frog", "minecraft:glow_squid", "minecraft:sniffer", "minecraft:tadpole", "minecraft:turtle",
				"minecraft:dolphin", "minecraft:goat"));
		/** Chance a zombie, skeleton or creeper spawns large (twice the size). */
		public double largeVariantChance = 0.005;
	}

	public static final class FishingTweaks {
		/** Without bait in the inventory only junk and treasure bite. */
		public boolean fishRequireBait = true;
		/**
		 * Item -> bait stats. The first bait found in the off hand, main hand, then inventory order is
		 * used; lava baits only count in lava and the rest only in water.
		 */
		public Map<String, BaitStats> baits = defaultBaits();
		/**
		 * Which version of the bait list this file holds. When the default baits change (worms, glow bait
		 * and infernal bait gave way to magma cream), the list is reset to the new defaults once. Don't edit.
		 */
		public int baitsVersion;
		/** Lava fishing works in ultrawarm dimensions (the Nether). True allows it in any lava. */
		public boolean lavaFishingOutsideNether = false;
		/**
		 * Chance for a catch in water to be a hostile mob instead of items (never on Peaceful). The mob
		 * comes up at the hook and goes for the angler. Bait is used up as for any catch.
		 */
		public double hostileCatchChance = 0.03;
		/** Which mob it is: id -> weight, and whether it only bites in ocean biomes (eternalreturn:ocean_waters). */
		public Map<String, HostileCatch> hostileCatches = defaultHostileCatches();
		/** Fishing the same spot again and again makes bites come slower there for a while. */
		public Overfishing overfishing = new Overfishing();
	}

	public static final class HostileCatch {
		public int weight;
		public boolean oceanOnly;

		public HostileCatch() {
		}

		HostileCatch(int weight, boolean oceanOnly) {
			this.weight = weight;
			this.oceanOnly = oceanOnly;
		}
	}

	/**
	 * Every catch counts against the spot it came from and fades away over recoveryMinutes. Where the
	 * catches within radius blocks add up to more than freeCatches, the wait for a bite there grows by
	 * slowdownPerCatch (0.5 = half as long again) for each catch over, up to maxSlowdown times as long.
	 */
	public static final class Overfishing {
		public boolean enabled = true;
		public double radius = 6.0;
		public double freeCatches = 3.0;
		public double slowdownPerCatch = 0.5;
		public double maxSlowdown = 4.0;
		public double recoveryMinutes = 5.0;
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
		/**
		 * Multiplies the emeralds fishermen pay in vanilla's own fish trades (cod, salmon, tropical fish,
		 * pufferfish). Rounded, at least 1. The mod's fish pay what the fish table says (fish.json).
		 */
		public double fishPriceMultiplier = 2.0;
		/** Wandering traders only come to a player with at least this many villagers within traderVillageRadius blocks. */
		public int traderVillageVillagers = 3;
		public double traderVillageRadius = 64.0;
		/** Villagers do chores by day: blacksmiths repair golems, clerics heal, farmers feed babies, shepherds shear, fishermen fish (VillagerChores). */
		public boolean chores = true;
		/** Villagers summon iron golems (vanilla); off: golems only come from players. */
		public boolean villagersSummonGolems = false;
		/** Curing a zombie villager gives a discount (vanilla); off: it doesn't. */
		public boolean cureDiscount = false;
		/** Every wandering trader sells one asylum papers for this many emeralds (0: none). */
		public int asylumPapersPrice = 16;
		/** Villagers' needs, their shop hours and healing with food (see VillagerNeeds). */
		public VillagerNeedSettings needs = new VillagerNeedSettings();
	}

	/**
	 * Every villager has five needs: sleep, gossip, exercise, sunlight and work-life balance. Each one
	 * missed for daysBeforePriceRise days adds emeraldsPerMissingNeed to every trade paid in emeralds;
	 * any one missed for daysBeforeRefusal days and the villager won't trade at all. Days are Minecraft
	 * days (24,000 ticks of the day clock, so sleeping through a night counts as a night passing).
	 */
	public static final class VillagerNeedSettings {
		public boolean enabled = true;
		public double daysBeforePriceRise = 2.0;
		public int emeraldsPerMissingNeed = 5;
		public double daysBeforeRefusal = 7.0;
		/** Sleep: a villager won't lie down while it can see more than this many other adult villagers. */
		public int maxOtherAdultsInSight = 1;
		/** Exercise: blocks a villager must cover in one walk (straight-line distance from where it set off). */
		public double exerciseBlocks = 8.0;
		/** Work-life balance: a workstation closer than this to the villager's bed breaks it. */
		public double minWorkstationDistance = 8.0;
		/** Shops close at night: no trading from nightStart to nightEnd on the day clock (13000 is dusk, 23000 dawn). */
		public boolean closedAtNight = true;
		public int nightStart = 13000;
		public int nightEnd = 23000;
		/** A hurt villager with food in its inventory (bread, carrots, potatoes, beetroot) eats one now and then, healing by the food's value. */
		public boolean healWithFood = true;
		/** Chance, every 2 seconds while hurt, that it eats. */
		public double eatChance = 0.25;
	}

	/** Launch, Reeling, Spread of Ailments, Curse of Ascension (com.eternalreturn.logic.NewEnchantments). */
	public static final class ExtraEnchantmentTweaks {
		/** Items with Curse of Ascension drift up when dropped. */
		public boolean ascensionCurseRises = true;
	}

	/** Redstone additions (com.eternalreturn.blocks.Redstone, ChainLinks, Minecarts). */
	public static final class RedstoneTweaks {
		/** Dispensers place block items that have no dispenser behaviour of their own. */
		public boolean dispensersPlaceBlocks = true;
		/** Pistons move blocks with data (chests, furnaces...), except those in eternalreturn:immovable_block_entities. */
		public boolean pistonsMoveBlockEntities = true;
		/** Chains link the blocks at their ends when pistons move them; chains stick to slime and honey only where they visibly join. */
		public boolean chainsLinkBlocks = true;
		/**
		 * How many times as fast minecarts run on rails (a whole number): each tick a cart takes this many of vanilla's
		 * steps, so it keeps to curves and slopes (vanilla tops out at 8 blocks a second, 4 in water) (Minecarts).
		 */
		public double minecartSpeedMultiplier = 3.0;
		/** Right-click a minecart with a chain, then another, to chain the second behind the first (Minecarts). */
		public boolean minecartChains = true;
	}

	/** Beds, beacons, animals and a few mob rules (com.eternalreturn.survival). */
	public static final class SurvivalTweaks {
		/**
		 * No hunger: the food bar stays full and hidden, health never regenerates by itself (except on Peaceful), and
		 * food heals instead, each kind its own way (Meals; the table is eternalreturn/foods.json).
		 */
		public boolean removeHunger = true;
		/**
		 * Beacons need no pyramid and give one effect; the payment sets their range (iron 32 to netherite 512 blocks)
		 * and how much effect time builds up (30 seconds to 4 minutes) (BeaconPower).
		 */
		public boolean beaconRework = true;
		/** How many times as fast crops, sugar cane, bamboo, cocoa and sweet berries grow in the rain (1: no faster). */
		public double rainGrowthMultiplier = 2.0;
		/** Saplings lying on the ground plant themselves before they despawn. */
		public boolean saplingsPlantThemselves = true;
		/** A sword only sweeps with Sweeping Edge on it. */
		public boolean sweepingNeedsEnchantment = true;
		/** Snowballs and eggs knock back players they hit, as they do mobs, at thrownKnockback strength. */
		public boolean snowballsKnockBack = true;
		/** How hard a snowball or egg pushes what it hits (vanilla's punch, and vanilla's snowball on a mob: 0.4). */
		public double thrownKnockback = 0.2;
		/** Firework rockets boost a gliding elytra (off: they don't). */
		public boolean elytraRocketBoost = false;
		/** How many drinkable, splash or lingering potions fit in a stack. Needs a restart. */
		public int potionStackSize = 16;
		/** Beds explode in the Nether and the End, as in vanilla (off: they just can't be slept in there). */
		public boolean bedsExplode = false;
		/** What a player drops on death never despawns. */
		public boolean deathDropsStay = true;
		/** False: however many players sleep, the night never skips; beds only set the respawn point. */
		public boolean bedsSkipNight = false;
		/** How many beds each player remembers; when the newest is gone, respawning uses the next one that works. */
		public int rememberedBeds = 3;
		/** No monster spawns naturally (nor phantoms or patrols) inside a working beacon's area. */
		public boolean beaconsBlockMonsters = true;
		/** Animals (the creature spawn group) spawn naturally only inside a working beacon's area; new land still gets its animals. */
		public boolean animalsOnlySpawnNearBeacons = true;
		/** Farm animals (entity tag eternalreturn:grazers) get hungry, graze or take food by hand, and starve without. */
		public boolean animalsGetHungry = true;
		/** Days without food before an animal grazes when it can, or takes food by hand outside breeding. */
		public double hungryAfterDays = 0.5;
		/** Chance each second that a hungry animal tries to graze where it stands. */
		public double grazeChance = 0.125;
		/** Days without food before an animal starts to lose health. */
		public double daysBeforeStarving = 3.0;
		/** A starving animal loses a point of health this often. */
		public int starveDamageSeconds = 30;
		/** When a herd animal (entity tag eternalreturn:herd_animals) is hurt, the same kind around it flees too. */
		public boolean herdPanic = true;
		public double herdPanicRadius = 16.0;
		/** A blinded mob notices and follows targets only this close (its follow range, in blocks). */
		public double blindFollowRange = 2.0;
		/** Mobs from monster spawners drop no items (their experience, and anything they picked up, still drop). */
		public boolean spawnerMobsDropNothing = true;
		/**
		 * Armor weight: armor material -> the share of walking speed a full set takes away (0.30 = 30%, as
		 * much as Slowness II). Split over the pieces: helmet 15%, chestplate 40%, leggings 30%, boots 15%.
		 * Materials not listed weigh nothing. Weightless on a piece makes it weigh nothing.
		 */
		public Map<String, Double> armorWeight = defaultArmorWeight();
		/** Which defaults the armor weights were written with; gold was made lighter than iron in version 1. Don't edit. */
		public int armorWeightVersion;
		/** Snow layers and snow blocks fall like sand when nothing holds them up. */
		public boolean snowFalls = true;
		/**
		 * Crops grow at full speed only in their ideal biomes (biome tags eternalreturn:ideal_biomes/<crop>);
		 * elsewhere each growth step happens only this often (0.25 = four times slower).
		 */
		public double cropGrowthOutsideIdealBiome = 0.25;
		/** Getting hurt stops eating and drinking. */
		public boolean hitsInterruptEating = true;
		/** Raw food (item tag eternalreturn:raw_foods) poisons for 5 seconds this often. */
		public double rawFoodPoisonChance = 0.7;
		/** Pigs have litters of 2 or 3 (2 to 5 if a parent ate a golden carrot). */
		public boolean pigLitters = true;
		/** A baby animal fed a poisonous potato is poisoned, and stays a baby for good, this often. */
		public double poisonousPotatoStuntChance = 0.5;
		/** Mobs see a glowing creature through blocks. */
		public boolean glowingSeenThroughWalls = true;
	}

	private static Map<String, Double> defaultArmorWeight() {
		Map<String, Double> m = new LinkedHashMap<>();
		m.put("minecraft:leather", 0.03);
		m.put("minecraft:turtle", 0.03);
		m.put("minecraft:chainmail", 0.08);
		m.put("minecraft:iron", 0.15);
		m.put("eternalreturn:copper", 0.15);
		m.put("eternalreturn:rosegold", 0.10);
		m.put("minecraft:netherite", 0.15);
		m.put("minecraft:gold", 0.10);
		m.put("minecraft:diamond", 0.30);
		return m;
	}

	/** Overworld generation (see docs/worldgen.md). Changes need a restart, and only affect newly generated chunks. */
	public static final class WorldgenTweaks {
		/** No tuff blobs in Eternal Return worlds (newly generated chunks). */
		public boolean noTuff = true;
		/**
		 * Debug only: carves one straight 3x3 tunnel at y=20 along z=8, through every chunk on that
		 * row. It checks that this mod's carvers run under the active world generator.
		 */
		public boolean debugTunnel = false;
		/**
		 * Biome stone: in Eternal Return worlds, the stone under each biome's topsoil, down to bedrock, is
		 * that biome's stone (andesite under forests, hardened sandstone under deserts and so on; the table
		 * is in docs/worldgen.md). Off: plain stone everywhere. Other worlds never change.
		 */
		public boolean biomeStone = true;
		/**
		 * Rounded overhangs: in Eternal Return worlds, the undersides of overhanging rock rise and fall by a
		 * few blocks instead of lying almost flat, and floating sheets of rock too thin to round (3 blocks or
		 * less) are removed (see docs/worldgen.md). Off: the terrain as Moderner Beta makes it. Other worlds
		 * never change.
		 */
		public boolean roundOverhangs = true;
		/**
		 * In Eternal Return worlds only strongholds, mineshafts, nether fossils and end cities are placed, in
		 * every dimension (structure-like features such as geodes and dungeons stay). Blazes spawn in soul
		 * sand valleys and basalt deltas there instead of fortresses. Other worlds never change.
		 */
		public boolean onlyEssentialStructures = true;
		/** Eternal Return worlds: twice as many overworld dungeons, in cobblestone, stone brick and brick (Dungeons). */
		public boolean moreDungeons = true;
		/** Eternal Return worlds: dungeon tries per Nether chunk (0: no Nether dungeons). */
		public int netherDungeonAttempts = 6;
		/** Eternal Return worlds: share of Nether chunks with a patch of wild nether wart on soul sand. */
		public double netherWartPatchChance = 0.3;
		/** Eternal Return worlds: share of strongholds built at ground level (above sea level ground only). */
		public double surfaceStrongholdChance = 1.0 / 3.0;
		/** Eternal Return worlds: illagers in strongholds, an illusioner in every portal room. */
		public boolean strongholdIllagers = true;
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
		/**
		 * Which version of the type settings this file holds. When the defaults change in a way old
		 * numbers no longer fit (what a setting means, not just its value), the types are reset to the
		 * new defaults once. Don't edit.
		 */
		public int typesVersion;

		/** Adds any type missing from an older config file, with its defaults; resets types written for older defaults. */
		void complete() {
			if (types == null || typesVersion < CaveTypes.SETTINGS_VERSION) {
				if (types != null) {
					EternalReturn.LOGGER.info("Cave type settings were written for older defaults; resetting worldgen.caves.types to the current defaults");
				}
				types = CaveTypes.defaultSettings();
				typesVersion = CaveTypes.SETTINGS_VERSION;
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
		/**
		 * Only for types whose radius follows a bell curve (large caverns): the most common radius. Most
		 * caves land near it and a few reach far toward maxRadius. 0 for the other types.
		 */
		public double typicalRadius;
		/**
		 * A second size range, for types with two sizes: the spiral's coil radius (how far the tunnel
		 * winds from its centre) and the toroidal room's ring radius (centre to the middle of the ring).
		 * 0 for the other types.
		 */
		public double minSize;
		public double maxSize;

		public CaveTypeSettings() {
		}

		public CaveTypeSettings(double weight, int minY, int maxY, double minRadius, double maxRadius) {
			this(weight, minY, maxY, minRadius, maxRadius, 0.0);
		}

		public CaveTypeSettings(double weight, int minY, int maxY, double minRadius, double maxRadius, double minSize, double maxSize) {
			this(weight, minY, maxY, minRadius, maxRadius, 0.0);
			this.minSize = minSize;
			this.maxSize = maxSize;
		}

		public CaveTypeSettings(double weight, int minY, int maxY, double minRadius, double maxRadius, double typicalRadius) {
			this.weight = weight;
			this.minY = minY;
			this.maxY = maxY;
			this.minRadius = minRadius;
			this.maxRadius = maxRadius;
			this.typicalRadius = typicalRadius;
		}
	}

	/** See FishingTweaks.baitsVersion. */
	static final int BAITS_VERSION = 1;

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
			loaded.materialsVersion = 1;
			loaded.fishing.baitsVersion = BAITS_VERSION;
			loaded.worldgen.caves.typesVersion = CaveTypes.SETTINGS_VERSION;
		}
		if (loaded.fishing == null) {
			loaded.fishing = new FishingTweaks();
		}
		if (loaded.fishing.baits == null || loaded.fishing.baitsVersion < BAITS_VERSION) {
			loaded.fishing.baits = defaultBaits();
			loaded.fishing.baitsVersion = BAITS_VERSION;
		}
		if (loaded.survival != null && loaded.survival.armorWeightVersion < 1) {
			// Gold armor used to weigh more than iron; it is lighter now (unless the file set its own number).
			if (loaded.survival.armorWeight != null && Double.valueOf(0.20).equals(loaded.survival.armorWeight.get("minecraft:gold"))) {
				loaded.survival.armorWeight.put("minecraft:gold", 0.10);
			}
			loaded.survival.armorWeightVersion = 1;
		}
		if (loaded.materialsVersion < 1) {
			loaded.updateMaterials();
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

	/**
	 * Brings a config file from before the material rules of version 1 up to them: the inherent enchantments take the
	 * new table if the file still had the old defaults (otherwise only the new tags' rules are added), the new caps
	 * and armor weights are added, and Soul Speed is disabled.
	 */
	private void updateMaterials() {
		Map<String, Map<String, Integer>> old = new LinkedHashMap<>();
		old.put("eternalreturn:inherent/iron_gear", Map.of("minecraft:unbreaking", 1));
		old.put("eternalreturn:inherent/gold_tools", Map.of("minecraft:fortune", 1));
		old.put("eternalreturn:inherent/gold_armor", Map.of("minecraft:fire_protection", 1));
		old.put("eternalreturn:inherent/diamond_tools", Map.of("minecraft:efficiency", 1));
		old.put("eternalreturn:inherent/diamond_armor", Map.of("minecraft:protection", 1));
		old.put("eternalreturn:inherent/netherite_tools", Map.of("minecraft:unbreaking", 5));
		old.put("eternalreturn:inherent/netherite_armor", Map.of("minecraft:fire_protection", 5));
		old.put("eternalreturn:inherent/chainmail_armor", Map.of("minecraft:projectile_protection", 5));
		if (this.inherentEnchantments == null || old.equals(this.inherentEnchantments)) {
			this.inherentEnchantments = defaultInherent();
		} else {
			defaultInherent().forEach(this.inherentEnchantments::putIfAbsent);
		}
		if (this.enchantCaps == null) {
			this.enchantCaps = defaultCaps();
		} else {
			// Rebuilt in the default order (first match wins), keeping this file's numbers and its own extra tags.
			Map<String, Integer> caps = new LinkedHashMap<>();
			defaultCaps().forEach((tag, cap) -> caps.put(tag, this.enchantCaps.getOrDefault(tag, cap)));
			this.enchantCaps.forEach(caps::putIfAbsent);
			this.enchantCaps = caps;
		}
		if (this.disabledEnchantments == null) {
			this.disabledEnchantments = new ArrayList<>();
		}
		if (!this.disabledEnchantments.contains("minecraft:soul_speed")) {
			this.disabledEnchantments.add("minecraft:soul_speed");
		}
		if (this.survival != null && this.survival.armorWeight != null) {
			this.survival.armorWeight.putIfAbsent("eternalreturn:copper", 0.15);
			this.survival.armorWeight.putIfAbsent("eternalreturn:rosegold", 0.10);
		}
		this.materialsVersion = 1;
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
		m.put("eternalreturn:enchant_cap/copper", 8);
		m.put("eternalreturn:enchant_cap/rosegold", 8);
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
		m.put("eternalreturn:chum", new BaitStats(10, 0, false));
		m.put("minecraft:glistering_melon_slice", new BaitStats(5, 0, false));
		m.put("minecraft:magma_cream", new BaitStats(5, 0, true));
		return m;
	}

	/** drowned_guardian_jockey and drowned_elder_guardian_jockey are a drowned with a trident riding one. */
	private static Map<String, HostileCatch> defaultHostileCatches() {
		Map<String, HostileCatch> m = new LinkedHashMap<>();
		m.put("drowned", new HostileCatch(60, false));
		m.put("guardian", new HostileCatch(20, true));
		m.put("drowned_guardian_jockey", new HostileCatch(10, true));
		m.put("elder_guardian", new HostileCatch(3, true));
		m.put("drowned_elder_guardian_jockey", new HostileCatch(1, true));
		return m;
	}

	private static Map<String, Map<String, Integer>> defaultInherent() {
		Map<String, Map<String, Integer>> m = new LinkedHashMap<>();
		m.put("eternalreturn:inherent/iron_gear", Map.of("minecraft:unbreaking", 1));
		m.put("eternalreturn:inherent/gold_hoes", Map.of("minecraft:fortune", 1));
		m.put("eternalreturn:inherent/gold_tools", Map.of("minecraft:silk_touch", 1));
		m.put("eternalreturn:inherent/gold_armor", Map.of("minecraft:fire_protection", 1));
		m.put("eternalreturn:inherent/diamond_tools", Map.of("minecraft:efficiency", 1));
		m.put("eternalreturn:inherent/diamond_armor", Map.of("minecraft:blast_protection", 1));
		m.put("eternalreturn:inherent/netherite_tools", Map.of("minecraft:unbreaking", 5));
		m.put("eternalreturn:inherent/netherite_armor", Map.of("minecraft:unbreaking", 5));
		m.put("eternalreturn:inherent/chainmail_armor", Map.of("minecraft:projectile_protection", 1));
		return m;
	}
}
