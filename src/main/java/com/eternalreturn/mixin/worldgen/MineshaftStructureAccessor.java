package com.eternalreturn.mixin.worldgen;

import net.minecraft.world.gen.structure.MineshaftStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A mineshaft structure's type (ordinary or badlands "mesa"), for StructureMixin. */
@Mixin(MineshaftStructure.class)
public interface MineshaftStructureAccessor {
	@Accessor("type")
	MineshaftStructure.Type eternalreturn$type();
}
