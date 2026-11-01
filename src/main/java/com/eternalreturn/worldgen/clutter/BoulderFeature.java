package com.eternalreturn.worldgen.clutter;

import com.eternalreturn.worldgen.stone.BiomeStoneProcessor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Places one template from a folder of structure templates (feature eternalreturn:boulder): any template whose id
 * starts with the folder, so hand-built ones saved there join the starter set from tools/clutter/build_boulders.py.
 * It is turned and mirrored at random, centred on the spot, set on the lowest ground under it and sunk (sink), and
 * its plain stone becomes the stone of the ground under its middle (BiomeStoneProcessor), if that stone is in the
 * "stones" tag, else stays plain stone. Nothing is placed where any
 * column under it is water or not solid ground, or where the ground under it falls more than max_slope blocks.
 */
public final class BoulderFeature extends Feature<BoulderFeature.Config> {
	public record Config(Identifier folder, TagKey<Block> stones, IntProvider sink, int maxSlope) implements FeatureConfig {
		public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Identifier.CODEC.fieldOf("folder").forGetter(Config::folder),
				TagKey.unprefixedCodec(RegistryKeys.BLOCK).fieldOf("stones").forGetter(Config::stones),
				IntProvider.createValidatingCodec(0, 8).fieldOf("sink").forGetter(Config::sink),
				Codec.intRange(0, 32).fieldOf("max_slope").forGetter(Config::maxSlope)).apply(instance, Config::new));
	}

	/** The templates in each folder, listed once per template manager (a server). */
	private final Map<StructureTemplateManager, Map<Identifier, List<Identifier>>> templates = new WeakHashMap<>();

	public BoulderFeature() {
		super(Config.CODEC);
	}

	private synchronized List<Identifier> templatesIn(StructureTemplateManager manager, Identifier folder) {
		return this.templates.computeIfAbsent(manager, m -> new java.util.HashMap<>()).computeIfAbsent(folder, f -> manager.streamTemplates()
				.filter(id -> id.getNamespace().equals(f.getNamespace()) && id.getPath().startsWith(f.getPath() + "/"))
				.sorted().toList());
	}

	@Override
	public boolean generate(FeatureContext<Config> context) {
		StructureWorldAccess world = context.getWorld();
		Random random = context.getRandom();
		BlockPos origin = context.getOrigin();
		StructureTemplateManager manager = world.toServerWorld().getServer().getStructureTemplateManager();
		List<Identifier> ids = this.templatesIn(manager, context.getConfig().folder());
		if (ids.isEmpty()) {
			return false;
		}
		StructureTemplate template = manager.getTemplateOrBlank(ids.get(random.nextInt(ids.size())));
		ChunkPos chunk = new ChunkPos(origin);
		StructurePlacementData data = new StructurePlacementData()
				.setRotation(BlockRotation.random(random))
				.setMirror(random.nextBoolean() ? BlockMirror.NONE : BlockMirror.FRONT_BACK)
				.setBoundingBox(new BlockBox(chunk.getStartX() - 16, world.getBottomY(), chunk.getStartZ() - 16,
						chunk.getEndX() + 16, world.getTopY(), chunk.getEndZ() + 16))
				.setRandom(random)
				.addProcessor(new BiomeStoneProcessor(java.util.Optional.of(context.getConfig().stones())));
		BlockBox box = template.calculateBoundingBox(data, BlockPos.ORIGIN);
		int startX = origin.getX() - box.getBlockCountX() / 2;
		int startZ = origin.getZ() - box.getBlockCountZ() / 2;
		// The ground under every column of the footprint: solid, no water, not too steep.
		int low = Integer.MAX_VALUE;
		int high = Integer.MIN_VALUE;
		BlockPos.Mutable ground = new BlockPos.Mutable();
		for (int x = startX; x < startX + box.getBlockCountX(); x++) {
			for (int z = startZ; z < startZ + box.getBlockCountZ(); z++) {
				int top = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z);
				ground.set(x, top - 1, z);
				if (!world.getFluidState(ground).isEmpty() || !world.getBlockState(ground).isSolidBlock(world, ground)) {
					return false;
				}
				low = Math.min(low, top);
				high = Math.max(high, top);
			}
		}
		if (high - low > context.getConfig().maxSlope()) {
			return false;
		}
		int y = low - context.getConfig().sink().get(random);
		BlockPos pos = new BlockPos(startX - box.getMinX(), y - box.getMinY(), startZ - box.getMinZ());
		// The pivot tells BiomeStoneProcessor where to read the ground's stone: under the middle of the footprint.
		BlockPos middle = new BlockPos(startX + box.getBlockCountX() / 2, y, startZ + box.getBlockCountZ() / 2);
		return template.place(world, pos, middle, data, random, Block.NOTIFY_LISTENERS);
	}
}
