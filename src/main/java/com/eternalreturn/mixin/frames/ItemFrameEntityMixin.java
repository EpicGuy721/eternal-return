package com.eternalreturn.mixin.frames;

import com.eternalreturn.blocks.Frames;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dyed item frames (Frames): the colour, tracked for the client, saved, and kept on the item it drops. */
@Mixin(ItemFrameEntity.class)
public abstract class ItemFrameEntityMixin extends AbstractDecorationEntity implements Frames.Dyed {
	@Unique
	private static final TrackedData<Integer> ETERNALRETURN_COLOR = DataTracker.registerData(ItemFrameEntity.class, TrackedDataHandlerRegistry.INTEGER);

	protected ItemFrameEntityMixin(EntityType<? extends AbstractDecorationEntity> entityType, World world) {
		super(entityType, world);
	}

	@Override
	public int eternalreturn$color() {
		return this.getDataTracker().get(ETERNALRETURN_COLOR);
	}

	@Override
	public void eternalreturn$setColor(int color) {
		this.getDataTracker().set(ETERNALRETURN_COLOR, color);
	}

	@Inject(method = "initDataTracker", at = @At("TAIL"))
	private void eternalreturn$trackColor(DataTracker.Builder builder, CallbackInfo ci) {
		builder.add(ETERNALRETURN_COLOR, -1);
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void eternalreturn$saveColor(NbtCompound nbt, CallbackInfo ci) {
		if (this.eternalreturn$color() >= 0) {
			nbt.putInt("EternalReturnColor", this.eternalreturn$color());
		}
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void eternalreturn$loadColor(NbtCompound nbt, CallbackInfo ci) {
		this.eternalreturn$setColor(nbt.contains("EternalReturnColor") ? nbt.getInt("EternalReturnColor") : -1);
	}

	@Inject(method = "getAsItemStack", at = @At("RETURN"), cancellable = true)
	private void eternalreturn$coloredItem(CallbackInfoReturnable<ItemStack> cir) {
		cir.setReturnValue(Frames.colored((ItemFrameEntity) (Object) this, cir.getReturnValue()));
	}
}
