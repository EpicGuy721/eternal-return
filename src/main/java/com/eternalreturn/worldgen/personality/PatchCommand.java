package com.eternalreturn.worldgen.personality;

import com.eternalreturn.birds.BirdTable;
import com.eternalreturn.worldgen.trees.BiomeTrees;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /eternalreturn patch: the personality roll of the patch where the command is run (Personality): its biome family and
 * cell, whether it's a rare one, its density, its tree mix, its flowers, its tint and its common birds. For checking.
 */
public final class PatchCommand {
	private PatchCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(CommandManager.literal("eternalreturn")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.literal("patch").executes(context -> report(context.getSource())))));
	}

	private static int report(ServerCommandSource source) {
		long salt = PersonalitySync.salt(source.getServer());
		if (salt == 0) {
			source.sendError(Text.literal("Personality rolls are only in Eternal Return worlds, with worldgen.personalityRolls on."));
			return 0;
		}
		BlockPos pos = BlockPos.ofFloored(source.getPosition());
		RegistryEntry<Biome> biome = source.getWorld().getBiome(pos);
		Identifier id = biome.getKey().map(key -> key.getValue()).orElse(null);
		if (id == null) {
			return 0;
		}
		Personality.Roll roll = Personality.at(salt, id, pos.getX(), pos.getZ());
		List<String> lines = new ArrayList<>();
		lines.add("Patch of " + roll.family() + " (cell " + (pos.getX() >> Personality.CELL_SHIFT) + ", " + (pos.getZ() >> Personality.CELL_SHIFT) + ")"
				+ (roll.rare() ? ", a rare one: the biome at its most itself" : ""));
		lines.add(String.format(Locale.ROOT, "Density: %.0f%% of normal (trees, grass, boulders)", roll.density() * 100));
		for (Map.Entry<Identifier, List<BiomeTrees.Choice>> list : BiomeTrees.ownLists(biome).entrySet()) {
			List<BiomeTrees.Choice> choices = list.getValue();
			int[] weights = choices.stream().mapToInt(BiomeTrees.Choice::weight).toArray();
			double[] scaled = roll.canopy(choices.stream().map(BiomeTrees::name).toList(), weights);
			double total = 0;
			for (double weight : scaled) {
				total += weight;
			}
			StringBuilder line = new StringBuilder("Trees from " + list.getKey().getPath() + ":");
			for (int i = 0; i < choices.size(); i++) {
				line.append(String.format(Locale.ROOT, " %s %.0f%%", BiomeTrees.name(choices.get(i)).replace("eternalreturn:tree/", ""), scaled[i] / total * 100));
			}
			lines.add(line.toString());
		}
		List<List<String>> flowers = PersonalityFeatures.describeFlowers(biome.value(), roll);
		lines.add("Flowers: " + names(flowers.get(1)) + " (of " + names(flowers.get(0)) + ")");
		if (!flowers.get(2).isEmpty()) {
			lines.add("Tall flowers: " + names(flowers.get(3)) + " (of " + names(flowers.get(2)) + ")");
		}
		float[] tint = roll.tint();
		lines.add(String.format(Locale.ROOT, "Tint: hue %+.1f%%, saturation %+.1f%%, brightness %+.1f%%",
				tint[0] * Personality.TINT_HUE * 100, tint[1] * Personality.TINT_VALUE * 100, tint[2] * Personality.TINT_VALUE * 100));
		List<String> common = new ArrayList<>();
		for (BirdTable.Preset preset : BirdTable.PRESETS) {
			if (preset.habitats().stream().anyMatch(biome::isIn) && roll.commonBird(preset.id())) {
				common.add(preset.id());
			}
		}
		lines.add("Common birds: " + (common.isEmpty() ? "none live here" : String.join(", ", common)));
		String accent = AccentFeature.accentOf(roll, id);
		lines.add("Accent: " + (accent == null ? "none" : accent.replace('_', ' ')));
		for (String line : lines) {
			source.sendFeedback(() -> Text.literal(line), false);
		}
		return 1;
	}

	private static String names(List<String> ids) {
		return ids.isEmpty() ? "none" : String.join(", ", ids.stream().map(id -> id.substring(id.indexOf(':') + 1)).toList());
	}
}
