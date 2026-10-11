package com.eternalreturn.mixin.survival;

import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Aiming pink petals at water with the pond floor behind it: the click lands on the floor block, so pass it
 * on to the item's use, which places the petals on the water (PinkPetalsItemMixin), as lily pads do.
 */
@Mixin(BlockItem.class)
public abstract class PinkPetalsBlockItemMixin {
	@Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$waterFirst(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
		if (!context.getStack().isOf(Items.PINK_PETALS) || context.getPlayer() == null) {
			return;
		}
		var player = context.getPlayer();
		BlockHitResult water = context.getWorld().raycast(new RaycastContext(player.getEyePos(),
				// Looking the same way the item's use looks (Item.raycast), so both agree on where the water is.
				player.getEyePos().add(player.getRotationVector(player.getPitch(), player.getYaw()).multiply(player.getBlockInteractionRange())),
				RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.SOURCE_ONLY, player));
		// Not when the click is already on the water's surface: that is the item's use placing them (it used to stop
		// itself here, so petals almost never went on water).
		if (water.getType() == HitResult.Type.BLOCK && !context.getWorld().getFluidState(water.getBlockPos()).isEmpty()
				&& !water.getBlockPos().equals(context.getBlockPos()) && !water.getBlockPos().up().equals(context.getBlockPos())) {
			cir.setReturnValue(ActionResult.PASS);
		}
	}
}
