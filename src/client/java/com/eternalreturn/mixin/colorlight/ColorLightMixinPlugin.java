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
 * fix (ColorLightHeaderMixin, ColorLightDaylightTintMixin) needs Sodium, whose loader reads ColorLight's shader, and a
 * header that still has room (16 numbers, sent by writeHeader); the shader mixin checks its own line.
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
		if (mixinClassName.endsWith("ColorLightHeaderMixin")) {
			return FabricLoader.getInstance().isModLoaded("sodium") && headerHasRoom(targetClassName);
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

	/** Whether ColorLight's header still has 16 numbers (it uses the first 10) and is still sent by writeHeader. */
	private static boolean headerHasRoom(String targetClassName) {
		boolean found;
		try {
			ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
			found = node.fields.stream().anyMatch(field -> field.name.equals("HEADER_INTS") && Integer.valueOf(16).equals(field.value))
					&& node.fields.stream().anyMatch(field -> field.name.equals("headerWritten"))
					&& node.methods.stream().anyMatch(method -> method.name.equals("writeHeader"));
		} catch (Exception e) {
			found = false;
		}
		if (!found) {
			LOGGER.warn("Eternal Return: ColorLight has changed ({} is not as in 0.2.6), so its colours by day are left as ColorLight draws them.",
					targetClassName);
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
