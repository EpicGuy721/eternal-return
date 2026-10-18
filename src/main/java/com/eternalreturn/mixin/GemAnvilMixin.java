package com.eternalreturn.mixin;

import com.eternalreturn.items.Gems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.screen.Property;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gems in the anvil (Gems): an item and a ruby, its inherent enchantment rerolled (decided when the result is
 * taken); an item and a sapphire, one more point of capacity. One gem is used.
 */
@Mixin(AnvilScreenHandler.class)
public abstract class GemAnvilMixin extends ForgingScreenHandler {
	@Shadow
	@Final
	private Property levelCost;
	@Shadow
	private int repairItemUsage;

	protected GemAnvilMixin(ScreenHandlerType<?> type, int syncId, PlayerInventory inventory, ScreenHandlerContext context) {
		super(type, syncId, inventory, context);
	}

	@Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$gems(CallbackInfo ci) {
		ItemStack item = this.input.getStack(0);
		ItemStack gem = this.input.getStack(1);
		// With the item gone (just taken), vanilla's own update keeps the gem count it is about to use up.
		if (item.isEmpty()) {
			return;
		}
		ItemStack result;
		if (gem.isOf(Gems.RUBY)) {
			result = Gems.withRuby(item, this.player.getWorld().getRegistryManager());
		} else if (gem.isOf(Gems.SAPPHIRE)) {
			result = Gems.withSapphire(item);
		} else {
			return;
		}
		this.output.setStack(0, result);
		this.levelCost.set(result.isEmpty() ? 0 : 1);
		this.repairItemUsage = result.isEmpty() ? 0 : 1;
		this.sendContentUpdates();
		ci.cancel();
	}

	@Inject(method = "onTakeOutput", at = @At("HEAD"))
	private void eternalreturn$reroll(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
		if (!player.getWorld().isClient && Gems.pendingReroll(stack)) {
			Gems.finishReroll(stack, player.getWorld().getRegistryManager(), player.getRandom());
		}
	}
}
