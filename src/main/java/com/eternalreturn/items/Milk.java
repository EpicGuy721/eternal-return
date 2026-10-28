package com.eternalreturn.items;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.InstantStatusEffect;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Milk by the bottle: a glass bottle used on a cow fills with milk (a bottle of milk, 4 to a stack), which, drunk, takes
 * a minute (CURDLE ticks) off every effect on the drinker. A bottle of milk and gunpowder make a splash bottle of milk,
 * and that and dragon's breath a lingering one (both potions of eternalreturn:milk, so they fly, splash, linger and tip
 * arrows as potions do): each takes a minute off the effects of whatever it reaches (less the further from the splash,
 * as splashes are). Cows give milk only so fast (CowMilkMixin): CowMilk.
 */
public final class Milk {
	public static final int CURDLE = 1200;
	public static final RegistryEntry<StatusEffect> EFFECT = Registry.registerReference(Registries.STATUS_EFFECT, EternalReturn.id("milk"), new Effect());
	public static final RegistryEntry<Potion> POTION = Registry.registerReference(Registries.POTION, EternalReturn.id("milk"),
			new Potion("milk", new StatusEffectInstance(EFFECT, 1)));
	public static final Item BOTTLE = Registry.register(Registries.ITEM, EternalReturn.id("milk_bottle"),
			new BottleItem(new Item.Settings().maxCount(4)));
	/** The milk's colour (the bottle's tint, the effect's). */
	public static final int COLOR = 0xF3F0E6;

	private Milk() {
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries -> entries.addAfter(Items.MILK_BUCKET, BOTTLE));
	}

	/** Takes this many ticks off every effect on the entity (ending those it runs out); endless ones stay. */
	public static void curdle(LivingEntity entity, int ticks) {
		if (ticks <= 0) {
			return;
		}
		for (StatusEffectInstance effect : new ArrayList<>(entity.getStatusEffects())) {
			if (effect.getEffectType() == EFFECT || effect.isInfinite()) {
				continue;
			}
			entity.removeStatusEffect(effect.getEffectType());
			int left = effect.getDuration() - ticks;
			if (left > 0) {
				entity.addStatusEffect(new StatusEffectInstance(effect.getEffectType(), left, effect.getAmplifier(), effect.isAmbient(),
						effect.shouldShowParticles(), effect.shouldShowIcon()));
			}
		}
	}

	/** The milk effect: instant, a minute off every other effect (scaled down as a splash's effects are). */
	static class Effect extends InstantStatusEffect {
		Effect() {
			super(StatusEffectCategory.BENEFICIAL, COLOR);
		}

		@Override
		public void applyInstantEffect(@Nullable Entity source, @Nullable Entity attacker, LivingEntity target, int amplifier, double proximity) {
			curdle(target, (int) Math.round(CURDLE * (amplifier + 1) * proximity));
		}

		@Override
		public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
			curdle(entity, CURDLE * (amplifier + 1));
			return true;
		}
	}

	/** A bottle of milk: drunk, a minute off every effect, the bottle given back. */
	public static class BottleItem extends Item {
		public BottleItem(Settings settings) {
			super(settings);
		}

		@Override
		public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
			if (user instanceof ServerPlayerEntity player) {
				Criteria.CONSUME_ITEM.trigger(player, stack);
				player.incrementStat(Stats.USED.getOrCreateStat(this));
			}
			if (!world.isClient) {
				curdle(user, CURDLE);
			}
			if (user instanceof PlayerEntity player && player.isInCreativeMode()) {
				return stack;
			}
			stack.decrement(1);
			if (stack.isEmpty()) {
				return new ItemStack(Items.GLASS_BOTTLE);
			}
			if (user instanceof PlayerEntity player && !player.getInventory().insertStack(new ItemStack(Items.GLASS_BOTTLE))) {
				player.dropItem(new ItemStack(Items.GLASS_BOTTLE), false);
			}
			return stack;
		}

		/** A second, as every drink (Food.DRINK_TICKS). */
		@Override
		public int getMaxUseTime(ItemStack stack, LivingEntity user) {
			return com.eternalreturn.survival.Food.DRINK_TICKS;
		}

		@Override
		public UseAction getUseAction(ItemStack stack) {
			return UseAction.DRINK;
		}

		@Override
		public SoundEvent getDrinkSound() {
			return SoundEvents.ENTITY_GENERIC_DRINK;
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			return ItemUsage.consumeHeldItem(world, user, hand);
		}
	}

	/**
	 * How much milk a cow has (CowMilkMixin): BOTTLES at most, one coming back each REFILL_TICKS (survival.cowMilkMinutes
	 * for all of them; 0, no limit). A bottle takes one, a bucket all of them, so only a full cow fills a bucket.
	 */
	public static final class CowMilk {
		public static final int BOTTLES = 4;

		/** What a cow keeps (AnimalEntityMixin): when it's full again, on the world's clock. */
		public interface Holder {
			long eternalreturn$milkFullAt();

			void eternalreturn$setMilkFullAt(long time);
		}

		private CowMilk() {
		}

		private static long refillTicks() {
			return Math.max(0L, Math.round(EternalReturnConfig.get().survival.cowMilkMinutes * 1200.0 / BOTTLES));
		}

		public static int available(CowEntity cow) {
			long refill = refillTicks();
			if (refill == 0L) {
				return BOTTLES;
			}
			long wait = ((Holder) cow).eternalreturn$milkFullAt() - cow.getWorld().getTime();
			return wait <= 0L ? BOTTLES : Math.max(0, BOTTLES - (int) Math.ceil(wait / (double) refill));
		}

		/** Milks this many bottles' worth, if it has them; whether it did. */
		public static boolean take(CowEntity cow, int bottles) {
			if (available(cow) < bottles) {
				return false;
			}
			Holder holder = (Holder) cow;
			holder.eternalreturn$setMilkFullAt(Math.max(holder.eternalreturn$milkFullAt(), cow.getWorld().getTime()) + bottles * refillTicks());
			return true;
		}
	}
}
