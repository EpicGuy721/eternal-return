package com.eternalreturn.villager;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.duck.NeedyVillager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.LivingTargetCache;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Villagers' needs (villagers.needs in the config). Each villager remembers when each need was last met,
 * on the day clock:
 * <ul>
 * <li>sleep: it slept in its bed. It won't lie down while it can see more than one other adult villager,
 * so it needs a bed with some privacy (SleepTaskMixin);</li>
 * <li>gossip: it gossiped with another villager (which needs a place where they meet);</li>
 * <li>exercise: it walked at least 8 blocks in one go (straight-line distance from where it set off);</li>
 * <li>sunlight: it saw the sky in daytime;</li>
 * <li>work-life balance: its workstation is at least 8 blocks from its bed (or it has no bed or no
 * workstation), checked every second.</li>
 * </ul>
 * A need missed for 2 days adds 5 emeralds to every trade paid in emeralds, per need; a need missed for
 * 7 days and the villager won't trade. A player who tries to trade is told in chat which needs are
 * lacking (missed for a day or more). Shops close at night. A hurt villager with food eats now and then
 * to heal. A villager starts with every need met the first time it ticks with this mod, and babies keep
 * theirs met until they grow up.
 */
public final class VillagerNeeds {
	public static final long DAY = 24000L;

	public enum Need {
		SLEEP("sleep"), GOSSIP("gossip"), EXERCISE("exercise"), SUNLIGHT("sunlight"), WORK_LIFE("work_life");

		private final String key;

		Need(String key) {
			this.key = key;
		}

		public String key() {
			return this.key;
		}
	}

	private VillagerNeeds() {
	}

	private static EternalReturnConfig.VillagerNeedSettings settings() {
		return EternalReturnConfig.get().villagers.needs;
	}

	/** The day clock (it jumps forward when players sleep through the night, and with /time add). */
	public static long now(World world) {
		return world.getTimeOfDay();
	}

	public static boolean isNight(World world) {
		EternalReturnConfig.VillagerNeedSettings cfg = settings();
		long time = Math.floorMod(world.getTimeOfDay(), DAY);
		return cfg.nightStart <= cfg.nightEnd ? time >= cfg.nightStart && time < cfg.nightEnd : time >= cfg.nightStart || time < cfg.nightEnd;
	}

	/** Days since the need was last met (0 if it was met in the future, after /time set). */
	public static double daysMissed(VillagerEntity villager, Need need) {
		long met = ((NeedyVillager) villager).eternalreturn$lastMet(need);
		if (met == Long.MIN_VALUE) {
			return 0.0;
		}
		return Math.max(0L, now(villager.getWorld()) - met) / (double) DAY;
	}

	/** Extra emeralds on each trade: per need missed for daysBeforePriceRise or more. */
	public static int priceRise(VillagerEntity villager) {
		EternalReturnConfig.VillagerNeedSettings cfg = settings();
		if (!cfg.enabled) {
			return 0;
		}
		int missing = 0;
		for (Need need : Need.values()) {
			if (daysMissed(villager, need) >= cfg.daysBeforePriceRise) {
				missing++;
			}
		}
		return missing * cfg.emeraldsPerMissingNeed;
	}

	/** Whether some need has been missed for daysBeforeRefusal or more. */
	public static boolean refuses(VillagerEntity villager) {
		EternalReturnConfig.VillagerNeedSettings cfg = settings();
		if (!cfg.enabled) {
			return false;
		}
		for (Need need : Need.values()) {
			if (daysMissed(villager, need) >= cfg.daysBeforeRefusal) {
				return true;
			}
		}
		return false;
	}

	/** Needs missed for a day or more, most missed first. */
	public static List<Need> lacking(VillagerEntity villager) {
		List<Need> lacking = new ArrayList<>();
		for (Need need : Need.values()) {
			if (daysMissed(villager, need) >= 1.0) {
				lacking.add(need);
			}
		}
		lacking.sort((a, b) -> Double.compare(daysMissed(villager, b), daysMissed(villager, a)));
		return lacking;
	}

