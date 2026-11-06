package com.eternalreturn.blocks;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.block.enums.Thickness;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stone dripstone (owner's request, 2026-11-05): pointed dripstone of 28 stones (stone, deepslate, andesite, diorite,
 * granite, tuff, sandstone, red sandstone, terracotta and its 16 colours, netherrack, blackstone, basalt), from the table
 * eternalreturn/stone_dripstones.json; resources from tools/dripstone/build_dripstone.py. Each behaves as vanilla's
 * pointed dripstone (StoneDripstoneBlock). In Eternal Return worlds the feature eternalreturn:stone_dripstone puts small
 * clusters in caves (the overworld's and the Nether's), each piece of the stone it hangs from or stands on (an ore
 * counts as its host stone).
 */
public final class StoneDripstones {
	/** Table entry name to block, in table order. */
	private static final Map<String, StoneDripstoneBlock> BLOCKS = new LinkedHashMap<>();
	/** A host stone (or an ore in it) to the dripstone of that stone; filled on first use, after every block exists. */
	private static volatile Map<Block, StoneDripstoneBlock> byHost;
	private static final Map<String, List<Identifier>> HOST_IDS = new HashMap<>();

	public static final Feature<FeatureConfigImpl> FEATURE = Registry.register(Registries.FEATURE, EternalReturn.id("stone_dripstone"), new ClusterFeature());

	static {
		JsonObject table = readTable();
		for (Map.Entry<String, JsonElement> entry : table.entrySet()) {
			if (entry.getKey().startsWith("_")) {
				continue;
			}
			JsonObject spec = entry.getValue().getAsJsonObject();
			String kind = entry.getKey();
			StoneDripstoneBlock block = new StoneDripstoneBlock(AbstractBlock.Settings.create().mapColor(mapColor(spec.get("map_color").getAsString()))
					.instrument(NoteBlockInstrument.BASEDRUM).nonOpaque().sounds(BlockSoundGroup.POINTED_DRIPSTONE).ticksRandomly().strength(1.5F, 3.0F)
					.dynamicBounds().offset(AbstractBlock.OffsetType.XZ).pistonBehavior(PistonBehavior.DESTROY).solidBlock((state, world, pos) -> false), kind);
			Registry.register(Registries.BLOCK, EternalReturn.id(kind + "_dripstone"), block);
			Registry.register(Registries.ITEM, EternalReturn.id(kind + "_dripstone"), new BlockItem(block, new Item.Settings()));
			BLOCKS.put(kind, block);
			List<Identifier> hosts = new ArrayList<>();
			spec.getAsJsonArray("hosts").forEach(id -> hosts.add(Identifier.of(id.getAsString())));
			HOST_IDS.put(kind, hosts);
		}
	}

	private StoneDripstones() {
	}

	private static MapColor mapColor(String name) {
		return switch (name) {
			case "deepslate_gray" -> MapColor.DEEPSLATE_GRAY;
			case "off_white" -> MapColor.OFF_WHITE;
			case "dirt_brown" -> MapColor.DIRT_BROWN;
			case "pale_yellow" -> MapColor.PALE_YELLOW;
			case "orange" -> MapColor.ORANGE;
			case "dark_red" -> MapColor.DARK_RED;
			case "black" -> MapColor.BLACK;
			case "terracotta_white" -> MapColor.TERRACOTTA_WHITE;
			case "terracotta_orange" -> MapColor.TERRACOTTA_ORANGE;
			case "terracotta_magenta" -> MapColor.TERRACOTTA_MAGENTA;
			case "terracotta_light_blue" -> MapColor.TERRACOTTA_LIGHT_BLUE;
			case "terracotta_yellow" -> MapColor.TERRACOTTA_YELLOW;
			case "terracotta_lime" -> MapColor.TERRACOTTA_LIME;
			case "terracotta_pink" -> MapColor.TERRACOTTA_PINK;
			case "terracotta_gray" -> MapColor.TERRACOTTA_GRAY;
			case "terracotta_light_gray" -> MapColor.TERRACOTTA_LIGHT_GRAY;
			case "terracotta_cyan" -> MapColor.TERRACOTTA_CYAN;
			case "terracotta_purple" -> MapColor.TERRACOTTA_PURPLE;
			case "terracotta_blue" -> MapColor.TERRACOTTA_BLUE;
			case "terracotta_brown" -> MapColor.TERRACOTTA_BROWN;
			case "terracotta_green" -> MapColor.TERRACOTTA_GREEN;
			case "terracotta_red" -> MapColor.TERRACOTTA_RED;
			case "terracotta_black" -> MapColor.TERRACOTTA_BLACK;
			default -> MapColor.STONE_GRAY;
		};
	}

