package com.eternalreturn.gametest.mixin;

import net.minecraft.test.GameTestState;
import net.minecraft.test.TestContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets a test helper hear when its test ends (FishingTests.survivalPlayer). */
@Mixin(TestContext.class)
public interface TestContextAccessor {
	@Accessor("test")
	GameTestState eternalreturn$test();
}
