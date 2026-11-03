package com.eternalreturn.blocks;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Pistons crush (redstone.pistonsCrush): a piston pushing a row of blocks (no slime or honey in it) into obsidian or
 * crying obsidian (block tag eternalreturn:piston_anvils) crushes the block against it, instead of failing to push,
 * and the rest of the row moves up into its place (CrushingMixin, PistonCrushDropsMixin). The crushed block drops
 * the next step of the crusher chain, set by the block tags eternalreturn:crushes_to/cobblestone (stone, granite,
 * diorite, andesite, tuff...), crushes_to/gravel (cobblestone and the like) and crushes_to/sand (gravel); anything
 * else drops as if mined with an iron pickaxe (no Fortune or Silk Touch: ores give their ore, glass nothing). A
 * chest or other container spills its contents as it's crushed. Only pushes crush, never pulls.
 */
public final class Crushing {
	public static final TagKey<net.minecraft.block.Block> ANVILS = tag("piston_anvils");
	public static final TagKey<net.minecraft.block.Block> TO_COBBLESTONE = tag("crushes_to/cobblestone");
	public static final TagKey<net.minecraft.block.Block> TO_GRAVEL = tag("crushes_to/gravel");
	public static final TagKey<net.minecraft.block.Block> TO_SAND = tag("crushes_to/sand");

	private Crushing() {
	}

	private static TagKey<net.minecraft.block.Block> tag(String path) {
		return TagKey.of(RegistryKeys.BLOCK, EternalReturn.id(path));
	}

	public static boolean enabled() {
		return EternalReturnConfig.get().redstone.pistonsCrush;
	}

	/** Whether a pushed block can be crushed: anything a piston moves that can be broken at all. */
	public static boolean crushable(World world, BlockPos pos, BlockState state) {
		return !state.isAir() && state.getHardness(world, pos) >= 0.0F && !state.isIn(ANVILS);
	}

	/** What a crushed block drops: its crusher-chain step, or what an iron pickaxe would mine. */
	public static List<ItemStack> drops(ServerWorld world, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (state.isIn(TO_COBBLESTONE)) {
			return List.of(new ItemStack(Items.COBBLESTONE));
		}
		if (state.isIn(TO_GRAVEL)) {
			return List.of(new ItemStack(Items.GRAVEL));
		}
		if (state.isIn(TO_SAND)) {
			return List.of(new ItemStack(Items.SAND));
		}
		return Block.getDroppedStacks(state, world, pos, blockEntity, null, new ItemStack(Items.IRON_PICKAXE));
	}

	/** Breaks a crushed block's drops out at its place, with a grinding sound. */
	public static void dropCrushed(ServerWorld world, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		for (ItemStack stack : drops(world, pos, state, blockEntity)) {
			Block.dropStack(world, pos, stack);
		}
		state.onStacksDropped(world, pos, new ItemStack(Items.IRON_PICKAXE), true);
		world.playSound(null, pos, SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.BLOCKS, 0.6F, 0.8F + world.random.nextFloat() * 0.2F);
	}
}
