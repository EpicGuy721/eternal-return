package com.eternalreturn.mixin.mobs;

import com.eternalreturn.mobs.PiglinTrades;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A piglin's count of trades (PiglinTrades), kept with it. */
@Mixin(PiglinEntity.class)
public abstract class PiglinTradesMixin implements PiglinTrades.Holder {
	@Unique
	private final PiglinTrades.Counts eternalreturn$trades = new PiglinTrades.Counts();

	@Override
	public PiglinTrades.Counts eternalreturn$trades() {
		return this.eternalreturn$trades;
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void eternalreturn$writeTrades(NbtCompound nbt, CallbackInfo ci) {
		this.eternalreturn$trades.write(nbt);
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void eternalreturn$readTrades(NbtCompound nbt, CallbackInfo ci) {
		this.eternalreturn$trades.read(nbt);
	}
}
