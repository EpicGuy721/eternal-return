package com.eternalreturn.mixin.compat;

import com.eternalreturn.compat.ModernerBetaHooks;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies the Moderner Beta hooks only when Moderner Beta is installed and each hook's target method is
 * still there (name, argument count and return type, read from the class before it loads). Moderner Beta
 * is an alpha, so a changed target skips that hook with a warning instead of failing to start; the
 * injectors are also not required (eternalreturn.modernerbeta.mixins.json), and a game test checks that
 * both hooks are applied and run.
 */
public class ModernerBetaMixinPlugin implements IMixinConfigPlugin {
	private static final Logger LOGGER = LoggerFactory.getLogger("eternalreturn");
	/** Mixin class to {target method name, argument count, return descriptor}. */
	private static final Map<String, Object[]> TARGETS = Map.of(
			"com.eternalreturn.mixin.compat.ModernerBetaSurfaceMixin", new Object[]{"isBlockSuitableForSurface", 1, "Z"},
			"com.eternalreturn.mixin.compat.ModernerBetaSurfaceExtraMixin", new Object[]{"provideSurfaceExtra", 5, "V"});

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		String hook = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
		if (!FabricLoader.getInstance().isModLoaded("moderner_beta")) {
			return false;
		}
		Object[] target = TARGETS.get(mixinClassName);
		boolean found = false;
		try {
			ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
			for (MethodNode method : node.methods) {
				if (method.name.equals(target[0]) && Type.getArgumentTypes(method.desc).length == (int) target[1]
						&& Type.getReturnType(method.desc).getDescriptor().equals(target[2])) {
					found = true;
				}
			}
		} catch (Exception e) {
			found = false;
		}
		ModernerBetaHooks.APPLIED.put(hook, found);
		if (!found) {
			LOGGER.warn("Eternal Return: Moderner Beta has changed ({} has no {} with {} argument(s)), so the {} hook into its surface pass is off. "
							+ "Eternal Return worlds still generate, but biome stone may show columns of plain stone under Moderner Beta's bare-rock patches. "
							+ "Please report this with your Moderner Beta version.", targetClassName, target[0], target[1], hook);
		}
		return found;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