	// ---------------------------------------------------------------- every tick

	/** From the villager's mobTick (server side). */
	public static void tick(VillagerEntity villager) {
		NeedyVillager needs = (NeedyVillager) villager;
		long now = now(villager.getWorld());
		for (Need need : Need.values()) {
			if (needs.eternalreturn$lastMet(need) == Long.MIN_VALUE || villager.isBaby()) {
				needs.eternalreturn$setLastMet(need, now);
			}
		}
		EternalReturnConfig.VillagerNeedSettings cfg = settings();
		if (!cfg.enabled) {
			return;
		}
		if (villager.isSleeping()) {
			needs.eternalreturn$setLastMet(Need.SLEEP, now);
		}
		trackWalk(villager, needs, now, cfg);
		if (villager.age % 20 == 0) {
			if (!isNight(villager.getWorld()) && villager.getWorld().getDimension().hasSkyLight()
					&& villager.getWorld().isSkyVisible(BlockPos.ofFloored(villager.getEyePos()))) {
				needs.eternalreturn$setLastMet(Need.SUNLIGHT, now);
			}
			if (workLifeBalanced(villager, cfg)) {
				needs.eternalreturn$setLastMet(Need.WORK_LIFE, now);
			}
		}
		if (cfg.healWithFood && villager.age % 40 == 0) {
			eatToHeal(villager, cfg);
		}
	}

	/** Two villagers gossiped (VillagerEntity.talkWithVillager shared gossip). */
	public static void gossiped(VillagerEntity villager) {
		((NeedyVillager) villager).eternalreturn$setLastMet(Need.GOSSIP, now(villager.getWorld()));
	}

	/**
	 * A walk starts when the villager moves and ends after a second standing still (or riding); it counts
	 * once the villager is exerciseBlocks from where it set off.
	 */
	private static void trackWalk(VillagerEntity villager, NeedyVillager needs, long now, EternalReturnConfig.VillagerNeedSettings cfg) {
		Vec3d pos = villager.getPos();
		double moved = villager.getVelocity().horizontalLengthSquared();
		boolean walking = !villager.hasVehicle() && !villager.isSleeping() && (moved > 1.0E-4 || villager.getNavigation().isFollowingPath());
		if (!walking) {
			int still = needs.eternalreturn$stillTicks() + 1;
			needs.eternalreturn$setStillTicks(still);
			if (still > 20) {
				needs.eternalreturn$setWalkStart(null);
			}
			return;
		}
		needs.eternalreturn$setStillTicks(0);
		Vec3d start = needs.eternalreturn$walkStart();
		if (start == null) {
			needs.eternalreturn$setWalkStart(pos);
		} else if (start.subtract(pos).horizontalLengthSquared() >= cfg.exerciseBlocks * cfg.exerciseBlocks) {
			needs.eternalreturn$setLastMet(Need.EXERCISE, now);
		}
	}

	/** True unless the villager's workstation is closer than minWorkstationDistance to its bed. */
	static boolean workLifeBalanced(VillagerEntity villager, EternalReturnConfig.VillagerNeedSettings cfg) {
		Brain<?> brain = villager.getBrain();
		Optional<GlobalPos> home = brain.getOptionalRegisteredMemory(MemoryModuleType.HOME);
		Optional<GlobalPos> job = brain.getOptionalRegisteredMemory(MemoryModuleType.JOB_SITE);
		if (home.isEmpty() || job.isEmpty() || !home.get().dimension().equals(job.get().dimension())) {
			return true;
		}
		return !home.get().pos().isWithinDistance(job.get().pos(), cfg.minWorkstationDistance);
	}

	/** Whether the villager may lie down: it sees no more than maxOtherAdultsInSight other adult villagers. */
	public static boolean hasPrivacy(VillagerEntity villager) {
		EternalReturnConfig.VillagerNeedSettings cfg = settings();
		if (!cfg.enabled) {
			return true;
		}
		Optional<LivingTargetCache> seen = villager.getBrain().getOptionalRegisteredMemory(MemoryModuleType.VISIBLE_MOBS);
		long others = seen.map(cache -> cache.stream(entity -> entity instanceof VillagerEntity other && other != villager && !other.isBaby()).count()).orElse(0L);
		return others <= cfg.maxOtherAdultsInSight;
	}

