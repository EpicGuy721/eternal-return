package com.eternalreturn.worldgen.landmarks;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.blocks.LeafDistance;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.worldgen.trees.BiomeTrees;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.entity.BeehiveBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Elder trees (worldgen expansion 2, phase 4; landmark kind elder_tree): about one per 2,000 x 2,000 blocks of land with
 * a tree table, a single colossal tree of a species from that biome's own tables (data/eternalreturn/eternalreturn/
 * elder_trees.json says which species can, in which of seven giant forms, with which wood, leaves and size: 50 to 90
 * tall, a trunk 3 to 5 wide). Its leaves are ordinary non-persistent leaves at their true distance from the wood, so a cut
 * elder decays as any tree. Round it a clearing of podzol and fallen leaves with saplings of its kind (ordinary trees
 * don't grow in it); in the base of its trunk, among its roots, a hollow, sometimes with a spring of water, sometimes a
 * bear's den; in its crown bee nests and roosting birds. Everything comes from its centre, size and seed, so each chunk
 * builds its own part.
 */
public final class ElderTrees {
	public enum Form { DOME, SPIRE, COLUMN, UMBRELLA, WEEPING, PALM, BOTTLE }

	/** One species that can grow as an elder. */
	public record Species(String id, Form form, Block log, List<Block> leaves, Block sapling, int minHeight, int maxHeight, int minTrunk, int maxTrunk) {
	}

	private static volatile Map<String, Species> species = Map.of();

	private ElderTrees() {
	}

	public static void register() {
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return EternalReturn.id("elder_trees");
			}

			@Override
			public void reload(ResourceManager manager) {
				load(manager);
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> Index.of(server.getOverworld()));
	}

	static void load(ResourceManager manager) {
		Map<String, Species> loaded = new LinkedHashMap<>();
		for (Map.Entry<Identifier, Resource> file : manager.findResources("eternalreturn", id -> id.getPath().equals("eternalreturn/elder_trees.json")).entrySet()) {
			try (Reader reader = file.getValue().getReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("species").entrySet()) {
					JsonObject s = entry.getValue().getAsJsonObject();
					List<Block> leaves = new ArrayList<>();
					s.getAsJsonArray("leaves").forEach(e -> leaves.add(Registries.BLOCK.get(Identifier.of(e.getAsString()))));
					loaded.put(entry.getKey(), new Species(entry.getKey(), Form.valueOf(s.get("form").getAsString().toUpperCase(java.util.Locale.ROOT)),
							Registries.BLOCK.get(Identifier.of(s.get("log").getAsString())), List.copyOf(leaves),
							Registries.BLOCK.get(Identifier.of(s.get("sapling").getAsString())),
							s.getAsJsonArray("height").get(0).getAsInt(), s.getAsJsonArray("height").get(1).getAsInt(),
							s.getAsJsonArray("trunk").get(0).getAsInt(), s.getAsJsonArray("trunk").get(1).getAsInt()));
				}
			} catch (Exception e) {
				EternalReturn.LOGGER.error("Couldn't read the elder trees {}", file.getKey(), e);
			}
		}
		species = Map.copyOf(loaded);
	}

	/** Every species that can grow as an elder, by its tree table id. */
	public static Map<String, Species> allSpecies() {
		return species;
	}

	/** The species an elder here grows as: picked by weight from the biome's own tree lists, among those that can (null: none). */
	@Nullable
	public static Species pick(RegistryEntry<Biome> biome, long seed) {
		Map<String, Species> known = species;
		List<Species> options = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		int total = 0;
		for (Map.Entry<Identifier, List<BiomeTrees.Choice>> list : BiomeTrees.ownLists(biome).entrySet()) {
			for (BiomeTrees.Choice choice : list.getValue()) {
				String key = choice.isVanilla() ? "vanilla:" + list.getKey() : BiomeTrees.name(choice);
				Species found = known.get(key);
				if (found != null && choice.weight() > 0) {
					options.add(found);
					weights.add(choice.weight());
					total += choice.weight();
				}
			}
		}
		if (total == 0) {
			return null;
		}
		int roll = Random.create(seed ^ 0x51ED).nextInt(total);
		for (int i = 0; i < options.size(); i++) {
			roll -= weights.get(i);
			if (roll < 0) {
				return options.get(i);
			}
		}
		return options.get(0);
	}

	// ------------------------------------------------------------------ the plan

	/** A whole elder, from its trunk's foot (0, 0, 0: the air over the ground): its blocks, and where its life goes. */
	public static final class Plan {
		public final Species species;
		public final int height;
		public final int trunk;
		/** The trunk's square: from lo to lo + trunk - 1 across, on both axes. */
		public final int lo;
		public final Map<BlockPos, BlockState> blocks = new HashMap<>();
		final Map<BlockPos, Block> leaves = new HashMap<>();
		public final List<BlockPos> nests = new ArrayList<>();
		public final List<BlockPos> roosts = new ArrayList<>();
		public final List<BlockPos> saplings = new ArrayList<>();
		public final int clearing;
		public boolean spring;
		public boolean den;
		/** The hollow's floor middle (the den's spot). */
		public BlockPos hollow = BlockPos.ORIGIN;
		private final Random random;

		Plan(Species species, long seed) {
			this.species = species;
			this.random = Random.create(seed);
			this.height = species.minHeight() + this.random.nextInt(species.maxHeight() - species.minHeight() + 1);
			this.trunk = species.minTrunk() + this.random.nextInt(species.maxTrunk() - species.minTrunk() + 1);
			this.lo = -(this.trunk / 2);
			this.clearing = 14 + this.random.nextInt(5);
		}

		void log(int x, int y, int z, Direction.Axis axis) {
			this.blocks.put(new BlockPos(x, y, z), this.species.log().getDefaultState().with(PillarBlock.AXIS, axis));
			this.leaves.remove(new BlockPos(x, y, z));
		}

		boolean isLog(BlockPos pos) {
			BlockState state = this.blocks.get(pos);
			return state != null && state.isOf(this.species.log());
		}

		/** The trunk, tapering near the top, from below the ground to "top". */
		void trunk(int top, int bottom) {
			for (int y = bottom; y <= top; y++) {
				int w = y > top * 0.85 ? Math.max(1, this.trunk - 2) : y > top * 0.6 ? Math.max(1, this.trunk - 1) : this.trunk;
				int from = -(w / 2);
				for (int x = from; x < from + w; x++) {
					for (int z = from; z < from + w; z++) {
						this.log(x, y, z, Direction.Axis.Y);
					}
				}
			}
		}

		/** A line of logs from one point to another, "thick" across (1 or 2). Returns the end. */
		BlockPos branch(double x0, double y0, double z0, double x1, double y1, double z1, int thick) {
			double dx = x1 - x0;
			double dy = y1 - y0;
			double dz = z1 - z0;
			double length = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
			Direction.Axis axis = Math.abs(dy) >= Math.max(Math.abs(dx), Math.abs(dz)) ? Direction.Axis.Y
					: Math.abs(dx) >= Math.abs(dz) ? Direction.Axis.X : Direction.Axis.Z;
			for (int i = 0; i <= length; i++) {
				double t = length == 0 ? 0 : i / length;
				int x = (int) Math.floor(x0 + dx * t);
				int y = (int) Math.floor(y0 + dy * t);
				int z = (int) Math.floor(z0 + dz * t);
				this.log(x, y, z, axis);
				if (thick > 1) {
					this.log(x + 1, y, z, axis);
					this.log(x, y, z + 1, axis);
					this.log(x + 1, y, z + 1, axis);
				}
			}
			return new BlockPos((int) Math.floor(x1), (int) Math.floor(y1), (int) Math.floor(z1));
		}

		/** A ragged ellipsoid of leaves round a point (never over wood). */
		void blob(BlockPos c, double rx, double ry, double rz) {
			Block leaf = this.species.leaves().get(this.random.nextInt(this.species.leaves().size()));
			for (int x = (int) -rx - 1; x <= rx + 1; x++) {
				for (int y = (int) -ry - 1; y <= ry + 1; y++) {
					for (int z = (int) -rz - 1; z <= rz + 1; z++) {
						double d = (x * x) / (rx * rx) + (y * y) / (ry * ry) + (z * z) / (rz * rz);
						if (d <= 1.0 - this.random.nextFloat() * 0.25) {
							BlockPos pos = c.add(x, y, z);
							if (!this.blocks.containsKey(pos)) {
								this.leaves.put(pos, leaf);
							}
						}
					}
				}
			}
		}

		/** Roots spreading from the trunk's foot, arching out over the ground and down into it. */
		void roots() {
			int count = 4 + this.trunk;
			for (int i = 0; i < count; i++) {
				double angle = (Math.PI * 2 * i) / count + this.random.nextDouble() * 0.5;
				double startR = this.trunk / 2.0;
				int length = 3 + this.random.nextInt(4);
				for (int k = 0; k <= length; k++) {
					double r = startR + k;
					int x = (int) Math.floor(Math.cos(angle) * r);
					int z = (int) Math.floor(Math.sin(angle) * r);
					int y = 1 - k / 2;
					this.log(x, y, z, Math.abs(Math.cos(angle)) > Math.abs(Math.sin(angle)) ? Direction.Axis.X : Direction.Axis.Z);
					if (y > -3) {
						this.log(x, y - 1, z, Direction.Axis.Y);
					}
				}
			}
		}

		/** Leaves too far from the wood go (an elder's leaves must keep their distance to decay as any tree's), the rest at their distance. */
		void settleLeaves() {
			int keep = LeafDistance.MAX - 1;
			Map<BlockPos, Integer> distance = new HashMap<>();
			ArrayDeque<BlockPos> queue = new ArrayDeque<>();
			for (BlockPos leaf : this.leaves.keySet()) {
				for (Direction side : Direction.values()) {
					if (this.isLog(leaf.offset(side))) {
						distance.put(leaf, 1);
						queue.add(leaf);
						break;
					}
				}
			}
			while (!queue.isEmpty()) {
				BlockPos at = queue.poll();
				int d = distance.get(at);
				if (d >= keep) {
					continue;
				}
				for (Direction side : Direction.values()) {
					BlockPos next = at.offset(side);
					if (this.leaves.containsKey(next) && !distance.containsKey(next)) {
						distance.put(next, d + 1);
						queue.add(next);
					}
				}
			}
			for (Map.Entry<BlockPos, Integer> leaf : distance.entrySet()) {
				this.blocks.put(leaf.getKey(), this.leaves.get(leaf.getKey()).getDefaultState().with(LeavesBlock.DISTANCE, leaf.getValue())
						.with(LeavesBlock.PERSISTENT, false));
			}
			this.leaves.clear();
		}

		/** The hollow in the trunk's base: a room under it among the roots, a way in on one side, sometimes a spring. */
		void hollow() {
			int size = Math.max(3, this.trunk);
			int from = -(size / 2);
			for (int x = from; x < from + size; x++) {
				for (int z = from; z < from + size; z++) {
					for (int y = -3; y <= -1; y++) {
						this.blocks.put(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
					}
					this.blocks.put(new BlockPos(x, -4, z), (x + z) % 2 == 0 ? Blocks.COARSE_DIRT.getDefaultState() : Blocks.ROOTED_DIRT.getDefaultState());
				}
			}
			this.hollow = new BlockPos(from + size / 2, -3, from + size / 2);
			Direction way = Direction.Type.HORIZONTAL.random(this.random);
			int edge = way.getDirection() == Direction.AxisDirection.POSITIVE ? from + size - 1 : from;
			for (int k = 1; k <= 4; k++) {
				int along = edge + way.getDirection().offset() * k;
				int x = way.getAxis() == Direction.Axis.X ? along : this.hollow.getX();
				int z = way.getAxis() == Direction.Axis.Z ? along : this.hollow.getZ();
				int floor = -3 + (k - 1);
				for (int y = floor; y <= floor + 1; y++) {
					this.blocks.put(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
				}
				this.blocks.put(new BlockPos(x, floor - 1, z), Blocks.ROOTED_DIRT.getDefaultState());
			}
			this.spring = this.random.nextInt(3) == 0;
			if (this.spring) {
				this.blocks.put(new BlockPos(from, -3, from), Blocks.WATER.getDefaultState());
			}
			this.den = this.random.nextInt(3) == 0;
		}

		/** Bee nests under the branches, roosts on the crown's top, saplings round the clearing. */
		void life() {
			List<BlockPos> under = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> entry : this.blocks.entrySet()) {
				BlockPos pos = entry.getKey();
				if (entry.getValue().isOf(this.species.log()) && pos.getY() > this.height * 0.35 && Math.abs(pos.getX()) + Math.abs(pos.getZ()) > this.trunk + 2
						&& !this.blocks.containsKey(pos.down())) {
					under.add(pos.down());
				}
			}
			under.sort((a, b) -> Long.compare(a.asLong(), b.asLong()));
			for (int i = 0; i < 2 + this.random.nextInt(3) && !under.isEmpty(); i++) {
				this.nests.add(under.remove(this.random.nextInt(under.size())));
			}
			List<BlockPos> tops = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> entry : this.blocks.entrySet()) {
				if (entry.getValue().isIn(BlockTags.LEAVES) && !this.blocks.containsKey(entry.getKey().up()) && entry.getKey().getY() > this.height * 0.5) {
					tops.add(entry.getKey().up());
				}
			}
			tops.sort((a, b) -> Long.compare(a.asLong(), b.asLong()));
			for (int i = 0; i < 4 + this.random.nextInt(3) && !tops.isEmpty(); i++) {
				this.roosts.add(tops.remove(this.random.nextInt(tops.size())));
			}
			for (int i = 0; i < 5 + this.random.nextInt(4); i++) {
				double angle = this.random.nextDouble() * Math.PI * 2;
				double r = 7 + this.random.nextDouble() * (this.clearing - 9);
				this.saplings.add(new BlockPos((int) Math.round(Math.cos(angle) * r), 0, (int) Math.round(Math.sin(angle) * r)));
			}
		}
	}

	/** The whole plan of an elder of this species, from its seed. */
	public static Plan plan(Species species, long seed) {
		Plan plan = new Plan(species, seed);
		Random random = Random.create(seed * 31 + 7);
		int h = plan.height;
		switch (species.form()) {
			case DOME, WEEPING -> {
				int top = (int) (h * 0.72);
				plan.trunk(top, -3);
				int limbs = 5 + random.nextInt(3);
				for (int i = 0; i < limbs; i++) {
					double angle = Math.PI * 2 * i / limbs + random.nextDouble() * 0.4;
					double fromY = h * (0.38 + random.nextDouble() * 0.2);
					double reach = h * (0.22 + random.nextDouble() * 0.1);
					double rise = reach * (0.55 + random.nextDouble() * 0.4);
					BlockPos end = plan.branch(0, fromY, 0, Math.cos(angle) * reach, fromY + rise, Math.sin(angle) * reach, h > 60 ? 2 : 1);
					plan.blob(end, 5.5, 3.5, 5.5);
					BlockPos mid = new BlockPos((int) (Math.cos(angle) * reach * 0.55), (int) (fromY + rise * 0.6), (int) (Math.sin(angle) * reach * 0.55));
					plan.blob(mid.up(2), 4, 3, 4);
					// A side twig carrying another tuft.
					double side = angle + (random.nextBoolean() ? 0.7 : -0.7);
					BlockPos twig = plan.branch(mid.getX(), mid.getY(), mid.getZ(), mid.getX() + Math.cos(side) * 5, mid.getY() + 3, mid.getZ() + Math.sin(side) * 5, 1);
					plan.blob(twig, 4, 3, 4);
				}
				plan.blob(new BlockPos(0, top + 2, 0), 6, 4.5, 6);
				if (species.form() == Form.WEEPING) {
					// Curtains of leaves hanging from the crown's rim (as far as they keep their distance to the wood).
					List<BlockPos> rim = new ArrayList<>();
					for (BlockPos leaf : plan.leaves.keySet()) {
						if (!plan.leaves.containsKey(leaf.down()) && !plan.blocks.containsKey(leaf.down()) && leaf.getY() > h * 0.4) {
							rim.add(leaf);
						}
					}
					rim.sort((a, b) -> Long.compare(a.asLong(), b.asLong()));
					for (BlockPos leaf : rim) {
						if (random.nextInt(3) != 0) {
							continue;
						}
						int length = 3 + random.nextInt(6);
						for (int k = 1; k <= length; k++) {
							plan.leaves.putIfAbsent(leaf.down(k), plan.leaves.get(leaf));
						}
					}
				}
			}
			case SPIRE -> {
				plan.trunk(h, -3);
				int start = (int) (h * 0.2);
				double widest = Math.min(14, h * 0.17);
				for (int y = start; y < h - 1; y += 3) {
					double t = (double) (y - start) / (h - start);
					double radius = 1.5 + widest * (1 - t);
					int spokes = radius > 9 ? 12 : radius > 5 ? 8 : 6;
					double turn = random.nextDouble();
					for (int s = 0; s < spokes; s++) {
						double angle = Math.PI * 2 * (s + turn) / spokes;
						double reach = radius - 1.5;
						BlockPos end = plan.branch(0, y, 0, Math.cos(angle) * reach, y - Math.min(2, reach / 4), Math.sin(angle) * reach, 1);
						for (double r = 1; r <= reach; r += 2.5) {
							plan.blob(new BlockPos((int) (Math.cos(angle) * r), y, (int) (Math.sin(angle) * r)), 2.5, 1.3, 2.5);
						}
						plan.blob(end, 2.2, 1.3, 2.2);
					}
				}
				plan.blob(new BlockPos(0, h, 0), 1.5, 2.5, 1.5);
			}
			case COLUMN -> {
				plan.trunk(h, -3);
				int start = (int) (h * 0.42);
				for (int y = start; y < h; y += 2) {
					double t = (double) (y - start) / (h - start);
					double radius = 3 + 2.5 * Math.sin(Math.PI * Math.min(1, t * 1.1));
					double angle = random.nextDouble() * Math.PI * 2;
					BlockPos end = plan.branch(0, y, 0, Math.cos(angle) * (radius - 1), y + 1, Math.sin(angle) * (radius - 1), 1);
					plan.blob(new BlockPos(0, y, 0), radius + plan.trunk / 2.0, 1.6, radius + plan.trunk / 2.0);
					plan.blob(end, 2.5, 2, 2.5);
				}
				plan.blob(new BlockPos(0, h + 1, 0), 2.5, 3, 2.5);
			}
			case UMBRELLA -> {
				int top = (int) (h * 0.62);
				plan.trunk(top, -3);
				int limbs = 3 + random.nextInt(3);
				double canopy = h * 0.82;
				for (int i = 0; i < limbs; i++) {
					double angle = Math.PI * 2 * i / limbs + random.nextDouble() * 0.6;
					double reach = h * (0.2 + random.nextDouble() * 0.08);
					BlockPos end = plan.branch(0, top, 0, Math.cos(angle) * reach, canopy, Math.sin(angle) * reach, h > 60 ? 2 : 1);
					// Spokes under the flat crown, so its leaves stay near wood.
					for (int s = 0; s < 5; s++) {
						double spoke = angle + (s - 2) * 0.6;
						BlockPos tip = plan.branch(end.getX(), end.getY(), end.getZ(), end.getX() + Math.cos(spoke) * 6, end.getY() + 1, end.getZ() + Math.sin(spoke) * 6, 1);
						plan.blob(tip.up(), 5, 1.6, 5);
					}
					plan.blob(end.up(2), 6.5, 2, 6.5);
				}
				plan.blob(new BlockPos(0, (int) canopy + 2, 0), 6, 2, 6);
			}
			case PALM -> {
				// A trunk leaning gently one way, a knob of log stubs at the top, long drooping fronds.
				double lean = random.nextDouble() * Math.PI * 2;
				double bend = 4 + random.nextDouble() * 5;
				BlockPos topPos = BlockPos.ORIGIN;
				for (int y = -3; y <= h; y++) {
					double t = Math.max(0, (double) y / h);
					int ox = (int) Math.round(Math.cos(lean) * bend * t * t);
					int oz = (int) Math.round(Math.sin(lean) * bend * t * t);
					for (int x = -1; x <= 1; x++) {
						for (int z = -1; z <= 1; z++) {
							if (y > h * 0.7 && Math.abs(x) + Math.abs(z) == 2) {
								continue;
							}
							plan.log(ox + x, y, oz + z, Direction.Axis.Y);
						}
					}
					topPos = new BlockPos(ox, y, oz);
				}
				int fronds = 8 + random.nextInt(3);
				for (int f = 0; f < fronds; f++) {
					double angle = Math.PI * 2 * f / fronds + random.nextDouble() * 0.3;
					// A wooden stub out of the crown's knob, then the frond: a line of leaves arching out and drooping, every leaf
					// touching the one before face to face (so each keeps its distance to the wood), a second row under it.
					BlockPos base = plan.branch(topPos.getX(), topPos.getY(), topPos.getZ(), topPos.getX() + Math.cos(angle) * 5, topPos.getY() + 1,
							topPos.getZ() + Math.sin(angle) * 5, 1);
					Block leaf = species.leaves().get(0);
					BlockPos last = base;
					for (int k = 1; k <= 9; k++) {
						double r = 5 + k;
						int droop = (k * k) / 12;
						BlockPos at = new BlockPos(topPos.getX() + (int) Math.round(Math.cos(angle) * r), base.getY() + 1 - droop,
								topPos.getZ() + (int) Math.round(Math.sin(angle) * r));
						// Step one axis at a time from the last leaf to this one.
						BlockPos.Mutable step = last.mutableCopy();
						while (!step.equals(at)) {
							if (step.getX() != at.getX()) {
								step.move(Integer.signum(at.getX() - step.getX()), 0, 0);
							} else if (step.getZ() != at.getZ()) {
								step.move(0, 0, Integer.signum(at.getZ() - step.getZ()));
							} else {
								step.move(0, Integer.signum(at.getY() - step.getY()), 0);
							}
							if (!plan.blocks.containsKey(step)) {
								plan.leaves.put(step.toImmutable(), leaf);
								plan.leaves.putIfAbsent(step.down(), leaf);
								// Broad near its base: a row each side.
								if (k <= 6) {
									Direction side = Direction.fromRotation(Math.toDegrees(angle) + 90);
									plan.leaves.putIfAbsent(step.offset(side), leaf);
									plan.leaves.putIfAbsent(step.offset(side.getOpposite()), leaf);
								}
							}
						}
						last = at;
					}
				}
				plan.blob(topPos.up(2), 3, 2, 3);
			}
			case BOTTLE -> {
				// A fat trunk swelling in the middle, a ring of short thick limbs on top, small tufts.
				int top = (int) (h * 0.7);
				for (int y = -3; y <= top; y++) {
					double t = Math.max(0, (double) y / top);
					double r = plan.trunk / 2.0 + 1.6 * Math.sin(Math.PI * Math.min(1, t * 1.2)) - (t > 0.85 ? 1 : 0);
					for (int x = (int) -r - 1; x <= r + 1; x++) {
						for (int z = (int) -r - 1; z <= r + 1; z++) {
							if (x * x + z * z <= r * r) {
								plan.log(x, y, z, Direction.Axis.Y);
							}
						}
					}
				}
				int limbs = 6 + random.nextInt(3);
				for (int i = 0; i < limbs; i++) {
					double angle = Math.PI * 2 * i / limbs + random.nextDouble() * 0.4;
					double reach = 6 + random.nextDouble() * 4;
					BlockPos end = plan.branch(Math.cos(angle) * 2, top, Math.sin(angle) * 2, Math.cos(angle) * reach, top + 5 + random.nextInt(5),
							Math.sin(angle) * reach, 2);
					plan.blob(end.up(), 3.5, 2.2, 3.5);
				}
			}
		}
		plan.roots();
		plan.settleLeaves();
		plan.hollow();
		plan.life();
		return plan;
	}

	private static final Map<Long, Plan> PLANS = java.util.Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Plan> eldest) {
			return this.size() > 8;
		}
	});

	/** How far an elder reaches round its trunk (generous: it decides which chunks build a part). */
	static int reach() {
		return 44;
	}

	// ------------------------------------------------------------------ building

	/** Builds this chunk's part of the elder (box: the chunk's part of the landmark's box); style is the biome it started in. */
	public static void build(LandmarkPiece piece, StructureWorldAccess world, BlockBox box) {
		var biome = world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.BIOME)
				.getEntry(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.BIOME, Identifier.of(piece.style)));
		if (biome.isEmpty()) {
			return;
		}
		Species chosen = pick(biome.get(), piece.seed);
		if (chosen == null) {
			return;
		}
		Plan plan = PLANS.computeIfAbsent(piece.seed, seed -> plan(chosen, seed));
		Index.of(world.toServerWorld()).add(piece.centre, plan.clearing);
		BlockPos c = piece.centre;
		Random random = Random.create(piece.seed ^ BlockPos.asLong(box.getMinX(), 0, box.getMinZ()));
		BlockPos.Mutable pos = new BlockPos.Mutable();
		// The clearing first (the tree goes over it).
		for (int x = box.getMinX(); x <= box.getMaxX(); x++) {
			for (int z = box.getMinZ(); z <= box.getMaxZ(); z++) {
				int dx = x - c.getX();
				int dz = z - c.getZ();
				if (dx * dx + dz * dz > plan.clearing * plan.clearing) {
					continue;
				}
				int top = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z) - 1;
				BlockState ground = world.getBlockState(pos.set(x, top, z));
				if (ground.isOf(Blocks.GRASS_BLOCK) || ground.isOf(Blocks.DIRT) || ground.isOf(Blocks.COARSE_DIRT) || ground.isOf(Blocks.MYCELIUM)
						|| ground.isOf(Blocks.SNOW_BLOCK) || ground.isOf(Blocks.MUD)) {
					world.setBlockState(pos, random.nextInt(4) == 0 ? Blocks.COARSE_DIRT.getDefaultState() : Blocks.PODZOL.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
				for (int y = top + 1; y <= top + 3; y++) {
					BlockState above = world.getBlockState(pos.set(x, y, z));
					if (!above.isAir() && (above.isReplaceable() || above.isIn(BlockTags.FLOWERS) || above.isIn(BlockTags.SAPLINGS)) && above.getFluidState().isEmpty()) {
						world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
				// Fallen leaves: a few lying on the ground.
				if (random.nextInt(9) == 0 && world.isAir(pos.set(x, top + 1, z)) && world.getBlockState(pos.down()).isOpaqueFullCube(world, pos.down())) {
					Block leaf = chosen.leaves().get(random.nextInt(chosen.leaves().size()));
					world.setBlockState(pos, leaf.getDefaultState().with(LeavesBlock.PERSISTENT, true), Block.NOTIFY_LISTENERS);
				}
			}
		}
		for (BlockPos sapling : plan.saplings) {
			int x = c.getX() + sapling.getX();
			int z = c.getZ() + sapling.getZ();
			if (box.contains(x, box.getMinY() + 1, z)) {
				int top = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z);
				BlockState state = chosen.sapling().getDefaultState();
				if (world.isAir(pos.set(x, top, z)) && state.canPlaceAt(world, pos)) {
					world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
				}
			}
		}
		// The tree.
		for (Map.Entry<BlockPos, BlockState> entry : plan.blocks.entrySet()) {
			BlockPos at = c.add(entry.getKey());
			if (!box.contains(at)) {
				continue;
			}
			BlockState there = world.getBlockState(at);
			BlockState state = entry.getValue();
			if (state.isIn(BlockTags.LEAVES) && !(there.isAir() || there.isReplaceable() || there.isIn(BlockTags.LEAVES))) {
				continue;
			}
			if (there.isOf(Blocks.BEDROCK)) {
				continue;
			}
			world.setBlockState(at, state, Block.NOTIFY_LISTENERS);
		}
		// The trunk carried down to the ground where the land falls away.
		int w = plan.trunk;
		for (int x = plan.lo; x < plan.lo + w; x++) {
			for (int z = plan.lo; z < plan.lo + w; z++) {
				BlockPos.Mutable down = new BlockPos.Mutable(c.getX() + x, c.getY() - 5, c.getZ() + z);
				if (!box.contains(down)) {
					continue;
				}
				for (int k = 0; k < 14 && !world.getBlockState(down).isOpaqueFullCube(world, down); k++) {
					world.setBlockState(down, chosen.log().getDefaultState(), Block.NOTIFY_LISTENERS);
					down.move(Direction.DOWN);
				}
			}
		}
		// Bee nests with bees, roosting birds, a bear in its den.
		for (BlockPos nest : plan.nests) {
			BlockPos at = c.add(nest);
			if (box.contains(at) && world.isAir(at)) {
				Direction facing = Direction.Type.HORIZONTAL.random(random);
				world.setBlockState(at, Blocks.BEE_NEST.getDefaultState().with(BeehiveBlock.FACING, facing), Block.NOTIFY_LISTENERS);
				if (world.getBlockEntity(at) instanceof BeehiveBlockEntity hive) {
					for (int i = 0; i < 2 + random.nextInt(2); i++) {
						hive.addBee(BeehiveBlockEntity.BeeData.create(random.nextInt(599)));
					}
				}
			}
		}
		for (BlockPos roost : plan.roosts) {
			BlockPos at = c.add(roost);
			if (box.contains(at)) {
				spawn(world, Registries.ENTITY_TYPE.get(EternalReturn.id("bird")), at, false);
			}
		}
		BlockPos den = c.add(plan.hollow);
		if (plan.den && box.contains(den)) {
			String bear = biome.get().value().getTemperature() > 0.95F ? "sun_bear" : biome.get().value().getTemperature() < 0.3F ? "brown_bear" : "black_bear";
			spawn(world, Registries.ENTITY_TYPE.get(EternalReturn.id(bear)), den, true);
		}
	}

	private static void spawn(StructureWorldAccess world, EntityType<?> type, BlockPos at, boolean keep) {
		Entity entity = type.create(world.toServerWorld());
		if (entity == null) {
			return;
		}
		entity.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, world.getRandom().nextFloat() * 360, 0);
		if (entity instanceof MobEntity mob) {
			mob.initialize(world, world.getLocalDifficulty(at), SpawnReason.STRUCTURE, null);
			if (keep) {
				mob.setPersistent();
			}
		}
		world.spawnEntityAndPassengers(entity);
	}

	// ------------------------------------------------------------------ the clearings' list

	/** Whether this spot is in an elder's clearing, where ordinary trees don't grow (Eternal Return worlds' overworld). */
	public static boolean inClearing(WorldView view, BlockPos pos) {
		ServerWorld world = view instanceof ServerWorld server ? server : view instanceof StructureWorldAccess access ? access.toServerWorld() : null;
		if (world == null || world.getRegistryKey() != World.OVERWORLD) {
			return false;
		}
		return Index.of(world).clears(pos.getX(), pos.getZ());
	}

	/** The elders of a world (centre and clearing), by 128-block cell, kept in the world's saved data. */
	public static final class Index extends PersistentState {
		private static final PersistentState.Type<Index> TYPE = new PersistentState.Type<>(Index::new, Index::read, null);
		private static final Map<ServerWorld, Index> LOADED = new java.util.WeakHashMap<>();
		private final Map<Long, int[]> all = new ConcurrentHashMap<>();
		private final Map<Long, List<int[]>> cells = new ConcurrentHashMap<>();

		public static Index of(ServerWorld world) {
			synchronized (LOADED) {
				return LOADED.computeIfAbsent(world, w -> w.getPersistentStateManager().getOrCreate(TYPE, EternalReturn.MOD_ID + "_elder_trees"));
			}
		}

		private static Index read(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
			Index index = new Index();
			NbtList list = nbt.getList("elders", NbtElement.INT_ARRAY_TYPE);
			for (int i = 0; i < list.size(); i++) {
				int[] e = list.getIntArray(i);
				index.put(e[0], e[1], e[2]);
			}
			return index;
		}

		@Override
		public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
			NbtList list = new NbtList();
			for (int[] e : this.all.values()) {
				list.add(new net.minecraft.nbt.NbtIntArray(e.clone()));
			}
			nbt.put("elders", list);
			return nbt;
		}

		void add(BlockPos centre, int clearing) {
			long key = BlockPos.asLong(centre.getX(), 0, centre.getZ());
			if (!this.all.containsKey(key)) {
				this.put(centre.getX(), centre.getZ(), clearing);
				this.markDirty();
			}
		}

		private void put(int x, int z, int clearing) {
			long key = BlockPos.asLong(x, 0, z);
			int[] entry = {x, z, clearing};
			if (this.all.putIfAbsent(key, entry) != null) {
				return;
			}
			for (int cx = (x - clearing) >> 7; cx <= (x + clearing) >> 7; cx++) {
				for (int cz = (z - clearing) >> 7; cz <= (z + clearing) >> 7; cz++) {
					this.cells.computeIfAbsent(((long) cx << 32) | (cz & 0xFFFFFFFFL), k -> java.util.Collections.synchronizedList(new ArrayList<>())).add(entry);
				}
			}
		}

		boolean clears(int x, int z) {
			List<int[]> list = this.cells.get(((long) (x >> 7) << 32) | ((z >> 7) & 0xFFFFFFFFL));
			if (list == null) {
				return false;
			}
			synchronized (list) {
				for (int[] e : list) {
					long dx = x - e[0];
					long dz = z - e[1];
					if (dx * dx + dz * dz <= (long) (e[2] + 1) * (e[2] + 1)) {
						return true;
					}
				}
			}
			return false;
		}
	}
}