	private static JsonObject readTable() {
		try (InputStream in = StoneDripstones.class.getResourceAsStream("/eternalreturn/stone_dripstones.json")) {
			if (in == null) {
				throw new IllegalStateException("eternalreturn/stone_dripstones.json missing from the mod jar");
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			}
		} catch (IOException e) {
			throw new IllegalStateException("cannot read eternalreturn/stone_dripstones.json", e);
		}
	}

	public static Map<String, StoneDripstoneBlock> all() {
		return Collections.unmodifiableMap(BLOCKS);
	}

	public static StoneDripstoneBlock get(String kind) {
		return BLOCKS.get(kind);
	}

	private static Map<Block, StoneDripstoneBlock> hosts() {
		Map<Block, StoneDripstoneBlock> map = byHost;
		if (map != null) {
			return map;
		}
		map = new HashMap<>();
		for (Map.Entry<String, StoneDripstoneBlock> entry : BLOCKS.entrySet()) {
			for (Identifier id : HOST_IDS.get(entry.getKey())) {
				Block block = Registries.BLOCK.get(id);
				if (block != Blocks.AIR) {
					map.put(block, entry.getValue());
				}
			}
		}
		// Ores count as the stone they sit in: vanilla's (deepslate ores in deepslate, the Nether's in netherrack, the
		// rest in stone) and this mod's host stone variants (OreVariants).
		for (Block block : Registries.BLOCK) {
			Identifier id = Registries.BLOCK.getId(block);
			if (id.getNamespace().equals("minecraft") && id.getPath().endsWith("_ore")) {
				String kind = id.getPath().startsWith("deepslate_") ? "deepslate" : id.getPath().startsWith("nether_") ? "netherrack" : "stone";
				map.put(block, BLOCKS.get(kind));
			}
		}
		for (com.eternalreturn.worldgen.ores.OreVariants.Variant variant : com.eternalreturn.worldgen.ores.OreVariants.variants()) {
			StoneDripstoneBlock host = map.get(variant.host().block());
			if (host != null) {
				map.put(variant.block(), host);
			}
		}
		byHost = map;
		return map;
	}

	/** The dripstone that hangs from or stands on this block, or null (dirt, gravel, ice and the like get none). */
	@Nullable
	public static StoneDripstoneBlock forHost(Block block) {
		return hosts().get(block);
	}

