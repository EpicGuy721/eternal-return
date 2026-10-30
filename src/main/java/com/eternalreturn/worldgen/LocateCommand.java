package com.eternalreturn.worldgen;

import com.eternalreturn.worldgen.caves.CaveWorlds;
import com.eternalreturn.worldgen.end.Megastructures;
import com.eternalreturn.worldgen.end.OuterEnd;
import com.eternalreturn.worldgen.end.OuterEndTerrain;
import com.eternalreturn.worldgen.terrain.FloatingIslands;
import com.eternalreturn.worldgen.terrain.Monoliths;
import com.eternalreturn.worldgen.terrain.TerrainShape;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.jetbrains.annotations.Nullable;

/**
 * /eternalreturn locate monolith|sinkhole|floating_island|end_island|end_megastructure [massive] (operators): the nearest of these terrain features to the
 * player, in an Eternal Return world, worked out from the world seed as the terrain does (nothing is generated). The
 * coordinates can be clicked to fill in a /tp.
 */
public final class LocateCommand {
	/** How far each search reaches, in blocks. */
	private static final int MONOLITH_REACH = 3 * Monoliths.CELL;
	private static final int SINKHOLE_REACH = 8 * TerrainShape.SINKHOLE_CELL;
	private static final int ISLAND_REACH = 2 * FloatingIslands.CELL;

