package com.eternalreturn.worldgen.cactus;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.blocks.CactusArms;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.worldgen.EternalReturnServer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CactusBlock;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cactus shapes by biome (worldgen expansion 2, phase 2), from data/eternalreturn/eternalreturn/cactus_shapes.json: each
 * dry biome's cactus grows its own shape (saguaro, organ pipe, cholla, barrel cluster, candelabra), as the world is made,
 * from growth and from bone meal, as saplings follow their tree tables. Eternal Return worlds only; elsewhere, and in a
 * biome with no shape, cactus grows straight up as in vanilla.
 * <p>
 * A shape is a plan: blocks relative to its root (the cactus standing on sand it started from), each joined to the one it
 * grows from, in growing order. The plan comes from the root's position, so it is the same at every growth step. Where a
 * plan stands on more than one block of sand (organ pipe, barrel), the root is the westmost, then northmost, of them, and
 * the plan never puts another of its ground blocks before it, so the root stays the root as the cactus grows. A growing
 * cactus adds the next block of its plan that it can (one per growth step; the root's random ticks drive it).
 */
public final class CactusShapes {
	public enum Kind { SAGUARO, ORGAN_PIPE, CHOLLA, BARREL, CANDELABRA }

	/** A shape's numbers: height, and its arms, stems or cells (what "parts" counts depends on the kind). */
	public record Shape(Kind kind, int minHeight, int maxHeight, int minParts, int maxParts) {
	}

	/** A biome's cactus: its shape, how many per chunk as the world is made, and the ground set under it (null: sand only). */
	public record Entry(Shape shape, float perChunk, @Nullable Block ground) {
	}

	/** One block of a plan: where (from the root), and the block it grows from (null for the root). */
	public record Node(BlockPos at, @Nullable BlockPos from) {
	}

	private static volatile Map<String, Entry> biomes = Map.of();

	private CactusShapes() {
	}

	public static void register() {
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return EternalReturn.id("cactus_shapes");
			}

			@Override
			public void reload(ResourceManager manager) {
				load(manager);
			}
		});
	}

	static void load(ResourceManager manager) {
		Map<String, Entry> loaded = new LinkedHashMap<>();
		for (Map.Entry<Identifier, Resource> file : manager.findResources("eternalreturn", id -> id.getPath().equals("eternalreturn/cactus_shapes.json")).entrySet()) {
			try (Reader reader = file.getValue().getReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				Map<String, Shape> shapes = new HashMap<>();
				for (Map.Entry<String, JsonElement> shape : json.getAsJsonObject("shapes").entrySet()) {
					JsonObject s = shape.getValue().getAsJsonObject();
					shapes.put(shape.getKey(), new Shape(Kind.valueOf(s.get("kind").getAsString().toUpperCase(java.util.Locale.ROOT)),
							s.getAsJsonArray("height").get(0).getAsInt(), s.getAsJsonArray("height").get(1).getAsInt(),
							s.getAsJsonArray("parts").get(0).getAsInt(), s.getAsJsonArray("parts").get(1).getAsInt()));
				}
				for (Map.Entry<String, JsonElement> biome : json.getAsJsonObject("biomes").entrySet()) {
					JsonObject b = biome.getValue().getAsJsonObject();
					Shape shape = shapes.get(b.get("shape").getAsString());
					if (shape == null) {
						EternalReturn.LOGGER.error("Cactus shapes: {} names an unknown shape {}", biome.getKey(), b.get("shape"));
						continue;
					}
					Block ground = null;
					if (b.has("ground")) {
						ground = net.minecraft.registry.Registries.BLOCK.get(Identifier.of(b.get("ground").getAsString()));
					}
					loaded.put(biome.getKey(), new Entry(shape, b.has("per_chunk") ? b.get("per_chunk").getAsFloat() : 0F, ground));
				}
			} catch (Exception e) {
				EternalReturn.LOGGER.error("Couldn't read the cactus shapes {}", file.getKey(), e);
			}
		}
		biomes = Map.copyOf(loaded);
	}

	/**
	 * This biome's cactus: from an entry naming the biome, else (a variant or sub-biome) its base, else a "#tag" entry.
	 * Null where none (vanilla growth), and anywhere but Eternal Return worlds or with worldgen.cactusShapes off.
	 */
	@Nullable
	public static Entry entryFor(RegistryEntry<Biome> biome) {
		if (!EternalReturnServer.active() || !EternalReturnConfig.get().worldgen.cactusShapes) {
			return null;
		}
		return lookup(biome);
	}

	@Nullable
	static Entry lookup(RegistryEntry<Biome> biome) {
		Map<String, Entry> table = biomes;
		String id = biome.getKey().map(key -> key.getValue().toString()).orElse("");
		Entry exact = table.get(id);
		if (exact != null) {
			return exact;
		}
		Entry base = biome.getKey().map(key -> table.get(com.eternalreturn.worldgen.variants.BiomeVariants.baseOf(key.getValue()).toString())).orElse(null);
		if (base != null) {
			return base;
		}
		for (Map.Entry<String, Entry> entry : table.entrySet()) {
			if (entry.getKey().startsWith("#")) {
				Identifier tag = Identifier.tryParse(entry.getKey().substring(1));
				if (tag != null && biome.isIn(TagKey.of(RegistryKeys.BIOME, tag))) {
					return entry.getValue();
				}
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ plans

	/** The plan of this shape for a cactus rooted at "root": positions from the root, each after the one it grows from. */
	public static List<Node> plan(Shape shape, BlockPos root) {
		Random random = Random.create(root.asLong() * 0x9E3779B97F4A7C15L ^ 0x5DEECE66DL);
		Map<BlockPos, BlockPos> nodes = new LinkedHashMap<>();
		BlockPos origin = BlockPos.ORIGIN;
		nodes.put(origin, null);
		int height = shape.minHeight() + random.nextInt(shape.maxHeight() - shape.minHeight() + 1);
		int parts = shape.minParts() + random.nextInt(shape.maxParts() - shape.minParts() + 1);
		List<Direction> sides = new ArrayList<>(Direction.Type.HORIZONTAL.stream().toList());
		net.minecraft.util.Util.shuffle(sides, random);
		switch (shape.kind()) {
			case SAGUARO -> {
				column(nodes, origin, height);
				// Arms at different heights, never the lowest two or the top two, each its own side where it can.
				List<Integer> heights = new ArrayList<>();
				for (int y = 2; y <= height - 3; y++) {
					heights.add(y);
				}
				net.minecraft.util.Util.shuffle(heights, random);
				for (int i = 0; i < Math.min(parts, heights.size()); i++) {
					int y = heights.get(i);
					Direction side = sides.get(i % 4);
					BlockPos out = origin.up(y).offset(side);
					if (add(nodes, out, origin.up(y))) {
						column(nodes, out, 1 + random.nextInt(Math.max(1, Math.min(3, height - 1 - y))) + 1);
					}
				}
			}
			case ORGAN_PIPE, BARREL -> {
				// Stems on the sand round the root, each joined at the base to one beside it, all after the root.
				List<BlockPos> ground = new ArrayList<>(List.of(origin));
				for (int i = 1; i < parts; i++) {
					List<BlockPos[]> options = new ArrayList<>();
					for (BlockPos cell : ground) {
						for (Direction side : Direction.Type.HORIZONTAL) {
							BlockPos next = cell.offset(side);
							if ((next.getX() > 0 || (next.getX() == 0 && next.getZ() > 0)) && !ground.contains(next)) {
								options.add(new BlockPos[]{next, cell});
							}
						}
					}
					if (options.isEmpty()) {
						break;
					}
					BlockPos[] pick = options.get(random.nextInt(options.size()));
					ground.add(pick[0]);
					nodes.put(pick[0], pick[1]);
				}
				for (int i = 0; i < ground.size(); i++) {
					int stem = shape.kind() == Kind.BARREL
							? (i < 2 ? shape.maxHeight() : shape.minHeight() + random.nextInt(shape.maxHeight() - shape.minHeight() + 1))
							: (i == 0 ? height : Math.max(shape.minHeight(), height - 1 - random.nextInt(3)));
					column(nodes, ground.get(i), stem);
				}
			}
			case CHOLLA -> {
				column(nodes, origin, height);
				// Branches from anywhere above the ground, out one or two, sometimes up one, at several heights.
				for (int i = 0; i < parts; i++) {
					List<BlockPos> stems = nodes.keySet().stream().filter(pos -> pos.getY() >= 1).toList();
					BlockPos from = stems.get(random.nextInt(stems.size()));
					Direction side = Direction.Type.HORIZONTAL.random(random);
					int reach = random.nextInt(3) == 0 ? 2 : 1;
					BlockPos at = from;
					for (int r = 0; r < reach; r++) {
						BlockPos out = at.offset(side);
						if (!add(nodes, out, at)) {
							break;
						}
						at = out;
					}
					if (!at.equals(from) && random.nextBoolean()) {
						add(nodes, at.up(), at);
					}
				}
			}
			case CANDELABRA -> {
				column(nodes, origin, height);
				// Two or three arms near the top: out, up, then out again and up.
				int y = Math.max(1, height - 2);
				for (int i = 0; i < Math.min(parts, 4); i++) {
					Direction side = i < 2 ? (i == 0 ? sides.get(0) : sides.get(0).getOpposite()) : sides.get(i);
					BlockPos out = origin.up(y).offset(side);
					if (!add(nodes, out, origin.up(y))) {
						continue;
					}
					int rise = 1 + random.nextInt(2);
					BlockPos top = column(nodes, out, rise + 1);
					Direction again = random.nextBoolean() ? side : (random.nextBoolean() ? side.rotateYClockwise() : side.rotateYCounterclockwise());
					BlockPos branch = top.offset(again);
					if (add(nodes, branch, top)) {
						column(nodes, branch, 1 + random.nextInt(2) + 1);
					}
				}
			}
		}
		List<Node> plan = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockPos> node : nodes.entrySet()) {
			plan.add(new Node(node.getKey(), node.getValue()));
		}
		return plan;
	}

	/** A column "height" tall standing on (and counting) "base", each block growing from the one below. The top. */
	private static BlockPos column(Map<BlockPos, BlockPos> nodes, BlockPos base, int height) {
		BlockPos at = base;
		for (int i = 1; i < height; i++) {
			if (!add(nodes, at.up(), at)) {
				break;
			}
			at = at.up();
		}
		return at;
	}

	/** Adds a block growing from "from", unless the plan has one there already or it is at ground level (stems on the sand are put in outright). */
	private static boolean add(Map<BlockPos, BlockPos> nodes, BlockPos at, BlockPos from) {
		if (nodes.containsKey(at) || at.getY() <= 0) {
			return false;
		}
		nodes.put(at, from);
		return true;
	}

	/** Whether the plan's block joins its neighbour at "at" sideways: it grows from it, or that one grows from it, or both are in a barrel's mound. */
	private static boolean joins(Shape shape, Map<BlockPos, BlockPos> plan, BlockPos at, BlockPos next) {
		if (!plan.containsKey(next) || at.getY() != next.getY()) {
			return false;
		}
		return next.equals(plan.get(at)) || at.equals(plan.get(next)) || shape.kind() == Kind.BARREL;
	}

	// ------------------------------------------------------------------ the world

	/** The root of the cactus this block belongs to: of the pieces joined to it standing on sand, the westmost, then northmost. */
	@Nullable
	public static BlockPos root(WorldView world, BlockPos pos) {
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> seen = new HashSet<>();
		queue.add(pos);
		seen.add(pos);
		BlockPos best = null;
		while (!queue.isEmpty() && seen.size() < 512) {
			BlockPos at = queue.poll();
			BlockState here = world.getBlockState(at);
			if (world.getBlockState(at.down()).isIn(BlockTags.SAND)) {
				if (best == null || at.getX() < best.getX() || (at.getX() == best.getX() && (at.getZ() < best.getZ()
						|| (at.getZ() == best.getZ() && at.getY() < best.getY())))) {
					best = at;
				}
			}
			for (Direction direction : Direction.values()) {
				BlockPos next = at.offset(direction);
				boolean linked = direction.getAxis().isVertical() || CactusArms.joined(here, direction);
				if (linked && world.getBlockState(next).isOf(Blocks.CACTUS) && seen.add(next)) {
					queue.add(next);
				}
			}
		}
		return best;
	}

	/** The next block this cactus would grow: the first in its plan that isn't there, grows from one that is, and fits. */
	@Nullable
	public static BlockPos next(WorldView world, BlockPos root, Shape shape) {
		List<Node> plan = plan(shape, root);
		Map<BlockPos, BlockPos> map = map(plan);
		for (Node node : plan) {
			if (node.from() == null) {
				continue;
			}
			BlockPos at = root.add(node.at());
			BlockPos from = root.add(node.from());
			if (world.getBlockState(at).isOf(Blocks.CACTUS) || !world.getBlockState(from).isOf(Blocks.CACTUS) || !world.isAir(at)) {
				continue;
			}
			if (fits(world, at, joinedState(shape, map, root, at, world))) {
				return at;
			}
		}
		return null;
	}

	private static Map<BlockPos, BlockPos> map(List<Node> plan) {
		Map<BlockPos, BlockPos> map = new LinkedHashMap<>();
		for (Node node : plan) {
			map.put(node.at(), node.from());
		}
		return map;
	}

	/** The cactus for this plan block, joined to the plan blocks beside it that are there. */
	private static BlockState joinedState(Shape shape, Map<BlockPos, BlockPos> map, BlockPos root, BlockPos at, WorldView world) {
		BlockState state = Blocks.CACTUS.getDefaultState();
		BlockPos rel = at.subtract(root);
		for (Direction direction : Direction.Type.HORIZONTAL) {
			if (joins(shape, map, rel, rel.offset(direction)) && world.getBlockState(at.offset(direction)).isOf(Blocks.CACTUS)) {
				state = state.with(CactusArms.side(direction), true);
			}
		}
		return state;
	}

	private static boolean fits(WorldView world, BlockPos at, BlockState state) {
		return state.canPlaceAt(world, at);
	}

	/** Grows the next block of the plan (and joins its neighbours to it). Whether one grew. */
	public static boolean growNext(WorldAccess world, BlockPos root, Shape shape) {
		BlockPos at = next(world, root, shape);
		if (at == null) {
			return false;
		}
		Map<BlockPos, BlockPos> map = map(plan(shape, root));
		BlockState state = joinedState(shape, map, root, at, world);
		world.setBlockState(at, state, Block.NOTIFY_ALL);
		for (Direction direction : Direction.Type.HORIZONTAL) {
			if (state.get(CactusArms.side(direction))) {
				BlockPos neighbour = at.offset(direction);
				world.setBlockState(neighbour, world.getBlockState(neighbour).with(CactusArms.side(direction.getOpposite()), true), Block.NOTIFY_ALL);
			}
		}
		return true;
	}

	/**
	 * A random tick of a cactus in a biome with a shape: only the root grows (its age counts up as vanilla's top block does,
	 * and at the end adds the next block of the plan); the rest wait. False where vanilla growth goes on.
	 */
	public static boolean randomTick(BlockState state, net.minecraft.server.world.ServerWorld world, BlockPos pos) {
		Entry entry = entryFor(world.getBiome(pos));
		if (entry == null) {
			return false;
		}
		if (!world.getBlockState(pos.down()).isIn(BlockTags.SAND) || !pos.equals(root(world, pos))) {
			return true;
		}
		int age = state.get(CactusBlock.AGE);
		if (age < 15) {
			world.setBlockState(pos, state.with(CactusBlock.AGE, age + 1), Block.NO_REDRAW);
		} else if (growNext(world, pos, entry.shape())) {
			world.setBlockState(pos, world.getBlockState(pos).with(CactusBlock.AGE, 0), Block.NO_REDRAW);
		}
		return true;
	}

	/**
	 * Places a whole cactus of this shape as the world is made, rooted at "root" (the air over the ground): each block of
	 * the plan that fits, in growing order, joined to the ones it grows from. With "ground" set, the ground under the plan's
	 * ground blocks becomes it (cactus on a savanna stands on a little sand). Whether any was placed.
	 */
	public static boolean placeWhole(WorldAccess world, BlockPos root, Shape shape, @Nullable Block ground) {
		List<Node> plan = plan(shape, root);
		Map<BlockPos, BlockPos> map = map(plan);
		if (ground != null) {
			for (Node node : plan) {
				if (node.at().getY() == 0) {
					BlockPos under = root.add(node.at()).down();
					BlockState there = world.getBlockState(under);
					if (there.isOf(Blocks.GRASS_BLOCK) || there.isOf(Blocks.DIRT) || there.isOf(Blocks.COARSE_DIRT) || there.isIn(BlockTags.SAND)
							|| there.isIn(BlockTags.TERRACOTTA)) {
						world.setBlockState(under, ground.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
			}
		}
		Set<BlockPos> placed = new HashSet<>();
		for (Node node : plan) {
			BlockPos at = root.add(node.at());
			if (node.from() != null && !placed.contains(root.add(node.from()))) {
				continue;
			}
			if (!world.isAir(at)) {
				continue;
			}
			BlockState state = joinedState(shape, map, root, at, world);
			if (!state.canPlaceAt(world, at)) {
				continue;
			}
			world.setBlockState(at, state, Block.NOTIFY_LISTENERS);
			placed.add(at);
			for (Direction direction : Direction.Type.HORIZONTAL) {
				if (state.get(CactusArms.side(direction))) {
					BlockPos neighbour = at.offset(direction);
					world.setBlockState(neighbour, world.getBlockState(neighbour).with(CactusArms.side(direction.getOpposite()), true), Block.NOTIFY_LISTENERS);
				}
			}
		}
		return !placed.isEmpty();
	}
}
