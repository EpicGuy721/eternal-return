package com.eternalreturn.worldgen.ores;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.worldgen.stone.NamedBlockItem;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.block.RedstoneOreBlock;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

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
import java.util.Set;

/**
 * Ore variants: every ore in every host stone, block eternalreturn:host_ore_ore (granite_iron_ore). The
 * table is src/main/resources/eternalreturn/ore_variants.json, which tools/ores/build_ores.py also reads
 * to write the variants' models, textures, loot tables, recipes and tags, so a new ore or host stone is
 * one entry there. Each variant is built from its vanilla stone ore: the same block class (experience-
 * dropping or redstone ore, with the same experience), and a copy of its settings (hardness, blast
 * resistance, tool, sounds, light), with the host's map colour. Its name is the host's name followed by
 * the ore's ("Granite Iron Ore"), from one language line.
 */
public final class OreVariants {
	public record Ore(String name, Block vanilla, Block deepslate) {
	}

	public record Host(String name, Block block) {
	}

	public record Variant(Ore ore, Host host, Block block) {
	}

	private static final List<Ore> ORES = new ArrayList<>();
	private static final List<Host> HOSTS = new ArrayList<>();
	private static final List<Variant> VARIANTS = new ArrayList<>();
	/** Vanilla ore block (stone or deepslate version) to host block to variant. */
	private static final Map<Block, Map<Block, Block>> BY_ORE_AND_HOST = new HashMap<>();
	private static final Map<Block, Host> HOST_BY_BLOCK = new LinkedHashMap<>();

	private OreVariants() {
	}

	public static void register() {
		JsonObject table = readTable();
		table.getAsJsonObject("ores").entrySet().forEach(entry -> {
			JsonObject ore = entry.getValue().getAsJsonObject();
			ORES.add(new Ore(entry.getKey(), block(ore.get("block").getAsString()), block(ore.get("deepslate").getAsString())));
		});
		table.getAsJsonObject("hosts").entrySet().forEach(entry ->
				HOSTS.add(new Host(entry.getKey(), block(entry.getValue().getAsJsonObject().get("block").getAsString()))));
		for (Host host : HOSTS) {
			HOST_BY_BLOCK.put(host.block(), host);
			for (Ore ore : ORES) {
				String name = host.name() + "_" + ore.name() + "_ore";
				Block block = Registry.register(Registries.BLOCK, EternalReturn.id(name), create(ore, host));
				Registry.register(Registries.ITEM, EternalReturn.id(name), new NamedBlockItem(block));
				VARIANTS.add(new Variant(ore, host, block));
				BY_ORE_AND_HOST.computeIfAbsent(ore.vanilla(), key -> new HashMap<>()).put(host.block(), block);
				BY_ORE_AND_HOST.computeIfAbsent(ore.deepslate(), key -> new HashMap<>()).put(host.block(), block);
			}
		}
		// Creative inventory: each ore's variants right after its deepslate version, in the natural blocks tab.
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> {
			for (Ore ore : ORES) {
				entries.addAfter(ore.deepslate(), VARIANTS.stream().filter(variant -> variant.ore() == ore).map(Variant::block).toArray(Block[]::new));
			}
		});
	}

	private static Block create(Ore ore, Host host) {
		AbstractBlock.Settings settings = AbstractBlock.Settings.copy(ore.vanilla()).mapColor(host.block().getDefaultMapColor());
		if (ore.vanilla() instanceof RedstoneOreBlock) {
			return new VariantRedstoneOreBlock(ore, host, settings);
		}
		if (ore.vanilla() instanceof ExperienceDroppingBlock vanilla) {
			return new VariantOreBlock(ore, host, vanilla.experienceDropped, settings);
		}
		throw new IllegalStateException("ore_variants.json: " + Registries.BLOCK.getId(ore.vanilla()) + " is neither an experience-dropping block nor a redstone ore");
	}

	private static Block block(String id) {
		Block block = Registries.BLOCK.get(Identifier.of(id));
		if (block == Blocks.AIR) {
			throw new IllegalStateException("ore_variants.json names unknown block " + id);
		}
		return block;
	}

	private static JsonObject readTable() {
		try (InputStream in = OreVariants.class.getResourceAsStream("/eternalreturn/ore_variants.json")) {
			if (in == null) {
				throw new IllegalStateException("eternalreturn/ore_variants.json missing from the mod jar");
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			}
		} catch (IOException e) {
			throw new IllegalStateException("cannot read eternalreturn/ore_variants.json", e);
		}
	}

	public static List<Variant> variants() {
		return Collections.unmodifiableList(VARIANTS);
	}

	public static List<Ore> ores() {
		return Collections.unmodifiableList(ORES);
	}

	public static List<Host> hosts() {
		return Collections.unmodifiableList(HOSTS);
	}

	public static Set<Block> hostBlocks() {
		return Collections.unmodifiableSet(HOST_BY_BLOCK.keySet());
	}

	public static boolean isHost(BlockState state) {
		return HOST_BY_BLOCK.containsKey(state.getBlock());
	}

	/** The variant of an ore for the block it replaces, or the ore unchanged when that block is not a host stone. */
	public static BlockState variantFor(BlockState ore, BlockState replaced) {
		Map<Block, Block> byHost = BY_ORE_AND_HOST.get(ore.getBlock());
		if (byHost == null) {
			return ore;
		}
		Block variant = byHost.get(replaced.getBlock());
		if (variant == null) {
			return ore;
		}
		BlockState state = variant.getDefaultState();
		if (ore.contains(RedstoneOreBlock.LIT)) {
			state = state.with(RedstoneOreBlock.LIT, ore.get(RedstoneOreBlock.LIT));
		}
		return state;
	}

	static MutableText name(Ore ore, Host host) {
		return Text.translatable("block.eternalreturn.ore_variant", host.block().getName(), ore.vanilla().getName());
	}

	/** An ore variant that drops experience like its vanilla ore (every ore but redstone). */
	public static class VariantOreBlock extends ExperienceDroppingBlock {
		private final Ore ore;
		private final Host host;

		public VariantOreBlock(Ore ore, Host host, net.minecraft.util.math.intprovider.IntProvider experience, AbstractBlock.Settings settings) {
			super(experience, settings);
			this.ore = ore;
			this.host = host;
		}

		@Override
		public MutableText getName() {
			return name(this.ore, this.host);
		}
	}

	/** A redstone ore variant: lights up when touched, with vanilla's particles and light, like redstone ore. */
	public static class VariantRedstoneOreBlock extends RedstoneOreBlock {
		private final Ore ore;
		private final Host host;

		public VariantRedstoneOreBlock(Ore ore, Host host, AbstractBlock.Settings settings) {
			super(settings);
			this.ore = ore;
			this.host = host;
		}

		@Override
		public MutableText getName() {
			return name(this.ore, this.host);
		}
	}
}
