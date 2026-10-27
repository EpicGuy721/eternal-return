package com.eternalreturn.mixin.mobs;

import com.eternalreturn.mobs.PiglinTrades;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.AbstractPiglinEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A piglin's count of trades (PiglinTrades), kept with it, and which it's out of, sent to the client. */
@Mixin(PiglinEntity.class)
public abstract class PiglinTradesMixin extends AbstractPiglinEntity implements PiglinTrades.Holder {
	@Unique
	private static final TrackedData<Integer> ETERNALRETURN_TRADED_OUT = DataTracker.registerData(PiglinEntity.class, TrackedDataHandlerRegistry.INTEGER);

	@Unique
	private final PiglinTrades.Counts eternalreturn$trades = new PiglinTrades.Counts();

	protected PiglinTradesMixin(EntityType<? extends AbstractPiglinEntity> type, World world) {
		super(type, world);
	}

	@Override
	public PiglinTrades.Counts eternalreturn$trades() {
		return this.eternalreturn$trades;
	}

	@Override
	public int eternalreturn$tradedOut() {
		return this.getDataTracker().get(ETERNALRETURN_TRADED_OUT);
	}

	@Override
	public void eternalreturn$setTradedOut(int flags) {
		this.getDataTracker().set(ETERNALRETURN_TRADED_OUT, flags);
	}

	@Inject(method = "initDataTracker", at = @At("TAIL"))
	private void eternalreturn$trackTradedOut(DataTracker.Builder builder, CallbackInfo ci) {
		builder.add(ETERNALRETURN_TRADED_OUT, 0);
	}

	/** Its trades coming back in time shows on the client too. */
	@Inject(method = "mobTick", at = @At("HEAD"))
	private void eternalreturn$syncTrades(CallbackInfo ci) {
		if (this.age % 20 == 0) {
			PiglinTrades.sync((PiglinEntity) (Object) this);
		}
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void eternalreturn$writeTrades(NbtCompound nbt, CallbackInfo ci) {
		this.eternalreturn$trades.write(nbt);
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void eternalreturn$readTrades(NbtCompound nbt, CallbackInfo ci) {
		this.eternalreturn$trades.read(nbt);
		PiglinTrades.sync((PiglinEntity) (Object) this);
	}
}
