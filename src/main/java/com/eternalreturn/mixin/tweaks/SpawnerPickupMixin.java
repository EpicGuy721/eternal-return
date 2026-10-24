package com.eternalreturn.mixin.tweaks;

import net.minecraft.block.BlockState;
import net.minecraft.block.SpawnerBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mined with a pickaxe, a spawner drops itself, empty (loot table minecraft:blocks/spawner), and so no experience: else
 * placing and mining it again would be an endless source of it.
 */
@Mixin(SpawnerBlock.class)
public abstract class SpawnerPickupMixin {
	@ModifyVariable(method = "onStacksDropped", at = @At("HEAD"), argsOnly = true)
	private boolean eternalreturn$noExperienceWhenKept(boolean dropExperience, BlockState state, ServerWorld world, BlockPos pos, ItemStack tool) {
		return dropExperience && !tool.isIn(ItemTags.PICKAXES);
	}
}
