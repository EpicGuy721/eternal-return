package com.eternalreturn.worldgen.trees;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.worldgen.caves.CaveWorlds;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.TreeFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;
import net.minecraft.world.gen.foliage.BushFoliagePlacer;
import net.minecraft.world.gen.trunk.DarkOakTrunkPlacer;
import net.minecraft.world.gen.trunk.GiantTrunkPlacer;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiFunction;

/**
 * Biome tree tables (worldgen expansion phase 2, docs/worldgen.md): in Eternal Return worlds a sapling grows a tree
 * picked from the table of the biome it is planted in, and the trees the world generates with are picked from the same
 * tables, so what grows wild in a biome is what its saplings grow into. The tables are data:
 * data/&lt;namespace&gt;/eternalreturn/tree_tables/*.json, each naming its biomes (ids, #tags, or "*" for any) and, per
 * sapling block, weighted "trees" (one sapling) and "mega_trees" (four in a square). An entry is "vanilla" (the tree
 * the sapling or the world would have grown anyway), a configured feature, or a structure template (TreeTemplates).
 * eternalreturn/sapling_trunks.json maps each sapling to its log, so a generated tree's trunk tells which list to use.
 * A biome or sapling with no list grows as vanilla. Switched by worldgen.biomeTrees.
 */
public final class BiomeTrees {
	/** One weighted choice: vanilla's tree (both empty), a configured feature or a structure template. */
	public record Choice(int weight, Optional<Identifier> feature, Optional<Identifier> template) {
		public boolean isVanilla() {
			return this.feature.isEmpty() && this.template.isEmpty();
		}
	}

	/** One table file: its biomes and, per sapling, its two lists. */
	record Table(Identifier id, List<String> biomes, Map<Identifier, List<Choice>> trees, Map<Identifier, List<Choice>> megaTrees) {
	}

	private static volatile List<Table> tables = List.of();
	private static volatile Map<Block, Identifier> saplingOfLog = Map.of();
	/** Who answers "which biome is this sapling growing in": the world's biome, unless a greenhouse says otherwise. */
	private static volatile BiFunction<ServerWorld, BlockPos, RegistryEntry<Biome>> growthBiome = ServerWorld::getBiome;
	private static final ThreadLocal<Boolean> REPLACING = ThreadLocal.withInitial(() -> false);
	/** The last table choice a sapling grew on this thread (null: vanilla grew it), for the tests. */
	public static final ThreadLocal<Choice> LAST_SAPLING_CHOICE = new ThreadLocal<>();
	/** How often each "biome tree" pair was picked as the world generated (vanilla included), for the tests and logs. */
	public static final Map<String, java.util.concurrent.atomic.AtomicInteger> GENERATED = new java.util.concurrent.ConcurrentHashMap<>();

	private BiomeTrees() {
	}

	/**
	 * Extension point for greenhouses (not built yet): a sapling under a greenhouse can grow from the table of the
	 * greenhouse's chosen biome. The function gets the world and the sapling's position and returns the biome to use.
	 */
	public static void setGrowthBiome(BiFunction<ServerWorld, BlockPos, RegistryEntry<Biome>> lookup) {
		growthBiome = lookup;
	}

	public static void register() {
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return EternalReturn.id("tree_tables");
			}

