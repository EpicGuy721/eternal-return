package com.eternalreturn.gametest;

import com.eternalreturn.fishing.Bait;
import com.eternalreturn.fishing.FishingItems;
import com.eternalreturn.fishing.Thriftiness;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.VillagerProfession;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.lang.reflect.Field;
import java.util.UUID;

/** Regression tests: Lure V with bait still bites, Thriftiness, doubled fish prices. */
public class FishingFixTests implements FabricGameTest {
	static Object field(Object target, String name) {
		try {
			Field f = target.getClass().getDeclaredField(name);
			f.setAccessible(true);
			return f.get(target);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	static ServerPlayerEntity survivalPlayer(TestContext ctx, BlockPos rel) {
		ServerWorld world = ctx.getWorld();
		ConnectedClientData data = ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(), "test-angler"), false);
		ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, data.gameProfile(), data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		new EmbeddedChannel(connection);
		world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
		player.changeGameMode(GameMode.SURVIVAL);
		Vec3d p = Vec3d.ofBottomCenter(ctx.getAbsolutePos(rel));
		player.teleport(world, p.x, p.y, p.z, 0.0F, 0.0F);
		return player;
	}

	static void waterPool(TestContext ctx) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
				boolean inside = x >= 3 && x <= 5 && z >= 3 && z <= 5;
				boolean rim = x >= 2 && x <= 6 && z >= 2 && z <= 6 && !inside;
				ctx.setBlockState(x, 1, z, inside ? Blocks.WATER : rim ? Blocks.STONE : Blocks.AIR);
			}
		}
		ctx.setBlockState(1, 1, 4, Blocks.STONE);
	}

	/**
	 * Lure V (this mod raises Lure's max level) is a 500 tick wait reduction and chum adds 200.
	 * No shortcuts here: the hook has to get a bite on its own.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "lure", tickLimit = 900)
	public void lureFiveWithChumStillBites(TestContext ctx) {
		ctx.getWorld().setTimeOfDay(6000);
		waterPool(ctx);
		ServerPlayerEntity player = survivalPlayer(ctx, new BlockPos(1, 2, 4));
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
		player.getInventory().main.set(9, new ItemStack(FishingItems.CHUM, 8));
		FishingBobberEntity hook = new FishingBobberEntity(player, ctx.getWorld(), 0, 500);
		Vec3d over = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(4, 2, 4)));
		hook.refreshPositionAndAngles(over.x, over.y, over.z, 0.0F, 0.0F);
		hook.setVelocity(Vec3d.ZERO);
		ctx.getWorld().spawnEntity(hook);
		ctx.runAtEveryTick(() -> {
			ctx.assertTrue(!hook.isRemoved(), "hook removed");
			if ((int) field(hook, "hookCountdown") > 0) {
				System.out.println("[gametest] Lure V + chum got a bite at tick " + ctx.getTick());
				player.getServer().getPlayerManager().remove(player);
				ctx.complete();
			} else if (ctx.getTick() % 100 == 0) {
				System.out.println("[gametest] t=" + ctx.getTick() + " state=" + field(hook, "state") + " wait=" + field(hook, "waitCountdown")
						+ " travel=" + field(hook, "fishTravelCountdown"));
			}
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "thrift", tickLimit = 40)
	public void thriftinessSavesBait(TestContext ctx) {
		RegistryEntry<Enchantment> thrift = ctx.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(Thriftiness.KEY).orElseThrow();
		Random random = Random.create(42);
		ctx.assertTrue(thrift.value().getMaxLevel() == 5, "max level " + thrift.value().getMaxLevel());
		ctx.assertTrue(Thriftiness.baitSavingChance(new ItemStack(Items.FISHING_ROD), random) == 0.0F, "plain rod saves nothing");
		for (int level = 1; level <= 5; level++) {
			ItemStack rod = new ItemStack(Items.FISHING_ROD);
			rod.addEnchantment(thrift, level);
			float chance = Thriftiness.baitSavingChance(rod, random);
			ctx.assertTrue(Math.abs(chance - 0.1F * level) < 0.001F, "level " + level + " chance " + chance);
		}
		ItemStack rod = new ItemStack(Items.FISHING_ROD);
		rod.addEnchantment(thrift, 5);
		PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
		player.getInventory().main.set(0, new ItemStack(FishingItems.WORM, 64));
		player.getInventory().main.set(1, new ItemStack(FishingItems.WORM, 64));
		int used = 0;
		for (int i = 0; i < 100; i++) {
			if (Bait.consumeAfterCatch(player, new ItemStack(FishingItems.WORM), rod, random)) {
				used++;
			}
		}
		int left = player.getInventory().main.get(0).getCount() + player.getInventory().main.get(1).getCount();
		ctx.assertTrue(used >= 35 && used <= 65, "Thriftiness V should save about half, used " + used + " of 100");
		ctx.assertTrue(left == 128 - used, "inventory lost " + (128 - left) + " but " + used + " were reported used");
		ItemStack plain = new ItemStack(Items.FISHING_ROD);
		int plainUsed = 0;
		for (int i = 0; i < 20; i++) {
			if (Bait.consumeAfterCatch(player, new ItemStack(FishingItems.WORM), plain, random)) {
				plainUsed++;
			}
		}
		ctx.assertTrue(plainUsed == 20, "plain rod always uses bait, used " + plainUsed);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "thrift", tickLimit = 40)
	public void thriftinessFromTheTable(TestContext ctx) {
		var registry = ctx.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
		Random random = Random.create(7);
		int found = 0;
		for (int i = 0; i < 400; i++) {
			List<EnchantmentLevelEntry> rolled = EnchantmentHelper.generateEnchantments(random, new ItemStack(Items.FISHING_ROD), 30,
					registry.getEntryList(EnchantmentTags.IN_ENCHANTING_TABLE).orElseThrow().stream());
			for (EnchantmentLevelEntry entry : rolled) {
				if (entry.enchantment.matchesKey(Thriftiness.KEY)) {
					found++;
				}
			}
		}
		ctx.assertTrue(found > 0, "Thriftiness never rolled on a fishing rod in 400 level-30 table rolls");
		System.out.println("[gametest] Thriftiness rolled " + found + " times in 400 rod enchants");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "prices", tickLimit = 100)
	public void fishSellForDouble(TestContext ctx) throws ReflectiveOperationException {
		Method fill = VillagerEntity.class.getDeclaredMethod("fillRecipes");
		fill.setAccessible(true);
		Map<String, Integer> emeralds = new HashMap<>();
		for (int n = 0; n < 25; n++) {
			VillagerEntity villager = EntityType.VILLAGER.create(ctx.getWorld());
			villager.setVillagerData(villager.getVillagerData().withProfession(VillagerProfession.FISHERMAN).withLevel(1));
			villager.getOffers();
			for (int level = 2; level <= 5; level++) {
				villager.setVillagerData(villager.getVillagerData().withLevel(level));
				fill.invoke(villager);
			}
			for (TradeOffer offer : villager.getOffers()) {
				if (offer.getSellItem().isOf(Items.EMERALD)) {
					String key = offer.getOriginalFirstBuyItem().getCount() + "x" + offer.getOriginalFirstBuyItem().getItem();
					emeralds.put(key, offer.getSellItem().getCount());
				}
			}
		}
		System.out.println("[gametest] fisherman buys: " + emeralds);
		ctx.assertTrue(emeralds.getOrDefault("15xminecraft:cod", 0) == 2, "15 cod should pay 2 (vanilla 1): " + emeralds);
		ctx.assertTrue(emeralds.getOrDefault("15xeternalreturn:perch", 0) == 2, "15 perch should pay 2: " + emeralds);
		ctx.assertTrue(emeralds.getOrDefault("1xeternalreturn:koi", 0) == 10, "koi should pay 10: " + emeralds);
		ctx.complete();
	}
}
