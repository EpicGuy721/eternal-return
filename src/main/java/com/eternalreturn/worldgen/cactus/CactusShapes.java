package com.eternalreturn.worldgen.cactus;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.blocks.CactusArmBlock;
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
 * A shape is a cactus trunk standing on sand with cactus arms (CactusArmBlock): each arm goes out one block from the
 * trunk, joined to it, and may rise from there in arms. The plan lists its blocks from the root (the trunk's foot), each
 * after the block it grows from; it comes from the root's position, so it is the same at every growth step. A growing
 * cactus adds the next block of its plan that it can (one per growth step; the root's random ticks drive it).
 */
public final class CactusShapes {
	public enum Kind { SAGUARO, ORGAN_PIPE, CHOLLA, BARREL, CANDELABRA }

	/** A shape's numbers: height, and its arms or stems ("parts"). */
	public record Shape(Kind kind, int minHeight, int maxHeight, int minParts, int maxParts) {
	}

	/** A biome's cactus: its shape and how many per chunk as the world is made (only on the sand or red sand already there). */
	public record Entry(Shape shape, float perChunk) {
	}

	/** One block of a plan: where (from the root), the block it grows from (null for the root), and whether it is an arm. */
	public record Node(BlockPos at, @Nullable BlockPos from, boolean arm) {
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
					loaded.put(biome.getKey(), new Entry(shape, b.has("per_chunk") ? b.get("per_chunk").getAsFloat() : 0F));
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

	/** A plan being written: each block, the block it grows from, and whether it is an arm. */
	private static final class Builder {
		final Map<BlockPos, Node> nodes = new LinkedHashMap<>();

		Builder() {
			this.nodes.put(BlockPos.ORIGIN, new Node(BlockPos.ORIGIN, null, false));
		}

		/** The trunk: cactus from the root up, "height" tall. */
		void trunk(int height) {
			for (int y = 1; y < height; y++) {
				this.nodes.put(BlockPos.ORIGIN.up(y), new Node(BlockPos.ORIGIN.up(y), BlockPos.ORIGIN.up(y - 1), false));
			}
		}

		/** An arm out of the trunk at height y to this side, then "rise" more arms up from it. False if there's no room. */
		boolean arm(int y, Direction side, int rise) {
			BlockPos from = BlockPos.ORIGIN.up(y);
			BlockPos out = from.offset(side);
			Node trunk = this.nodes.get(from);
			if (trunk == null || trunk.arm() || this.nodes.containsKey(out)) {
				return false;
			}
			this.nodes.put(out, new Node(out, from, true));
			BlockPos at = out;
			for (int i = 0; i < rise && !this.nodes.containsKey(at.up()); i++) {
				this.nodes.put(at.up(), new Node(at.up(), at, true));
				at = at.up();
			}
			return true;
		}

		List<Node> build() {
			return List.copyOf(this.nodes.values());
		}
	}

	/** The plan of this shape for a cactus rooted at "root": positions from the root, each after the one it grows from. */
	public static List<Node> plan(Shape shape, BlockPos root) {
		Random random = Random.create(root.asLong() * 0x9E3779B97F4A7C15L ^ 0x5DEECE66DL);
		Builder plan = new Builder();
		int height = shape.minHeight() + random.nextInt(shape.maxHeight() - shape.minHeight() + 1);
		int parts = shape.minParts() + random.nextInt(shape.maxParts() - shape.minParts() + 1);
		List<Direction> sides = new ArrayList<>(Direction.Type.HORIZONTAL.stream().toList());
		net.minecraft.util.Util.shuffle(sides, random);
		plan.trunk(height);
		switch (shape.kind()) {
			case SAGUARO -> {
				// Arms at different heights, never the lowest two or the top two, each its own side where it can: out, then up.
				List<Integer> heights = new ArrayList<>();
				for (int y = 2; y <= height - 3; y++) {
					heights.add(y);
				}
				net.minecraft.util.Util.shuffle(heights, random);
				for (int i = 0; i < Math.min(parts, heights.size()); i++) {
					int y = heights.get(i);
					plan.arm(y, sides.get(i % 4), 1 + random.nextInt(Math.max(1, Math.min(3, height - 1 - y))));
				}
			}
			case ORGAN_PIPE -> {
				// Stems from the trunk's foot: arms out at the bottom or one up, rising nearly as tall as the trunk.
				for (int i = 0; i < Math.min(parts - 1, 4); i++) {
					int y = random.nextInt(2);
					plan.arm(y, sides.get(i), Math.max(1, height - 1 - y - random.nextInt(3)));
				}
			}
			case CHOLLA -> {
				// Short arms out at several heights, some turning up one.
				for (int i = 0; i < parts; i++) {
					int y = 1 + random.nextInt(Math.max(1, height - 1));
					plan.arm(Math.min(y, height - 1), Direction.Type.HORIZONTAL.random(random), random.nextBoolean() ? 1 : 0);
				}
			}
			case BARREL -> {
				// Low arms round the foot, some rising one, into a mound no taller than the trunk.
				for (int i = 0; i < Math.min(parts, 4); i++) {
					plan.arm(0, sides.get(i), height > 1 && random.nextBoolean() ? 1 : 0);
				}
			}
			case CANDELABRA -> {
				// Two tiers near the top: a pair of opposite arms, then the other sides one higher; each out, then up.
				int low = Math.max(1, height - 2);
				plan.arm(low, sides.get(0), 1 + random.nextInt(2));
				plan.arm(low, sides.get(0).getOpposite(), 1 + random.nextInt(2));
				Direction across = sides.get(0).rotateYClockwise();
				plan.arm(height - 1, across, 1 + random.nextInt(2));
				if (parts > 2) {
					plan.arm(height - 1, across.getOpposite(), 1 + random.nextInt(2));
				}
			}
		}
		return plan.build();
	}

	// ------------------------------------------------------------------ the world

	private static boolean isCactus(BlockState state) {
		return state.isOf(Blocks.CACTUS) || CactusArms.isArm(state);
	}

	/** The root of the cactus this block (trunk or arm) belongs to: a trunk's foot on sand (null where none). */
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
			if (here.isOf(Blocks.CACTUS) && world.getBlockState(at.down()).isIn(BlockTags.SAND)) {
				if (best == null || at.getX() < best.getX() || (at.getX() == best.getX() && at.getZ() < best.getZ())) {
					best = at;
				}
			}
			for (Direction direction : Direction.values()) {
				BlockPos next = at.offset(direction);
				BlockState there = world.getBlockState(next);
				// Up and down through cactus and arms; sideways only between an arm and a trunk.
				boolean linked = direction.getAxis().isVertical() ? isCactus(there)
						: (CactusArms.isArm(here) && there.isOf(Blocks.CACTUS)) || (here.isOf(Blocks.CACTUS) && CactusArms.isArm(there));
				if (linked && isCactus(here) && seen.add(next)) {
					queue.add(next);
				}
			}
		}
		return best;
	}

	/** The block this plan block is: cactus, or an arm joined as it stands. */
	private static BlockState stateFor(Node node, WorldView world, BlockPos at) {
		if (!node.arm()) {
			return Blocks.CACTUS.getDefaultState();
		}
		CactusArmBlock arm = (CactusArmBlock) CactusArms.CACTUS_ARM;
		return arm.joined(arm.getDefaultState(), world, at);
	}

	private static boolean isPlanned(BlockState state, Node node) {
		return node.arm() ? CactusArms.isArm(state) : state.isOf(Blocks.CACTUS);
	}

	/** The next block this cactus would grow: the first in its plan that isn't there, grows from one that is, and fits. */
	@Nullable
	public static BlockPos next(WorldView world, BlockPos root, Shape shape) {
		List<Node> plan = plan(shape, root);
		Map<BlockPos, Node> byPos = new HashMap<>();
		for (Node node : plan) {
			byPos.put(node.at(), node);
		}
		for (Node node : plan) {
			if (node.from() == null) {
				continue;
			}
			BlockPos at = root.add(node.at());
			if (isPlanned(world.getBlockState(at), node) || !world.isAir(at)) {
				continue;
			}
			if (!isPlanned(world.getBlockState(root.add(node.from())), byPos.get(node.from()))) {
				continue;
			}
			if (stateFor(node, world, at).canPlaceAt(world, at)) {
				return at;
			}
		}
		return null;
	}

	/** Grows the next block of the plan. Whether one grew. */
	public static boolean growNext(WorldAccess world, BlockPos root, Shape shape) {
		BlockPos at = next(world, root, shape);
		if (at == null) {
			return false;
		}
		for (Node node : plan(shape, root)) {
			if (root.add(node.at()).equals(at)) {
				world.setBlockState(at, stateFor(node, world, at), Block.NOTIFY_ALL);
				return true;
			}
		}
		return false;
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

	/** Room for a cactus as the world is made: air, or a small plant it grows over (grass, flowers, dead bushes), never water. */
	public static boolean clear(net.minecraft.world.WorldView world, BlockPos at) {
		BlockState state = world.getBlockState(at);
		return state.isAir() || (state.isReplaceable() && state.getFluidState().isEmpty()) || (state.isIn(BlockTags.FLOWERS) && !state.isIn(BlockTags.LEAVES));
	}

	/**
	 * Places a whole cactus of this shape as the world is made, rooted at "root" (the air over the ground): each block of
	 * the plan that fits, in growing order. It never changes the ground under it. Whether any was placed.
	 */
	public static boolean placeWhole(WorldAccess world, BlockPos root, Shape shape) {
		Set<BlockPos> placed = new HashSet<>();
		for (Node node : plan(shape, root)) {
			BlockPos at = root.add(node.at());
			if ((node.from() != null && !placed.contains(root.add(node.from()))) || !clear(world, at)) {
				continue;
			}
			BlockState state = stateFor(node, world, at);
			if (!state.canPlaceAt(world, at)) {
				continue;
			}
			if (world.getBlockState(at).getBlock() instanceof net.minecraft.block.TallPlantBlock) {
				world.setBlockState(at.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
			world.setBlockState(at, state, Block.NOTIFY_LISTENERS);
			placed.add(at);
		}
		return !placed.isEmpty();
	}
}
