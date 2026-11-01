package com.eternalreturn.mixin.worldgen;

import net.minecraft.structure.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** A template's blocks, so TreeTemplates checks only the spots a template tree fills. */
@Mixin(StructureTemplate.class)
public interface StructureTemplateAccessor {
	@Accessor("blockInfoLists")
	List<StructureTemplate.PalettedBlockInfoList> eternalreturn$blockInfoLists();
}
