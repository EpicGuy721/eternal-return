package com.eternalreturn.gametest;

import com.eternalreturn.items.Gems;
import com.eternalreturn.mobs.PiglinTrades;
import com.eternalreturn.scrolls.Scrolls;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Piglins bartering for rubies, and their trade limits. */
public class PiglinTradeTests implements FabricGameTest {
	/** Rubies roll their own table: scrolls and enchanted books among it, none of gold's gravel or soul sand. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "piglin_rubies")
	public void rubyBarterLoot(TestContext ctx) {
		PiglinEntity piglin = ctx.spawnEntity(EntityType.PIGLIN, new BlockPos(1, 1, 1));
		piglin.setAiDisabled(true);
		Map<Item, Integer> counts = new HashMap<>();
		for (int i = 0; i < 1000; i++) {
			for (ItemStack stack : PiglinTrades.rubyLoot(piglin)) {
				counts.merge(stack.getItem(), 1, Integer::sum);
			}
		}
		piglin.discard();
		int scrolls = counts.getOrDefault(Scrolls.SCROLL, 0);
		int books = counts.getOrDefault(Items.ENCHANTED_BOOK, 0);
		ctx.assertTrue(scrolls > 20 && books > 20, "scrolls and enchanted books: " + scrolls + " " + books);
		ctx.assertTrue(!counts.containsKey(Items.GRAVEL) && !counts.containsKey(Items.SOUL_SAND), "nothing so cheap: " + counts);
		ctx.complete();
	}

	/**
	 * A ruby held out to a piglin is taken, admired and bartered (counted); a piglin out of ruby trades won't take one
	 * but still takes gold, and its trades come back in time.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "piglin_rubies", tickLimit = 200)
	public void piglinsBarterRubiesUpToALimit(TestContext ctx) {
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 1, 3));
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
		PiglinEntity piglin = ctx.spawnEntity(EntityType.PIGLIN, new BlockPos(1, 1, 1));
		piglin.setImmuneToZombification(true);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Gems.RUBY, 3));
		piglin.interactMob(player, Hand.MAIN_HAND);
		ctx.assertTrue(piglin.getOffHandStack().isOf(Gems.RUBY) && player.getMainHandStack().getCount() == 2, "the piglin takes a ruby");
		ctx.runAtTick(160, () -> {
			try {
				ctx.assertTrue(piglin.getOffHandStack().isEmpty(), "bartered: " + piglin.getOffHandStack());
				ctx.assertTrue(PiglinTrades.counts(piglin).ruby == 1, "one ruby trade counted");
				Box around = new Box(ctx.getAbsolutePos(new BlockPos(-3, 0, -3))).union(new Box(ctx.getAbsolutePos(new BlockPos(5, 4, 7))));
				List<ItemEntity> drops = ctx.getWorld().getEntitiesByClass(ItemEntity.class, around, item -> !item.getStack().isOf(Gems.RUBY));
				ctx.assertTrue(!drops.isEmpty(), "something given for it");

				PiglinTrades.Counts counts = PiglinTrades.counts(piglin);
				counts.ruby = PiglinTrades.tradesLeft(piglin, new ItemStack(Gems.RUBY)) + counts.ruby;
				ctx.assertTrue(PiglinTrades.tradesLeft(piglin, new ItemStack(Gems.RUBY)) == 0, "out of ruby trades");
				piglin.interactMob(player, Hand.MAIN_HAND);
				ctx.assertTrue(piglin.getOffHandStack().isEmpty() && player.getMainHandStack().getCount() == 2, "no ruby taken when out of trades");
				ctx.assertTrue(PiglinTrades.willTrade(piglin, new ItemStack(Items.GOLD_INGOT)), "gold still taken");
				PiglinTrades.sync(piglin);
				int out = ((PiglinTrades.Holder) piglin).eternalreturn$tradedOut();
				ctx.assertTrue(out == PiglinTrades.RUBY_OUT, "the client is told it's out of ruby trades, not gold: " + out);
				ctx.assertTrue(!PiglinTrades.mayGrumble(piglin), "it grumbled just now, and won't again so soon");
				counts.resetAt = ctx.getWorld().getTime();
				ctx.assertTrue(PiglinTrades.willTrade(piglin, new ItemStack(Gems.RUBY)), "the trades come back");
				PiglinTrades.sync(piglin);
				ctx.assertTrue(((PiglinTrades.Holder) piglin).eternalreturn$tradedOut() == 0, "and the client is told");
			} finally {
				FishingTests.disconnect(player);
				piglin.discard();
			}
			ctx.complete();
		});
	}

	/** /enchant raises an enchantment the held item already has (as an inherent one), where vanilla refuses it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "piglin_rubies")
	public void enchantCommandRaisesOwnEnchantment(TestContext ctx) {
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 1, 1));
		try {
			var sharpness = ctx.getWorld().getRegistryManager().get(net.minecraft.registry.RegistryKeys.ENCHANTMENT).getEntry(net.minecraft.enchantment.Enchantments.SHARPNESS)
					.orElseThrow();
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			sword.addEnchantment(sharpness, 1);
			player.setStackInHand(Hand.MAIN_HAND, sword);
			var server = ctx.getWorld().getServer();
			server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(), "enchant " + player.getGameProfile().getName() + " minecraft:sharpness 3");
			ctx.assertTrue(player.getMainHandStack().getEnchantments().getLevel(sharpness) == 3, "Sharpness I raised to III: "
					+ player.getMainHandStack().getEnchantments());
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}
}
