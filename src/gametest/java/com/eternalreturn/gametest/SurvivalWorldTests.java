package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.survival.CropBiomes;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Marking, lightning fusing sand, greenhouses. */
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
}
