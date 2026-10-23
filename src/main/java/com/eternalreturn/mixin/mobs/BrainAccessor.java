package com.eternalreturn.mixin.mobs;

import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.Memory;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Optional;

@Mixin(Brain.class)
public interface BrainAccessor {
	@Accessor("memories")
	Map<MemoryModuleType<?>, Optional<? extends Memory<?>>> eternalreturn$memories();
}
