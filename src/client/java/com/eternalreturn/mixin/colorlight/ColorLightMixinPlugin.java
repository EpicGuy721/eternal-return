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
 * Applies ColorLightMovingLightMixin only when ColorLight is installed and its moving-light hook still halves the light
 * level as 0.2.6 does: one "level / 2" in ColorLightEntityLightTicker.apply, read from the class before it loads. If
 * ColorLight has changed, moving light is left as ColorLight makes it, with a warning instead of a failed start.
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
