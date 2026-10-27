package com.eternalreturn.mobs;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.items.Gems;
import net.minecraft.entity.mob.PiglinBrain;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;

import java.util.List;

/**
 * Bartering with piglins (PiglinBarterMixin): besides gold ingots, they take rubies, for better things (loot table
 * eternalreturn:gameplay/piglin_ruby_bartering, scrolls and enchanted books among them). Each piglin takes only so
 * many of each (mobs.piglinGoldTrades, piglinRubyTrades) before it won't barter any more, ignoring what's thrown it,
 * until its trades come back, mobs.piglinTradeResetMinutes after the first of them.
 */
public final class PiglinTrades {
	public static final RegistryKey<LootTable> RUBY_BARTERING = RegistryKey.of(RegistryKeys.LOOT_TABLE, EternalReturn.id("gameplay/piglin_ruby_bartering"));

	/** What a piglin keeps (PiglinTradesMixin). */
	public interface Holder {
		Counts eternalreturn$trades();
	}

	public static final class Counts {
		public int gold;
		public int ruby;
		/** When the trades come back, on the world's clock; 0 when none have been made since they last did. */
		public long resetAt;

		public void write(NbtCompound nbt) {
			NbtCompound trades = new NbtCompound();
			trades.putInt("Gold", this.gold);
			trades.putInt("Ruby", this.ruby);
			trades.putLong("ResetAt", this.resetAt);
			nbt.put("EternalReturnTrades", trades);
		}

		public void read(NbtCompound nbt) {
			NbtCompound trades = nbt.getCompound("EternalReturnTrades");
			this.gold = trades.getInt("Gold");
			this.ruby = trades.getInt("Ruby");
			this.resetAt = trades.getLong("ResetAt");
		}
	}

	private PiglinTrades() {
	}

	public static boolean isRuby(ItemStack stack) {
		return stack.isOf(Gems.RUBY);
	}

	/** What a piglin barters for: a gold ingot or a ruby. */
	public static boolean isBarterItem(ItemStack stack) {
		return stack.isOf(PiglinBrain.BARTERING_ITEM) || isRuby(stack);
	}

	public static Counts counts(PiglinEntity piglin) {
		Counts counts = ((Holder) piglin).eternalreturn$trades();
		if (counts.resetAt != 0L && piglin.getWorld().getTime() >= counts.resetAt) {
			counts.gold = 0;
			counts.ruby = 0;
			counts.resetAt = 0L;
		}
		return counts;
	}

	/** How many more of this the piglin will take before its trades come back. */
	public static int tradesLeft(PiglinEntity piglin, ItemStack stack) {
		EternalReturnConfig.MobTweaks config = EternalReturnConfig.get().mobs;
		if (config.piglinTradeResetMinutes <= 0) {
			return Integer.MAX_VALUE;
		}
		Counts counts = counts(piglin);
		return isRuby(stack) ? config.piglinRubyTrades - counts.ruby : config.piglinGoldTrades - counts.gold;
	}

	public static boolean willTrade(PiglinEntity piglin, ItemStack stack) {
		return !isBarterItem(stack) || tradesLeft(piglin, stack) > 0;
	}

	/** One trade of this made: counted, the clock started if it's the first. */
	public static void traded(PiglinEntity piglin, ItemStack stack) {
		Counts counts = counts(piglin);
		if (isRuby(stack)) {
			counts.ruby++;
		} else {
			counts.gold++;
		}
		if (counts.resetAt == 0L) {
			counts.resetAt = piglin.getWorld().getTime() + 1200L * Math.max(1, EternalReturnConfig.get().mobs.piglinTradeResetMinutes);
		}
	}

	public static List<ItemStack> rubyLoot(PiglinEntity piglin) {
		ServerWorld world = (ServerWorld) piglin.getWorld();
		LootTable table = world.getServer().getReloadableRegistries().getLootTable(RUBY_BARTERING);
		return table.generateLoot(new LootContextParameterSet.Builder(world).add(LootContextParameters.THIS_ENTITY, piglin).build(LootContextTypes.BARTER));
	}
}
