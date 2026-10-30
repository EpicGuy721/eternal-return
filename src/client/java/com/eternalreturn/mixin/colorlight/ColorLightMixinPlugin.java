package com.eternalreturn.mixin.colorlight;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

/**
 * Applies the ColorLight fixes only when ColorLight is installed, each checked against ColorLight's classes before they
 * load, with a warning instead of a failed start if ColorLight has changed. ColorLightMovingLightMixin: its moving-light
 * hook still halves the light level as 0.2.6 does (one "level / 2" in ColorLightEntityLightTicker.apply). The daylight
 * fix (ColorLightDaylightTintMixin) needs Sodium, whose loader reads ColorLight's shader; it checks its own line.
 */
public class ColorLightMixinPlugin implements IMixinConfigPlugin {
	private static final Logger LOGGER = LoggerFactory.getLogger("eternalreturn");

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (!FabricLoader.getInstance().isModLoaded("colorlight")) {
			return false;
		}
		if (mixinClassName.endsWith("ColorLightDaylightTintMixin")) {
			return FabricLoader.getInstance().isModLoaded("sodium");
		}
		if (mixinClassName.endsWith("ColorLightHeldLevelMixin")) {
			return hasMethod(targetClassName, "effectiveStrength", 2, "I");
		}
		boolean found = false;
		try {
			ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
			for (MethodNode method : node.methods) {
				if (method.name.equals("apply")) {
					found = halvings(method) == 1;
				}
			}
		} catch (Exception e) {
			found = false;
		}
		if (!found) {
			LOGGER.warn("Eternal Return: ColorLight has changed ({} no longer halves moving light as 0.2.6 did), so held and dropped "
					+ "light sources keep ColorLight's own strength.", targetClassName);
		}
		return found;
	}

	/** Whether the class still has this method, with this many arguments and this return type. */
	private static boolean hasMethod(String targetClassName, String name, int arguments, String returns) {
		boolean found;
		try {
			ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
			found = node.methods.stream().anyMatch(method -> method.name.equals(name)
					&& org.objectweb.asm.Type.getArgumentTypes(method.desc).length == arguments
					&& org.objectweb.asm.Type.getReturnType(method.desc).getDescriptor().equals(returns));
		} catch (Exception e) {
			found = false;
		}
		if (!found) {
			LOGGER.warn("Eternal Return: ColorLight has changed ({} has no {}), so held and dropped block items keep ColorLight's light level for them.",
					targetClassName, name);
		}
		return found;
	}

	/** How many "/ 2" the method has. */
	private static int halvings(MethodNode method) {
		int count = 0;
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() == Opcodes.ICONST_2 && insn.getNext() != null && insn.getNext().getOpcode() == Opcodes.IDIV) {
				count++;
			}
		}
		return count;
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
