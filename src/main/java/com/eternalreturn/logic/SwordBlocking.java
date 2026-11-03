package com.eternalreturn.logic;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Sword blocking, which Nostalgic Tweaks brings back (its "Old Sword Blocking": hold use with a sword and an empty off
 * hand, and hits do less damage, its "Sword Block Damage Reduction", 50% by default):
 * <ul>
 * <li>a player blocking with a sword can't attack (survival.noAttackWhileBlocking): the server ignores the hit
 * (SwordBlockingMixin) and the client neither swings nor sends it (client SwordBlockingClientMixin), whatever
 * Nostalgic Tweaks' own "Attack While Sword Blocking" says;</li>
 * <li>Defense (swords, I to V) blocks 5% more of the damage per level: the reduction becomes Nostalgic Tweaks' setting
 * plus 5% a level (50% + 25% at V), never past 100% (DefenseMixin). Nostalgic Tweaks is read as it runs, so its
 * slider counts as it's set; without Nostalgic Tweaks there is no sword blocking, and Defense does nothing.</li>
 * </ul>
 * Nostalgic Tweaks is an optional mod this one doesn't compile against: it's reached by reflection, and if anything
 * there isn't as expected the blocking rules here simply don't apply.
 */
public final class SwordBlocking {
	public static final Identifier DEFENSE = EternalReturn.id("defense");
	public static final float DEFENSE_PER_LEVEL = 0.05F;

	/** Tests only: stands in for Nostalgic Tweaks, which the test runs don't load. Its blocking reduction, or null. */
	@Nullable
	public static Float testReduction;

	private static boolean looked;
	@Nullable
	private static Method ntIsBlocking;
	@Nullable
	private static Object ntReductionTweak;
	@Nullable
	private static Method ntTweakGet;

	private SwordBlocking() {
	}

	/** Whether the player is holding a sword up to block (using it), as Nostalgic Tweaks has it. */
	public static boolean isBlocking(PlayerEntity player) {
		if (!player.isUsingItem() || !player.getActiveItem().isIn(ItemTags.SWORDS)) {
			return false;
		}
		if (testReduction != null) {
			return true;
		}
		lookUp();
		if (ntIsBlocking == null) {
			// Without Nostalgic Tweaks a sword can't be raised at all; with an unexpected one, any sword in use counts.
			return FabricLoader.getInstance().isModLoaded("nostalgic_tweaks");
		}
		try {
			return (Boolean) ntIsBlocking.invoke(null, player);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return true;
		}
	}

	/** Whether a hit by this player is cancelled: it's blocking with a sword. */
	public static boolean cantAttack(PlayerEntity player) {
		return EternalReturnConfig.get().survival.noAttackWhileBlocking && isBlocking(player);
	}

	/** Nostalgic Tweaks' blocking reduction (0 to 1), or -1 without it. */
	public static float baseReduction() {
		if (testReduction != null) {
			return testReduction;
		}
		lookUp();
		if (ntReductionTweak == null || ntTweakGet == null) {
			return -1.0F;
		}
		try {
			Object value = ntTweakGet.invoke(ntReductionTweak);
			return value instanceof Number number ? Math.max(0.0F, Math.min(1.0F, number.floatValue() / 100.0F)) : -1.0F;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return -1.0F;
		}
	}

	/**
	 * What the damage left after Nostalgic Tweaks' reduction (base) is multiplied by so that, with Defense at this
	 * level, the whole reduction is base + 5% a level (at most 100%).
	 */
	public static float defenseFactor(float base, int level) {
		if (level <= 0 || base < 0.0F || base >= 1.0F) {
			return 1.0F;
		}
		float total = Math.min(1.0F, base + DEFENSE_PER_LEVEL * level);
		return (1.0F - total) / (1.0F - base);
	}

	/** The damage a blocking player takes, Defense counted (Nostalgic Tweaks takes off its own share separately). */
	public static float defend(PlayerEntity player, float damage) {
		if (damage <= 0.0F || !isBlocking(player)) {
			return damage;
		}
		int level = NewEnchantments.level(player.getActiveItem(), DEFENSE);
		if (level <= 0) {
			return damage;
		}
		return damage * defenseFactor(baseReduction(), level);
	}

	private static synchronized void lookUp() {
		if (looked) {
			return;
		}
		looked = true;
		if (!FabricLoader.getInstance().isModLoaded("nostalgic_tweaks")) {
			return;
		}
		try {
			Class<?> helper = Class.forName("mod.adrenix.nostalgic.helper.gameplay.combat.SwordBlockingHelper");
			ntIsBlocking = helper.getMethod("isBlocking", PlayerEntity.class);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			EternalReturn.LOGGER.warn("Nostalgic Tweaks' sword blocking wasn't found; blocking counts any sword in use", e);
		}
		try {
			Class<?> tweaks = Class.forName("mod.adrenix.nostalgic.tweak.config.GameplayTweak");
			ntReductionTweak = tweaks.getField("SWORD_BLOCK_DAMAGE_REDUCTION").get(null);
			ntTweakGet = ntReductionTweak.getClass().getMethod("get");
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			ntReductionTweak = null;
			EternalReturn.LOGGER.warn("Nostalgic Tweaks' sword block damage reduction wasn't found; Defense does nothing", e);
		}
	}
}
