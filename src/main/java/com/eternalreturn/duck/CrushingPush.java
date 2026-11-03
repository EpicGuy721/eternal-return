package com.eternalreturn.duck;

import net.minecraft.util.math.BlockPos;

import java.util.Set;

/** A piston push (PistonHandler) that crushes blocks against obsidian (Crushing): the places of the crushed blocks. */
public interface CrushingPush {
	Set<BlockPos> eternalreturn$crushed();
}
