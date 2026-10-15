package com.eternalreturn.survival;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Beacons without pyramids (survival.beaconRework): a beacon needs only a clear beam to the sky, and every effect
 * can be picked (Regeneration among them); it gives one effect, not two. What it was paid with decides how far it
 * reaches and how much effect time builds up:
 * <ul>
 * <li>iron 32 blocks and 30 seconds, gold 64 and 1 minute, emerald 128 and 1.5 minutes, diamond 256 and 2 minutes,
 * netherite 512 and 4 minutes;</li>
 * <li>each second a player spends in range adds a second of the effect, up to that time, and it keeps running after
 * they leave.</li>
 * </ul>
 * The same range is the beacon's area for BeaconZones. A beacon paid before this (it has an effect but no payment
 * recorded) counts as iron. The payment is saved with the beacon as eternalreturn_tier.
 */
public final class BeaconPower {
	public static final int[] RANGES = {0, 32, 64, 128, 256, 512};
	public static final int[] MAX_TICKS = {0, 600, 1200, 1800, 2400, 4800};
	/** Vanilla gives beacon effects every 4 seconds. */
	private static final int INTERVAL = 80;

	/** What a beacon keeps. */
	public interface Holder {
		int eternalreturn$tier();

		void eternalreturn$setTier(int tier);
	}

	private BeaconPower() {
	}

	public static boolean enabled() {
		return EternalReturnConfig.get().survival.beaconRework;
	}

	public static int tierOf(ItemStack payment) {
		if (payment.isOf(Items.IRON_INGOT)) {
			return 1;
		}
		if (payment.isOf(Items.GOLD_INGOT)) {
			return 2;
		}
		if (payment.isOf(Items.EMERALD)) {
			return 3;
		}
		if (payment.isOf(Items.DIAMOND)) {
			return 4;
		}
		return payment.isOf(Items.NETHERITE_INGOT) ? 5 : 0;
	}

	/** The tier a beacon works at: its payment, or iron for one with an effect from before payments counted. */
	public static int tier(BeaconBlockEntity beacon, @Nullable RegistryEntry<StatusEffect> primary) {
		int tier = ((Holder) beacon).eternalreturn$tier();
		return tier == 0 && primary != null ? 1 : tier;
	}

	public static int range(int tier) {
		return RANGES[Math.max(0, Math.min(tier, RANGES.length - 1))];
	}

	/** Every 4 seconds, in place of vanilla's: the beacon's one effect, building up on players in range. */
	public static void apply(World world, BlockPos pos, @Nullable RegistryEntry<StatusEffect> primary) {
		if (world.isClient || primary == null || !(world.getBlockEntity(pos) instanceof BeaconBlockEntity beacon)) {
			return;
		}
		int tier = tier(beacon, primary);
		if (tier <= 0) {
			return;
		}
		int range = range(tier);
		int max = MAX_TICKS[tier];
		Box box = new Box(pos).expand(range).stretch(0.0, world.getHeight(), 0.0);
		for (PlayerEntity player : world.getNonSpectatingEntities(PlayerEntity.class, box)) {
			StatusEffectInstance current = player.getStatusEffect(primary);
			if (current != null && (current.getAmplifier() > 0 || current.isInfinite())) {
				continue;
			}
			int left = current == null ? 0 : current.getDuration();
			// Twice the interval: one makes up for the time that ran out since the last pulse, one is the gain.
			int duration = Math.min(max, left + INTERVAL * 2);
			if (duration > left) {
				player.addStatusEffect(new StatusEffectInstance(primary, duration, 0, true, true));
			}
		}
	}
}
