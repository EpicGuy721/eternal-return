package com.eternalreturn.gametest.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets a test have an entity take a step on a block. */
@Mixin(Entity.class)
public interface StepSoundInvoker {
	@Invoker("playStepSounds")
	void eternalreturn$playStepSounds(BlockPos pos, BlockState state);
}
