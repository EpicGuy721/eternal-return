package com.eternalreturn.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MushroomPlantBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The new cave mushrooms (CaveMushrooms): small mushrooms as vanilla's (they spread in low light, bone meal grows a huge
 * one), each its own colour. The purple and blue ones come puffed or unpuffed, two blocks that look and are named the
 * same: a puffed one releases its gas (MushroomGas: poison from the purple, night vision from the blue) when it is
 * destroyed by anything other than a Silk Touch tool, and drops the unpuffed one (its loot table); Silk Touch keeps it
 * puffed and quiet; an unpuffed one is quiet and drops itself. Mushrooms that grow by themselves (generated, or spread
 * from another) are puffed; nothing re-puffs one. The green one glows (light 8, set on the block).
 */
public class GasMushroomBlock extends MushroomPlantBlock {
	/** Spots where the puffed mushroom being removed must stay quiet (Silk Touch, or growing into a huge mushroom). */
	private static final Set<BlockPos> QUIET = Collections.synchronizedSet(new HashSet<>());

	@Nullable
	public final MushroomGas.Kind gas;
	public final boolean puffed;
	/** Spread and generation place this one (the puffed block where there is one). */
	private final java.util.function.Supplier<Block> natural;
	/** Counted together towards the crowding limit of spreading (puffed and unpuffed of one colour). */
	private final java.util.function.Supplier<Set<Block>> family;

	public GasMushroomBlock(RegistryKey<ConfiguredFeature<?, ?>> huge, Settings settings, @Nullable MushroomGas.Kind gas, boolean puffed,
			java.util.function.Supplier<Block> natural, java.util.function.Supplier<Set<Block>> family) {
		super(huge, settings);
		this.gas = gas;
		this.puffed = puffed;
		this.natural = natural;
		this.family = family;
	}

	@Override
	public MapCodec<MushroomPlantBlock> getCodec() {
		return MushroomPlantBlock.CODEC;
	}

	/** As vanilla's: one try in 25 per random tick, unless 5 of its kind stand within 4 (1 up or down); the new one is puffed. */
	@Override
	protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		if (random.nextInt(25) != 0) {
			return;
		}
		int room = 5;
		Set<Block> kin = this.family.get();
		for (BlockPos near : BlockPos.iterate(pos.add(-4, -1, -4), pos.add(4, 1, 4))) {
			if (kin.contains(world.getBlockState(near).getBlock()) && --room <= 0) {
				return;
			}
		}
		BlockState child = this.natural.get().getDefaultState();
		BlockPos to = pos.add(random.nextInt(3) - 1, random.nextInt(2) - random.nextInt(2), random.nextInt(3) - 1);
		for (int k = 0; k < 4; k++) {
			if (world.isAir(to) && child.canPlaceAt(world, to)) {
				pos = to;
			}
			to = pos.add(random.nextInt(3) - 1, random.nextInt(2) - random.nextInt(2), random.nextInt(3) - 1);
		}
		if (world.isAir(to) && child.canPlaceAt(world, to)) {
			world.setBlockState(to, child, Block.NOTIFY_LISTENERS);
		}
	}

	@Override
	public boolean trySpawningBigMushroom(ServerWorld world, BlockPos pos, BlockState state, Random random) {
		QUIET.add(pos.toImmutable());
		try {
			return super.trySpawningBigMushroom(world, pos, state, random);
		} finally {
			QUIET.remove(pos);
		}
	}

	@Override
	public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		if (this.puffed) {
			quietIfSilkTouch(world, pos, player);
		}
		return super.onBreak(world, pos, state, player);
	}

	@Override
	public void afterBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
		super.afterBreak(world, player, pos, state, blockEntity, tool);
		forget(pos);
	}

	/** A puffed block mined with Silk Touch stays quiet (call from onBreak, before it is removed). */
	static void quietIfSilkTouch(World world, BlockPos pos, PlayerEntity player) {
		if (!world.isClient && silkTouch(world, player.getMainHandStack())) {
			QUIET.add(pos.toImmutable());
		}
	}

	/** After a puffed block is gone: forget the spot (call from afterBreak). */
	static void forget(BlockPos pos) {
		QUIET.remove(pos);
	}

	/** A puffed block replaced by something else lets out its gas, unless it was mined with Silk Touch or grew. */
	static void releaseOnRemoval(World world, BlockPos pos, MushroomGas.Kind gas) {
		if (!world.isClient && gas != null && !QUIET.remove(pos)) {
			MushroomGas.release((ServerWorld) world, pos, gas);
		}
	}

	private static boolean silkTouch(World world, ItemStack tool) {
		return world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.SILK_TOUCH)
				.map(silk -> net.minecraft.enchantment.EnchantmentHelper.getLevel(silk, tool) > 0).orElse(false);
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		super.onStateReplaced(state, world, pos, newState, moved);
		if (this.puffed && !moved && !newState.isOf(this)) {
			releaseOnRemoval(world, pos, this.gas);
		}
	}
}