			@Override
			public void reload(ResourceManager manager) {
				load(manager);
			}
		});
	}

	static void load(ResourceManager manager) {
		List<Table> loaded = new ArrayList<>();
		for (Map.Entry<Identifier, Resource> file : new TreeMap<>(manager.findResources("eternalreturn/tree_tables", id -> id.getPath().endsWith(".json"))).entrySet()) {
			try (Reader reader = file.getValue().getReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				List<String> biomes = new ArrayList<>();
				for (JsonElement biome : json.getAsJsonArray("biomes")) {
					biomes.add(biome.getAsString());
				}
				Map<Identifier, List<Choice>> trees = new HashMap<>();
				Map<Identifier, List<Choice>> megaTrees = new HashMap<>();
				for (Map.Entry<String, JsonElement> sapling : json.getAsJsonObject("saplings").entrySet()) {
					JsonObject lists = sapling.getValue().getAsJsonObject();
					Identifier saplingId = Identifier.of(sapling.getKey());
					if (lists.has("trees")) {
						trees.put(saplingId, choices(lists.getAsJsonArray("trees")));
					}
					if (lists.has("mega_trees")) {
						megaTrees.put(saplingId, choices(lists.getAsJsonArray("mega_trees")));
					}
				}
				loaded.add(new Table(file.getKey(), biomes, trees, megaTrees));
			} catch (Exception e) {
				EternalReturn.LOGGER.error("Couldn't read the tree table {}", file.getKey(), e);
			}
		}
		Map<Block, Identifier> logs = new HashMap<>();
		for (Map.Entry<Identifier, Resource> file : manager.findResources("eternalreturn", id -> id.getPath().equals("eternalreturn/sapling_trunks.json")).entrySet()) {
			try (Reader reader = file.getValue().getReader()) {
				for (Map.Entry<String, JsonElement> entry : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
					Identifier logId = Identifier.of(entry.getValue().getAsString());
					if (Registries.BLOCK.containsId(logId)) {
						logs.putIfAbsent(Registries.BLOCK.get(logId), Identifier.of(entry.getKey()));
					}
				}
			} catch (Exception e) {
				EternalReturn.LOGGER.error("Couldn't read {}", file.getKey(), e);
			}
		}
		tables = List.copyOf(loaded);
		saplingOfLog = Map.copyOf(logs);
		EternalReturn.LOGGER.info("Loaded {} biome tree tables", loaded.size());
	}

	private static List<Choice> choices(com.google.gson.JsonArray array) {
		List<Choice> choices = new ArrayList<>();
		for (JsonElement element : array) {
			JsonObject entry = element.getAsJsonObject();
			int weight = entry.has("weight") ? entry.get("weight").getAsInt() : 1;
			if (entry.has("template")) {
				choices.add(new Choice(weight, Optional.empty(), Optional.of(Identifier.of(entry.get("template").getAsString()))));
			} else {
				String tree = entry.get("tree").getAsString();
				choices.add(new Choice(weight, tree.equals("vanilla") ? Optional.empty() : Optional.of(Identifier.of(tree)), Optional.empty()));
			}
		}
		return List.copyOf(choices);
	}

	/**
	 * The list for this sapling in this biome: from a table naming the biome by id, else by tag, else "*"; the first
	 * such table (by file name) with a list for this sapling and size wins. Null: none, so vanilla.
	 */
	@Nullable
	public static List<Choice> listFor(RegistryEntry<Biome> biome, Identifier sapling, boolean mega) {
		String id = biome.getKey().map(key -> key.getValue().toString()).orElse("");
		for (int precedence = 0; precedence < 3; precedence++) {
			for (Table table : tables) {
				List<Choice> list = (mega ? table.megaTrees : table.trees).get(sapling);
				if (list == null || list.isEmpty()) {
					continue;
				}
				for (String name : table.biomes) {
					boolean matches = switch (precedence) {
						case 0 -> name.equals(id);
						case 1 -> name.startsWith("#") && Identifier.tryParse(name.substring(1)) != null
								&& biome.isIn(TagKey.of(RegistryKeys.BIOME, Identifier.of(name.substring(1))));
						default -> name.equals("*");
					};
					if (matches) {
						return list;
					}
				}
			}
		}
		return null;
	}

	/** A choice as written in the tables: "vanilla", or the feature's or template's id. */
	public static String name(Choice choice) {
		return choice.feature().or(choice::template).map(Identifier::toString).orElse("vanilla");
	}

	public static Choice pick(List<Choice> list, Random random) {
		int total = list.stream().mapToInt(Choice::weight).sum();
		int roll = random.nextInt(Math.max(total, 1));
		for (Choice choice : list) {
			roll -= choice.weight();
			if (roll < 0) {
				return choice;
			}
		}
		return list.get(list.size() - 1);
	}

	/** Grows the choice at pos (the north-west sapling of a square for mega trees). */
	public static boolean grow(Choice choice, StructureWorldAccess world, ChunkGenerator generator, Random random, BlockPos pos) {
		if (choice.template().isPresent()) {
			return TreeTemplates.place(world, choice.template().get(), pos, random);
		}
		Optional<ConfiguredFeature<?, ?>> feature = world.getRegistryManager().get(RegistryKeys.CONFIGURED_FEATURE)
				.getOrEmpty(choice.feature().orElseThrow());
		if (feature.isEmpty()) {
			EternalReturn.LOGGER.warn("Tree table names a tree that doesn't exist: {}", choice.feature().get());
			return false;
		}
		boolean outer = REPLACING.get();
		REPLACING.set(true);
		try {
			return feature.get().generate(world, generator, random, pos);
		} finally {
			REPLACING.set(outer);
		}
	}

	private static boolean active(ChunkGenerator generator, StructureWorldAccess world) {
		return EternalReturnConfig.get().worldgen.biomeTrees && generator instanceof NoiseChunkGenerator noise
				&& CaveWorlds.usesEternalReturnSettings(noise, world.getRegistryManager());
	}

	/**
	 * A sapling (or an azalea bush) is growing (SaplingGeneratorMixin). Returns null to let vanilla grow it, else
	 * whether the table's tree grew. Four saplings of a kind in a square use the biome's mega list, or grow as vanilla
	 * if it has none; a single sapling uses the biome's list for one sapling.
	 */
	@Nullable
	public static Boolean growSapling(ServerWorld world, ChunkGenerator generator, BlockPos pos, BlockState state, Random random) {
		LAST_SAPLING_CHOICE.remove();
		if (!active(generator, world)) {
			return null;
		}
		Identifier sapling = Registries.BLOCK.getId(state.getBlock());
		RegistryEntry<Biome> biome = growthBiome.apply(world, pos);
		List<Choice> mega = listFor(biome, sapling, true);
		for (int i = 0; i >= -1; i--) {
			for (int j = 0; j >= -1; j--) {
				if (square(world, pos.add(i, 0, j), state.getBlock())) {
					// Four in a square: the biome's mega list, or vanilla's own (a big spruce, jungle tree or dark oak).
					if (mega == null) {
						return null;
					}
					Choice choice = pick(mega, random);
					LAST_SAPLING_CHOICE.set(choice);
					if (choice.isVanilla()) {
						return null;
					}
					BlockPos corner = pos.add(i, 0, j);
					BlockPos[] four = {corner, corner.east(), corner.south(), corner.south().east()};
					for (BlockPos p : four) {
						world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NO_REDRAW);
					}
					if (grow(choice, world, generator, random, corner)) {
						return true;
					}
					for (BlockPos p : four) {
						world.setBlockState(p, state, Block.NO_REDRAW);
					}
					return false;
				}
			}
		}
		List<Choice> small = listFor(biome, sapling, false);
		if (small == null) {
			return null;
		}
		Choice choice = pick(small, random);
		LAST_SAPLING_CHOICE.set(choice);
		if (choice.isVanilla()) {
			return null;
		}
		BlockState under = world.getFluidState(pos).getBlockState();
		world.setBlockState(pos, under, Block.NO_REDRAW);
		if (grow(choice, world, generator, random, pos)) {
			return true;
		}
		world.setBlockState(pos, state, Block.NO_REDRAW);
		return false;
	}

	private static boolean square(ServerWorld world, BlockPos corner, Block block) {
		return world.getBlockState(corner).isOf(block) && world.getBlockState(corner.east()).isOf(block)
				&& world.getBlockState(corner.south()).isOf(block) && world.getBlockState(corner.south().east()).isOf(block);
	}

	/**
	 * A tree is about to generate with the world (TreeFeatureBiomeMixin). Returns null to let it, else whether the
	 * table's tree grew in its place. Bushes (bush foliage) are left alone, and so are the trees a table picked.
	 */
	@Nullable
	public static Boolean replaceGenerated(FeatureContext<TreeFeatureConfig> context) {
		if (REPLACING.get() || !(context.getWorld() instanceof net.minecraft.world.ChunkRegion) || !active(context.getGenerator(), context.getWorld())) {
			return null;
		}
		TreeFeatureConfig config = context.getConfig();
		if (config.foliagePlacer instanceof BushFoliagePlacer) {
			return null;
		}
		Identifier sapling = saplingOfLog.get(config.trunkProvider.get(context.getRandom(), context.getOrigin()).getBlock());
		if (sapling == null) {
			return null;
		}
		boolean mega = config.trunkPlacer instanceof GiantTrunkPlacer || config.trunkPlacer instanceof DarkOakTrunkPlacer;
		RegistryEntry<Biome> biome = context.getWorld().getBiome(context.getOrigin());
		List<Choice> list = listFor(biome, sapling, mega);
		if (list == null) {
			return null;
		}
		Choice choice = pick(list, context.getRandom());
		GENERATED.computeIfAbsent(biome.getKey().map(key -> key.getValue().toString()).orElse("?") + " " + name(choice),
				key -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
		if (choice.isVanilla()) {
			return null;
		}
		return grow(choice, context.getWorld(), context.getGenerator(), context.getRandom(), context.getOrigin());
	}
}