	public static boolean isHostOf(Block host, StoneDripstoneBlock dripstone) {
		return hosts().get(host) == dripstone;
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries ->
				entries.addAfter(Items.POINTED_DRIPSTONE, BLOCKS.values().stream().map(Block::asItem).toArray(Item[]::new)));
		// Caves of Eternal Return worlds only (the placed features carry eternalreturn:eternal_return_only), last of the
		// underground decoration so no other feature's place in the order moves.
		BiomeModifications.addFeature(com.eternalreturn.worldgen.WorldgenFeatures.overworld().and(context -> EternalReturnConfig.get().worldgen.stoneDripstone),
				GenerationStep.Feature.UNDERGROUND_DECORATION, RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalReturn.id("stone_dripstone")));
		BiomeModifications.addFeature(BiomeSelectors.foundInTheNether().and(context -> EternalReturnConfig.get().worldgen.stoneDripstone),
				GenerationStep.Feature.UNDERGROUND_DECORATION, RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalReturn.id("nether_stone_dripstone")));
	}

	// ------------------------------------------------------------------ the cave feature

	/**
	 * A cluster round the spot (which must be underground, in air, with a ceiling within 16 above): "tries" columns
	 * within "spread" blocks; each finds the ceiling above it and, by chance "floor_chance", the floor below, and hangs a
	 * stalactite (or stands a stalagmite) of the stone it touches, 1 to "max_length" long, mostly short. Where the two
	 * meet, their tips merge.
	 */
	public record FeatureConfigImpl(int tries, int spread, int maxLength, float floorChance) implements FeatureConfig {
		public static final Codec<FeatureConfigImpl> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.intRange(1, 64).fieldOf("tries").forGetter(FeatureConfigImpl::tries),
				Codec.intRange(0, 7).fieldOf("spread").forGetter(FeatureConfigImpl::spread),
				Codec.intRange(1, 7).fieldOf("max_length").forGetter(FeatureConfigImpl::maxLength),
				Codec.floatRange(0, 1).fieldOf("floor_chance").forGetter(FeatureConfigImpl::floorChance)).apply(instance, FeatureConfigImpl::new));
	}

	public static final class ClusterFeature extends Feature<FeatureConfigImpl> {
		ClusterFeature() {
			super(FeatureConfigImpl.CODEC);
		}

		private static boolean open(StructureWorldAccess world, BlockPos pos) {
			BlockState state = world.getBlockState(pos);
			return state.isAir() || state.isOf(Blocks.CAVE_AIR);
		}

		@Override
		public boolean generate(FeatureContext<FeatureConfigImpl> context) {
			StructureWorldAccess world = context.getWorld();
			BlockPos origin = context.getOrigin();
			Random random = context.getRandom();
			FeatureConfigImpl config = context.getConfig();
			if (!open(world, origin) || origin.getY() >= world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, origin.getX(), origin.getZ()) - 3
					|| ceiling(world, origin) == null) {
				return false;
			}
			boolean placed = false;
			for (int i = 0; i < config.tries(); i++) {
				int s = config.spread();
				BlockPos start = origin.add(random.nextInt(2 * s + 1) - s, random.nextInt(5) - 2, random.nextInt(2 * s + 1) - s);
				if (!open(world, start) || start.getY() >= world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, start.getX(), start.getZ()) - 3) {
					continue;
				}
				BlockPos top = ceiling(world, start);
				int down = 0;
				if (top != null) {
					down = this.hang(world, top, Direction.DOWN, length(random, config.maxLength()));
					placed |= down > 0;
				}
				if (random.nextFloat() < config.floorChance()) {
					BlockPos bottom = floor(world, start);
					if (bottom != null) {
						placed |= this.hang(world, bottom, Direction.UP, length(random, config.maxLength())) > 0;
					}
				}
			}
			return placed;
		}

		/** 1 to most long, short ones likelier. */
		private static int length(Random random, int most) {
			return 1 + Math.min(random.nextInt(most), random.nextInt(most));
		}

		/** The first air block under a solid ceiling within 16 above, or null. */
		@Nullable
		private static BlockPos ceiling(StructureWorldAccess world, BlockPos from) {
			BlockPos.Mutable at = from.mutableCopy();
			for (int i = 0; i < 16; i++) {
				if (!open(world, at.up())) {
					return world.getBlockState(at.up()).isSideSolidFullSquare(world, at.up(), Direction.DOWN) ? at.toImmutable() : null;
				}
				at.move(Direction.UP);
			}
			return null;
		}

		@Nullable
		private static BlockPos floor(StructureWorldAccess world, BlockPos from) {
			BlockPos.Mutable at = from.mutableCopy();
			for (int i = 0; i < 16; i++) {
				if (!open(world, at.down())) {
					return world.getBlockState(at.down()).isSideSolidFullSquare(world, at.down(), Direction.UP) ? at.toImmutable() : null;
				}
				at.move(Direction.DOWN);
			}
			return null;
		}

		/** A column of the stone behind "first" pointing that way, into open air only; merges tips it runs into. */
		private int hang(StructureWorldAccess world, BlockPos first, Direction pointing, int length) {
			StoneDripstoneBlock block = forHost(world.getBlockState(first.offset(pointing.getOpposite())).getBlock());
			if (block == null) {
				return 0;
			}
			int fits = 0;
			BlockPos.Mutable at = first.mutableCopy();
			while (fits < length && open(world, at)) {
				fits++;
				at.move(pointing);
			}
			if (fits == 0) {
				return 0;
			}
			// Leave a gap of air above the floor (or below the ceiling) unless it meets a tip of the same stone.
			BlockState beyond = world.getBlockState(first.offset(pointing, fits));
			boolean merge = fits < length && block.isPiece(beyond, pointing.getOpposite()) && beyond.get(StoneDripstoneBlock.THICKNESS) == Thickness.TIP;
			if (!merge && fits < length && fits > 1) {
				fits--;
			}
			for (int i = 0; i < fits; i++) {
				Thickness thickness = i == fits - 1 ? (merge ? Thickness.TIP_MERGE : Thickness.TIP)
						: i == fits - 2 ? Thickness.FRUSTUM : i == 0 ? Thickness.BASE : Thickness.MIDDLE;
				world.setBlockState(first.offset(pointing, i), block.getDefaultState().with(StoneDripstoneBlock.VERTICAL_DIRECTION, pointing)
						.with(StoneDripstoneBlock.THICKNESS, thickness), Block.NOTIFY_LISTENERS);
			}
			if (merge) {
				world.setBlockState(first.offset(pointing, fits), beyond.with(StoneDripstoneBlock.THICKNESS, Thickness.TIP_MERGE), Block.NOTIFY_LISTENERS);
			}
			return fits;
		}
	}
}
