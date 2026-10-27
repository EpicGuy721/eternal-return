package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.survival.ColdExposure;
import com.eternalreturn.survival.CropBiomes;
import com.eternalreturn.survival.IceCracking;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.StrayEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.PolarBearEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Marking, lightning fusing sand, greenhouses, ice cracking, the cold. */
public class SurvivalWorldTests implements FabricGameTest {
	/** A Marking sword's hit makes its target glow for 30 seconds. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "marking")
	public void markingMakesTargetsGlow(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 1, 1));
		try {
			ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, new BlockPos(2, 1, 1));
			husk.setAiDisabled(true);
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			sword.addEnchantment(world.getRegistryManager().get(RegistryKeys.ENCHANTMENT)
					.getEntry(RegistryKey.of(RegistryKeys.ENCHANTMENT, EternalReturn.id("marking"))).orElseThrow(), 1);
			player.setStackInHand(Hand.MAIN_HAND, sword);
			player.attack(husk);
			StatusEffectInstance glowing = husk.getStatusEffect(StatusEffects.GLOWING);
			ctx.assertTrue(glowing != null && glowing.getDuration() == 600, "glowing for 30 seconds: " + glowing);
			Enchantment marking = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).get(EternalReturn.id("marking"));
			ctx.assertTrue(marking.getMaxLevel() == 1, "one level");
			ctx.assertTrue(marking.isAcceptableItem(new ItemStack(Items.DIAMOND_AXE)) && !marking.isAcceptableItem(new ItemStack(Items.BOW)),
					"on melee weapons, not bows");
			husk.discard();
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	/** Lightning on sand: the struck block and some sand around and below it become glass. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "lightning_glass", tickLimit = 60)
	public void lightningFusesSand(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		for (BlockPos pos : BlockPos.iterate(new BlockPos(1, 1, 1), new BlockPos(5, 3, 5))) {
			ctx.setBlockState(pos, Blocks.SAND);
		}
		LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
		bolt.refreshPositionAfterTeleport(Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(3, 4, 3))));
		world.spawnEntity(bolt);
		ctx.runAtTick(10, () -> {
			ctx.expectBlock(Blocks.GLASS, new BlockPos(3, 3, 3));
			long glass = BlockPos.stream(new BlockPos(2, 1, 2), new BlockPos(4, 3, 4)).filter(pos -> ctx.getBlockState(pos).isOf(Blocks.GLASS)).count();
			ctx.assertTrue(glass >= 3 && glass <= 18, "a small cluster of glass: " + glass);
			ctx.complete();
		});
	}

	/** A melon stem (its ideal biomes are jungles) grows as if in one under glass, not in the open or under wood. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "greenhouse")
	public void greenhousesGrowCrops(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ctx.setBlockState(new BlockPos(1, 0, 1), Blocks.FARMLAND);
		ctx.setBlockState(new BlockPos(1, 1, 1), Blocks.MELON_STEM);
		BlockPos stem = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
		ctx.assertFalse(CropBiomes.isIdeal(world, stem, Blocks.MELON_STEM), "not ideal in the open here");
		ctx.setBlockState(new BlockPos(1, 4, 1), Blocks.OAK_PLANKS);
		ctx.assertFalse(CropBiomes.inGreenhouse(world, stem, Blocks.MELON_STEM), "a wooden roof isn't a greenhouse");
		ctx.setBlockState(new BlockPos(1, 4, 1), Blocks.GLASS);
		ctx.assertTrue(CropBiomes.inGreenhouse(world, stem, Blocks.MELON_STEM) && CropBiomes.isIdeal(world, stem, Blocks.MELON_STEM),
				"under glass it's as good as a jungle");
		ctx.setBlockState(new BlockPos(1, 4, 1), Blocks.WHITE_STAINED_GLASS_PANE);
		ctx.assertTrue(CropBiomes.inGreenhouse(world, stem, Blocks.MELON_STEM), "glass panes too");
		ctx.complete();
	}

	/** An iron golem standing on ice cracks it, stage by stage, until it breaks to water (stone under it). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "ice_cracks", tickLimit = 140)
	public void heavyMobsCrackIce(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		for (BlockPos pos : BlockPos.iterate(new BlockPos(1, 0, 1), new BlockPos(3, 0, 3))) {
			ctx.setBlockState(pos, Blocks.STONE);
			ctx.setBlockState(pos.up(), Blocks.ICE);
		}
		IronGolemEntity golem = ctx.spawnEntity(EntityType.IRON_GOLEM, new BlockPos(2, 2, 2));
		((com.eternalreturn.mixin.mobs.MobEntityAccessor) golem).eternalreturn$goalSelector().clear(goal -> true);
		BlockPos ice = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
		ctx.runAtTick(30, () -> {
			float progress = IceCracking.progress(world, ice);
			ctx.assertTrue(progress > 0.1F && progress < 1.0F, "cracking under the golem: " + progress);
		});
		ctx.runAtTick(120, () -> {
			ctx.expectBlock(Blocks.WATER, new BlockPos(2, 1, 2));
			golem.discard();
			ctx.complete();
		});
	}

	/**
	 * How fast ice cracks: heavier armor faster, sprinting twice as fast, sneaking half; a polar bear with a stray on
	 * it is heavier than one alone; landing cracks it at once, and it heals when left alone. Packed ice doesn't crack.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "ice_cracks", tickLimit = 160)
	public void iceCrackRates(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(5, 1, 5));
		try {
			float bare = IceCracking.rate(player);
			ctx.assertTrue(Math.abs(IceCracking.load(player) - 1.0F) < 0.01F, "a player alone weighs 1");
			player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
			player.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
			player.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
			player.equipStack(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
			float diamond = IceCracking.rate(player);
			ctx.assertTrue(Math.abs(IceCracking.load(player) - 4.0F) < 0.01F, "in diamond, 4: " + IceCracking.load(player));
			ctx.assertTrue(diamond > 4.0F * bare, "diamond cracks it much faster: " + bare + " " + diamond);
			player.setSprinting(true);
			ctx.assertTrue(Math.abs(IceCracking.rate(player) - 2.0F * diamond) < 1.0E-6F, "sprinting twice as fast");
			player.setSprinting(false);
			player.setSneaking(true);
			ctx.assertTrue(Math.abs(IceCracking.rate(player) - 0.5F * diamond) < 1.0E-6F, "sneaking half as fast");
			player.setSneaking(false);
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				player.equipStack(slot, ItemStack.EMPTY);
			}

			PolarBearEntity bear = ctx.spawnEntity(EntityType.POLAR_BEAR, new BlockPos(6, 1, 1));
			bear.setAiDisabled(true);
			float alone = IceCracking.rate(bear);
			StrayEntity stray = ctx.spawnEntity(EntityType.STRAY, new BlockPos(6, 1, 1));
			stray.setAiDisabled(true);
			stray.startRiding(bear, true);
			ctx.assertTrue(Math.abs(IceCracking.load(bear) - 4.0F) < 0.01F && IceCracking.rate(bear) > alone,
					"a stray on a polar bear: " + IceCracking.load(bear));
			ctx.assertTrue(IceCracking.load(stray) == 0.0F, "a stray alone doesn't crack ice");
			stray.discard();
			bear.discard();

			ctx.setBlockState(new BlockPos(1, 1, 1), Blocks.ICE);
			ctx.setBlockState(new BlockPos(3, 1, 1), Blocks.PACKED_ICE);
			BlockPos ice = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
			IceCracking.land(world, ice, player, 4.0F);
			IceCracking.land(world, ctx.getAbsolutePos(new BlockPos(3, 1, 1)), player, 4.0F);
			ctx.assertTrue(Math.abs(IceCracking.progress(world, ice) - 0.2F) < 0.001F, "a 4-block fall cracks it a fifth: " + IceCracking.progress(world, ice));
			ctx.assertTrue(IceCracking.progress(world, ctx.getAbsolutePos(new BlockPos(3, 1, 1))) == 0.0F, "packed ice doesn't crack");
		} finally {
			FishingTests.disconnect(player);
		}
		BlockPos ice = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
		ctx.runAtTick(70, () -> {
			float progress = IceCracking.progress(world, ice);
			ctx.assertTrue(progress > 0.0F && progress < 0.15F, "healing, left alone: " + progress);
		});
		ctx.runAtTick(120, () -> {
			ctx.assertTrue(IceCracking.progress(world, ice) == 0.0F, "healed");
			ctx.expectBlock(Blocks.ICE, new BlockPos(1, 1, 1));
			ctx.complete();
		});
	}

	/**
	 * Out in the cold too long, a player starts freezing; a lit campfire near stops it (an unlit one doesn't), and
	 * leather lets them stay out longer.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "cold", tickLimit = 100)
	public void coldFreezesPlayers(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		fillBiome(ctx, "minecraft:snowy_plains");
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(2, 1, 2));
		ctx.assertTrue(ColdExposure.cold(world, player.getBlockPos()), "snowy plains are cold");
		int bare = ColdExposure.allowance(player);
		ctx.assertTrue(bare == 20 * 120, "two minutes in the cold: " + bare);
		player.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
		player.equipStack(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
		ctx.assertTrue(ColdExposure.allowance(player) == 20 * (120 + 2 * 90), "leather keeps the cold off longer: " + ColdExposure.allowance(player));
		ColdExposure.setExposure(player, ColdExposure.allowance(player) + 20);
		ctx.setBlockState(new BlockPos(4, 1, 2), Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, false));
		ctx.runAtTick(15, () -> ctx.assertTrue(player.getFrozenTicks() > 20, "freezing, leather or not: " + player.getFrozenTicks()));
		ctx.runAtTick(16, () -> ctx.setBlockState(new BlockPos(4, 1, 2), Blocks.CAMPFIRE));
		// (warmth is looked for once a second)
		int[] frozen = new int[1];
		ctx.runAtTick(40, () -> frozen[0] = player.getFrozenTicks());
		ctx.runAtTick(60, () -> {
			ctx.assertTrue(player.getFrozenTicks() == frozen[0], "warm by the fire, no colder: " + frozen[0] + " " + player.getFrozenTicks());
			ctx.assertTrue(ColdExposure.exposure(player) < ColdExposure.allowance(player), "warming up: " + ColdExposure.exposure(player));
			FishingTests.disconnect(player);
			ctx.complete();
		});
	}

	private static void fillBiome(TestContext ctx, String biome) {
		MinecraftServer server = ctx.getWorld().getServer();
		BlockPos a = ctx.getAbsolutePos(new BlockPos(-4, -4, -4));
		BlockPos b = ctx.getAbsolutePos(new BlockPos(11, 20, 11));
		server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(),
				"fillbiome " + a.getX() + " " + a.getY() + " " + a.getZ() + " " + b.getX() + " " + b.getY() + " " + b.getZ() + " " + biome);
	}
}
