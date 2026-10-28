package com.eternalreturn.gametest.mixin;

import net.minecraft.block.FireBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets a test run one step of fire spreading onto a block, and ask how readily fire would start in a space. */
@Mixin(FireBlock.class)
public interface FireSpreadInvoker {
	@Invoker("trySpreadingFire")
	void eternalreturn$trySpreadingFire(World world, BlockPos pos, int spreadFactor, Random random, int currentAge);

	@Invoker("getBurnChance")
	int eternalreturn$burnChanceAt(WorldView world, BlockPos pos);
}
