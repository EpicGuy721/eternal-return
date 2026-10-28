package com.eternalreturn.gametest;

import com.eternalreturn.fishing.FishingItems;
import com.eternalreturn.survival.Meals;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.CakeBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;

import java.util.List;

/**
 * No hunger, and food that heals (Meals): the food bar stays full and health doesn't come back by itself; each
 * food heals its own amount at its own pace, with its own stack size, eating time and effects; ordinary food only
 * when hurt; Hunger halves food healing, Saturation heals; cake slices heal; the mod's fish heal like raw salmon.
 */
public class FoodTests implements FabricGameTest {
	private static final String BATCH = "food";

	private static void within(TestContext ctx, float actual, float expected, String what) {
		ctx.assertTrue(Math.abs(actual - expected) < 0.6F, what + ": " + actual + ", expected about " + expected);
	}

	/** The table's values are on the items: stack sizes, eating times, effects. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void foodTableOnTheItems(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 1, 1));
		try {
			ctx.assertTrue(new ItemStack(Items.COOKED_BEEF).getMaxCount() == 4 && new ItemStack(Items.COOKED_BEEF).getMaxUseTime(player) == 48,
					"steak: 4 a stack, 2.4 seconds to eat");
			ctx.assertTrue(new ItemStack(Items.ROTTEN_FLESH).getMaxCount() == 64 && new ItemStack(Items.SPIDER_EYE).getMaxCount() == 64,
					"rotten flesh and spider eyes stack to 64");
			ctx.assertTrue(new ItemStack(Items.PUMPKIN_PIE).getMaxCount() == 2 && new ItemStack(Items.DRIED_KELP).getMaxUseTime(player) == 8,
					"pie: 2 a stack; dried kelp: 0.4 seconds");
			ctx.assertTrue(new ItemStack(Items.HONEY_BOTTLE).getMaxUseTime(player) == 40, "honey: 2 seconds");
			FoodComponent steak = new ItemStack(Items.COOKED_BEEF).get(DataComponentTypes.FOOD);
			ctx.assertTrue(steak.canAlwaysEat() && steak.effects().stream().anyMatch(e -> e.effect().getEffectType() == StatusEffects.STRENGTH),
					"steak gives Strength and can always be eaten");
			FoodComponent notch = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE).get(DataComponentTypes.FOOD);
			ctx.assertTrue(notch.effects().stream().anyMatch(e -> e.effect().getEffectType() == StatusEffects.REGENERATION), "the enchanted golden apple keeps its own effects");
			ItemStack fish = new ItemStack(FishingItems.FISH.get(0));
			ctx.assertTrue(fish.getMaxCount() == new ItemStack(Items.SALMON).getMaxCount() && Meals.heal(fish).equals(Meals.heal(new ItemStack(Items.SALMON))),
					"the mod's fish stack and heal like raw salmon");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	/**
	 * On Normal: health never comes back by itself and the food bar stays full; ordinary food only when hurt; a melon
	 * slice heals at once, steak spread over 10 seconds; Hunger halves it; Saturation heals; the healing still to come
	 * is saved with the player.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 400)
	public void foodHealsAndNothingElse(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		Difficulty difficulty = world.getDifficulty();
		world.getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 1, 1));
		// The scripted player has no network connection the server ticks, so tick it as a real player is ticked.
		ctx.runAtEveryTick(player::playerTick);
		player.getHungerManager().addExhaustion(40.0F);
		ctx.assertTrue(!player.canConsume(false), "at full health, bread can't be eaten");
		ctx.assertTrue(player.canConsume(true), "food with a perk can");
		player.setHealth(6.0F);
		ctx.assertTrue(player.canConsume(false), "hurt, bread can be eaten");

		Meals.ate(player, new ItemStack(Items.MELON_SLICE));
		within(ctx, player.getHealth(), 8.0F, "a melon slice heals 2 at once");
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 200));
		Meals.ate(player, new ItemStack(Items.MELON_SLICE));
		within(ctx, player.getHealth(), 9.0F, "with Hunger, half that");
		player.removeStatusEffect(StatusEffects.HUNGER);
		player.setHealth(6.0F);
		Meals.ate(player, new ItemStack(Items.COOKED_BEEF));
		NbtCompound saved = new NbtCompound();
		player.writeCustomDataToNbt(saved);
		ctx.assertTrue(saved.getList("eternalreturn_meals", 10).size() == 1, "the healing to come is saved");
		ctx.runAtTick(ctx.getTick() + 100, () -> within(ctx, player.getHealth(), 12.0F, "steak: 6 of its 12 after 5 seconds"));
		ctx.runAtTick(ctx.getTick() + 220, () -> {
			within(ctx, player.getHealth(), 18.0F, "steak: all 12 after 10 seconds");
			ctx.assertTrue(player.getHungerManager().getFoodLevel() == 20, "the food bar stays full");
		});
		ctx.runAtTick(ctx.getTick() + 320, () -> {
			within(ctx, player.getHealth(), 18.0F, "no healing by itself on Normal");
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 2, 0));
		});
		ctx.runAtTick(ctx.getTick() + 330, () -> {
			ctx.assertTrue(player.getHealth() > 18.5F, "Saturation heals: " + player.getHealth());
			FishingTests.disconnect(player);
			world.getServer().setDifficulty(difficulty, true);
			ctx.complete();
		});
	}

	/** A slice of cake heals 2 hearts over 3 seconds, and is only eaten when hurt. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 200)
	public void cakeSlicesHeal(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		Difficulty difficulty = world.getDifficulty();
		world.getServer().setDifficulty(Difficulty.NORMAL, true);
		ctx.setBlockState(new BlockPos(2, 0, 2), Blocks.STONE);
		ctx.setBlockState(new BlockPos(2, 1, 2), Blocks.CAKE);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 1, 1));
		ctx.runAtEveryTick(player::playerTick);
		BlockPos cake = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
		BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(cake), Direction.UP, cake, false);
		player.interactionManager.interactBlock(player, world, ItemStack.EMPTY, Hand.MAIN_HAND, hit);
		ctx.assertTrue(world.getBlockState(cake).get(CakeBlock.BITES) == 0, "not eaten at full health");
		player.setHealth(10.0F);
		player.interactionManager.interactBlock(player, world, ItemStack.EMPTY, Hand.MAIN_HAND, hit);
		ctx.assertTrue(world.getBlockState(cake).get(CakeBlock.BITES) == 1, "a slice eaten when hurt");
		ctx.runAtTick(ctx.getTick() + 80, () -> {
			within(ctx, player.getHealth(), 14.0F, "a slice heals 4");
			FishingTests.disconnect(player);
			world.getServer().setDifficulty(difficulty, true);
			ctx.complete();
		});
	}

	/**
	 * A glass bottle milks a cow (4 to a stack); the cow fills up again only slowly, so a bucket needs a full one. Drunk, a
	 * bottle of milk takes a minute off each effect; it and gunpowder make a splash bottle of milk, and that and dragon's
	 * breath a lingering one; a splash takes a minute off the effects of whatever it lands on.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "milk", tickLimit = 100)
	public void milkByTheBottle(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 1, 1));
		try {
			net.minecraft.entity.passive.CowEntity cow = ctx.spawnEntity(EntityType.COW, new BlockPos(3, 1, 1));
			cow.setAiDisabled(true);
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE, 6));
			for (int i = 0; i < 5; i++) {
				cow.interactMob(player, Hand.MAIN_HAND);
			}
			ctx.assertTrue(player.getInventory().count(com.eternalreturn.items.Milk.BOTTLE) == 4 && player.getMainHandStack().getCount() == 2,
					"four bottles from a full cow, then no more: " + player.getInventory().count(com.eternalreturn.items.Milk.BOTTLE));
			ctx.assertTrue(new ItemStack(com.eternalreturn.items.Milk.BOTTLE).getMaxCount() == 4, "4 to a stack");
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BUCKET));
			cow.interactMob(player, Hand.MAIN_HAND);
			ctx.assertTrue(player.getMainHandStack().isOf(Items.BUCKET), "no bucket from a milked-dry cow");
			net.minecraft.entity.passive.CowEntity full = ctx.spawnEntity(EntityType.COW, new BlockPos(5, 1, 1));
			full.setAiDisabled(true);
			full.interactMob(player, Hand.MAIN_HAND);
			ctx.assertTrue(player.getMainHandStack().isOf(Items.MILK_BUCKET), "a bucket from a full cow");
			ctx.assertTrue(com.eternalreturn.items.Milk.CowMilk.available(full) == 0, "which empties it");

			player.clearStatusEffects();
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 3000));
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 1000));
			com.eternalreturn.items.Milk.BOTTLE.finishUsing(new ItemStack(com.eternalreturn.items.Milk.BOTTLE), world, player);
			StatusEffectInstance speed = player.getStatusEffect(StatusEffects.SPEED);
			ctx.assertTrue(speed != null && speed.getDuration() == 1800 && !player.hasStatusEffect(StatusEffects.POISON),
					"a minute off each effect: speed " + speed + ", poison gone");

			var input = net.minecraft.recipe.input.CraftingRecipeInput.create(2, 1,
					List.of(new ItemStack(com.eternalreturn.items.Milk.BOTTLE), new ItemStack(Items.GUNPOWDER)));
			ItemStack splash = world.getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING, input, world)
					.map(r -> r.value().craft(input, world.getRegistryManager())).orElse(ItemStack.EMPTY);
			ctx.assertTrue(splash.isOf(Items.SPLASH_POTION) && splash.get(DataComponentTypes.POTION_CONTENTS).matches(com.eternalreturn.items.Milk.POTION),
					"bottle of milk and gunpowder: a splash bottle of milk: " + splash);
			var input2 = net.minecraft.recipe.input.CraftingRecipeInput.create(2, 1, List.of(splash, new ItemStack(Items.DRAGON_BREATH)));
			ItemStack lingering = world.getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING, input2, world)
					.map(r -> r.value().craft(input2, world.getRegistryManager())).orElse(ItemStack.EMPTY);
			ctx.assertTrue(lingering.isOf(Items.LINGERING_POTION) && lingering.get(DataComponentTypes.POTION_CONTENTS).matches(com.eternalreturn.items.Milk.POTION),
					"and dragon's breath: a lingering one: " + lingering);

			net.minecraft.entity.mob.ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, new BlockPos(1, 1, 4));
			husk.setAiDisabled(true);
			husk.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 2000));
			net.minecraft.entity.projectile.thrown.PotionEntity thrown = new net.minecraft.entity.projectile.thrown.PotionEntity(world, player);
			thrown.setItem(splash);
			thrown.setPosition(husk.getX(), husk.getY() + 1.0, husk.getZ());
			thrown.setVelocity(0.0, -0.5, 0.0);
			world.spawnEntity(thrown);
			ctx.runAtTick(20, () -> {
				StatusEffectInstance slow = husk.getStatusEffect(StatusEffects.SLOWNESS);
				ctx.assertTrue(slow != null && slow.getDuration() < 1000, "the splash took most of a minute off: " + slow);
				husk.discard();
				ctx.complete();
			});
		} finally {
			FishingTests.disconnect(player);
		}
	}
}
