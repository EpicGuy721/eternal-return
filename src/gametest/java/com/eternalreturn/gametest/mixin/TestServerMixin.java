package com.eternalreturn.gametest.mixin;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.gametest.GameTestWorld;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.test.TestFunction;
import net.minecraft.test.TestServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.Collection;
import java.util.List;

/**
 * The vanilla test server always builds a flat world and ignores datapack dimensions. For worldgen
 * tests, the eternalreturn.gametest.* system properties (set per run in build.gradle) choose the
 * world preset, which test batches run, and worldgen config overrides.
 */
@Mixin(TestServer.class)
public abstract class TestServerMixin {
	@Inject(method = "create", at = @At("HEAD"))
	private static void eternalreturn$configOverrides(CallbackInfoReturnable<TestServer> cir) {
		String tunnel = System.getProperty("eternalreturn.gametest.debugTunnel");
		if (tunnel != null) {
			EternalReturnConfig.get().worldgen.debugTunnel = Boolean.parseBoolean(tunnel);
		}
	}

	/**
	 * The vanilla test server switches on every datapack it finds, including optional ones a normal
	 * new world leaves off. Moderner Beta's optional "reduced_height" pack moves the overworld floor
	 * to y=0, so keep its optional packs off, matching a default world.
	 */
	@ModifyArgs(method = "create", at = @At(value = "INVOKE", target = "Lnet/minecraft/resource/DataPackSettings;<init>(Ljava/util/List;Ljava/util/List;)V"))
	private static void eternalreturn$defaultPacksOnly(Args args) {
		List<String> enabled = args.get(0);
		List<String> optional = enabled.stream().filter(GameTestWorld::isOptionalPack).toList();
		args.set(0, enabled.stream().filter(id -> !GameTestWorld.isOptionalPack(id)).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)));
		args.set(1, optional);
	}

	@ModifyVariable(method = "create", at = @At("HEAD"), argsOnly = true)
	private static Collection<TestFunction> eternalreturn$selectBatches(Collection<TestFunction> tests) {
		return tests.stream().filter(test -> GameTestWorld.selects(test.batchId())).toList();
	}

	/** The test world's seed is fixed at 0; worldgen runs pick their own (eternalreturn.gametest.seed). */
	@ModifyExpressionValue(method = "method_40377", at = @At(value = "FIELD", target = "Lnet/minecraft/test/TestServer;TEST_LEVEL:Lnet/minecraft/world/gen/GeneratorOptions;"))
	private static GeneratorOptions eternalreturn$seed(GeneratorOptions options) {
		Long seed = GameTestWorld.seedProperty();
		return seed == null ? options : new GeneratorOptions(seed, options.shouldGenerateStructures(), options.hasBonusChest());
	}

	/** method_40377 is the lambda in create() that builds the world from WorldPresets.FLAT. */
	@ModifyArg(method = "method_40377", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/registry/Registry;entryOf(Lnet/minecraft/registry/RegistryKey;)Lnet/minecraft/registry/entry/RegistryEntry$Reference;"))
	private static RegistryKey<WorldPreset> eternalreturn$worldPreset(RegistryKey<WorldPreset> flat) {
		String preset = GameTestWorld.presetProperty();
		return preset == null ? flat : RegistryKey.of(RegistryKeys.WORLD_PRESET, Identifier.of(preset));
	}
}