	private LocateCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(CommandManager.literal("eternalreturn")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.literal("locate")
						.then(CommandManager.literal("monolith").executes(context -> report(context.getSource(), "monolith", monolith(context.getSource()))))
						.then(CommandManager.literal("sinkhole").executes(context -> report(context.getSource(), "sinkhole", sinkhole(context.getSource()))))
						.then(CommandManager.literal("floating_island")
								.executes(context -> report(context.getSource(), "floating_island", island(context.getSource()))))
						.then(CommandManager.literal("end_island").executes(context -> endIsland(context.getSource())))
						.then(CommandManager.literal("end_megastructure").executes(context -> endMegastructure(context.getSource(), false))
								.then(CommandManager.literal("massive").executes(context -> endMegastructure(context.getSource(), true)))))));
	}

	/** Where to measure from in the End: the player there, or 0, 0 from another dimension. */
	private static BlockPos endOrigin(ServerCommandSource source) {
		return source.getWorld().getRegistryKey() == World.END ? BlockPos.ofFloored(source.getPosition()) : BlockPos.ORIGIN;
	}

	private static int reportEnd(ServerCommandSource source, String kind, @Nullable BlockPos found, BlockPos target, Object... extra) {
		BlockPos at = endOrigin(source);
		int distance = MathHelper.floor(Math.sqrt(at.getSquaredDistance(found.getX(), at.getY(), found.getZ())));
		String tp = "/execute in minecraft:the_end run tp @s " + target.getX() + " " + target.getY() + " " + target.getZ();
		Text coordinates = Texts.bracketed(Text.translatable("chat.coordinates", found.getX(), found.getY(), found.getZ())).styled(style -> style
				.withColor(Formatting.GREEN)
				.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tp))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("chat.coordinates.tooltip"))));
		Object[] args = new Object[2 + extra.length];
		args[0] = coordinates;
		args[1] = distance;
		System.arraycopy(extra, 0, args, 2, extra.length);
		source.sendFeedback(() -> Text.translatable("commands.eternalreturn.locate." + kind, args), false);
		return Math.max(1, distance);
	}

	/** The nearest main island of the outer End: its middle, on top. Clicking fills in a /tp there. */
	private static int endIsland(ServerCommandSource source) {
		ServerWorld end = source.getServer().getWorld(World.END);
		OuterEndTerrain terrain = end == null ? null : OuterEnd.terrain(end);
		if (terrain == null) {
			source.sendError(Text.translatable("commands.eternalreturn.locate.not_eternal_return_end"));
			return 0;
		}
		BlockPos at = endOrigin(source);
		BlockPos best = null;
		int reach = 2000;
		int cell = OuterEndTerrain.CELL;
		for (int cx = Math.floorDiv(at.getX() - reach, cell); cx <= Math.floorDiv(at.getX() + reach, cell); cx++) {
			for (int cz = Math.floorDiv(at.getZ() - reach, cell); cz <= Math.floorDiv(at.getZ() + reach, cell); cz++) {
				for (OuterEndTerrain.Island island : terrain.mainIslands(cx, cz)) {
					if (nearer(at, (int) island.x, (int) island.z, best)) {
						best = new BlockPos((int) island.x, island.level, (int) island.z);
					}
				}
			}
		}
		if (best == null) {
			source.sendError(Text.translatable("commands.eternalreturn.locate.end_island.none"));
			return 0;
		}
		int top = terrain.surface(best.getX(), best.getZ());
		BlockPos found = new BlockPos(best.getX(), top == Integer.MIN_VALUE ? best.getY() : top + 1, best.getZ());
		return reportEnd(source, "end_island", found, found);
	}

	/**
	 * The nearest obsidian megastructure (or massive one): its middle. Clicking fills in a /tp to a spot in the open
	 * beside it, level with its middle, from where it can be seen whole (fly there: it is often over the void).
	 */
	private static int endMegastructure(ServerCommandSource source, boolean massive) {
		ServerWorld end = source.getServer().getWorld(World.END);
		Megastructures megastructures = end == null ? null : Megastructures.of(end);
		if (megastructures == null) {
			source.sendError(Text.translatable("commands.eternalreturn.locate.not_eternal_return_end"));
			return 0;
		}
		BlockPos at = endOrigin(source);
		Megastructures.Structure best = null;
		int reach = 4000;
		int cell = megastructures.settings().spacing();
		for (int cx = Math.floorDiv(at.getX() - reach, cell); cx <= Math.floorDiv(at.getX() + reach, cell); cx++) {
			for (int cz = Math.floorDiv(at.getZ() - reach, cell); cz <= Math.floorDiv(at.getZ() + reach, cell); cz++) {
				Megastructures.Structure structure = megastructures.inCell(cx, cz);
				if (structure != null && (!massive || structure.massive)
						&& (best == null || at.getSquaredDistance(structure.x, at.getY(), structure.z) < at.getSquaredDistance(best.x, at.getY(), best.z))) {
					best = structure;
				}
			}
		}
		if (best == null) {
			source.sendError(Text.translatable("commands.eternalreturn.locate.end_megastructure.none"));
			return 0;
		}
		BlockPos found = BlockPos.ofFloored(best.x, best.y, best.z);
		// Stand back on the side towards the player, far enough to see it whole.
		double dx = at.getX() - best.x, dz = at.getZ() - best.z;
		double length = Math.max(1, Math.sqrt(dx * dx + dz * dz));
		double back = best.size * 2.2 + 16;
		BlockPos view = BlockPos.ofFloored(best.x + dx / length * back, Math.min(240, best.y + best.size * 0.3), best.z + dz / length * back);
		String what = best.kind.name().toLowerCase(java.util.Locale.ROOT) + ", " + best.setting.name().toLowerCase(java.util.Locale.ROOT)
				+ (best.massive ? ", massive" : "");
		return reportEnd(source, "end_megastructure", found, view, what);
	}

	private static boolean eternalReturn(ServerWorld world) {
		return world.getRegistryKey() == World.OVERWORLD && world.getChunkManager().getChunkGenerator() instanceof NoiseChunkGenerator generator
				&& CaveWorlds.usesEternalReturnSettings(generator, world.getRegistryManager());
	}

	private static int report(ServerCommandSource source, String kind, @Nullable BlockPos found) {
		if (!eternalReturn(source.getWorld())) {
			source.sendError(Text.translatable("commands.eternalreturn.locate.not_eternal_return"));
			return 0;
		}
		if (found == null) {
			source.sendError(Text.translatable("commands.eternalreturn.locate." + kind + ".none"));
			return 0;
		}
		BlockPos at = BlockPos.ofFloored(source.getPosition());
		int distance = MathHelper.floor(Math.sqrt(at.getSquaredDistance(found.getX(), at.getY(), found.getZ())));
		Text coordinates = Texts.bracketed(Text.translatable("chat.coordinates", found.getX(), found.getY(), found.getZ())).styled(style -> style
				.withColor(Formatting.GREEN)
				.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + found.getX() + " " + found.getY() + " " + found.getZ()))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("chat.coordinates.tooltip"))));
		source.sendFeedback(() -> Text.translatable("commands.eternalreturn.locate." + kind, coordinates, distance), false);
		return distance;
	}

	private static boolean nearer(BlockPos at, int x, int z, @Nullable BlockPos best) {
		return best == null || at.getSquaredDistance(x, at.getY(), z) < at.getSquaredDistance(best.getX(), at.getY(), best.getZ());
	}

	/** The nearest monolith: its middle, at its top. */
	@Nullable
	public static BlockPos monolith(ServerCommandSource source) {
		ServerWorld world = source.getWorld();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		BlockPos at = BlockPos.ofFloored(source.getPosition());
		BlockPos best = null;
		for (int cx = Math.floorDiv(at.getX() - MONOLITH_REACH, Monoliths.CELL); cx <= Math.floorDiv(at.getX() + MONOLITH_REACH, Monoliths.CELL); cx++) {
			for (int cz = Math.floorDiv(at.getZ() - MONOLITH_REACH, Monoliths.CELL); cz <= Math.floorDiv(at.getZ() + MONOLITH_REACH, Monoliths.CELL); cz++) {
				Monoliths.Monolith monolith = Monoliths.inCell(noise, cx, cz);
				if (nearer(at, monolith.x(), monolith.z(), best)) {
					best = new BlockPos(monolith.x(), monolith.top() + 1, monolith.z());
				}
			}
		}
		return best;
	}

	/**
	 * The nearest sinkhole that is made (in the sea, or on land one time in five, never on a river or beach): its
	 * middle, at sea level.
	 */
	@Nullable
	public static BlockPos sinkhole(ServerCommandSource source) {
		ServerWorld world = source.getWorld();
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		int sea = generator.getSeaLevel();
		BlockPos at = BlockPos.ofFloored(source.getPosition());
		BlockPos best = null;
		int cell = TerrainShape.SINKHOLE_CELL;
		for (int cx = Math.floorDiv(at.getX() - SINKHOLE_REACH, cell); cx <= Math.floorDiv(at.getX() + SINKHOLE_REACH, cell); cx++) {
			for (int cz = Math.floorDiv(at.getZ() - SINKHOLE_REACH, cell); cz <= Math.floorDiv(at.getZ() + SINKHOLE_REACH, cell); cz++) {
				TerrainShape.Sinkhole hole = TerrainShape.plannedSinkhole(world.getSeed(), cx, cz);
				if (hole == null || !nearer(at, hole.x(), hole.z(), best)) {
					continue;
				}
				String biome = generator.getBiomeSource().getBiome(BiomeCoords.fromBlock(hole.x()), BiomeCoords.fromBlock(sea), BiomeCoords.fromBlock(hole.z()),
						noise.getMultiNoiseSampler()).getKey().map(key -> key.getValue().toString()).orElse("");
				if (TerrainShape.keeps(hole, biome)) {
					best = new BlockPos(hole.x(), sea, hole.z());
				}
			}
		}
		return best;
	}

	/** The nearest floating island: its middle, on top. */
	@Nullable
	public static BlockPos island(ServerCommandSource source) {
		NoiseConfig noise = source.getWorld().getChunkManager().getNoiseConfig();
		BlockPos at = BlockPos.ofFloored(source.getPosition());
		BlockPos best = null;
		int cell = FloatingIslands.CELL;
		for (int cx = Math.floorDiv(at.getX() - ISLAND_REACH, cell); cx <= Math.floorDiv(at.getX() + ISLAND_REACH, cell); cx++) {
			for (int cz = Math.floorDiv(at.getZ() - ISLAND_REACH, cell); cz <= Math.floorDiv(at.getZ() + ISLAND_REACH, cell); cz++) {
				for (FloatingIslands.Island island : FloatingIslands.inCell(noise, cx, cz)) {
					if (nearer(at, island.x(), island.z(), best)) {
						best = new BlockPos(island.x(), island.top() + 1, island.z());
					}
				}
			}
		}
		return best;
	}
}
