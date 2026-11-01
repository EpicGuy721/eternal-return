package com.eternalreturn.worldgen.trees;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.TreeFeature;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Trees from structure templates (a "template" entry in a tree table): the trunk is at the middle of the template's
 * bottom layer and lands on the sapling. The template is turned and mirrored at random. Like a sapling's own tree, it
 * only grows if it fits: the ground under the trunk must be dirt, grass or the like, and every spot the template fills
 * must be air, leaves or a plant a tree may replace. Its leaves are then set as a tree's own (not persistent, each
 * its real distance from a log), so they decay when the tree is cut, and logs keep their direction when turned.
 */
public final class TreeTemplates {
	private TreeTemplates() {
	}

	public static boolean place(StructureWorldAccess world, Identifier id, BlockPos pos, Random random) {
		Optional<StructureTemplate> found = world.toServerWorld().getStructureTemplateManager().getTemplate(id);
		if (found.isEmpty()) {
			return false;
		}
		StructureTemplate template = found.get();
		Vec3i size = template.getSize();
		StructurePlacementData data = new StructurePlacementData()
				.setRotation(BlockRotation.random(random))
				.setMirror(random.nextBoolean() ? BlockMirror.NONE : BlockMirror.FRONT_BACK)
				.setRandom(random);
		// Where the trunk ends up once turned and mirrored (the game mirrors across the template's corner, not its
		// middle), so the template is shifted to put it on the sapling.
		BlockPos trunk = StructureTemplate.transform(data, new BlockPos(size.getX() / 2, 0, size.getZ() / 2));
		BlockPos origin = pos.subtract(trunk);
		BlockBox box = template.calculateBoundingBox(data, origin);
		if (!world.getBlockState(pos.down()).isIn(BlockTags.DIRT) || box.getMaxY() >= world.getTopY()) {
			return false;
		}
		// Only the spots the tree fills: each must be air, leaves or a plant a tree may replace (or the sapling itself).
		var lists = ((com.eternalreturn.mixin.worldgen.StructureTemplateAccessor) template).eternalreturn$blockInfoLists();
		if (lists.isEmpty()) {
			return false;
		}
		for (StructureTemplate.StructureBlockInfo info : data.getRandomBlockInfos(lists, origin).getAll()) {
			BlockPos p = StructureTemplate.transform(data, info.pos()).add(origin);
			if (!TreeFeature.canReplace(world, p) && !p.equals(pos)) {
				return false;
			}
		}
		if (!template.place(world, origin, origin, data, random, Block.NOTIFY_LISTENERS | Block.FORCE_STATE)) {
			return false;
		}
		fixLeaves(world, box);
		return true;
	}

	/** Every leaf in the box: not persistent, and its distance counted from the logs as the game counts it (up to 7). */
	private static void fixLeaves(StructureWorldAccess world, BlockBox box) {
		Map<BlockPos, Integer> distance = new HashMap<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos p : BlockPos.iterate(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ())) {
			if (world.getBlockState(p).isIn(BlockTags.LOGS)) {
				BlockPos log = p.toImmutable();
				distance.put(log, 0);
				queue.add(log);
			}
		}
		while (!queue.isEmpty()) {
			BlockPos p = queue.poll();
			int d = distance.get(p);
			if (d >= LeavesBlock.MAX_DISTANCE) {
				continue;
			}
			for (Direction direction : Direction.values()) {
				BlockPos next = p.offset(direction);
				if (box.contains(next) && !distance.containsKey(next) && world.getBlockState(next).getBlock() instanceof LeavesBlock) {
					distance.put(next, d + 1);
					queue.add(next);
				}
			}
		}
		for (BlockPos p : BlockPos.iterate(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ())) {
			BlockState state = world.getBlockState(p);
			if (state.getBlock() instanceof LeavesBlock && state.contains(LeavesBlock.DISTANCE)) {
				int d = Math.min(distance.getOrDefault(p, LeavesBlock.MAX_DISTANCE), LeavesBlock.MAX_DISTANCE);
				world.setBlockState(p, state.with(LeavesBlock.DISTANCE, d).with(LeavesBlock.PERSISTENT, false), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
			}
		}
	}
}
