package com.eternalreturn.mixin.frames;

import com.eternalreturn.blocks.Frames;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.DecorationItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A dyed item frame item places a frame of its colour (Frames). */
@Mixin(DecorationItem.class)
public abstract class DecorationItemMixin {
	@WrapOperation(method = "useOnBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"))
	private boolean eternalreturn$color(World world, Entity entity, Operation<Boolean> original, @Local ItemStack stack) {
		if (entity instanceof ItemFrameEntity frame) {
			Frames.colorFrom(frame, stack);
		}
		return original.call(world, entity);
	}
}
