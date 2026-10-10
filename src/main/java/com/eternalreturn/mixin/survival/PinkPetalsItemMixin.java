package com.eternalreturn.mixin.survival;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pink petals are placed on water the way lily pads are: aimed at the water surface (Farming). */
@Mixin(Item.class)
public abstract class PinkPetalsItemMixin {
	@Shadow
	protected static BlockHitResult raycast(World world, PlayerEntity player, RaycastContext.FluidHandling fluidHandling) {
		throw new AssertionError();
	}

	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$petalsOnWater(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
		ItemStack stack = user.getStackInHand(hand);
		if (!stack.isOf(Items.PINK_PETALS)) {
			return;
		}
		BlockHitResult water = raycast(world, user, RaycastContext.FluidHandling.SOURCE_ONLY);
		// The petals go on top of the water: at the water's surface block, aimed at from above.
		if (water.getType() != HitResult.Type.BLOCK || world.getFluidState(water.getBlockPos()).isEmpty()) {
			return;
		}
		BlockHitResult above = water.withBlockPos(water.getBlockPos().up());
		ActionResult result = ((BlockItem) stack.getItem()).useOnBlock(new ItemUsageContext(user, hand, above));
		cir.setReturnValue(new TypedActionResult<>(result, user.getStackInHand(hand)));
	}
}