	private static void eatToHeal(VillagerEntity villager, EternalReturnConfig.VillagerNeedSettings cfg) {
		if (villager.getHealth() >= villager.getMaxHealth() || villager.isSleeping() || villager.getRandom().nextDouble() >= cfg.eatChance) {
			return;
		}
		for (int i = 0; i < villager.getInventory().size(); i++) {
			ItemStack stack = villager.getInventory().getStack(i);
			FoodComponent food = stack.get(DataComponentTypes.FOOD);
			if (food == null || !VillagerEntity.ITEM_FOOD_VALUES.containsKey(stack.getItem())) {
				continue;
			}
			ItemStack eaten = stack.copyWithCount(1);
			villager.getInventory().removeStack(i, 1);
			villager.heal(food.nutrition());
			villager.playSound(SoundEvents.ENTITY_GENERIC_EAT, 0.5F, 1.0F);
			if (villager.getWorld() instanceof ServerWorld world) {
				world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, eaten), villager.getX(), villager.getEyeY() - 0.2, villager.getZ(), 6, 0.15, 0.1, 0.15, 0.05);
			}
			return;
		}
	}

	// ---------------------------------------------------------------- trading

	/**
	 * Called when a player starts trading. Returns false (after shaking its head and telling the player
	 * why) when the shop is closed or the villager refuses; otherwise tells the player about any lacking
	 * needs and returns true.
	 */
	public static boolean allowTrade(VillagerEntity villager, PlayerEntity player) {
		EternalReturnConfig.VillagerNeedSettings cfg = settings();
		if (!cfg.enabled) {
			return true;
		}
		Text name = villager.getDisplayName();
		if (cfg.closedAtNight && isNight(villager.getWorld())) {
			sayNo(villager);
			player.sendMessage(Text.translatable("villager.eternalreturn.closed", name).formatted(Formatting.YELLOW), false);
			return false;
		}
		List<Need> lacking = lacking(villager);
		if (refuses(villager)) {
			sayNo(villager);
			player.sendMessage(Text.translatable("villager.eternalreturn.refuses", name, (int) cfg.daysBeforeRefusal).formatted(Formatting.RED), false);
		} else if (priceRise(villager) > 0) {
			player.sendMessage(Text.translatable("villager.eternalreturn.prices_up", name, priceRise(villager)).formatted(Formatting.GOLD), false);
		} else if (!lacking.isEmpty()) {
			player.sendMessage(Text.translatable("villager.eternalreturn.unhappy", name, (int) cfg.daysBeforePriceRise).formatted(Formatting.YELLOW), false);
		}
		for (Need need : lacking) {
			player.sendMessage(Text.literal(" - ").append(Text.translatable("villager.eternalreturn.need." + need.key(), (int) daysMissed(villager, need),
					(int) cfg.exerciseBlocks, (int) cfg.minWorkstationDistance, cfg.maxOtherAdultsInSight)).formatted(Formatting.GRAY), false);
		}
		return !refuses(villager);
	}

	/** Adds the price rise to every trade the player pays for in emeralds (cleared again when trading ends, as vanilla does). */
	public static void raisePrices(VillagerEntity villager) {
		int rise = priceRise(villager);
		if (rise <= 0) {
			return;
		}
		for (TradeOffer offer : villager.getOffers()) {
			if (offer.getOriginalFirstBuyItem().isOf(Items.EMERALD)) {
				offer.increaseSpecialPrice(rise);
			}
		}
	}

	private static void sayNo(VillagerEntity villager) {
		villager.setHeadRollingTimeLeft(40);
		villager.playSound(SoundEvents.ENTITY_VILLAGER_NO, 1.0F, villager.getSoundPitch());
	}
}
